package com.example.kb.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.config.KbProperties;
import com.example.kb.domain.entity.Message;
import com.example.kb.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆冷启动回填。
 *
 * <p>问题背景：对话记忆是最近才加的能力，而 kb_message 表里已经有大量历史对话。
 * 用户切回一个老会话时，如果记忆为空，模型就"失忆"了，追问「那第二条呢」会答不上来。
 *
 * <p>做法：第一次访问某个会话时，从 kb_message 取最近 N 条回填进 ChatMemory。
 * 只做一次——回填后记忆非空，后续读到的就是持久化里的内容。
 */
@Slf4j
@Component
public class ConversationMemorySeeder {

    private final MessageMapper messageMapper;
    private final ChatMemory chatMemory;
    private final KbProperties properties;

    public ConversationMemorySeeder(MessageMapper messageMapper,
                                    ChatMemory chatMemory,
                                    KbProperties properties) {
        this.messageMapper = messageMapper;
        this.chatMemory = chatMemory;
        this.properties = properties;
    }

    public void seedIfAbsent(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        String memoryId = String.valueOf(conversationId);
        try {
            if (!chatMemory.get(memoryId).isEmpty()) {
                return;
            }
            int limit = Math.max(0, properties.getMemory().getSeedMessages());
            if (limit == 0) {
                return;
            }
            List<Message> records = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId)
                    .orderByDesc(Message::getId)
                    .last("LIMIT " + limit));
            if (records == null || records.isEmpty()) {
                return;
            }
            Collections.reverse(records);

            List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>(records.size());
            for (Message record : records) {
                if (record.getContent() == null || record.getContent().isBlank()) {
                    continue;
                }
                history.add("USER".equals(record.getMsgRole())
                        ? new org.springframework.ai.chat.messages.UserMessage(record.getContent())
                        : new org.springframework.ai.chat.messages.AssistantMessage(record.getContent()));
            }
            if (!history.isEmpty()) {
                chatMemory.add(memoryId, history);
                log.debug("回填历史对话 conversationId={} size={}", conversationId, history.size());
            }
        } catch (Exception e) {
            // 回填失败不影响本次问答，只是模型少了一点上下文
            log.warn("回填对话记忆失败 conversationId={}", conversationId, e);
        }
    }
}
