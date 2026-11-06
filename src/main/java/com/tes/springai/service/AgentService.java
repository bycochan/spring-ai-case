package com.tes.springai.service;

import com.tes.springai.model.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 管理服务
 */
@Slf4j
@Service
public class AgentService {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 创建默认 Agent
        createDefaultAgent();
    }

    /**
     * 创建默认 Agent
     */
    private void createDefaultAgent() {
        Agent defaultAgent = Agent.builder()
                .id("default")
                .name("通用助手")
                .description("一个友好、专业的通用 AI 助手")
                .avatar("🤖")
                .systemPrompt("你是一个智能助手，能够进行多轮对话。你会记住之前对话的内容，" +
                        "并根据上下文提供连贯的回答。请保持友好、专业的态度。")
                .welcomeMessage("你好！我是你的智能助手，有什么可以帮助你的吗？")
                .enableMCP(false)
                .enableImageUpload(true)
                .isDefault(true)
                .createdAt(System.currentTimeMillis())
                .build();
        agents.put(defaultAgent.getId(), defaultAgent);

        // 创建更多预设 Agent
        createPresetAgent("code-assistant", "代码助手", "🧑‍💻",
                "专注于编程和技术问题的 AI 助手",
                "你是一个专业的编程助手，精通多种编程语言和技术栈。" +
                "你能帮助用户解决代码问题、优化代码、解释技术概念，并提供最佳实践建议。" +
                "请用清晰、简洁的方式回答，必要时提供代码示例。",
                "你好！我是你的代码助手，可以帮你解决编程问题、审查代码或解释技术概念。",
                false);

        createPresetAgent("writing-assistant", "写作助手", "✍️",
                "帮助你撰写和优化文本内容",
                "你是一个专业的写作助手，擅长各类文体的创作和润色。" +
                "你能帮助用户撰写文章、改进文笔、修正语法错误，并提供创意建议。" +
                "请注意保持文本的连贯性和可读性。",
                "你好！我是你的写作助手，可以帮你创作内容、润色文字或提供写作建议。",
                false);

        createPresetAgent("data-analyst", "数据分析师", "📊",
                "专业的数据分析和可视化助手",
                "你是一个专业的数据分析师，精通数据分析、统计学和可视化。" +
                "你能帮助用户理解数据、发现模式、进行统计分析，并提供数据驱动的洞察。" +
                "请用清晰的语言解释复杂的数据概念。",
                "你好！我是你的数据分析师，可以帮你分析数据、生成报告或解答统计问题。",
                false);

        createPresetAgent("mcp-agent", "工具专家", "🔧",
                "能够调用各种 MCP 工具的智能助手",
                "你是一个能够使用各种工具的智能助手。" +
                "当用户需要执行特定任务时，你可以调用相应的 MCP 工具来完成。" +
                "如果需要调用工具，请以 JSON 格式返回：{\"tool\": \"工具名称\", \"arguments\": {...}}。",
                "你好！我是工具专家，可以帮你执行各种任务。我能使用多种工具来完成你的需求。",
                true);

        log.info("初始化完成，共创建 {} 个 Agent", agents.size());
    }

    /**
     * 创建预设 Agent
     */
    private void createPresetAgent(String id, String name, String avatar, String description,
                                   String systemPrompt, String welcomeMessage, boolean enableMCP) {
        Agent agent = Agent.builder()
                .id(id)
                .name(name)
                .avatar(avatar)
                .description(description)
                .systemPrompt(systemPrompt)
                .welcomeMessage(welcomeMessage)
                .enableMCP(enableMCP)
                .isDefault(false)
                .createdAt(System.currentTimeMillis())
                .build();
        agents.put(agent.getId(), agent);
    }

    /**
     * 创建自定义 Agent
     */
    public Agent createAgent(Agent agent) {
        if (agent.getId() == null || agent.getId().isEmpty()) {
            agent.setId(UUID.randomUUID().toString());
        }
        agent.setCreatedAt(System.currentTimeMillis());
        agents.put(agent.getId(), agent);
        log.info("创建 Agent: {}", agent.getName());
        return agent;
    }

    /**
     * 更新 Agent
     */
    public Agent updateAgent(String id, Agent updatedAgent) {
        Agent agent = agents.get(id);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + id);
        }
        
        // 不允许修改默认 Agent
        if (agent.isDefault()) {
            throw new IllegalArgumentException("不允许修改默认 Agent");
        }
        
        updatedAgent.setId(id);
        updatedAgent.setCreatedAt(agent.getCreatedAt());
        agents.put(id, updatedAgent);
        log.info("更新 Agent: {}", updatedAgent.getName());
        return updatedAgent;
    }

    /**
     * 删除 Agent
     */
    public void deleteAgent(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + id);
        }
        
        // 不允许删除默认 Agent
        if (agent.isDefault()) {
            throw new IllegalArgumentException("不允许删除默认 Agent");
        }
        
        agents.remove(id);
        log.info("删除 Agent: {}", agent.getName());
    }

    /**
     * 获取所有 Agent
     */
    public List<Agent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * 根据 ID 获取 Agent
     */
    public Agent getAgentById(String id) {
        Agent agent = agents.get(id);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + id);
        }
        return agent;
    }

    /**
     * 获取默认 Agent
     */
    public Agent getDefaultAgent() {
        return agents.values().stream()
                .filter(Agent::isDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到默认 Agent"));
    }
}
