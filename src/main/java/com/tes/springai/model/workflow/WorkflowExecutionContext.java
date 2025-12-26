package com.tes.springai.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流执行上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionContext {
    
    /**
     * 执行ID
     */
    private String executionId;
    
    /**
     * 工作流ID
     */
    private String workflowId;
    
    /**
     * 输入参数
     */
    private Map<String, Object> input;
    
    /**
     * 节点执行结果（节点ID -> 输出数据）
     */
    @Builder.Default
    private Map<String, Object> nodeOutputs = new HashMap<>();
    
    /**
     * 全局变量
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    
    /**
     * 当前节点ID
     */
    private String currentNodeId;
    
    /**
     * 执行状态
     */
    private ExecutionStatus status;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 开始时间
     */
    private Long startTime;
    
    /**
     * 结束时间
     */
    private Long endTime;
    
    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        RUNNING,    // 运行中
        SUCCESS,    // 成功
        FAILED,     // 失败
        CANCELLED   // 已取消
    }
    
    /**
     * 获取节点的输出数据
     */
    public Object getNodeOutput(String nodeId) {
        return nodeOutputs.get(nodeId);
    }
    
    /**
     * 设置节点的输出数据
     */
    public void setNodeOutput(String nodeId, Object output) {
        nodeOutputs.put(nodeId, output);
    }
}
