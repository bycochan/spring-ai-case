package com.tes.springai.service.workflow.executors;

import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环节点执行器
 */
@Slf4j
@Component
public class LoopNodeExecutor implements NodeExecutor {

    @Override
    public Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context) {
        log.info("执行LOOP节点: {}", node.getName());

        // 获取配置
        Map<String, Object> config = node.getConfig();
        String loopType = (String) config.getOrDefault("loopType", "forEach");
        String arrayPath = (String) config.get("arrayPath");
        Integer maxIterations = config.get("maxIterations") != null ? 
            ((Number) config.get("maxIterations")).intValue() : 100;

        try {
            List<Object> results = new ArrayList<>();
            
            if ("forEach".equals(loopType)) {
                // 遍历数组
                Object arrayValue = input.get(arrayPath);
                if (arrayValue instanceof List) {
                    List<?> array = (List<?>) arrayValue;
                    for (int i = 0; i < Math.min(array.size(), maxIterations); i++) {
                        Object item = array.get(i);
                        Map<String, Object> itemContext = new HashMap<>(input);
                        itemContext.put("item", item);
                        itemContext.put("index", i);
                        results.add(itemContext);
                    }
                }
            } else if ("while".equals(loopType)) {
                // While循环（简化实现）
                String condition = (String) config.get("condition");
                int iterations = 0;
                while (iterations < maxIterations) {
                    // 这里需要实现条件判断逻辑
                    iterations++;
                    results.add(Map.of("iteration", iterations));
                }
            }

            Map<String, Object> output = new HashMap<>();
            output.put("iterations", results.size());
            output.put("results", results);

            return output;
        } catch (Exception e) {
            log.error("LOOP节点执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("LOOP节点执行失败: " + e.getMessage());
        }
    }

    @Override
    public WorkflowNode.NodeType getSupportedType() {
        return WorkflowNode.NodeType.LOOP;
    }
}
