package com.example.kb.ai.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 持久化用的「轻量消息」。
 *
 * <p>为什么不直接对 Spring AI 的 Message 做序列化？
 * 因为 AssistantMessage 里可能带 ToolCall、Media 等复杂结构，Kryo 需要额外注册大量类，
 * 升级 Spring AI 版本时很容易炸。对话记忆真正需要的只是「角色 + 文本」，
 * 所以这里转换成扁平结构再序列化，稳定且体积小。
 *
 * <p>代价是工具调用记录不入记忆——这类信息本来也不该回灌给模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredTurn {

    /** USER / ASSISTANT / SYSTEM */
    private String role;
    private String content;

    public static List<StoredTurn> from(List<Message> messages) {
        List<StoredTurn> turns = new ArrayList<>(messages == null ? 0 : messages.size());
        if (messages == null) {
            return turns;
        }
        for (Message message : messages) {
            String role = roleOf(message.getMessageType());
            String text = message.getText();
            if (role == null || text == null || text.isBlank()) {
                continue;
            }
            turns.add(new StoredTurn(role, text));
        }
        return turns;
    }

    public Message toMessage() {
        if (content == null) {
            return null;
        }
        return switch (role) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> null;
        };
    }

    private static String roleOf(MessageType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case USER -> "USER";
            case ASSISTANT -> "ASSISTANT";
            case SYSTEM -> "SYSTEM";
            // TOOL 消息不回灌，避免把工具原始返回当成对话内容
            default -> null;
        };
    }
}
