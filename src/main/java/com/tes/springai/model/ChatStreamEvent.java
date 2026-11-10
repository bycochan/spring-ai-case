package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式对话事件（SSE data 载荷）
 * 以 type 字段区分事件类型，事件序列见 docs/specs/chat-streaming-output.md：
 * - delta：正文增量文本
 * - thinking：深度思考增量文本
 * - tool_call：MCP 工具调用（工具名 + 参数）
 * - tool_result：工具执行结果（工具名 + 结果）
 * - done：流结束元数据，对齐 ChatResponse 的会话字段
 * - error：流内错误（中文提示；SSE 响应提交后无法再改状态码，错误必须在流内表达）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    /**
     * 事件类型（必填）
     */
    private String type;

    /**
     * 增量文本（delta / thinking）
     */
    private String content;

    /**
     * 工具名称（tool_call / tool_result）
     */
    private String tool;

    /**
     * 工具调用参数（tool_call）
     */
    private Object arguments;

    /**
     * 工具执行结果（tool_result）
     */
    private Object result;

    /**
     * 会话ID（done）
     */
    private String sessionId;

    /**
     * 当前会话的对话轮数（done）
     */
    private Integer conversationRound;

    /**
     * 开场白，仅新对话首次返回（done）
     */
    private String welcomeMessage;

    /**
     * 使用的 Agent ID（done）
     */
    private String agentId;

    /**
     * 错误提示（error，中文）
     */
    private String error;
}
