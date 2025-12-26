package com.tes.springai.service;

import com.google.gson.Gson;
import com.tes.springai.model.ChatMessage;
import com.tes.springai.model.ChatResponse;
import com.tes.springai.model.mcp.MCPTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多轮对话 Agent 服务
 * 实现会话上下文管理和多轮对话能力
 */
@Slf4j
@Service
public class ConversationAgentService {

    private final ChatModel chatModel;
    private final RAGService ragService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MCPClientService mcpClientService;
    private final AgentService agentService;
    private final Gson gson = new Gson();
    
    /**
     * 存储每个会话的历史消息
     * Key: sessionId, Value: 消息列表
     */
    private final Map<String, List<Message>> sessionHistories = new ConcurrentHashMap<>();
    
    /**
     * 存储每个会话使用的 Agent
     * Key: sessionId, Value: agentId
     */
    private final Map<String, String> sessionAgents = new ConcurrentHashMap<>();
    
    /**
     * 系统提示词，定义 Agent 的角色和行为
     */
    private static final String SYSTEM_PROMPT = 
        "你是一个智能助手，能够进行多轮对话。你会记住之前对话的内容，" +
        "并根据上下文提供连贯的回答。请保持友好、专业的态度。" +
        "当你需要执行特定任务时，可以使用可用的 MCP 工具。" +
        "如果需要调用工具，请以 JSON 格式返回：{\"tool\": \"工具名称\", \"arguments\": {...}}。";

    public ConversationAgentService(ChatModel chatModel, RAGService ragService, 
                                   KnowledgeBaseService knowledgeBaseService,
                                   MCPClientService mcpClientService,
                                   AgentService agentService) {
        this.chatModel = chatModel;
        this.ragService = ragService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mcpClientService = mcpClientService;
        this.agentService = agentService;
    }

    /**
     * 处理用户消息，实现多轮对话
     *
     * @param sessionId 会话ID，如果为空则创建新会话
     * @param userMessage 用户消息
     * @param knowledgeBaseId 知识库ID，可为空
     * @param enableMCP 是否启用 MCP 工具
     * @param agentId Agent ID，可为空
     * @return ChatResponse 包含AI回复和会话信息
     */
    public ChatResponse chat(String sessionId, String userMessage, String knowledgeBaseId, 
                            boolean enableMCP, String agentId) {
        // 如果没有 sessionId，创建新会话
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("创建新会话: {}", sessionId);
        }
        
        // 获取 Agent，如果没有指定则使用默认 Agent
        if (agentId == null || agentId.isEmpty()) {
            agentId = "default";
        }
        
        com.tes.springai.model.Agent agent;
        try {
            agent = agentService.getAgentById(agentId);
        } catch (Exception e) {
            log.warn("未找到 Agent: {}，使用默认 Agent", agentId);
            agent = agentService.getDefaultAgent();
            agentId = agent.getId();
        }
        
        log.info("使用 Agent: {} ({})", agent.getName(), agent.getId());
        
        // 记录会话使用的 Agent
        final String finalAgentId = agentId;
        final com.tes.springai.model.Agent finalAgent = agent;
        final boolean finalEnableMCP = enableMCP;
        sessionAgents.put(sessionId, finalAgentId);
        
        // 检查是否为新会话
        boolean isNewSession = !sessionHistories.containsKey(sessionId);

        // 获取或创建会话历史
        List<Message> messages = sessionHistories.computeIfAbsent(sessionId, k -> {
            List<Message> newMessages = new ArrayList<>();
            // 添加系统提示词
            String systemPrompt = finalAgent.getSystemPrompt();
            
            // 如果启用 MCP，添加工具信息
            if (finalAgent.isEnableMCP() || finalEnableMCP) {
                List<MCPTool> tools = mcpClientService.getAllTools();
                if (!tools.isEmpty()) {
                    StringBuilder toolsInfo = new StringBuilder("\n\n可用的 MCP 工具：\n");
                    for (MCPTool tool : tools) {
                        toolsInfo.append(String.format("- %s: %s\n", tool.getName(), tool.getDescription()));
                    }
                    systemPrompt += toolsInfo.toString();
                }
            }
            
            newMessages.add(new SystemMessage(systemPrompt));
            return newMessages;
        });

