package com.tes.springai.service.workflow.executors;

import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM节点执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMNodeExecutor implements NodeExecutor {

    private final ChatClient.Builder chatClientBuilder;
    
    @Autowired(required = false)
    private VectorStore vectorStore;

    @Override
    public Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context) {
        log.info("执行LLM节点: {}", node.getName());

        // 获取配置
        Map<String, Object> config = node.getConfig();
        String systemPrompt = (String) config.get("systemPrompt");
        String userPromptTemplate = (String) config.get("userPrompt");
        String model = (String) config.getOrDefault("model", "glm-5.3-flash");
        Double temperature = config.get("temperature") != null ? 
            ((Number) config.get("temperature")).doubleValue() : 0.7;
        
        // 增强功能配置
        Boolean enableMCP = (Boolean) config.getOrDefault("enableMCP", false);
        String knowledgeBaseId = (String) config.get("knowledgeBaseId");

        // 替换用户提示词中的变量
        String userPrompt = replaceVariables(userPromptTemplate, input);

        try {
            // 构建 ChatClient
            ChatClient.Builder builder = chatClientBuilder;
            
            // 如果启用MCP工具
            if (enableMCP != null && enableMCP) {
                log.info("启用MCP工具支持");
                // MCP工具已经在ChatClient.Builder中配置，无需额外设置
            }
            
            // 如果指定了知识库
            if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty() && vectorStore != null) {
                log.info("使用知识库: {}", knowledgeBaseId);
                // 添加RAG advisor
                builder = builder.defaultAdvisors(
                    QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build()
                );
            }
            
            // 构建提示词
            Prompt prompt;
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
                ));
            } else {
                prompt = new Prompt(new UserMessage(userPrompt));
            }

            // 调用大模型
            ChatClient chatClient = builder.build();
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();

            // 构建输出
            Map<String, Object> output = new HashMap<>();
            output.put("response", response);
            output.put("model", model);
            output.put("userPrompt", userPrompt);
            output.put("enabledMCP", enableMCP);
            output.put("knowledgeBaseId", knowledgeBaseId);

            return output;
        } catch (Exception e) {
            log.error("LLM节点执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("LLM节点执行失败: " + e.getMessage());
        }
    }

    /**
     * 替换变量
     */
    private String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }

        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = extractValue(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * 提取值，处理嵌套的Map和List
     */
    private String extractValue(Object value) {
        if (value == null) {
            return "";
        }
        
        // 如果是Map，尝试提取单个值或转换为JSON
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.size() == 1) {
                // 如果只有一个键值对，直接返回值
                return extractValue(map.values().iterator().next());
            }
            // 多个键值对，返回JSON格式
            try {
                return new com.google.gson.Gson().toJson(map);
            } catch (Exception e) {
                return map.toString();
            }
        }
        
        // 如果是List，转换为JSON
        if (value instanceof List) {
            try {
                return new com.google.gson.Gson().toJson(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        
        return value.toString();
    }

    @Override
    public WorkflowNode.NodeType getSupportedType() {
        return WorkflowNode.NodeType.LLM;
    }
}
