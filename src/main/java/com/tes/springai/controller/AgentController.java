package com.tes.springai.controller;

import com.tes.springai.model.Agent;
import com.tes.springai.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 管理 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ChatModel chatModel;

    /**
     * 获取所有 Agent
     */
    @GetMapping
    public ResponseEntity<List<Agent>> getAllAgents() {
        log.info("获取所有 Agent");
        List<Agent> agents = agentService.getAllAgents();
        return ResponseEntity.ok(agents);
    }

    /**
     * 根据 ID 获取 Agent
     */
    @GetMapping("/{id}")
    public ResponseEntity<Agent> getAgentById(@PathVariable String id) {
        log.info("获取 Agent: {}", id);
        try {
            Agent agent = agentService.getAgentById(id);
            return ResponseEntity.ok(agent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 创建 Agent
     */
    @PostMapping
    public ResponseEntity<Agent> createAgent(@RequestBody Agent agent) {
        log.info("创建 Agent: {}", agent.getName());
        Agent created = agentService.createAgent(agent);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    public ResponseEntity<Agent> updateAgent(@PathVariable String id, @RequestBody Agent agent) {
        log.info("更新 Agent: {}", id);
        try {
            Agent updated = agentService.updateAgent(id, agent);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("更新 Agent 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAgent(@PathVariable String id) {
        log.info("删除 Agent: {}", id);
        try {
            agentService.deleteAgent(id);
            return ResponseEntity.ok("Agent 删除成功");
        } catch (IllegalArgumentException e) {
            log.error("删除 Agent 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 根据 name/description 生成系统提示词与开场白（用于前端智能生成）
     * 使用大模型智能生成
     * 请求体: { "name": "...", "description": "..." }
     * 返回: { "systemPrompt": "...", "welcomeMessage": "..." }
     */
    @PostMapping("/generate-prompt")
    public ResponseEntity<Map<String, String>> generatePrompt(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        String description = body.getOrDefault("description", "").trim();
        log.info("使用大模型生成提示词 name='{}' description='{}'", name, description);

        try {
            // 构建提示让大模型生成
            String userPrompt = buildGenerationPrompt(name, description);
            
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个专业的 AI Prompt 工程师，擅长为不同类型的 AI Agent 设计系统提示词和开场白。"));
            messages.add(new UserMessage(userPrompt));
            
            Prompt prompt = new Prompt(messages);
            String response = chatModel.call(prompt).getResult().getOutput().getContent();
            
            log.info("大模型生成结果: {}", response);
            
            // 解析返回的 JSON 格式
            Map<String, String> result = parseGeneratedPrompts(response);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("大模型生成提示词失败，使用默认模板", e);
            // 降级到模板生成
            return ResponseEntity.ok(generatePromptByTemplate(name, description));
        }
    }
    
    /**
     * 构建用于生成提示词的 prompt
     */
    private String buildGenerationPrompt(String name, String description) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下 AI Agent 生成专业的系统提示词(systemPrompt)和友好的开场白(welcomeMessage)。\n\n");
        
        if (!name.isEmpty() && !description.isEmpty()) {
            prompt.append("Agent 名称: ").append(name).append("\n");
            prompt.append("Agent 描述: ").append(description).append("\n");
        } else if (!name.isEmpty()) {
            prompt.append("Agent 名称: ").append(name).append("\n");
        } else if (!description.isEmpty()) {
            prompt.append("Agent 描述: ").append(description).append("\n");
        } else {
            prompt.append("这是一个通用的 AI 助手\n");
        }
        
        prompt.append("\n要求：\n");
        prompt.append("1. systemPrompt 要清晰定义 Agent 的角色、能力范围和回答风格\n");
        prompt.append("2. systemPrompt 应该在 100-200 字之间，专业且具体\n");
        prompt.append("3. welcomeMessage 要简洁友好，在 30-50 字之间\n");
        prompt.append("4. 请以 JSON 格式返回，格式如下：\n");
        prompt.append("{\n");
        prompt.append("  \"systemPrompt\": \"这里是系统提示词\",\n");
        prompt.append("  \"welcomeMessage\": \"这里是开场白\"\n");
        prompt.append("}\n");
        prompt.append("\n请只返回 JSON，不要添加其他解释。");
        
        return prompt.toString();
    }
    
    /**
     * 解析大模型生成的 JSON 格式提示词
     */
    private Map<String, String> parseGeneratedPrompts(String response) {
        try {
            // 尝试提取 JSON 部分
            String json = response.trim();
            
            // 如果包含 markdown 代码块，提取其中的 JSON
            if (json.contains("```")) {
                Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
                Matcher matcher = pattern.matcher(json);
                if (matcher.find()) {
                    json = matcher.group(1).trim();
                }
            }
            
            // 简单的 JSON 解析（提取 systemPrompt 和 welcomeMessage）
            String systemPrompt = extractJsonValue(json, "systemPrompt");
            String welcomeMessage = extractJsonValue(json, "welcomeMessage");
            
            if (systemPrompt != null && welcomeMessage != null) {
                return Map.of(
                    "systemPrompt", systemPrompt,
                    "welcomeMessage", welcomeMessage
                );
            }
        } catch (Exception e) {
            log.warn("解析生成的提示词失败", e);
        }
        
        throw new RuntimeException("无法解析生成的提示词");
    }
    
    /**
     * 从 JSON 字符串中提取指定字段的值
     */
    private String extractJsonValue(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 模板方式生成提示词（降级方案）
     */
    private Map<String, String> generatePromptByTemplate(String name, String description) {
        String systemPrompt;
        String welcomeMessage;

        if (name.isEmpty() && description.isEmpty()) {
            systemPrompt = "你是一个高级的 AI 助手，擅长通用任务，回答要简洁、专业、友好。";
            welcomeMessage = "你好！我是你的 AI 助手，很高兴为你提供帮助。请告诉我你想要做什么。";
        } else if (!name.isEmpty() && description.isEmpty()) {
            systemPrompt = String.format("你是\"%s\"，一位专业的 AI 助手。请根据用户意图提供准确、礼貌且实用的回复。", name);
            welcomeMessage = String.format("你好，我是%s，可以帮你完成多种任务。请告诉我当前需要我做什么。", name);
        } else if (name.isEmpty() && !description.isEmpty()) {
            systemPrompt = String.format("你是一位专注于%s的 AI 助手，回答要结合领域知识，语言通顺易懂。", description);
            welcomeMessage = String.format("你好！我可以帮助你处理与%s相关的问题，请告诉我你的需求。", description);
        } else {
            systemPrompt = String.format("你是\"%s\"，专注于%s的 AI 助手。请在回答中体现专业性、清晰度与用户引导性，必要时给出示例和步骤。", name, description);
            welcomeMessage = String.format("你好，我是%s，我专注于%s。有什么我可以帮你处理的事情吗？", name, description);
        }

        return Map.of(
            "systemPrompt", systemPrompt,
            "welcomeMessage", welcomeMessage
        );

    }
    
    /**
     * 从Workflow发布为Agent
     * 请求体: { "workflowId": "...", "name": "...", "description": "...", "type": "SINGLE_TURN" | "MULTI_TURN" }
     */
    @PostMapping("/publish-from-workflow")
    public ResponseEntity<Agent> publishFromWorkflow(@RequestBody Map<String, Object> request) {
        String workflowId = (String) request.get("workflowId");
        String name = (String) request.get("name");
        String description = (String) request.getOrDefault("description", "");
        String typeStr = (String) request.getOrDefault("type", "SINGLE_TURN");
        
        log.info("从Workflow发布Agent: workflowId={}, name={}, type={}", workflowId, name, typeStr);
        
        try {
            // 构建Agent对象
            Agent.AgentType agentType = Agent.AgentType.valueOf(typeStr);
            
            Agent agent = Agent.builder()
                .name(name)
                .description(description)
                .workflowId(workflowId)
                .type(agentType)
                .status(Agent.AgentStatus.ACTIVE)
                .avatar("🤖") // 默认机器人emoji
                .createdAt(System.currentTimeMillis())
                .build();
            
            // 创建Agent
            Agent created = agentService.createAgent(agent);
            
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("从Workflow发布Agent失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
