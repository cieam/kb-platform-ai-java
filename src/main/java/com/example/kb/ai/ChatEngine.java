package com.example.kb.ai;

import com.example.kb.ai.tool.KnowledgeTools;
import com.example.kb.config.KbProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话引擎：模型路由 + 流式输出 + 工具调用。
 *
 * <p>路由策略是「够用就好」：
 * 短问题、且不含复杂意图词（对比 / 分析 / 总结 / 方案…）交给本地 Ollama 小模型，
 * 其余走云端。云端按 {@link ModelRegistry} 注册的 priority 顺序降级——
 * 千问失败切 DeepSeek，再失败切 Kimi，保证单模型故障不影响可用性。
 *
 * <p>工具调用：{@link KnowledgeTools} 里带 {@code @Tool} 的方法会注册给模型，
 * 模型可以在「参考资料不够」时自己再检索一次。工具需要的 kbId / conversationId
 * 通过 {@code toolContext} 透传，不让模型自己编造参数，避免越权检索。
 */
@Slf4j
@Component
public class ChatEngine {

    /** 命中这些词说明需要更强的推理能力，不能交给小模型 */
    private static final List<String> COMPLEX_HINTS =
            List.of("对比", "比较", "分析", "总结", "方案", "设计", "为什么", "推导", "计算", "跨文档", "区别");

    private final ModelRegistry modelRegistry;
    private final KnowledgeTools knowledgeTools;
    private final KbProperties properties;
    private final MockAiClient mockAiClient;

    public ChatEngine(ModelRegistry modelRegistry,
                      KnowledgeTools knowledgeTools,
                      KbProperties properties,
                      MockAiClient mockAiClient) {
        this.modelRegistry = modelRegistry;
        this.knowledgeTools = knowledgeTools;
        this.properties = properties;
        this.mockAiClient = mockAiClient;
    }

    /**
     * 流式对话，返回完整答案。
     *
     * @param kbId           知识库 ID，透传给工具做数据隔离
     * @param conversationId 会话 ID，记忆 Advisor 靠它定位历史
     * @param question       原始问题，只用于路由判断
     * @param prompt         已经拼好参考资料的用户 Prompt
     * @param onDelta        每吐出一小段就回调一次，用于 SSE 推送
     */
    public String stream(Long kbId, String conversationId, String question,
                         String prompt, Consumer<String> onDelta) {
        // 没有任何 Key 的纯本地演示：不调外部模型，靠占位实现把链路跑通
        if (properties.getAi().mock()) {
            mockAiClient.chatStream(prompt, onDelta);
            return "";
        }

        StringBuilder sink = new StringBuilder();

        if (preferLocal(question) && modelRegistry.local() != null) {
            try {
                return doStream(modelRegistry.local(), kbId, conversationId, prompt, onDelta, sink);
            } catch (Exception e) {
                // Ollama 没启动是最常见的情况，降级到云端即可
                log.warn("本地模型调用失败，降级云端：{}", e.getMessage());
                sink.setLength(0);
            }
        }

        List<ChatClient> chain = modelRegistry.cloudChain();
        if (chain.isEmpty()) {
            throw new IllegalStateException("没有可用的云端模型，请检查 kb.ai.providers 配置");
        }
        RuntimeException lastError = null;
        for (ChatClient client : chain) {
            try {
                return doStream(client, kbId, conversationId, prompt, onDelta, sink);
            } catch (Exception e) {
                lastError = e instanceof RuntimeException re ? re : new IllegalStateException(e);
                log.warn("模型调用失败，尝试下一个：{}", e.getMessage());
                sink.setLength(0);
            }
        }
        throw new IllegalStateException("所有模型均调用失败", lastError);
    }

    /**
     * 简单问题走本地：长度短 + 不含复杂意图词。
     * 这个比例直接决定 API 成本，也是「成本降低 60%」的口径来源。
     */
    boolean preferLocal(String question) {
        KbProperties.Local local = properties.getAi().getLocal();
        if (!local.isEnabled() || question == null) {
            return false;
        }
        if (question.length() > local.getMaxQuestionLength()) {
            return false;
        }
        return COMPLEX_HINTS.stream().noneMatch(question::contains);
    }

    private String doStream(ChatClient client, Long kbId, String conversationId,
                            String prompt, Consumer<String> onDelta, StringBuilder sink) {
        Flux<String> tokens = client.prompt()
                .user(prompt)
                // 记忆 Advisor 从这里取 conversationId，自动注入历史、自动写回
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // 工具上下文：模型只能看到这些值，无法伪造其它知识库的 ID
                .toolContext(Map.of(
                        "kbId", kbId == null ? 0L : kbId,
                        "conversationId", conversationId))
                .tools(knowledgeTools)
                .stream()
                .content();

        tokens.doOnNext(chunk -> {
            sink.append(chunk);
            if (onDelta != null) {
                onDelta.accept(chunk);
            }
        }).blockLast();

        return sink.toString();
    }
}
