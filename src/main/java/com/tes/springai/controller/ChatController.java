package com.tes.springai.controller;

import com.google.gson.Gson;
import com.tes.springai.model.ChatMessage;
import com.tes.springai.model.ChatRequest;
import com.tes.springai.model.ChatResponse;
import com.tes.springai.model.ChatStreamEvent;
import com.tes.springai.service.ConversationAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 多轮对话 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationAgentService conversationAgentService;

    private final Gson gson = new Gson();

    /**
     * 发送聊天消息
     *
     * @param request 聊天请求
     * @return ChatResponse
     */
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        log.info("收到聊天请求 - sessionId: {}, message: {}, knowledgeBaseId: {}, enableMCP: {}, agentId: {}, 附件: {} 个",
                request.getSessionId(), request.getMessage(), request.getKnowledgeBaseId(),
                request.isEnableMCP(), request.getAgentId(),
                request.getFileIds() == null ? 0 : request.getFileIds().size());

        ChatResponse response = conversationAgentService.chat(
                request.getSessionId(),
                request.getMessage(),
                request.getFileIds(),
                request.getKnowledgeBaseId(),
                request.isEnableMCP(),
                request.getAgentId()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 流式发送聊天消息（SSE）：
     * 模型每产出一个增量文本实时推送一条事件，事件 data 为 JSON，type 字段区分
     * delta/thinking/tool_call/tool_result/done/error（见 ChatStreamEvent）。
     * 校验类错误在 SSE 响应提交前同步抛出，仍走全局异常处理器返回 HTTP 错误码；
     * 流式过程中的错误在流内以 error 事件表达（响应提交后无法再改状态码）。
     *
     * @param request 聊天请求（与同步端点入参一致）
     * @return SSE 事件流
     */
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> streamMessage(@RequestBody ChatRequest request) {
        log.info("收到流式聊天请求 - sessionId: {}, message: {}, knowledgeBaseId: {}, enableMCP: {}, agentId: {}, 附件: {} 个",
                request.getSessionId(), request.getMessage(), request.getKnowledgeBaseId(),
                request.isEnableMCP(), request.getAgentId(),
                request.getFileIds() == null ? 0 : request.getFileIds().size());

        Flux<ChatStreamEvent> events = conversationAgentService.chatStream(
                request.getSessionId(),
                request.getMessage(),
                request.getFileIds(),
                request.getKnowledgeBaseId(),
                request.isEnableMCP(),
                request.getAgentId()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                // 声明反向代理不缓冲，保证 SSE 逐条下发（Nginx 等默认缓冲会破坏流式体验）
                .header("X-Accel-Buffering", "no")
                .body(events.map(event -> ServerSentEvent.builder(gson.toJson(event)).build()));
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @return 消息历史列表
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        log.info("获取会话历史 - sessionId: {}", sessionId);
        List<ChatMessage> history = conversationAgentService.getSessionHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<String> clearSession(@PathVariable String sessionId) {
        log.info("清除会话 - sessionId: {}", sessionId);
        conversationAgentService.clearSession(sessionId);
        return ResponseEntity.ok("会话已清除");
    }

    /**
     * 获取活跃会话数量
     *
     * @return 活跃会话数量
     */
    @GetMapping("/sessions/count")
    public ResponseEntity<Integer> getActiveSessionCount() {
        int count = conversationAgentService.getActiveSessionCount();
        return ResponseEntity.ok(count);
    }
}
