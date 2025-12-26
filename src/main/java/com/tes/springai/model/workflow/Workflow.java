package com.tes.springai.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工作流模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {
    
    /**
     * 工作流ID
     */
    private String id;
    
    /**
     * 工作流名称
     */
    private String name;
    
    /**
     * 工作流描述
     */
    private String description;
    
    /**
     * 工作流版本
     */
    @Builder.Default
    private Integer version = 1;
    
    /**
     * 节点列表
     */
    private List<WorkflowNode> nodes;
    
    /**
     * 连线列表
     */
    private List<WorkflowEdge> edges;
    
    /**
     * 全局变量
     */
    private Map<String, Object> globalVariables;
    
    /**
     * 创建时间
     */
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;
}
