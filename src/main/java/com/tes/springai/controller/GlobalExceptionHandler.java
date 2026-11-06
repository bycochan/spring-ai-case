package com.tes.springai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理：
 * 业务校验类异常返回 400/503 并携带具体错误信息（前端读取 error.response.data.error 展示），
 * 其余未预期异常记录完整堆栈后返回 500，避免默认响应只暴露 "Internal Server Error"。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数/业务校验失败（如消息为空、附件失效、Agent 未开启附件上传等）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("业务校验失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
    }

    /**
     * 外部依赖调用失败（如 AI 服务调用失败、模型返回空结果），客户端可稍后重试
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        log.error("服务调用失败: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(e.getMessage()));
    }

    /**
     * 未预期异常：记录完整堆栈便于排查，响应只暴露简要信息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("未处理的服务器异常", e);
        return ResponseEntity.internalServerError()
                .body(errorBody("服务器内部错误，请稍后重试"));
    }

    /**
     * 统一错误响应体：error 与 message 同值，兼容前端两种读取方式
     */
    private Map<String, String> errorBody(String msg) {
        return Map.of("error", msg == null ? "未知错误" : msg,
                "message", msg == null ? "未知错误" : msg);
    }
}
