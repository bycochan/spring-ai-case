package com.tes.springai.controller;

import com.google.gson.Gson;
import com.tes.springai.model.workflow.Workflow;
import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工作流 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final Gson gson;

    /**
     * 创建工作流
     */
    @PostMapping
    public ResponseEntity<Workflow> createWorkflow(@RequestBody Workflow workflow) {
        log.info("创建工作流: {}", workflow.getName());
        Workflow created = workflowService.createWorkflow(workflow);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新工作流
     */
    @PutMapping("/{id}")
    public ResponseEntity<Workflow> updateWorkflow(
            @PathVariable String id,
            @RequestBody Workflow workflow) {
        log.info("更新工作流: {}", id);
        Workflow updated = workflowService.updateWorkflow(id, workflow);
        return ResponseEntity.ok(updated);
    }

    /**
     * 获取工作流
     */
    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String id) {
        log.info("获取工作流: {}", id);
        Workflow workflow = workflowService.getWorkflow(id);
        return ResponseEntity.ok(workflow);
    }

    /**
     * 获取所有工作流
     */
    @GetMapping
    public ResponseEntity<Map<String, Workflow>> getAllWorkflows() {
        log.info("获取所有工作流");
        Map<String, Workflow> workflows = workflowService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String id) {
        log.info("删除工作流: {}", id);
        workflowService.deleteWorkflow(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 执行工作流
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<WorkflowExecutionContext> executeWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> input) {
        log.info("执行工作流: {}", id);
        WorkflowExecutionContext result = workflowService.executeWorkflow(id, input);
        return ResponseEntity.ok(result);
    }

    /**
     * 测试工作流
     */
    @PostMapping("/test")
    public ResponseEntity<WorkflowExecutionContext> testWorkflow(
            @RequestBody Map<String, Object> request) {
        log.info("测试工作流");
        
        // 使用Gson转换LinkedHashMap为Workflow对象
        Object workflowObj = request.get("workflow");
        Workflow workflow = gson.fromJson(gson.toJson(workflowObj), Workflow.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) request.get("input");
        
        WorkflowExecutionContext result = workflowService.testWorkflow(workflow, input);
        return ResponseEntity.ok(result);
    }
}
