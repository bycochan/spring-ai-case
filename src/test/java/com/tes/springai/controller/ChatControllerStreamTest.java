package com.tes.springai.controller;

import com.tes.springai.model.Agent;
import com.tes.springai.service.AgentService;
import com.tes.springai.service.KnowledgeBaseService;
import com.tes.springai.service.MCPClientService;
import com.tes.springai.service.RAGService;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 流式聊天端点冒烟测试：验证 SSE 事件序列、响应头与提交前校验的错误语义
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerStreamTest {

    @Autowired
    private MockMvc mockMvc;

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
        Agent defaultAgent = Agent.builder()
                .id("default")
                .name("通用助手")
                .systemPrompt("你是一个智能助手。")
                .welcomeMessage("你好！")
                .enableMCP(false)
                .isDefault(true)
                .build();
        when(agentService.getAgentById("default")).thenReturn(defaultAgent);
        when(agentService.getDefaultAgent()).thenReturn(defaultAgent);
    }

    @Test
    void streamEndpointEmitsDeltaThenDoneWithNoBufferHeader() throws Exception {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                chunk("流式"),
                chunk("回复")));

        // 流式端点为异步请求：先启动异步，再等待模型流完成并派发
        MvcResult mvcResult = mockMvc.perform(post("/api/chat/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatched = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"type\":\"delta\""), "应包含 delta 事件: " + body);
        assertTrue(body.contains("流式"), "delta 应携带增量文本: " + body);
        assertTrue(body.contains("\"type\":\"done\""), "应以 done 事件收尾: " + body);
        assertTrue(body.contains("\"agentId\":\"default\""), "done 应携带 Agent 元数据: " + body);
    }

    @Test
    void streamEndpointValidationErrorsReturnHttpError() throws Exception {
        // 空白消息在 SSE 响应提交前同步抛出，仍走全局异常处理器返回 400
        mockMvc.perform(post("/api/chat/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("消息内容不能为空"));
    }

    /**
     * 构造单条流式增量响应
     */
    private org.springframework.ai.chat.model.ChatResponse chunk(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }
}
