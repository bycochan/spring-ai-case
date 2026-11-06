package com.tes.springai.controller;

import com.tes.springai.model.Attachment;
import com.tes.springai.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 公共附件接口
 * 供聊天等多功能场景上传、读取、删除附件；支持图片与 PDF/Word/TXT 文档。
 */
@Slf4j
@RestController
@RequestMapping("/api/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 上传附件（支持图片与 PDF/Word/TXT 文档）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        log.info("收到附件上传请求: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        Map<String, Object> response = new HashMap<>();
        try {
            Attachment attachment = attachmentService.upload(file);

            response.put("success", true);
            response.put("fileId", attachment.getFileId());
            response.put("fileName", attachment.getFileName());
            response.put("contentType", attachment.getContentType());
            response.put("size", attachment.getSize());
            response.put("message", "附件上传成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("附件上传失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 读取附件内容（inline 返回图片字节，供前端 img 标签直接展示与历史回显）
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> getAttachment(@PathVariable String fileId) {
        try {
            Attachment attachment = attachmentService.getAttachment(fileId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(attachment.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(attachment.getData());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 删除附件（消息发送前移除附件时调用，释放内存）
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String fileId) {
        log.info("收到附件删除请求: {}", fileId);

        Map<String, Object> response = new HashMap<>();
        try {
            attachmentService.delete(fileId);

            response.put("success", true);
            response.put("message", "附件删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
