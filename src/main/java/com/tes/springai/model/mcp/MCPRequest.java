package com.tes.springai.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP JSON-RPC 请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPRequest {
    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private Map<String, Object> params;
}
