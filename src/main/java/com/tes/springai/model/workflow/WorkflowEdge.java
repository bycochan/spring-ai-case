package com.tes.springai.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流连线模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {
    
    /**
     * 连线ID
     */
    private String id;
    
    /**
     * 源节点ID
     */
    private String source;
    
    /**
     * 目标节点ID
     */
    private String target;
    
    /**
     * 源节点的输出端口（可选）
     */
    private String sourceHandle;
    
    /**
     * 目标节点的输入端口（可选）
     */
    private String targetHandle;
    
    /**
     * 连线条件（用于IF节点的分支）
     */
    private String condition;
    
    /**
     * 连线标签
     */
    private String label;
}
