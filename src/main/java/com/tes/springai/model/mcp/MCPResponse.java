package com.tes.springai.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP JSON-RPC 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {
    private String jsonrpc = "2.0";
    private String id;
    private Object result;
    private MCPError error;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MCPError {
        private int code;
        private String message;
        private Object data;
    }
}
