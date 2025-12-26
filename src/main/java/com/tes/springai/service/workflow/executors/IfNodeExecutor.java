package com.tes.springai.service.workflow.executors;

import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IF条件节点执行器
 */
@Slf4j
@Component
public class IfNodeExecutor implements NodeExecutor {

    @Override
    public Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context) {
        log.info("执行IF节点: {}", node.getName());

        // 获取配置
        Map<String, Object> config = node.getConfig();
        String condition = (String) config.get("condition");
        String leftOperand = (String) config.get("leftOperand");
        String operator = (String) config.get("operator");
        String rightOperand = (String) config.get("rightOperand");

        try {
            // 获取左操作数的值
            Object leftValue = input.get(leftOperand);
            
            // 评估条件
            boolean result = evaluateCondition(leftValue, operator, rightOperand);
            
            log.info("IF节点条件评估结果: {} {} {} = {}", 
                leftValue, operator, rightOperand, result);

            return result;
        } catch (Exception e) {
            log.error("IF节点执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("IF节点执行失败: " + e.getMessage());
        }
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(Object leftValue, String operator, String rightOperand) {
        if (leftValue == null) {
            return "==".equals(operator) && "null".equals(rightOperand);
        }

        switch (operator) {
            case "==":
                return leftValue.toString().equals(rightOperand);
            case "!=":
                return !leftValue.toString().equals(rightOperand);
            case ">":
                return compareNumbers(leftValue, rightOperand) > 0;
            case "<":
                return compareNumbers(leftValue, rightOperand) < 0;
            case ">=":
                return compareNumbers(leftValue, rightOperand) >= 0;
            case "<=":
                return compareNumbers(leftValue, rightOperand) <= 0;
            case "contains":
                return leftValue.toString().contains(rightOperand);
            case "startsWith":
                return leftValue.toString().startsWith(rightOperand);
            case "endsWith":
                return leftValue.toString().endsWith(rightOperand);
            default:
                throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
    }

    /**
     * 比较数字
     */
    private int compareNumbers(Object left, String right) {
        try {
            double leftNum = Double.parseDouble(left.toString());
            double rightNum = Double.parseDouble(right);
            return Double.compare(leftNum, rightNum);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法比较非数字值");
        }
    }

    @Override
    public WorkflowNode.NodeType getSupportedType() {
        return WorkflowNode.NodeType.IF;
    }
}
