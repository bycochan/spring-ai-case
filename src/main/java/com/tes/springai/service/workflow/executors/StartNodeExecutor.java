package com.tes.springai.service.workflow.executors;

import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 开始节点执行器
 */
@Slf4j
@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context) {
        log.info("执行START节点: {}", node.getName());
        // 开始节点直接返回输入
        return context.getInput();
    }

    @Override
    public WorkflowNode.NodeType getSupportedType() {
        return WorkflowNode.NodeType.START;
    }
}
