package com.tes.springai.service;

import com.tes.springai.model.Agent;
import com.tes.springai.model.ChatResponse;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
