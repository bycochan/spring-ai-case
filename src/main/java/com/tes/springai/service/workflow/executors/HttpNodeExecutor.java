package com.tes.springai.service.workflow.executors;

import com.google.gson.Gson;
import com.tes.springai.model.workflow.WorkflowExecutionContext;
import com.tes.springai.model.workflow.WorkflowNode;
import com.tes.springai.service.workflow.NodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP节点执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpNodeExecutor implements NodeExecutor {

    private final Gson gson;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public Object execute(WorkflowNode node, Map<String, Object> input, WorkflowExecutionContext context) {
        log.info("执行HTTP节点: {}", node.getName());

        // 获取配置
        Map<String, Object> config = node.getConfig();
        String method = (String) config.getOrDefault("method", "GET");
        String url = (String) config.get("url");
        Map<String, String> headers = (Map<String, String>) config.get("headers");
        Object body = config.get("body");

        // 替换URL中的变量
        url = replaceVariables(url, input);

        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);

            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), replaceVariables(entry.getValue(), input));
                }
            }

            // 构建请求体
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                String bodyJson = body != null ? gson.toJson(body) : "{}";
                bodyJson = replaceVariables(bodyJson, input);
                RequestBody requestBody = RequestBody.create(
                    bodyJson,
                    MediaType.parse("application/json")
                );
                requestBuilder.method(method, requestBody);
            } else {
                requestBuilder.method(method, null);
            }

            // 执行请求
            Request request = requestBuilder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                // 构建输出
                Map<String, Object> output = new HashMap<>();
                output.put("statusCode", response.code());
                output.put("body", responseBody);
                output.put("success", response.isSuccessful());

                return output;
            }
        } catch (Exception e) {
            log.error("HTTP节点执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP节点执行失败: " + e.getMessage());
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
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    @Override
    public WorkflowNode.NodeType getSupportedType() {
        return WorkflowNode.NodeType.HTTP;
    }
}
