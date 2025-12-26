package com.tes.springai.controller;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 测试 MCP 服务器
 * 提供符合 MCP 协议的工具接口
 */
@Slf4j
@RestController
@RequestMapping("/mcp-server")
public class MCPServerController {

    private final Gson gson = new Gson();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * MCP 协议统一入口
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleMCPRequest(@RequestBody Map<String, Object> request) {
        log.info("收到 MCP 请求: {}", request);

        String method = (String) request.get("method");
        String id = (String) request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            Object result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolCall(params);
                default -> throw new IllegalArgumentException("未知的方法: " + method);
            };
            response.put("result", result);
        } catch (Exception e) {
            log.error("处理 MCP 请求失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", -32603);
            error.put("message", e.getMessage());
            response.put("error", error);
        }

        log.info("返回 MCP 响应: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * 初始化
     */
    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Spring AI Test MCP Server");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);
        
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();
        tools.put("listChanged", false);
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);
        
        return result;
    }

    /**
     * 获取工具列表
     */
    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 工具1：获取当前时间
        Map<String, Object> getTimeTool = new HashMap<>();
        getTimeTool.put("name", "get_current_time");
        getTimeTool.put("description", "获取当前系统时间");
        Map<String, Object> getTimeSchema = new HashMap<>();
        getTimeSchema.put("type", "object");
        getTimeSchema.put("properties", Map.of());
        getTimeTool.put("inputSchema", getTimeSchema);
        tools.add(getTimeTool);

        // 工具2：计算器
        Map<String, Object> calculatorTool = new HashMap<>();
        calculatorTool.put("name", "calculator");
        calculatorTool.put("description", "执行基本的数学计算（加减乘除）");
        Map<String, Object> calcSchema = new HashMap<>();
        calcSchema.put("type", "object");
        Map<String, Object> calcProperties = new HashMap<>();
        calcProperties.put("expression", Map.of(
            "type", "string",
            "description", "数学表达式，例如: '2 + 3', '10 * 5'"
        ));
        calcSchema.put("properties", calcProperties);
        calcSchema.put("required", List.of("expression"));
        calculatorTool.put("inputSchema", calcSchema);
        tools.add(calculatorTool);

        // 工具3：生成随机数
        Map<String, Object> randomTool = new HashMap<>();
        randomTool.put("name", "random_number");
        randomTool.put("description", "生成指定范围内的随机整数");
        Map<String, Object> randomSchema = new HashMap<>();
        randomSchema.put("type", "object");
        Map<String, Object> randomProperties = new HashMap<>();
        randomProperties.put("min", Map.of(
            "type", "number",
            "description", "最小值（包含）"
        ));
        randomProperties.put("max", Map.of(
            "type", "number",
            "description", "最大值（包含）"
        ));
        randomSchema.put("properties", randomProperties);
        randomSchema.put("required", List.of("min", "max"));
        randomTool.put("inputSchema", randomSchema);
        tools.add(randomTool);

        // 工具4：文本转大写
        Map<String, Object> upperCaseTool = new HashMap<>();
        upperCaseTool.put("name", "to_uppercase");
        upperCaseTool.put("description", "将文本转换为大写");
        Map<String, Object> upperSchema = new HashMap<>();
        upperSchema.put("type", "object");
        Map<String, Object> upperProperties = new HashMap<>();
        upperProperties.put("text", Map.of(
            "type", "string",
            "description", "要转换的文本"
        ));
        upperSchema.put("properties", upperProperties);
        upperSchema.put("required", List.of("text"));
        upperCaseTool.put("inputSchema", upperSchema);
        tools.add(upperCaseTool);

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return result;
    }

    /**
     * 调用工具
     */
    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        String name = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        Object result = switch (name) {
            case "get_current_time" -> getCurrentTime();
            case "calculator" -> calculate(arguments);
            case "random_number" -> generateRandomNumber(arguments);
            case "to_uppercase" -> toUpperCase(arguments);
            default -> throw new IllegalArgumentException("未知的工具: " + name);
        };

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", gson.toJson(result));
        content.add(contentItem);

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        return response;
    }

    /**
     * 获取当前时间
     */
    private Map<String, Object> getCurrentTime() {
        Map<String, Object> result = new HashMap<>();
        result.put("currentTime", LocalDateTime.now().format(formatter));
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 计算器
     */
    private Map<String, Object> calculate(Map<String, Object> arguments) {
        String expression = (String) arguments.get("expression");
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }

        // 简单的计算实现（仅支持基本运算）
        expression = expression.replaceAll("\\s+", "");
        double result;
        
        try {
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                result = Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-");
                result = Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]);
            } else if (expression.contains("*")) {
                String[] parts = expression.split("\\*");
                result = Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
            } else if (expression.contains("/")) {
                String[] parts = expression.split("/");
                result = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            } else {
                result = Double.parseDouble(expression);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的表达式: " + expression);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("expression", expression);
        response.put("result", result);
        return response;
    }

    /**
     * 生成随机数
     */
    private Map<String, Object> generateRandomNumber(Map<String, Object> arguments) {
        Number minNum = (Number) arguments.get("min");
        Number maxNum = (Number) arguments.get("max");
        
        if (minNum == null || maxNum == null) {
            throw new IllegalArgumentException("min 和 max 参数不能为空");
        }

        int min = minNum.intValue();
        int max = maxNum.intValue();
        
        if (min > max) {
            throw new IllegalArgumentException("min 不能大于 max");
        }

        Random random = new Random();
        int randomNumber = random.nextInt(max - min + 1) + min;

        Map<String, Object> result = new HashMap<>();
        result.put("min", min);
        result.put("max", max);
        result.put("randomNumber", randomNumber);
        return result;
    }

    /**
     * 文本转大写
     */
    private Map<String, Object> toUpperCase(Map<String, Object> arguments) {
        String text = (String) arguments.get("text");
        if (text == null) {
            throw new IllegalArgumentException("text 参数不能为空");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("originalText", text);
        result.put("uppercaseText", text.toUpperCase());
        return result;
    }
}
