package com.tes.springai.model;

import lombok.Data;

import java.util.List;

/**
 * 聊天请求模型
 */
@Data
public class ChatRequest {
    
    /**
     * 会话ID，用于标识同一会话
     */
    private String sessionId;
    
    /**
     * 用户消息
     */
    private String message;
    
    /**
     * 知识库ID（可选）
     */
    private String knowledgeBaseId;
    
    /**
     * 是否启用 MCP 工具（可选）
     */
    private boolean enableMCP;
    
    /**
     * Agent ID（可选，默认使用默认 Agent）
     */
    private String agentId;

    /**
     * 附件 fileId 列表（可选，图片与文档共用额度，单条消息最多 5 个）
     */
    private List<String> fileIds;
}
