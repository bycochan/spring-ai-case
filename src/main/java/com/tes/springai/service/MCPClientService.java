package com.tes.springai.service;

import com.google.gson.Gson;
import com.tes.springai.model.mcp.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端服务
 */
@Slf4j
@Service
public class MCPClientService {

    private final Map<String, MCPServer> servers = new ConcurrentHashMap<>();
    private final Map<String, List<MCPTool>> serverTools = new ConcurrentHashMap<>();
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final AtomicLong requestIdCounter = new AtomicLong(0);

    public MCPClientService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * 注册 MCP 服务器
     */
    public MCPServer registerServer(String name, String description, String url, 
                                   MCPServer.MCPTransportType transportType) {
        String id = UUID.randomUUID().toString();
        MCPServer server = MCPServer.builder()
                .id(id)
                .name(name)
                .description(description)
                .url(url)
                .transportType(transportType)
                .enabled(true)
                .build();
        
        servers.put(id, server);
        log.info("注册 MCP 服务器: {} ({})", name, url);
        
        // 初始化连接并获取工具列表
        try {
            initializeServer(server);
        } catch (Exception e) {
            log.error("初始化 MCP 服务器失败: {}", e.getMessage());
        }
        
        return server;
    }

    /**
     * 初始化服务器连接
     */
    private void initializeServer(MCPServer server) throws IOException {
        // 发送初始化请求
        MCPRequest initRequest = MCPRequest.builder()
                .id(String.valueOf(requestIdCounter.incrementAndGet()))
                .method("initialize")
                .params(Map.of(
                    "protocolVersion", "2024-11-05",
                    "clientInfo", Map.of(
                        "name", "spring-ai-agent",
                        "version", "1.0.0"
                    )
                ))
                .build();
        
        MCPResponse response = sendRequest(server, initRequest);
        log.info("MCP 服务器初始化成功: {}", server.getName());
        
        // 获取工具列表
        listTools(server.getId());
    }

    /**
     * 获取工具列表
     */
    public List<MCPTool> listTools(String serverId) {
        MCPServer server = servers.get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("服务器不存在: " + serverId);
        }

        try {
            MCPRequest request = MCPRequest.builder()
                    .id(String.valueOf(requestIdCounter.incrementAndGet()))
                    .method("tools/list")
                    .params(new HashMap<>())
                    .build();

            MCPResponse response = sendRequest(server, request);
            
            if (response.getError() != null) {
                throw new RuntimeException("获取工具列表失败: " + response.getError().getMessage());
            }

            // 解析工具列表
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolsData = (List<Map<String, Object>>) result.get("tools");
            
            List<MCPTool> tools = new ArrayList<>();
            if (toolsData != null) {
                for (Map<String, Object> toolData : toolsData) {
                    MCPTool tool = MCPTool.builder()
                            .name((String) toolData.get("name"))
                            .description((String) toolData.get("description"))
                            .inputSchema((Map<String, Object>) toolData.get("inputSchema"))
                            .serverId(serverId)
                            .build();
                    tools.add(tool);
                }
            }
            
            serverTools.put(serverId, tools);
            log.info("服务器 {} 提供 {} 个工具", server.getName(), tools.size());
            return tools;
            
        } catch (Exception e) {
            log.error("获取工具列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 调用工具
     */
    public Object callTool(String serverId, String toolName, Map<String, Object> arguments) {
        MCPServer server = servers.get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("服务器不存在: " + serverId);
        }

        try {
            MCPRequest request = MCPRequest.builder()
                    .id(String.valueOf(requestIdCounter.incrementAndGet()))
                    .method("tools/call")
                    .params(Map.of(
                        "name", toolName,
                        "arguments", arguments != null ? arguments : new HashMap<>()
                    ))
                    .build();

            MCPResponse response = sendRequest(server, request);
            
            if (response.getError() != null) {
                throw new RuntimeException("调用工具失败: " + response.getError().getMessage());
            }

            return response.getResult();
            
        } catch (Exception e) {
            log.error("调用工具失败: {}", e.getMessage());
            throw new RuntimeException("调用工具失败: " + e.getMessage());
        }
    }

    /**
     * 发送请求到 MCP 服务器
     */
    private MCPResponse sendRequest(MCPServer server, MCPRequest request) throws IOException {
        String jsonRequest = gson.toJson(request);
        log.debug("发送 MCP 请求: {}", jsonRequest);

        RequestBody body = RequestBody.create(
            jsonRequest, 
            MediaType.get("application/json; charset=utf-8")
        );

        Request httpRequest = new Request.Builder()
                .url(server.getUrl())
                .post(body)
                .build();

        try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
            if (!httpResponse.isSuccessful()) {
                throw new IOException("HTTP 请求失败: " + httpResponse.code());
            }

            String responseBody = httpResponse.body().string();
            log.debug("收到 MCP 响应: {}", responseBody);
            
            return gson.fromJson(responseBody, MCPResponse.class);
        }
    }

    /**
     * 获取所有服务器
     */
    public List<MCPServer> getAllServers() {
        return new ArrayList<>(servers.values());
    }

    /**
     * 获取所有工具
     */
    public List<MCPTool> getAllTools() {
        List<MCPTool> allTools = new ArrayList<>();
        serverTools.values().forEach(allTools::addAll);
        return allTools;
    }

    /**
     * 删除服务器
     */
    public void removeServer(String serverId) {
        servers.remove(serverId);
        serverTools.remove(serverId);
        log.info("删除 MCP 服务器: {}", serverId);
    }

    /**
     * 启用/禁用服务器
     */
    public void toggleServer(String serverId, boolean enabled) {
        MCPServer server = servers.get(serverId);
        if (server != null) {
            server.setEnabled(enabled);
            log.info("MCP 服务器 {} 状态: {}", server.getName(), enabled ? "启用" : "禁用");
        }
    }
}
