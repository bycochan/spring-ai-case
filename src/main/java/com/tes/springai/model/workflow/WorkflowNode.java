package com.tes.springai.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工作流节点模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {
    
    /**
     * 节点ID
     */
    private String id;
    
    /**
     * 节点类型：LLM、HTTP、IF、LOOP
     */
    private NodeType type;
    
    /**
     * 节点名称
     */
    private String name;
    
    /**
     * 节点描述
     */
    private String description;
    
    /**
     * 节点位置信息（用于前端展示）
     */
    private Position position;
    
    /**
     * 节点配置参数
     */
    private Map<String, Object> config;
    
    /**
     * 输入参数映射（支持JSONPath）
     */
    private Map<String, String> inputMapping;
    
    /**
     * 输出参数映射
     */
    private Map<String, String> outputMapping;
    
    /**
     * 节点类型枚举
     */
    public enum NodeType {
        LLM,      // 大模型节点
        HTTP,     // HTTP请求节点
        IF,       // 条件判断节点
        LOOP,     // 循环节点
        START,    // 开始节点
        END       // 结束节点
    }
    
    /**
     * 节点位置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private Double x;
        private Double y;
    }
}
