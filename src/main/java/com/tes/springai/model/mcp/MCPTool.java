package com.tes.springai.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPTool {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
    private String serverId;
}