        // 如果指定了知识库，使用 RAG
        String assistantResponse;
        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            log.info("使用知识库: {}", knowledgeBaseId);
            assistantResponse = ragService.ragQueryWithKnowledgeBase(userMessage, knowledgeBaseId, 3);
        } else {
            // 添加用户消息
            messages.add(new UserMessage(userMessage));
            log.info("会话 {} 收到用户消息: {}", sessionId, userMessage);

            // 调用 AI 模型
            Prompt prompt = new Prompt(messages);
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
            AssistantMessage assistantMsg = (AssistantMessage) response.getResult().getOutput();
            assistantResponse = assistantMsg.getContent();
            
            // 如果启用 MCP 且回复包含工具调用，执行工具
            if ((agent.isEnableMCP() || enableMCP) && assistantResponse != null) {
                assistantResponse = handleToolCall(assistantResponse, messages, sessionId);
            }
            
            // 将 AI 回复添加到历史
            messages.add(new AssistantMessage(assistantResponse));
        }
        
        log.info("会话 {} AI 回复: {}", sessionId, assistantResponse);

        // 计算对话轮数（排除系统消息）
        int conversationRound = (messages.size() - 1) / 2;

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(assistantResponse)
                .conversationRound(conversationRound)
                .welcomeMessage(isNewSession ? agent.getWelcomeMessage() : null)
                .agentId(finalAgentId)
                .build();
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @return 会话历史消息列表
     */
    public List<ChatMessage> getSessionHistory(String sessionId) {
        List<Message> messages = sessionHistories.get(sessionId);
        if (messages == null) {
            return new ArrayList<>();
        }

        List<ChatMessage> history = new ArrayList<>();
        for (Message message : messages) {
            String role;
            if (message instanceof SystemMessage) {
                role = "system";
            } else if (message instanceof UserMessage) {
                role = "user";
            } else if (message instanceof AssistantMessage) {
                role = "assistant";
            } else {
                role = "unknown";
            }
            history.add(new ChatMessage(role, message.toString()));
        }
        return history;
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        sessionHistories.remove(sessionId);
        log.info("清除会话: {}", sessionId);
    }

    /**
     * 获取所有活跃会话数量
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessionHistories.size();
    }

    /**
     * 处理工具调用
     */
    private String handleToolCall(String response, List<Message> messages, String sessionId) {
        try {
            // 尝试解析 JSON 格式的工具调用
            if (response.contains("{\"tool\"") || response.contains("'tool'")) {
                // 提取 JSON 部分
                int start = response.indexOf('{');
                int end = response.lastIndexOf('}') + 1;
                if (start >= 0 && end > start) {
                    String jsonPart = response.substring(start, end);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> toolCall = gson.fromJson(jsonPart, Map.class);
                    
                    String toolName = (String) toolCall.get("tool");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) toolCall.get("arguments");
                    
                    if (toolName != null) {
                        log.info("检测到工具调用: {}", toolName);
                        
                        // 查找工具
                        MCPTool tool = findTool(toolName);
                        if (tool != null) {
                            // 执行工具
                            Object result = mcpClientService.callTool(
                                tool.getServerId(), 
                                toolName, 
                                arguments != null ? arguments : Map.of()
                            );
                            
                            log.info("工具执行结果: {}", result);
                            
                            // 将工具结果反馈给 AI
                            String toolResultMessage = String.format(
                                "工具 %s 执行完毕，结果：%s", 
                                toolName, 
                                gson.toJson(result)
                            );
                            messages.add(new UserMessage(toolResultMessage));
                            
                            // 再次调用 AI 获取最终回复
                            Prompt prompt = new Prompt(messages);
                            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
                            AssistantMessage assistantMsg = (AssistantMessage) aiResponse.getResult().getOutput();
                            return assistantMsg.getContent();
                        } else {
                            return "抱歉，找不到名为 '" + toolName + "' 的工具。";
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理工具调用失败", e);
        }
        return response;
    }

    /**
     * 查找工具
     */
    private MCPTool findTool(String toolName) {
        List<MCPTool> tools = mcpClientService.getAllTools();
        for (MCPTool tool : tools) {
            if (tool.getName().equals(toolName)) {
                return tool;
            }
        }
        return null;
    }
}
