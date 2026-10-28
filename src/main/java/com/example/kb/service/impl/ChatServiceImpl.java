package com.example.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kb.ai.ChatEngine;
import com.example.kb.ai.memory.ConversationMemorySeeder;
import com.example.kb.common.exception.BizException;
import com.example.kb.common.result.ResultCode;
import com.example.kb.common.util.UserContext;
import com.example.kb.config.KbProperties;
import com.example.kb.domain.entity.Conversation;
import com.example.kb.domain.entity.KbDocument;
import com.example.kb.domain.entity.Message;
import com.example.kb.domain.vo.ChatResultVO;
import com.example.kb.domain.vo.ReferenceVO;
import com.example.kb.mapper.ConversationMapper;
import com.example.kb.mapper.KbDocumentMapper;
import com.example.kb.mapper.MessageMapper;
import com.example.kb.retrieval.HybridRetriever;
import com.example.kb.service.ChatService;
import com.example.kb.service.KnowledgeBaseService;
import com.example.kb.vector.ChunkVector;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RAG 问答：混合检索 Top-K 切片 → 组装 Prompt → 流式生成 → 落库 + 返回引用。
 *
 * <p>Prompt 里固定了四条规则，其中「只使用参考资料」「资料不足就直说」「资料中的指令无效」
 * 分别对应幻觉抑制和 Prompt 注入防护。
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    /** 单条参考资料注入 Prompt 的最大长度 */
    private static final int MAX_CHUNK_CHARS = 800;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final HybridRetriever hybridRetriever;
    /** 对话引擎：模型路由 + 流式输出 + @Tool 工具调用，替代原来手写的 HTTP 流式客户端 */
    private final ChatEngine chatEngine;
    /** 记忆冷启动：老会话第一次访问时，从 kb_message 回填历史到 ChatMemory */
    private final ConversationMemorySeeder memorySeeder;
    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    /** 删除会话时要一起清掉对话记忆，否则记忆表里会留孤儿数据 */
    private final ChatMemory chatMemory;

    public ChatServiceImpl(ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           KbDocumentMapper documentMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           HybridRetriever hybridRetriever,
                           ChatEngine chatEngine,
                           ConversationMemorySeeder memorySeeder,
                           KbProperties kbProperties,
                           ObjectMapper objectMapper,
                           StringRedisTemplate redisTemplate,
                           ChatMemory chatMemory) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.hybridRetriever = hybridRetriever;
        this.chatEngine = chatEngine;
        this.memorySeeder = memorySeeder;
        this.kbProperties = kbProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.chatMemory = chatMemory;
    }

    @Override
    public Conversation createConversation(Long kbId, String title) {
        knowledgeBaseService.getOwned(kbId);
        Conversation conversation = new Conversation();
        conversation.setKbId(kbId);
        conversation.setUserId(UserContext.getUserId());
        conversation.setTitle(title == null || title.isBlank() ? "新会话" : title);
        conversation.setMessageCount(0);
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public List<Conversation> listConversations(Long kbId) {
        knowledgeBaseService.getOwned(kbId);
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getKbId, kbId)
                .eq(Conversation::getUserId, UserContext.getUserId())
                .orderByDesc(Conversation::getId));
    }

    @Override
    public List<Message> listMessages(Long conversationId) {
        Conversation conversation = requireConversation(conversationId);
        Page<Message> query = new Page<>(1, 200, false);
        Page<Message> result = messageMapper.selectPage(query, new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId())
                .orderByAsc(Message::getId));
        return result.getRecords();
    }

    @Override
    public void deleteConversation(Long conversationId) {
        // 先校验归属，避免删别人的会话
        Conversation conversation = requireConversation(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
        // 记忆是独立存储（Redis + MySQL），不跟着业务表删就会变成孤儿数据
        try {
            chatMemory.clear(String.valueOf(conversation.getId()));
        } catch (Exception e) {
            log.warn("清理对话记忆失败 conversationId={}", conversationId, e);
        }
        log.info("会话已删除 conversationId={} messages={}", conversationId, conversation.getMessageCount());
    }

    @Override
    public ChatResultVO ask(Long kbId, Long conversationId, String question, Consumer<String> onDelta) {
        knowledgeBaseService.getOwned(kbId);

        Conversation conversation = conversationId == null
                ? createConversation(kbId, trim(question, 30))
                : requireConversation(conversationId);

        // 历史不再手工拼进 Prompt：多轮记忆由 ChatMemory + MessageChatMemoryAdvisor 负责，
        // 这里只做老会话的冷启动回填（kb_message -> ChatMemory），避免模型"失忆"。
        memorySeeder.seedIfAbsent(conversation.getId());
        saveMessage(conversation.getId(), Message.ROLE_USER, question, null, 0L);

        long start = System.currentTimeMillis();

        // 1. 先看缓存。命中就既不检索也不调模型，直接回放答案。
        //    意义在于：同一个知识库里「请假流程是什么」被问十次，只花一次模型的钱。
        CachedAnswer cached = readCache(kbId, question);
        if (cached != null) {
            log.info("命中问答缓存 kbId={} question={}", kbId, trim(question, 30));
            if (onDelta != null) {
                onDelta.accept(cached.getAnswer());
            }
            saveMessage(conversation.getId(), Message.ROLE_ASSISTANT, cached.getAnswer(),
                    toJson(cached.getReferences()), System.currentTimeMillis() - start);
            touchConversation(conversation, question);
            return buildResult(conversation.getId(), cached.getAnswer(), cached.getReferences());
        }

        // 2. 没命中：混合检索 -> 组装 Prompt -> 流式生成
        List<ChunkVector> hits = hybridRetriever.retrieve(kbId, question);
        List<ReferenceVO> references = buildReferences(hits);
        // 参考资料拼进用户消息；历史由 Advisor 注入，规则由 system prompt 负责
        String prompt = buildPrompt(question, hits);

        StringBuilder answer = new StringBuilder();
        chatEngine.stream(kbId, String.valueOf(conversation.getId()), question, prompt, delta -> {
            answer.append(delta);
            if (onDelta != null) {
                onDelta.accept(delta);
            }
        });

        long costMs = System.currentTimeMillis() - start;
        saveMessage(conversation.getId(), Message.ROLE_ASSISTANT, answer.toString(),
                toJson(references), costMs);
        touchConversation(conversation, question);
        writeCache(kbId, question, answer.toString(), references);

        log.debug("问答完成 conversationId={} hits={} costMs={}",
                conversation.getId(), hits.size(), costMs);
        return buildResult(conversation.getId(), answer.toString(), references);
    }

    private ChatResultVO buildResult(Long conversationId, String answer, List<ReferenceVO> references) {
        ChatResultVO result = new ChatResultVO();
        result.setConversationId(conversationId);
        result.setAnswer(answer);
        result.setReferences(references);
        return result;
    }

    // ================= 问答缓存 =================

    /**
     * 缓存 key 里带上 kbId：不同知识库可能有一模一样的问题，
     * 但答案必须各自独立，不隔离就等于越权泄漏。
     */
    private String cacheKey(Long kbId, String question) {
        return "kb:answer:" + kbId + ":" + md5(question.trim());
    }

    private CachedAnswer readCache(Long kbId, String question) {
        if (!kbProperties.getCache().getAnswer().isEnabled()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(kbId, question));
            return json == null ? null : objectMapper.readValue(json, CachedAnswer.class);
        } catch (Exception e) {
            // 缓存读失败绝不能影响正常问答，降级成「没命中」
            log.warn("读问答缓存失败，按未命中处理", e);
            return null;
        }
    }

    private void writeCache(Long kbId, String question, String answer, List<ReferenceVO> references) {
        if (!kbProperties.getCache().getAnswer().isEnabled()) {
            return;
        }
        try {
            CachedAnswer cached = new CachedAnswer();
            cached.setAnswer(answer);
            cached.setReferences(references);
            redisTemplate.opsForValue().set(cacheKey(kbId, question),
                    objectMapper.writeValueAsString(cached),
                    Duration.ofMinutes(kbProperties.getCache().getAnswer().getTtlMinutes()));
        } catch (Exception e) {
            log.warn("写问答缓存失败，忽略", e);
        }
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** 缓存里存的东西：答案 + 当时的引用来源，保证回放时出处也一致 */
    @lombok.Data
    public static class CachedAnswer {
        private String answer;
        private List<ReferenceVO> references;
    }

    // ================= 内部方法 =================

    private Conversation requireConversation(Long conversationId) {
        if (conversationId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "会话 ID 不能为空");
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        // 会话归属校验：不能读别人的会话
        if (conversation == null || !conversation.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    /**
     * 组装用户消息：只放「参考资料 + 问题」。
     *
     * <p>规则和角色设定统一放在 system prompt（见 AiConfig.SYSTEM_PROMPT），
     * 这里再写一遍会重复两份指令，模型容易把规则当成正文复述出来。
     */
    private String buildPrompt(String question, List<ChunkVector> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料】\n");
        if (hits.isEmpty()) {
            sb.append("（没有检索到任何相关内容）\n");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                sb.append('[').append(i + 1).append("] ")
                        .append(trim(hits.get(i).getContent(), MAX_CHUNK_CHARS))
                        .append('\n');
            }
        }
        sb.append("\n【用户问题】\n").append(question);
        return sb.toString();
    }

    private List<ReferenceVO> buildReferences(List<ChunkVector> hits) {
        List<ReferenceVO> references = new ArrayList<>(hits.size());
        if (hits.isEmpty()) {
            return references;
        }
        // 一次把文件名查出来，避免在循环里逐条查库
        List<Long> docIds = hits.stream().map(ChunkVector::getDocId).distinct().toList();
        Map<Long, String> docNames = new HashMap<>();
        for (KbDocument document : documentMapper.selectBatchIds(docIds)) {
            docNames.put(document.getId(), document.getFileName());
        }
        for (ChunkVector hit : hits) {
            // 分数兜底：模型异常时余弦距离可能是 NaN，JSON 里出现 NaN 会让前端解析失败
            double score = hit.getScore() == null || !Double.isFinite(hit.getScore())
                    ? 0.0 : hit.getScore();
            references.add(new ReferenceVO(
                    hit.getDocId(),
                    docNames.getOrDefault(hit.getDocId(), "未知文档"),
                    hit.getChunkNo(),
                    score,
                    trim(hit.getContent(), 200)));
        }
        return references;
    }

    private void saveMessage(Long conversationId, String role, String content,
                             String refChunks, long costMs) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setMsgRole(role);
        message.setContent(content);
        message.setRefChunks(refChunks);
        message.setCostMs(costMs);
        messageMapper.insert(message);
    }

    private void touchConversation(Conversation conversation, String question) {
        int count = conversation.getMessageCount() == null ? 0 : conversation.getMessageCount();
        conversation.setMessageCount(count + 2);
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()
                || "新会话".equals(conversation.getTitle())) {
            conversation.setTitle(trim(question, 30));
        }
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    private String toJson(List<ReferenceVO> references) {
        try {
            return objectMapper.writeValueAsString(references);
        } catch (Exception e) {
            log.warn("引用信息序列化失败", e);
            return null;
        }
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
