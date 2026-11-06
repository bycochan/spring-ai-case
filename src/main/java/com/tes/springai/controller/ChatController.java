package com.tes.springai.controller;

import com.tes.springai.model.ChatMessage;
import com.tes.springai.model.ChatRequest;
import com.tes.springai.model.ChatResponse;
import com.tes.springai.service.ConversationAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
