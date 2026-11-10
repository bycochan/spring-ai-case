package com.tes.springai.service;

import com.tes.springai.model.Agent;
import com.tes.springai.model.ChatMessage;
import com.tes.springai.model.ChatResponse;
import com.tes.springai.model.ChatStreamEvent;
import com.tes.springai.model.mcp.MCPTool;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ConversationAgentService 测试类
 */
@SpringBootTest
class ConversationAgentServiceTest {

    @Autowired
    private ConversationAgentService conversationAgentService;

    @Autowired
    private AttachmentService attachmentService;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private RAGService ragService;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private MilvusServiceClient milvusClient;

    @MockBean
    private MCPClientService mcpClientService;
    
    @MockBean
    private AgentService agentService;
    
    @BeforeEach
    void setUp() {
        // Mock 默认 Agent
        Agent defaultAgent = Agent.builder()
                .id("default")
                .name("通用助手")
                .description("一个友好、专业的通用 AI 助手")
                .avatar("🤖")
                .systemPrompt("你是一个智能助手，能够进行多轮对话。")
                .welcomeMessage("你好！我是你的智能助手，有什么可以帮助你的吗？")
                .enableMCP(false)
                .isDefault(true)
                .build();
        
        // Mock 代码助手 Agent（未开启附件上传）
        Agent codeAgent = Agent.builder()
                .id("code-assistant")
                .name("代码助手")
                .description("专注于编程和技术问题的 AI 助手")
                .avatar("🧑‍💻")
                .systemPrompt("你是一个专业的编程助手。")
                .welcomeMessage("你好！我是你的代码助手。")
                .enableMCP(false)
                .build();

        // Mock 开启附件上传的 Agent
        Agent docAgent = Agent.builder()
                .id("doc-agent")
                .name("文档助手")
                .description("支持携带文档附件对话的 AI 助手")
                .avatar("📄")
                .systemPrompt("你是一个能够阅读文档的助手。")
                .welcomeMessage("你好！我是你的文档助手。")
                .enableMCP(false)
                .enableImageUpload(true)
                .build();

        // 设置 Mock 行为
        when(agentService.getAgentById("default")).thenReturn(defaultAgent);
        when(agentService.getAgentById("code-assistant")).thenReturn(codeAgent);
        when(agentService.getAgentById("doc-agent")).thenReturn(docAgent);
        when(agentService.getDefaultAgent()).thenReturn(defaultAgent);

        // 当传入 null 或空字符串时，返回默认 Agent
        when(agentService.getAgentById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if ("default".equals(id)) {
                return defaultAgent;
            } else if ("code-assistant".equals(id)) {
                return codeAgent;
            } else if ("doc-agent".equals(id)) {
                return docAgent;
            }
            // 如果找不到，抛出异常，会被 ConversationAgentService 捕获并使用默认 Agent
            throw new IllegalArgumentException("Agent not found: " + id);
        });
    }

    @Test
    void testSingleRoundConversation() {
        // 模拟 AI 响应
        AssistantMessage assistantMessage = new AssistantMessage("你好！我是 AI 助手，很高兴为您服务。");
        Generation generation = new Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse mockResponse = 
            new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(generation));
        
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // 测试第一轮对话（使用默认 Agent）
        com.tes.springai.model.ChatResponse response = conversationAgentService.chat(null, "你好", null, null, false, null);
        
        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertEquals("你好！我是 AI 助手，很高兴为您服务。", response.getMessage());
        assertEquals(1, response.getConversationRound());
        assertNotNull(response.getAgentId()); // 验证 Agent ID
    }

    @Test
    void testMultiRoundConversation() {
        String sessionId = "test-session-123";
        
        // 第一轮对话
        AssistantMessage firstResponse = new AssistantMessage("我的名字是小智。");
        Generation firstGeneration = new Generation(firstResponse);
        org.springframework.ai.chat.model.ChatResponse mockResponse1 = 
            new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(firstGeneration));
        
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse1);
        
        com.tes.springai.model.ChatResponse response1 = 
            conversationAgentService.chat(sessionId, "你叫什么名字？", null, null, false, null);
        
        assertEquals(sessionId, response1.getSessionId());
        assertEquals(1, response1.getConversationRound());
        assertNotNull(response1.getAgentId()); // 验证 Agent ID

        // 第二轮对话
        AssistantMessage secondResponse = new AssistantMessage("我刚才说了，我叫小智。");
        Generation secondGeneration = new Generation(secondResponse);
        org.springframework.ai.chat.model.ChatResponse mockResponse2 = 
            new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(secondGeneration));
        
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse2);
        
        com.tes.springai.model.ChatResponse response2 = 
            conversationAgentService.chat(sessionId, "你刚才说你叫什么？", null, null, false, null);
        
        assertEquals(sessionId, response2.getSessionId());
        assertEquals(2, response2.getConversationRound());
        assertEquals(response1.getAgentId(), response2.getAgentId()); // 同一会话应使用同一 Agent
    }

    @Test
    void testClearSession() {
        String sessionId = "test-clear-session";
        
        AssistantMessage assistantMessage = new AssistantMessage("你好！");
        Generation generation = new Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse mockResponse = 
            new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(generation));
        
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // 创建会话
        conversationAgentService.chat(sessionId, "你好", null, null, false, null);
        assertTrue(conversationAgentService.getActiveSessionCount() > 0);

        // 清除会话
        conversationAgentService.clearSession(sessionId);
        assertEquals(0, conversationAgentService.getSessionHistory(sessionId).size());
    }
    
    @Test
    void testWithSpecificAgent() {
        // 模拟 AI 响应
        AssistantMessage assistantMessage = new AssistantMessage("我是代码助手，可以帮你解决编程问题。");
        Generation generation = new Generation(assistantMessage);
        org.springframework.ai.chat.model.ChatResponse mockResponse = 
            new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(generation));
        
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // 使用指定的 Agent
        String agentId = "code-assistant";
        com.tes.springai.model.ChatResponse response = 
            conversationAgentService.chat(null, "帮我查看这段代码", null, null, false, agentId);
        
        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertNotNull(response.getAgentId());
        assertEquals("我是代码助手，可以帮你解决编程问题。", response.getMessage());
    }

    // ---------- 文档附件场景（见 docs/specs/attachment-doc-to-text.md） ----------

    @Test
    void testDocumentAttachmentTextReachesModel() {
        String fileId = attachmentService.upload(AttachmentFixtures.docxFile("spec.docx")).getFileId();
        stubAssistantReply("已收到文档");

        conversationAgentService.chat("doc-session-1", "总结这份文档", List.of(fileId), null, false, "doc-agent");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        String userText = lastUserText(captor.getValue());

        assertTrue(userText.contains("总结这份文档"), "用户提问应保留，实际为: " + userText);
        assertTrue(userText.contains("[附件文件：spec.docx]"), "应带文件名标注，实际为: " + userText);
        assertTrue(userText.contains(AttachmentFixtures.DOCX_TEXT), "文档内容应转为文本进入消息，实际为: " + userText);
    }

    @Test
    void testMissingDocumentAttachmentDegradesGracefully() {
        String fileId = attachmentService.upload(AttachmentFixtures.docxFile("spec.docx")).getFileId();
        stubAssistantReply("好的");

        conversationAgentService.chat("doc-session-2", "看看这份文档", List.of(fileId), null, false, "doc-agent");
        attachmentService.delete(fileId);
        clearInvocations(chatModel);

        // 附件已失效：历史重建时降级，不应抛异常中断对话
        ChatResponse response = conversationAgentService.chat("doc-session-2", "继续", null, null, false, "doc-agent");

        assertEquals("好的", response.getMessage());
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        List<String> userTexts = allUserTexts(captor.getValue());
        assertTrue(userTexts.stream().noneMatch(t -> t.contains("[附件文件：")),
                "失效附件不应再产出文档文本块，实际为: " + userTexts);
    }

    @Test
    void testCorruptedDocumentAttachmentDegradesGracefully() {
        String fileId = attachmentService.upload(AttachmentFixtures.corruptedPdfFile("broken.pdf")).getFileId();
        stubAssistantReply("内容无法读取");

        ChatResponse response = conversationAgentService
                .chat("doc-session-3", "解析这个文件", List.of(fileId), null, false, "doc-agent");

        assertEquals("内容无法读取", response.getMessage());
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        String userText = lastUserText(captor.getValue());
        assertTrue(userText.contains("broken.pdf"), "占位提示应带文件名，实际为: " + userText);
        assertTrue(userText.contains("提取失败"), "解析失败应以占位提示降级，实际为: " + userText);
    }

    @Test
    void testDocumentAttachmentRejectedWhenAgentUploadDisabled() {
        String fileId = attachmentService.upload(AttachmentFixtures.docxFile("spec.docx")).getFileId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> conversationAgentService.chat(null, "总结这份文档", List.of(fileId), null, false, "code-assistant"));
        assertTrue(ex.getMessage().contains("附件上传"));
    }

    @Test
    void testPdfWithoutTextLayerDegradesGracefully() {
        // 无文本层的扫描件 PDF：不报错中断，降级为带文件名的"提取失败"占位提示
        String fileId = attachmentService.upload(AttachmentFixtures.emptyPdfFile("scan.pdf")).getFileId();
        stubAssistantReply("没有读到内容");

        ChatResponse response = conversationAgentService
                .chat("doc-session-4", "读这个扫描件", List.of(fileId), null, false, "doc-agent");

        assertEquals("没有读到内容", response.getMessage());
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        String userText = lastUserText(captor.getValue());
        assertTrue(userText.contains("scan.pdf"), "占位提示应带文件名，实际为: " + userText);
        assertTrue(userText.contains("提取失败"), "无文本层 PDF 应以占位提示降级，实际为: " + userText);
    }

    // ---------- 流式链路（见 docs/specs/chat-streaming-output.md） ----------

    @Test
    void testStreamSingleRoundEmitsDeltasThenDone() {
        stubStreamReply("你好", "！", "我是流式助手");

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(null, "你好", null, null, false, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        // 增量事件按序到达且拼接完整
        assertNotNull(events);
        List<ChatStreamEvent> deltas = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_DELTA.equals(e.getType()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(3, deltas.size());
        String joined = deltas.stream().map(ChatStreamEvent::getContent).collect(java.util.stream.Collectors.joining());
        assertEquals("你好！我是流式助手", joined);

        // 最后一条为 done，携带会话元数据
        ChatStreamEvent done = events.get(events.size() - 1);
        assertEquals(ChatStreamEvent.TYPE_DONE, done.getType());
        assertNotNull(done.getSessionId());
        assertEquals(1, done.getConversationRound());
        assertEquals("default", done.getAgentId());
        assertEquals("你好！我是你的智能助手，有什么可以帮助你的吗？", done.getWelcomeMessage());

        // 流正常结束后用户与助手消息均写入会话历史
        List<ChatMessage> history = conversationAgentService.getSessionHistory(done.getSessionId());
        assertTrue(history.stream().anyMatch(m -> "assistant".equals(m.getRole())));
        assertTrue(history.stream().anyMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void testStreamBlankReplyEmitsErrorAndSkipsHistory() {
        String sessionId = "stream-blank-session";
        stubStreamReply(" ", "\n ");

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(sessionId, "你好", null, null, false, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        // 全文空白：回 error 事件（沿用"空白回复不入历史"语义），不回 done
        assertNotNull(events);
        assertEquals(1, events.stream().filter(e -> ChatStreamEvent.TYPE_ERROR.equals(e.getType())).count());
        assertTrue(events.stream().noneMatch(e -> ChatStreamEvent.TYPE_DONE.equals(e.getType())));
        assertTrue(events.get(events.size() - 1).getError().contains("空白回复"));

        // 空白回复不入历史：无 assistant 消息，本轮用户消息已回滚
        List<ChatMessage> history = conversationAgentService.getSessionHistory(sessionId);
        assertTrue(history.stream().noneMatch(m -> "assistant".equals(m.getRole())));
        assertTrue(history.stream().noneMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void testStreamModelErrorEmitsErrorEventAndRollsBack() {
        String sessionId = "stream-error-session";
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("模型连接超时")));

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(sessionId, "你好", null, null, false, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertEquals(1, events.size());
        assertEquals(ChatStreamEvent.TYPE_ERROR, events.get(0).getType());
        assertTrue(events.get(0).getError().contains("AI 服务调用失败"));

        // 异常路径不写历史：无 assistant 消息，本轮用户消息已回滚
        List<ChatMessage> history = conversationAgentService.getSessionHistory(sessionId);
        assertTrue(history.stream().noneMatch(m -> "assistant".equals(m.getRole())));
        assertTrue(history.stream().noneMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void testStreamKnowledgeBaseBranchStreamsAnswer() {
        when(ragService.ragQueryWithKnowledgeBaseStream(anyString(), anyString(), anyInt()))
                .thenReturn(Flux.just(streamChunk("根据知识库，"), streamChunk("RAG 是检索增强生成。")));

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(null, "什么是RAG", null, "kb-1", false, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        // 知识库分支回答以 delta 流式下发并以 done 收尾
        assertNotNull(events);
        String joined = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_DELTA.equals(e.getType()))
                .map(ChatStreamEvent::getContent)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("根据知识库，RAG 是检索增强生成。", joined);
        assertEquals(ChatStreamEvent.TYPE_DONE, events.get(events.size() - 1).getType());
    }

    // ---------- 同步链路 MCP 工具调用（重构回归保障） ----------

    @Test
    void testSyncToolCallStillExecutesAndFeedsBack() {
        String sessionId = "sync-tool-session";
        MCPTool tool = mcpWeatherTool();
        when(mcpClientService.getAllTools()).thenReturn(List.of(tool));
        when(mcpClientService.callTool(eq(tool.getServerId()), eq("get_weather"), any()))
                .thenReturn(Map.of("city", "北京", "weather", "晴"));

        // 第一次调用返回工具协议 JSON，第二次返回最终回答
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatCallReply("{\"tool\": \"get_weather\", \"arguments\": {\"city\": \"北京\"}}"))
                .thenReturn(chatCallReply("北京今天晴。"));

        ChatResponse response = conversationAgentService
                .chat(sessionId, "北京天气如何", null, null, true, null);

        assertEquals("北京今天晴。", response.getMessage());
        verify(mcpClientService).callTool(eq(tool.getServerId()), eq("get_weather"), any());
    }

    // ---------- 流式 MCP 工具调用与深度思考（见 docs/specs/chat-streaming-output.md） ----------

    @Test
    void testStreamMcpToolCallEmitsToolEventsThenFinalAnswer() {
        String sessionId = "stream-mcp-session";
        MCPTool tool = mcpWeatherTool();
        when(mcpClientService.getAllTools()).thenReturn(List.of(tool));
        when(mcpClientService.callTool(eq(tool.getServerId()), eq("get_weather"), any()))
                .thenReturn(Map.of("city", "北京", "weather", "晴"));

        // 第一轮缓冲返回工具协议 JSON，第二轮流式返回最终回答
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(streamChunk("{\"tool\": \"get_weather\", \"arguments\": {\"city\": \"北京\"}}")))
                .thenReturn(Flux.just(streamChunk("北京今天"), streamChunk("晴。")));

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(sessionId, "北京天气如何", null, null, true, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        List<String> types = events.stream()
                .map(ChatStreamEvent::getType)
                .collect(java.util.stream.Collectors.toList());

        // 事件序列：tool_call → tool_result → delta… → done
        int callIdx = types.indexOf(ChatStreamEvent.TYPE_TOOL_CALL);
        int resultIdx = types.indexOf(ChatStreamEvent.TYPE_TOOL_RESULT);
        int deltaIdx = types.indexOf(ChatStreamEvent.TYPE_DELTA);
        assertTrue(callIdx >= 0, "应包含 tool_call 事件: " + types);
        assertTrue(resultIdx > callIdx, "tool_result 应在 tool_call 之后: " + types);
        assertTrue(deltaIdx > resultIdx, "最终回答的 delta 应在工具事件之后: " + types);
        assertEquals(ChatStreamEvent.TYPE_DONE, types.get(types.size() - 1));

        ChatStreamEvent toolCall = events.get(callIdx);
        assertEquals("get_weather", toolCall.getTool());
        assertEquals(Map.of("city", "北京"), toolCall.getArguments());

        ChatStreamEvent toolResult = events.get(resultIdx);
        assertEquals("get_weather", toolResult.getTool());
        assertEquals(Map.of("city", "北京", "weather", "晴"), toolResult.getResult());

        // 最终回答仍流式输出
        String joined = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_DELTA.equals(e.getType()))
                .map(ChatStreamEvent::getContent)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("北京今天晴。", joined);

        // 历史包含工具结果回喂条目与最终回答
        List<ChatMessage> history = conversationAgentService.getSessionHistory(sessionId);
        assertTrue(history.stream().anyMatch(m -> "user".equals(m.getRole()) && m.getContent().contains("get_weather")));
        assertTrue(history.stream().anyMatch(m -> "assistant".equals(m.getRole()) && "北京今天晴。".equals(m.getContent())));
    }

    @Test
    void testStreamMcpToolNotFoundDeliversTip() {
        String sessionId = "stream-mcp-notfound";
        when(mcpClientService.getAllTools()).thenReturn(List.of());

        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(streamChunk("{\"tool\": \"unknown_tool\", \"arguments\": {}}")));

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(sessionId, "调用一个不存在的工具", null, null, true, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        // 工具不存在时不发 tool_call 事件，以中文提示作为 delta 下发并正常 done，不中断流
        assertTrue(events.stream().noneMatch(e -> ChatStreamEvent.TYPE_TOOL_CALL.equals(e.getType())));
        String joined = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_DELTA.equals(e.getType()))
                .map(ChatStreamEvent::getContent)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("抱歉，找不到名为 'unknown_tool' 的工具。", joined);
        assertEquals(ChatStreamEvent.TYPE_DONE, events.get(events.size() - 1).getType());
    }

    @Test
    void testStreamThinkingEventsEmittedAndNotInHistory() {
        String sessionId = "stream-thinking-session";
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                thinkingChunk("", "先想一想"),
                thinkingChunk("北京今天晴", "再检查一下"),
                streamChunk("。")));

        List<ChatStreamEvent> events = conversationAgentService
                .chatStream(sessionId, "北京天气", null, null, false, null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        // 思考增量以 thinking 事件推送（空白思考增量跳过）
        List<ChatStreamEvent> thinking = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_THINKING.equals(e.getType()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, thinking.size());
        assertEquals("先想一想", thinking.get(0).getContent());
        assertEquals("再检查一下", thinking.get(1).getContent());

        // 正文增量完整拼接
        String joined = events.stream()
                .filter(e -> ChatStreamEvent.TYPE_DELTA.equals(e.getType()))
                .map(ChatStreamEvent::getContent)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("北京今天晴。", joined);

        // 思考内容不写入会话历史（多轮上下文仍只含正文）
        List<ChatMessage> history = conversationAgentService.getSessionHistory(sessionId);
        String assistantContent = history.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("北京今天晴。", assistantContent);
    }

    /**
     * 桩定流式模型回复：每个元素作为一个增量块
     */
    private void stubStreamReply(String... chunks) {
        List<org.springframework.ai.chat.model.ChatResponse> responses = Arrays.stream(chunks)
                .map(this::streamChunk)
                .collect(java.util.stream.Collectors.toList());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.fromIterable(responses));
    }

    /**
     * 构造单条流式增量响应
     */
    private org.springframework.ai.chat.model.ChatResponse streamChunk(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * 构造携带深度思考元数据的流式增量响应（reasoningContent 字段）
     */
    private org.springframework.ai.chat.model.ChatResponse thinkingChunk(String text, String thinking) {
        AssistantMessage message = AssistantMessage.builder()
                .content(text)
                .properties(Map.of("reasoningContent", thinking))
                .build();
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(message)));
    }

    /**
     * 构造同步链路单次模型响应
     */
    private org.springframework.ai.chat.model.ChatResponse chatCallReply(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * 测试用 MCP 天气工具
     */
    private MCPTool mcpWeatherTool() {
        return MCPTool.builder()
                .name("get_weather")
                .description("查询城市天气")
                .serverId("server-1")
                .build();
    }

    /**
     * 桩定 AI 回复文本
     */
    private void stubAssistantReply(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new org.springframework.ai.chat.model.ChatResponse(List.of(generation)));
    }

    /**
     * 取 Prompt 中最后一条用户消息的文本
     */
    private String lastUserText(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).getText())
                .reduce((first, second) -> second)
                .orElse("");
    }

    /**
     * 取 Prompt 中全部用户消息的文本
     */
    private List<String> allUserTexts(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).getText())
                .collect(java.util.stream.Collectors.toList());
    }
}
