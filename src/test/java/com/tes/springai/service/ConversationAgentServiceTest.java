package com.tes.springai.service;

import com.tes.springai.model.Agent;
import com.tes.springai.model.ChatResponse;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ConversationAgentService 测试类
 */
@SpringBootTest
class ConversationAgentServiceTest {

    @Autowired
    private ConversationAgentService conversationAgentService;

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
        
        // Mock 代码助手 Agent
        Agent codeAgent = Agent.builder()
                .id("code-assistant")
                .name("代码助手")
                .description("专注于编程和技术问题的 AI 助手")
                .avatar("🧑‍💻")
                .systemPrompt("你是一个专业的编程助手。")
                .welcomeMessage("你好！我是你的代码助手。")
                .enableMCP(false)
                .build();
        
        // 设置 Mock 行为
        when(agentService.getAgentById("default")).thenReturn(defaultAgent);
        when(agentService.getAgentById("code-assistant")).thenReturn(codeAgent);
        when(agentService.getDefaultAgent()).thenReturn(defaultAgent);
        
        // 当传入 null 或空字符串时，返回默认 Agent
        when(agentService.getAgentById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if ("default".equals(id)) {
                return defaultAgent;
            } else if ("code-assistant".equals(id)) {
                return codeAgent;
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
        com.tes.springai.model.ChatResponse response = conversationAgentService.chat(null, "你好", null, false, null);
        
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
            conversationAgentService.chat(sessionId, "你叫什么名字？", null, false, null);
        
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
            conversationAgentService.chat(sessionId, "你刚才说你叫什么？", null, false, null);
        
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
        conversationAgentService.chat(sessionId, "你好", null, false, null);
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
            conversationAgentService.chat(null, "帮我查看这段代码", null, false, agentId);
        
        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertNotNull(response.getAgentId());
        assertEquals("我是代码助手，可以帮你解决编程问题。", response.getMessage());
    }
}
