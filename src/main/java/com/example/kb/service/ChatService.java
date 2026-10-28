package com.example.kb.service;

import com.example.kb.domain.entity.Conversation;
import com.example.kb.domain.entity.Message;
import com.example.kb.domain.vo.ChatResultVO;

import java.util.List;
import java.util.function.Consumer;

public interface ChatService {

    Conversation createConversation(Long kbId, String title);

    List<Conversation> listConversations(Long kbId);

    List<Message> listMessages(Long conversationId);

    /**
     * 问一次。生成过程中的增量内容会回调 onDelta，onDelta 传 null 就是非流式。
     */
    ChatResultVO ask(Long kbId, Long conversationId, String question, Consumer<String> onDelta);
}
