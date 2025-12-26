package com.tes.springai.config;

import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流配置
 */
@Configuration
public class WorkflowConfig {

    /**
     * 注册所有节点执行器
     */
    @Bean
    public Map<WorkflowNode.NodeType, NodeExecutor> nodeExecutors(List<NodeExecutor> executorList) {
        return executorList.stream()
                .collect(Collectors.toMap(
                        NodeExecutor::getSupportedType,
                        Function.identity()
                ));
    }
}
