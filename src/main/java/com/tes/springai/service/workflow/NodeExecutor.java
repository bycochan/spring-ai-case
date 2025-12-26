package com.tes.springai.service.workflow;

import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;

import java.util.Map;

/**
 * 节点执行器接口
 */
public interface NodeExecutor {
    
    /**
     * 执行节点
     * @param node 节点配置
     * @param input 输入参数
     * @param context 执行上下文
     * @return 节点输出
     */
    Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context);
    
    /**
     * 获取支持的节点类型
     */
    WorkflowNode.NodeType getSupportedType();
}
