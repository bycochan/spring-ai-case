package com.tes.springai.service.workflow;

import com.jayway.jsonpath.JsonPath;
import com.tes.springai.model.workflow.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final Map<WorkflowNode.NodeType, NodeExecutor> executors;

    /**
     * 执行工作流
     */
    public WorkflowExecutionContext execute(Workflow workflow, Map<String, Object> input) {
        // 创建执行上下文
        WorkflowExecutionContext context = WorkflowExecutionContext.builder()
                .executionId(UUID.randomUUID().toString())
                .workflowId(workflow.getId())
                .input(input)
                .status(WorkflowExecutionContext.ExecutionStatus.RUNNING)
                .startTime(System.currentTimeMillis())
                .variables(workflow.getGlobalVariables() != null ? 
                    new HashMap<>(workflow.getGlobalVariables()) : new HashMap<>())
                .build();

        try {
            // 查找开始节点
            WorkflowNode startNode = findStartNode(workflow);
            if (startNode == null) {
                throw new RuntimeException("未找到开始节点");
            }

            // 执行工作流
            executeNode(workflow, startNode, context);

            context.setStatus(WorkflowExecutionContext.ExecutionStatus.SUCCESS);
        } catch (Exception e) {
            log.error("工作流执行失败: {}", e.getMessage(), e);
            context.setStatus(WorkflowExecutionContext.ExecutionStatus.FAILED);
            context.setError(e.getMessage());
        } finally {
            context.setEndTime(System.currentTimeMillis());
        }

        return context;
    }

    /**
     * 执行节点
     */
    private void executeNode(Workflow workflow, WorkflowNode node, WorkflowExecutionContext context) {
        log.info("执行节点: {} ({})", node.getName(), node.getType());
        context.setCurrentNodeId(node.getId());

        // 结束节点，直接返回
        if (node.getType() == WorkflowNode.NodeType.END) {
            return;
        }

        // 准备节点输入
        Map<String, Object> nodeInput = prepareNodeInput(node, context);

        // 执行节点
        NodeExecutor executor = executors.get(node.getType());
        if (executor == null) {
            throw new RuntimeException("未找到节点执行器: " + node.getType());
        }

        Object nodeOutput = executor.execute(node, nodeInput, context);
        
        // 保存节点输出
        context.setNodeOutput(node.getId(), nodeOutput);

        // 查找下一个节点
        List<WorkflowNode> nextNodes = findNextNodes(workflow, node, nodeOutput, context);
        
        // 执行下一个节点
        for (WorkflowNode nextNode : nextNodes) {
            executeNode(workflow, nextNode, context);
        }
    }

    /**
     * 准备节点输入
     */
    private Map<String, Object> prepareNodeInput(WorkflowNode node, WorkflowExecutionContext context) {
        Map<String, Object> input = new HashMap<>();

        if (node.getInputMapping() == null || node.getInputMapping().isEmpty()) {
            return input;
        }

        // 根据输入映射提取参数
        for (Map.Entry<String, String> entry : node.getInputMapping().entrySet()) {
            String paramName = entry.getKey();
            String jsonPath = entry.getValue();

            try {
                Object value = extractValue(jsonPath, context);
                input.put(paramName, value);
            } catch (Exception e) {
                log.warn("提取参数失败: {} = {}, error: {}", paramName, jsonPath, e.getMessage());
            }
        }

        return input;
    }

    /**
     * 提取值（支持JSONPath）
     */
    private Object extractValue(String path, WorkflowExecutionContext context) {
        // 处理特殊路径
        if (path.startsWith("$.input.")) {
            // 从输入参数中提取
            String jsonPathExpr = path.substring(2); // 移除 "$."
            return JsonPath.read(context.getInput(), jsonPathExpr);
        } else if (path.startsWith("$.nodes.")) {
            // 从节点输出中提取: $.nodes.nodeId.output.xxx
            String[] parts = path.split("\\.", 4);
            if (parts.length >= 3) {
                String nodeId = parts[2];
                Object nodeOutput = context.getNodeOutput(nodeId);
                if (nodeOutput == null) {
                    return null;
                }
                
                if (parts.length == 4) {
                    // 提取具体字段
                    String fieldPath = "$." + parts[3];
                    return JsonPath.read(nodeOutput, fieldPath);
                } else {
                    return nodeOutput;
                }
            }
        } else if (path.startsWith("$.variables.")) {
            // 从全局变量中提取
            String varName = path.substring("$.variables.".length());
            return context.getVariables().get(varName);
        }

        return path; // 直接返回字符串
    }

    /**
     * 查找开始节点
     */
    private WorkflowNode findStartNode(Workflow workflow) {
        return workflow.getNodes().stream()
                .filter(node -> node.getType() == WorkflowNode.NodeType.START)
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找下一个节点
     */
    private List<WorkflowNode> findNextNodes(Workflow workflow, WorkflowNode currentNode, 
                                             Object nodeOutput, WorkflowExecutionContext context) {
        List<WorkflowNode> nextNodes = new ArrayList<>();

        // 查找所有从当前节点出发的边
        List<WorkflowEdge> outgoingEdges = workflow.getEdges().stream()
                .filter(edge -> edge.getSource().equals(currentNode.getId()))
                .collect(Collectors.toList());

        for (WorkflowEdge edge : outgoingEdges) {
            // IF节点需要判断条件
            if (currentNode.getType() == WorkflowNode.NodeType.IF) {
                if (!evaluateCondition(edge.getCondition(), nodeOutput)) {
                    continue;
                }
            }

            // 查找目标节点
            WorkflowNode targetNode = workflow.getNodes().stream()
                    .filter(node -> node.getId().equals(edge.getTarget()))
                    .findFirst()
                    .orElse(null);

            if (targetNode != null) {
                nextNodes.add(targetNode);
            }
        }

        return nextNodes;
    }

    /**
     * 评估条件（简单实现）
     */
    private boolean evaluateCondition(String condition, Object value) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        // 简单的条件判断：true/false
        if ("true".equalsIgnoreCase(condition)) {
            return Boolean.TRUE.equals(value);
        } else if ("false".equalsIgnoreCase(condition)) {
            return Boolean.FALSE.equals(value);
        }

        return true;
    }
}
