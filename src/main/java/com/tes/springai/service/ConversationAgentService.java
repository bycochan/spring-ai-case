package com.tes.springai.service;

import com.google.gson.Gson;
import com.tes.springai.model.Attachment;
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
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多轮对话 Agent 服务
 * 实现会话上下文管理和多轮对话能力。
 * 支持携带附件（图片与 PDF/Word/TXT 文档）的消息：会话历史仅保存"文本 + fileIds"引用，
 * 构建 Prompt 时再从附件服务重建——图片组装多模态 Media，文档提取纯文本拼入消息正文，
 * 附件失效时降级为纯文本（见 docs/adr/0001）。
 */
@Slf4j
@Service
public class ConversationAgentService {

    private final ChatModel chatModel;
    private final RAGService ragService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MCPClientService mcpClientService;
    private final AgentService agentService;
    private final AttachmentService attachmentService;
    private final Gson gson = new Gson();

    /**
     * 会话历史条目：仅保存文本与附件引用，不保存附件字节
     */
    private record HistoryEntry(String role, String content, List<String> fileIds) {
    }

    /**
     * 存储每个会话的历史消息（引用形态）
     * Key: sessionId, Value: 历史条目列表
     */
    private final Map<String, List<HistoryEntry>> sessionHistories = new ConcurrentHashMap<>();

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
                                   AgentService agentService,
                                   AttachmentService attachmentService) {
        this.chatModel = chatModel;
        this.ragService = ragService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mcpClientService = mcpClientService;
        this.agentService = agentService;
        this.attachmentService = attachmentService;
    }

    /**
     * 处理用户消息，实现多轮对话
     *
     * @param sessionId 会话ID，如果为空则创建新会话
     * @param userMessage 用户消息
     * @param fileIds 附件 fileId 列表（可选，图片与文档共用额度，最多 5 个）
     * @param knowledgeBaseId 知识库ID，可为空
     * @param enableMCP 是否启用 MCP 工具
     * @param agentId Agent ID，可为空
     * @return ChatResponse 包含AI回复和会话信息
     */
    public ChatResponse chat(String sessionId, String userMessage, List<String> fileIds,
                            String knowledgeBaseId, boolean enableMCP, String agentId) {
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

        // 校验附件引用：数量上限、逐个存在、Agent 是否开启附件上传（图片与文档共用该开关）
        List<Attachment> chatAttachments = attachmentService.validateForChat(fileIds);
        if (!chatAttachments.isEmpty() && !agent.isEnableImageUpload()) {
            throw new IllegalArgumentException("该 Agent 未开启附件上传功能");
        }

        // 记录会话使用的 Agent
        final String finalAgentId = agentId;
        final com.tes.springai.model.Agent finalAgent = agent;
        final boolean finalEnableMCP = enableMCP;
        sessionAgents.put(sessionId, finalAgentId);

        // 检查是否为新会话
        boolean isNewSession = !sessionHistories.containsKey(sessionId);

        // 获取或创建会话历史
        List<HistoryEntry> history = sessionHistories.computeIfAbsent(sessionId, k -> {
            List<HistoryEntry> newHistory = new ArrayList<>();
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

            newHistory.add(new HistoryEntry("system", systemPrompt, List.of()));
            return newHistory;
        });

        // 将用户消息（含附件引用）记入会话历史
        history.add(new HistoryEntry("user", userMessage,
                chatAttachments.stream().map(Attachment::getFileId).collect(Collectors.toList())));

        // 如果指定了知识库，使用 RAG
        String assistantResponse;
        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            log.info("使用知识库: {}", knowledgeBaseId);
            assistantResponse = ragService.ragQueryWithKnowledgeBase(userMessage, knowledgeBaseId, 3);
        } else {
            log.info("会话 {} 收到用户消息: {} (附件 {} 个)", sessionId, userMessage, chatAttachments.size());

            // 由历史条目构建 Prompt（按需重建多模态 Media）并调用 AI 模型
            Prompt prompt = new Prompt(buildMessages(history));
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
            AssistantMessage assistantMsg = (AssistantMessage) response.getResult().getOutput();
            assistantResponse = assistantMsg.getText();

            // 如果启用 MCP 且回复包含工具调用，执行工具
            if ((agent.isEnableMCP() || enableMCP) && assistantResponse != null) {
                assistantResponse = handleToolCall(assistantResponse, history, sessionId);
            }
        }

        // 将 AI 回复记入会话历史（RAG 分支也统一记录，保证多轮上下文与历史回显一致）
        history.add(new HistoryEntry("assistant", assistantResponse, List.of()));

        log.info("会话 {} AI 回复: {}", sessionId, assistantResponse);

        // 计算对话轮数（排除系统消息）
        int conversationRound = (history.size() - 1) / 2;

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
     * @return 会话历史消息列表（用户消息携带 fileIds 供前端回显图片）
     */
    public List<ChatMessage> getSessionHistory(String sessionId) {
        List<HistoryEntry> history = sessionHistories.get(sessionId);
        if (history == null) {
            return new ArrayList<>();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (HistoryEntry entry : history) {
            result.add(new ChatMessage(entry.role(), entry.content(), entry.fileIds()));
        }
        return result;
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
     * 将会话历史（引用形态）构建为 Spring AI 消息列表：
     * 用户消息按需从附件服务重建 Media，实现多模态上下文
     */
    private List<Message> buildMessages(List<HistoryEntry> history) {
        List<Message> messages = new ArrayList<>(history.size());
        for (HistoryEntry entry : history) {
            switch (entry.role()) {
                case "system" -> messages.add(new SystemMessage(entry.content()));
                case "user" -> messages.add(buildUserMessage(entry));
                case "assistant" -> messages.add(new AssistantMessage(entry.content()));
                default -> log.warn("会话历史中存在未知角色消息，已跳过: {}", entry.role());
            }
        }
        return messages;
    }

    /**
     * 构建用户消息：
     * 图片附件从附件服务读取字节组装多模态 Media；
     * 文档附件（PDF/Word/TXT）调用附件服务提取纯文本，以带文件名标注的文本块拼入消息正文；
     * 附件已失效（重启或被删）或文档提取失败时降级（跳过/占位提示），不中断对话
     */
    private UserMessage buildUserMessage(HistoryEntry entry) {
        List<String> fileIds = entry.fileIds();
        if (fileIds == null || fileIds.isEmpty()) {
            return new UserMessage(entry.content());
        }

        List<Media> mediaList = new ArrayList<>(fileIds.size());
        StringBuilder contentBuilder = new StringBuilder(entry.content() == null ? "" : entry.content());
        for (String fileId : fileIds) {
            Attachment attachment = attachmentService.findAttachment(fileId);
            if (attachment == null) {
                log.warn("会话历史中的附件已失效，降级为纯文本: {}", fileId);
                continue;
            }
            if (isImageAttachment(attachment)) {
                mediaList.add(Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(attachment.getContentType()))
                        .data(new ByteArrayResource(attachment.getData()))
                        .build());
                continue;
            }

            // 文档附件：提取文本拼入消息正文，提取失败时降级为占位提示
            try {
                String text = attachmentService.extractText(fileId);
                appendDocumentBlock(contentBuilder, "[附件文件：" + attachment.getFileName() + "]\n" + text);
            } catch (Exception e) {
                log.warn("文档附件文本提取失败，降级为占位提示: {} ({})", attachment.getFileName(), fileId);
                appendDocumentBlock(contentBuilder, "[附件文件：" + attachment.getFileName() + "：内容提取失败]");
            }
        }

        // 仅有图片附件且无文本时使用占位文本，避免空文本块被模型拒绝
        String content = contentBuilder.toString().trim();
        if (!mediaList.isEmpty() && content.isBlank()) {
            content = "[图片]";
        }
        if (mediaList.isEmpty()) {
            return new UserMessage(content);
        }
        return UserMessage.builder()
                .text(content)
                .media(mediaList)
                .build();
    }

    /**
     * 判断附件是否为图片类型（MIME 以 image/ 开头）；其余附件一律走文档转文本路径
     */
    private boolean isImageAttachment(Attachment attachment) {
        String contentType = attachment.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("image/");
    }

    /**
     * 向消息正文追加文档文本块：正文非空时以空行分隔
     */
    private void appendDocumentBlock(StringBuilder builder, String block) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(block);
    }

    /**
     * 处理工具调用
     */
    private String handleToolCall(String response, List<HistoryEntry> history, String sessionId) {
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
                            history.add(new HistoryEntry("user", toolResultMessage, List.of()));

                            // 再次调用 AI 获取最终回复
                            Prompt prompt = new Prompt(buildMessages(history));
                            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
                            AssistantMessage assistantMsg = (AssistantMessage) aiResponse.getResult().getOutput();
                            return assistantMsg.getText();
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
