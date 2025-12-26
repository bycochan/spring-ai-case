package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    
    /**
     * Agent ID
     */
    private String id;
    
    /**
     * Agent 名称
     */
    private String name;
    
    /**
     * Agent 描述
     */
    private String description;
    
    /**
     * Agent 头像（emoji 或图标）
     */
    private String avatar;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 开场白
     */
    private String welcomeMessage;
    
    /**
     * 是否启用 MCP 工具
     */
    @Builder.Default
    private boolean enableMCP = false;
    
    /**
     * 关联的知识库 ID
     */
    private String knowledgeBaseId;
    
    /**
     * 是否为默认 Agent
     */
    @Builder.Default
    private boolean isDefault = false;
    
    /**
     * 工作流ID（如果Agent基于工作流创建）
     */
    private String workflowId;
    
    /**
     * Agent类型：SINGLE_TURN(单轮对话) / MULTI_TURN(多轮对话)
     */
    @Builder.Default
    private AgentType type = AgentType.MULTI_TURN;
    
    /**
     * Agent状态
     */
    @Builder.Default
    private AgentStatus status = AgentStatus.ACTIVE;
    
    /**
     * 创建时间
     */
    private Long createdAt;
    
    /**
     * Agent类型枚举
     */
    public enum AgentType {
        SINGLE_TURN,  // 单轮对话
        MULTI_TURN    // 多轮对话
    }
    
    /**
     * Agent状态枚举
     */
    public enum AgentStatus {
        ACTIVE,    // 活跃
        INACTIVE,  // 停用
        ARCHIVED   // 归档
    }
}
