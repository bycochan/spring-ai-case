package com.tes.springai.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 服务器配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPServer {
    private String id;
    private String name;
    private String description;
    private String url;
    private MCPTransportType transportType;
    private boolean enabled;
    
    public enum MCPTransportType {
        STDIO,      // 标准输入输出
        HTTP,       // HTTP/SSE
        WEBSOCKET   // WebSocket
    }
}
