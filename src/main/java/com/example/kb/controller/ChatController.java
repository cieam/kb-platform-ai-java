package com.example.kb.controller;

import com.example.kb.common.result.R;
import com.example.kb.config.AsyncConfig;
import com.example.kb.domain.dto.ChatRequest;
import com.example.kb.domain.entity.Conversation;
import com.example.kb.domain.entity.Message;
import com.example.kb.domain.vo.ChatResultVO;
import com.example.kb.common.util.UserContext;
import com.example.kb.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 问答接口。
 *
 * <p>SSE 的 payload 一律走 JSON（而不是裸字符串）：裸字符串里的换行会破坏
 * {@code data:} 分帧，客户端解析会错位。用 JSON 序列化后换行会变成 {@code \n} 转义。
 */
@Slf4j
@Tag(name = "04 问答")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ThreadPoolTaskExecutor aiExecutor;

    public ChatController(ChatService chatService,
                          @Qualifier(AsyncConfig.AI_EXECUTOR) ThreadPoolTaskExecutor aiExecutor) {
        this.chatService = chatService;
        this.aiExecutor = aiExecutor;
    }

    @Operation(summary = "新建会话")
    @PostMapping("/conversation")
    public R<Conversation> createConversation(@RequestParam Long kbId,
                                              @RequestParam(required = false) String title) {
        return R.ok(chatService.createConversation(kbId, title));
    }

    @Operation(summary = "会话列表")
    @GetMapping("/conversation")
    public R<List<Conversation>> conversations(@RequestParam Long kbId) {
        return R.ok(chatService.listConversations(kbId));
    }

    @Operation(summary = "会话消息")
    @GetMapping("/message")
    public R<List<Message>> messages(@RequestParam Long conversationId) {
        return R.ok(chatService.listMessages(conversationId));
    }

    @Operation(summary = "删除会话（连同消息与对话记忆）")
    @DeleteMapping("/conversation/{id}")
    public R<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return R.ok();
    }

    @Operation(summary = "普通问答（调试方便，等全部生成完才返回）")
    @PostMapping("/ask")
    public R<ChatResultVO> ask(@RequestBody @Valid ChatRequest request) {
        return R.ok(chatService.ask(request.getKbId(), request.getConversationId(),
                request.getQuestion(), null));
    }

    /**
     * 流式问答。事件类型：
     * <ul>
     *   <li>{@code delta} —— {"t":"增量文本"}</li>
     *   <li>{@code references} —— 引用来源数组</li>
     *   <li>{@code done} —— {"conversationId":1}</li>
     * </ul>
     * 前端用 fetch + ReadableStream 读，别用 EventSource —— 它发不了 POST body。
     */
    @Operation(summary = "流式问答（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody @Valid ChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        // UserContext 是 ThreadLocal，线程池里的线程读不到，
        // 必须用 UserContext.wrap 把登录身份带过去，否则这里会 401
        aiExecutor.execute(UserContext.wrap(() -> {
            try {
                ChatResultVO result = chatService.ask(request.getKbId(), request.getConversationId(),
                        request.getQuestion(),
                        delta -> send(emitter, "delta", Map.of("t", delta)));
                send(emitter, "references", result.getReferences());
                send(emitter, "done", Map.of("conversationId", result.getConversationId()));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答失败", e);
                emitter.completeWithError(e);
            }
        }));
        return emitter;
    }

    /** 客户端断开时 send 会抛异常，这里转成运行时异常中断本次生成 */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 连接已断开", e);
        }
    }
}
