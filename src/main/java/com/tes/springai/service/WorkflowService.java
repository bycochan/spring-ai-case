package com.tes.springai.service;

import com.tes.springai.model.workflow.Workflow;
import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.service.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowEngine workflowEngine;
    
    // 工作流存储（内存存储，生产环境应使用数据库）
    private final Map<String, Workflow> workflows = new ConcurrentHashMap<>();

    /**
     * 创建工作流
     */
    public Workflow createWorkflow(Workflow workflow) {
        if (workflow.getId() == null || workflow.getId().isEmpty()) {
            workflow.setId(UUID.randomUUID().toString());
        }
        
        long now = System.currentTimeMillis();
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        
        workflows.put(workflow.getId(), workflow);
        log.info("创建工作流: {} ({})", workflow.getName(), workflow.getId());
        
        return workflow;
    }

    /**
     * 更新工作流
     */
    public Workflow updateWorkflow(String id, Workflow workflow) {
        Workflow existing = workflows.get(id);
        if (existing == null) {
            throw new RuntimeException("工作流不存在: " + id);
        }
        
        workflow.setId(id);
        workflow.setCreatedAt(existing.getCreatedAt());
        workflow.setUpdatedAt(System.currentTimeMillis());
        
        workflows.put(id, workflow);
        log.info("更新工作流: {} ({})", workflow.getName(), id);
        
        return workflow;
    }

    /**
     * 获取工作流
     */
    public Workflow getWorkflow(String id) {
        Workflow workflow = workflows.get(id);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + id);
        }
        return workflow;
    }

    /**
     * 获取所有工作流
     */
    public Map<String, Workflow> getAllWorkflows() {
        return new HashMap<>(workflows);
    }

    /**
     * 删除工作流
     */
    public void deleteWorkflow(String id) {
        workflows.remove(id);
        log.info("删除工作流: {}", id);
    }

    /**
     * 执行工作流
     */
    public WorkflowExecutionContext executeWorkflow(String workflowId, Map<String, Object> input) {
        Workflow workflow = getWorkflow(workflowId);
        log.info("开始执行工作流: {} ({})", workflow.getName(), workflowId);
        
        return workflowEngine.execute(workflow, input);
    }

    /**
     * 测试工作流
     */
    public WorkflowExecutionContext testWorkflow(Workflow workflow, Map<String, Object> input) {
        log.info("测试工作流: {}", workflow.getName());
        return workflowEngine.execute(workflow, input);
    }
}
