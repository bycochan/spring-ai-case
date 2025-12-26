package com.tes.springai.controller;

import com.tes.springai.model.mcp.MCPServer;
import com.tes.springai.model.mcp.MCPTool;
import com.tes.springai.service.MCPClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class MCPController {

    private final MCPClientService mcpClientService;

    /**
     * 注册 MCP 服务器
     */
    @PostMapping("/servers")
    public ResponseEntity<Map<String, Object>> registerServer(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String description = request.get("description");
            String url = request.get("url");
            String transportType = request.getOrDefault("transportType", "HTTP");

            MCPServer server = mcpClientService.registerServer(
                name, 
                description, 
                url, 
                MCPServer.MCPTransportType.valueOf(transportType)
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("server", server);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("注册服务器失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取所有服务器
     */
    @GetMapping("/servers")
    public ResponseEntity<List<MCPServer>> getAllServers() {
        return ResponseEntity.ok(mcpClientService.getAllServers());
    }

    /**
     * 删除服务器
     */
    @DeleteMapping("/servers/{serverId}")
    public ResponseEntity<Map<String, Object>> removeServer(@PathVariable String serverId) {
        try {
            mcpClientService.removeServer(serverId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除服务器失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 启用/禁用服务器
     */
    @PutMapping("/servers/{serverId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleServer(
            @PathVariable String serverId,
            @RequestBody Map<String, Boolean> request) {
        try {
            boolean enabled = request.getOrDefault("enabled", true);
            mcpClientService.toggleServer(serverId, enabled);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("切换服务器状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取所有工具
     */
    @GetMapping("/tools")
    public ResponseEntity<List<MCPTool>> getAllTools() {
        return ResponseEntity.ok(mcpClientService.getAllTools());
    }

    /**
     * 获取指定服务器的工具
     */
    @GetMapping("/servers/{serverId}/tools")
    public ResponseEntity<List<MCPTool>> getServerTools(@PathVariable String serverId) {
        try {
            List<MCPTool> tools = mcpClientService.listTools(serverId);
            return ResponseEntity.ok(tools);
        } catch (Exception e) {
            log.error("获取工具列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 调用工具
     */
    @PostMapping("/tools/call")
    public ResponseEntity<Map<String, Object>> callTool(@RequestBody Map<String, Object> request) {
        try {
            String serverId = (String) request.get("serverId");
            String toolName = (String) request.get("toolName");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");

            Object result = mcpClientService.callTool(serverId, toolName, arguments);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("调用工具失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
