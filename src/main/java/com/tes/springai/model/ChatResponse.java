package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * AI回复消息
     */
    private String message;
    
    /**
     * 当前会话的对话轮数
     */
    private Integer conversationRound;
    
    /**
     * 开场白（仅新对话首次返回）
     */
    private String welcomeMessage;
    
    /**
     * 使用的 Agent ID
     */
    private String agentId;
}
