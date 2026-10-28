package com.example.kb.ai.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话记忆序列化的往返测试。
 *
 * <p>这条链路是「服务重启后上下文不丢」的核心：
 * Message → StoredTurn → Kryo bytes → StoredTurn → Message，
 * 任何一步丢了角色或文本，恢复出来的历史就会错位。
 */
class KryoCodecTest {

    private final KryoCodec codec = new KryoCodec();

    @Test
    void 编解码后角色与内容保持一致() {
        List<Message> messages = List.of(
                new UserMessage("年假有几天？"),
                new org.springframework.ai.chat.messages.AssistantMessage("年假为 10 天 [1]"));

        byte[] payload = codec.encode(StoredTurn.from(messages));
        List<StoredTurn> decoded = codec.decode(payload);

        assertEquals(2, decoded.size());
        assertEquals("USER", decoded.get(0).getRole());
        assertEquals("年假有几天？", decoded.get(0).getContent());
        assertEquals("ASSISTANT", decoded.get(1).getRole());
        assertEquals("年假为 10 天 [1]", decoded.get(1).getContent());
    }

    @Test
    void 空历史编解码不报错() {
        byte[] payload = codec.encode(List.of());
        assertTrue(codec.decode(payload).isEmpty());
    }

    @Test
    void 解码非法字节流时返回空列表而不是抛异常() {
        // 记忆是旁路数据，读坏了也不能把问答主链路带崩
        assertTrue(codec.decode(new byte[0]).isEmpty());
    }
}
