package com.tes.springai.service;

import com.google.gson.Gson;
import com.tes.springai.model.Attachment;
import com.tes.springai.model.ChatMessage;
import com.tes.springai.model.ChatResponse;
import com.tes.springai.model.ChatStreamEvent;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
     * 对话前置流程结果：Agent 解析、附件校验、会话历史初始化与用户消息登记后的上下文
     */
    private record ConversationSetup(String sessionId, String agentId, com.tes.springai.model.Agent agent,
                                     List<HistoryEntry> history, HistoryEntry userEntry,
                                     boolean newSession, List<Attachment> attachments) {
    }

    /**
     * 流式收尾上下文：负责流结束后的历史写入、异常/中断时的用户消息回滚与流内事件构造。
     * 历史写入时机：助手消息仅在流正常结束后写入；空白回复与异常、客户端中断均不入历史
     */
    private class StreamContext {
        private final String sessionId;
        private final String agentId;
        private final boolean newSession;
        private final String welcomeMessage;
        private final List<HistoryEntry> history;
        private final HistoryEntry userEntry;
        /** 本轮是否已收尾（回滚过），避免异常/中断/完成路径重复处理 */
        private volatile boolean settled;

        private StreamContext(String sessionId, String agentId, boolean newSession, String welcomeMessage,
                              List<HistoryEntry> history, HistoryEntry userEntry) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.newSession = newSession;
            this.welcomeMessage = welcomeMessage;
            this.history = history;
            this.userEntry = userEntry;
        }

        /**
         * 流正常结束：全文非空白则写入助手历史并回 done 元数据；
         * 空白回复沿用既有"不入历史"语义，回滚后回 error 事件
         */
        ChatStreamEvent complete(StringBuilder content) {
            if (settled) {
                // 已因错误回滚，不再写历史、不再回 done
                return null;
            }
            settled = true;
            String fullText = content.toString();
            if (fullText.isBlank()) {
                history.remove(userEntry);
                return errorEvent("AI 模型返回了空白回复");
            }
            history.add(new HistoryEntry("assistant", fullText, List.of()));
            log.info("会话 {} AI 回复(流式): {}", sessionId, fullText);
            int conversationRound = (history.size() - 1) / 2;
            return ChatStreamEvent.builder()
                    .type(ChatStreamEvent.TYPE_DONE)
                    .sessionId(sessionId)
                    .conversationRound(conversationRound)
                    .welcomeMessage(newSession ? welcomeMessage : null)
                    .agentId(agentId)
                    .build();
        }

        /**
         * 流内异常：回滚本轮用户消息（与同步链路一致，不残留无回复的脏数据），回 error 事件
         */
        ChatStreamEvent fail(Throwable e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("流式对话异常: {}", detail, e);
            rollback();
            return errorEvent("AI 服务调用失败，请稍后重试: " + detail);
        }

        /**
         * 当前会话历史（工具结果回喂后的二次模型调用需要基于最新历史构建 Prompt）
         */
        List<HistoryEntry> history() {
            return history;
        }

        /**
         * 客户端中断等取消路径：已生成的部分文本不入历史（与刷新丢失语义一致），回滚用户消息
         */
        void rollback() {
            if (settled) {
                return;
            }
            settled = true;
            history.remove(userEntry);
            log.info("会话 {} 流式中断，已回滚本轮用户消息", sessionId);
        }
    }

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
        ConversationSetup setup = prepareConversation(sessionId, userMessage, fileIds, agentId, enableMCP);
        List<HistoryEntry> history = setup.history();

        // 如果指定了知识库，使用 RAG
        String assistantResponse;
        try {
            if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
                log.info("使用知识库: {}", knowledgeBaseId);
                assistantResponse = ragService.ragQueryWithKnowledgeBase(userMessage, knowledgeBaseId, 3);
            } else {
                log.info("会话 {} 收到用户消息: {} (附件 {} 个)", setup.sessionId(), userMessage, setup.attachments().size());

                // 由历史条目构建 Prompt（按需重建多模态 Media）并调用 AI 模型
                Prompt prompt = new Prompt(buildMessages(history));
                assistantResponse = callModelAndExtractText(prompt);

                // 如果启用 MCP 且回复包含工具调用，执行工具
                if ((setup.agent().isEnableMCP() || enableMCP) && assistantResponse != null) {
                    assistantResponse = handleToolCall(assistantResponse, history, setup.sessionId());
                }
            }
        } catch (Exception e) {
            // 调用失败：回滚刚记入的用户消息，避免历史中残留无回复的脏数据导致后续轮次错乱
            history.remove(setup.userEntry());
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalStateException("AI 服务调用失败，请稍后重试: " + detail, e);
        }

        // 将 AI 回复记入会话历史（RAG 分支也统一记录，保证多轮上下文与历史回显一致）
        history.add(new HistoryEntry("assistant", assistantResponse, List.of()));

        log.info("会话 {} AI 回复: {}", setup.sessionId(), assistantResponse);

        // 计算对话轮数（排除系统消息）
        int conversationRound = (history.size() - 1) / 2;

        return ChatResponse.builder()
                .sessionId(setup.sessionId())
                .message(assistantResponse)
                .conversationRound(conversationRound)
                .welcomeMessage(setup.newSession() ? setup.agent().getWelcomeMessage() : null)
                .agentId(setup.agentId())
                .build();
    }

    /**
     * 处理用户消息并流式返回（chat 的流式变体）：
     * 复用既有的 Prompt 构建、附件注入、RAG 分支与会话历史管理逻辑，仅把阻塞式模型调用替换为流式调用。
     * 校验类错误（空白消息、附件策略、RAG 检索失败等）在 SSE 响应提交前同步抛出，
     * 仍走全局异常处理器返回 HTTP 错误码；流式过程中的错误在流内以 error 事件表达。
     *
     * @param sessionId 会话ID，如果为空则创建新会话
     * @param userMessage 用户消息
     * @param fileIds 附件 fileId 列表（可选）
     * @param knowledgeBaseId 知识库ID，可为空
     * @param enableMCP 是否启用 MCP 工具
     * @param agentId Agent ID，可为空
     * @return ChatStreamEvent 事件流（delta/done/error 等，见 ChatStreamEvent）
     */
    public Flux<ChatStreamEvent> chatStream(String sessionId, String userMessage, List<String> fileIds,
                                            String knowledgeBaseId, boolean enableMCP, String agentId) {
        ConversationSetup setup = prepareConversation(sessionId, userMessage, fileIds, agentId, enableMCP);
        List<HistoryEntry> history = setup.history();

        // 流式收尾上下文：负责历史写入、回滚与流内 error 事件
        StreamContext ctx = new StreamContext(setup.sessionId(), setup.agentId(), setup.newSession(),
                setup.agent().getWelcomeMessage(), history, setup.userEntry());

        // 如果指定了知识库，使用 RAG 流式变体（检索逻辑不变，回答逐段下发）
        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            log.info("使用知识库: {} [流式]", knowledgeBaseId);
            Flux<org.springframework.ai.chat.model.ChatResponse> ragStream;
            try {
                ragStream = ragService.ragQueryWithKnowledgeBaseStream(userMessage, knowledgeBaseId, 3);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                // 检索等前置失败：与同步链路一致回滚用户消息后抛出，走 HTTP 错误语义
                history.remove(setup.userEntry());
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                throw new IllegalStateException("AI 服务调用失败，请稍后重试: " + detail, e);
            }
            return guarded(ragStream, ctx, new StringBuilder());
        }

        log.info("会话 {} 收到用户消息: {} (附件 {} 个) [流式]", setup.sessionId(), userMessage, setup.attachments().size());

        // MCP 工具调用走"缓冲-检测-二次流式下发"模式，其余直通流式（增量实时下发）
        boolean useMCP = setup.agent().isEnableMCP() || enableMCP;
        if (!useMCP) {
            return guarded(chatModel.stream(new Prompt(buildMessages(history))), ctx, new StringBuilder());
        }

        // MCP 流式：先缓冲第一轮完整输出 → 沿用既有 JSON 协议检测工具调用
        // （检测到工具调用时用户会先等第一轮缓冲完成，属接受的体验折衷）
        StringBuilder buffered = new StringBuilder();
        StringBuilder content = new StringBuilder();
        return Flux.concat(
                chatModel.stream(new Prompt(buildMessages(history)))
                        .concatMap(chunk -> bufferChunkEvents(chunk, buffered))
                        .onErrorResume(e -> Flux.just(ctx.fail(e))),
                Flux.defer(() -> afterMcpFirstRound(buffered.toString(), ctx, content))
        ).doFinally(signal -> {
            if (signal == SignalType.CANCEL) {
                ctx.rollback();
            }
        });
    }

    /**
     * 为模型流包裹统一的收尾语义：
     * 思考增量 → thinking 事件、正文增量 → delta 事件；异常 → 回滚 + error 事件；
     * 完成 → 历史写入 + done 事件；客户端中断（cancel）→ 回滚，且随订阅取消释放上游模型流资源。
     * content 为正文累积器（MCP 二次调用需跨段共享）
     */
    private Flux<ChatStreamEvent> guarded(Flux<org.springframework.ai.chat.model.ChatResponse> modelStream,
                                          StreamContext ctx, StringBuilder content) {
        return Flux.concat(
                modelStream
                        .concatMap(chunk -> chunkEvents(chunk, content))
                        .onErrorResume(e -> Flux.just(ctx.fail(e))),
                Mono.fromSupplier(() -> ctx.complete(content))
        ).doFinally(signal -> {
            if (signal == SignalType.CANCEL) {
                ctx.rollback();
            }
        });
    }

    /**
     * 单个流式增量 → 事件列表：思考增量先于正文增量，空白思考增量跳过；
     * 正文增量同时累积进 content
     */
    private Flux<ChatStreamEvent> chunkEvents(org.springframework.ai.chat.model.ChatResponse chunk,
                                              StringBuilder content) {
        String thinking = extractChunkThinking(chunk);
        String text = extractChunkText(chunk);
        if (text != null && !text.isEmpty()) {
            content.append(text);
        }
        if (thinking == null || thinking.isEmpty()) {
            return text != null && !text.isEmpty() ? Flux.just(deltaEvent(text)) : Flux.empty();
        }
        if (text != null && !text.isEmpty()) {
            return Flux.just(thinkingEvent(thinking), deltaEvent(text));
        }
        return Flux.just(thinkingEvent(thinking));
    }

    /**
     * MCP 第一轮缓冲：思考增量实时下发，正文增量只写入缓冲区（缓冲完成后统一检测工具调用）
     */
    private Flux<ChatStreamEvent> bufferChunkEvents(org.springframework.ai.chat.model.ChatResponse chunk,
                                                    StringBuilder buffer) {
        String thinking = extractChunkThinking(chunk);
        String text = extractChunkText(chunk);
        if (text != null && !text.isEmpty()) {
            buffer.append(text);
        }
        return thinking != null && !thinking.isEmpty() ? Flux.just(thinkingEvent(thinking)) : Flux.empty();
    }

    /**
     * MCP 第一轮缓冲完成后的处理：
     * 空缓冲 → 沿用"空白回复不入历史"语义（error 事件）；
     * 检测到工具调用 → tool_call/tool_result 事件，工具结果回喂后二次模型调用改为流式下发；
     * 未检测到 → 缓冲内容整体作为 delta 下发（有工具调用时用户先等第一轮缓冲完成，属接受的体验折衷）
     */
    private Flux<ChatStreamEvent> afterMcpFirstRound(String bufferedText, StreamContext ctx, StringBuilder content) {
        if (bufferedText.isBlank()) {
            ctx.rollback();
            return Flux.just(errorEvent("AI 模型返回了空白回复"));
        }

        ParsedToolCall toolCall = detectToolCall(bufferedText);
        if (toolCall == null) {
            content.append(bufferedText);
            return Flux.concat(
                    Flux.just(deltaEvent(bufferedText)),
                    Mono.fromSupplier(() -> ctx.complete(content)));
        }

        return Flux.defer(() -> {
            MCPTool tool = findTool(toolCall.toolName());
            if (tool == null) {
                // 工具不存在：沿用同步链路语义，以中文提示作为最终回复，不中断流
                String tip = "抱歉，找不到名为 '" + toolCall.toolName() + "' 的工具。";
                content.append(tip);
                return Flux.concat(
                        Flux.just(deltaEvent(tip)),
                        Mono.fromSupplier(() -> ctx.complete(content)));
            }

            log.info("检测到工具调用: {}", toolCall.toolName());
            ChatStreamEvent callEvent = ChatStreamEvent.builder()
                    .type(ChatStreamEvent.TYPE_TOOL_CALL)
                    .tool(toolCall.toolName())
                    .arguments(toolCall.arguments())
                    .build();

            return Flux.concat(
                    Flux.just(callEvent),
                    Mono.fromCallable(() -> {
                        Object result = mcpClientService.callTool(tool.getServerId(), toolCall.toolName(),
                                toolCall.arguments() != null ? toolCall.arguments() : Map.of());
                        log.info("工具执行结果: {}", result);
                        // 工具结果回喂给 AI（沿用同步链路的回喂格式与历史记录）
                        ctx.history().add(new HistoryEntry("user", String.format(
                                "工具 %s 执行完毕，结果：%s", toolCall.toolName(), gson.toJson(result)), List.of()));
                        return result;
                    }).map(result -> ChatStreamEvent.builder()
                            .type(ChatStreamEvent.TYPE_TOOL_RESULT)
                            .tool(toolCall.toolName())
                            .result(result)
                            .build()),
                    // 二次模型调用改为流式下发（delta/thinking/done）
                    Flux.defer(() -> guarded(chatModel.stream(new Prompt(buildMessages(ctx.history()))), ctx, content))
            );
        }).onErrorResume(e -> {
            // 工具执行等失败：沿用同步链路语义，把第一轮缓冲内容按普通文本下发，不中断流
            log.error("处理工具调用失败", e);
            content.append(bufferedText);
            return Flux.concat(
                    Flux.just(deltaEvent(bufferedText)),
                    Mono.fromSupplier(() -> ctx.complete(content)));
        });
    }

    /**
     * 提取流式增量中的正文文本（无输出的元数据块返回 null，由调用方跳过）
     */
    private String extractChunkText(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 提取流式增量中的深度思考文本（智谱思考模型）：
     * 智谱原生模型放在 ZhiPuAiAssistantMessage 的专用字段上；
     * OpenAI 兼容等通用路径则位于增量元数据的 reasoningContent 键。两条路径都尝试
     */
    private String extractChunkThinking(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        org.springframework.ai.chat.messages.AssistantMessage output = response.getResult().getOutput();
        if (output instanceof org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage zhiPuMessage) {
            String reasoning = zhiPuMessage.getReasoningContent();
            if (reasoning != null && !reasoning.isEmpty()) {
                return reasoning;
            }
        }
        Object reasoning = output.getMetadata().get("reasoningContent");
        if (reasoning == null) {
            return null;
        }
        String text = String.valueOf(reasoning);
        return text.isEmpty() ? null : text;
    }

    private ChatStreamEvent deltaEvent(String text) {
        return ChatStreamEvent.builder()
                .type(ChatStreamEvent.TYPE_DELTA)
                .content(text)
                .build();
    }

    private ChatStreamEvent thinkingEvent(String text) {
        return ChatStreamEvent.builder()
                .type(ChatStreamEvent.TYPE_THINKING)
                .content(text)
                .build();
    }

    private ChatStreamEvent errorEvent(String message) {
        return ChatStreamEvent.builder()
                .type(ChatStreamEvent.TYPE_ERROR)
                .error(message)
                .build();
    }

    /**
     * 对话前置公共流程（chat 与 chatStream 共用，保证两条链路会话语义一致）：
     * 会话/Agent 解析 → 附件校验 → 会话历史初始化（系统提示词与工具信息）→ 用户消息登记
     * 校验失败在响应生成前同步抛出，由调用方/全局异常处理器处理
     */
    private ConversationSetup prepareConversation(String sessionId, String userMessage, List<String> fileIds,
                                                  String agentId, boolean enableMCP) {
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

        // 获取或创建会话历史（同一会话并发请求需线程安全，避免并发修改导致历史错乱）
        List<HistoryEntry> history = sessionHistories.computeIfAbsent(sessionId, k -> {
            List<HistoryEntry> newHistory = new CopyOnWriteArrayList<>();
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

        // 校验用户消息非空：空白消息会让模型端 UserMessage 构造直接抛异常（500）
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // 将用户消息（含附件引用）记入会话历史
        HistoryEntry userEntry = new HistoryEntry("user", userMessage,
                chatAttachments.stream().map(Attachment::getFileId).collect(Collectors.toList()));
        history.add(userEntry);

        return new ConversationSetup(sessionId, finalAgentId, finalAgent, history, userEntry,
                isNewSession, chatAttachments);
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
                // AssistantMessage 不接受空白文本，历史中的空回复用占位文本兜底，避免后续轮次 500
                case "assistant" -> messages.add(new AssistantMessage(
                        entry.content() == null || entry.content().isBlank() ? "（空回复）" : entry.content()));
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
     * 按既有 JSON 协议从回复文本中解析工具调用：{"tool": "工具名称", "arguments": {...}}
     * 未检测到、解析失败或缺少工具名时返回 null（与同步链路的检测语义一致）
     */
    @SuppressWarnings("unchecked")
    private ParsedToolCall detectToolCall(String response) {
        if (response == null || !(response.contains("{\"tool\"") || response.contains("'tool'"))) {
            return null;
        }
        // 提取 JSON 部分
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}') + 1;
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            Map<String, Object> toolCall = gson.fromJson(response.substring(start, end), Map.class);
            if (toolCall == null) {
                return null;
            }
            String toolName = (String) toolCall.get("tool");
            if (toolName == null) {
                return null;
            }
            return new ParsedToolCall(toolName, (Map<String, Object>) toolCall.get("arguments"));
        } catch (Exception e) {
            log.warn("工具调用协议解析失败，按普通文本处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析后的工具调用：工具名与参数
     */
    private record ParsedToolCall(String toolName, Map<String, Object> arguments) {
    }

    /**
     * 处理工具调用（同步链路）
     */
    private String handleToolCall(String response, List<HistoryEntry> history, String sessionId) {
        ParsedToolCall toolCall = detectToolCall(response);
        if (toolCall == null) {
            return response;
        }
        try {
            log.info("检测到工具调用: {}", toolCall.toolName());

            // 查找工具
            MCPTool tool = findTool(toolCall.toolName());
            if (tool == null) {
                return "抱歉，找不到名为 '" + toolCall.toolName() + "' 的工具。";
            }

            // 执行工具
            Object result = mcpClientService.callTool(
                tool.getServerId(),
                toolCall.toolName(),
                toolCall.arguments() != null ? toolCall.arguments() : Map.of()
            );

            log.info("工具执行结果: {}", result);

            // 将工具结果反馈给 AI
            String toolResultMessage = String.format(
                "工具 %s 执行完毕，结果：%s",
                toolCall.toolName(),
                gson.toJson(result)
            );
            history.add(new HistoryEntry("user", toolResultMessage, List.of()));

            // 再次调用 AI 获取最终回复
            Prompt prompt = new Prompt(buildMessages(history));
            return callModelAndExtractText(prompt);
        } catch (Exception e) {
            log.error("处理工具调用失败", e);
        }
        return response;
    }

    /**
     * 调用 AI 模型并提取回复文本：
     * 模型返回空结果（无 Generation）或回复为空白时抛出明确异常，
     * 避免 NPE 裸抛成 500，也避免空白/null 回复被写入会话历史
     */
    private String callModelAndExtractText(Prompt prompt) {
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("AI 模型返回了空结果");
        }
        AssistantMessage assistantMsg = response.getResult().getOutput();
        if (assistantMsg == null) {
            throw new IllegalStateException("AI 模型返回了空消息");
        }
        String text = assistantMsg.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("AI 模型返回了空白回复");
        }
        return text;
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
