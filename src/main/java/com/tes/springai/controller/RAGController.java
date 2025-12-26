package com.tes.springai.controller;

import com.tes.springai.model.KnowledgeBase;
import com.tes.springai.service.KnowledgeBaseService;
import com.tes.springai.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
        
        log.info("收到文档上传请求: {}, 大小: {} bytes, 知识库ID: {}", 
                file.getOriginalFilename(), file.getSize(), knowledgeBaseId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件为空");
            }
            
            log.info("开始上传和索引文档: {}", file.getOriginalFilename());
            String documentId = ragService.uploadAndIndexDocument(file);
            log.info("文档上传和索引成功，文档ID: {}", documentId);
            
            // 如果指定了知识库，将文档添加到知识库
            if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
                log.info("将文档 {} 添加到知识库 {}", documentId, knowledgeBaseId);
                knowledgeBaseService.addDocumentToKnowledgeBase(knowledgeBaseId, documentId);
                log.info("文档已成功添加到知识库");
            }
            
            response.put("success", true);
            response.put("documentId", documentId);
            response.put("fileName", file.getOriginalFilename());
            response.put("message", "文档上传并索引成功");
            
            log.info("文档上传流程全部完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("文档上传失败: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "文档上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * RAG 问答
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        Integer topK = request.get("topK") != null ? (Integer) request.get("topK") : 3;
        
        log.info("收到 RAG 查询: {}", question);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String answer = ragService.ragQuery(question, topK);
            
            response.put("success", true);
            response.put("question", question);
            response.put("answer", answer);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("RAG 查询失败", e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        log.info("删除文档: {}", documentId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            ragService.deleteDocument(documentId);
            knowledgeBaseService.removeDocumentFromKnowledgeBase(documentId);
            
            response.put("success", true);
            response.put("message", "文档删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("文档删除失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ==================== 知识库管理 ====================

    /**
     * 创建知识库
     */
    @PostMapping("/knowledge-base")
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String description = request.get("description");
        
        log.info("创建知识库: {}", name);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            KnowledgeBase kb = knowledgeBaseService.createKnowledgeBase(name, description);
            
            response.put("success", true);
            response.put("knowledgeBase", kb);
            response.put("message", "知识库创建成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("知识库创建失败", e);
            response.put("success", false);
            response.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取所有知识库
     */
    @GetMapping("/knowledge-bases")
    public ResponseEntity<List<KnowledgeBase>> getAllKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseService.getAllKnowledgeBases();
        return ResponseEntity.ok(knowledgeBases);
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/knowledge-base/{id}")
    public ResponseEntity<KnowledgeBase> getKnowledgeBase(@PathVariable String id) {
        KnowledgeBase kb = knowledgeBaseService.getKnowledgeBase(id);
        if (kb != null) {
            return ResponseEntity.ok(kb);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/knowledge-base/{id}")
    public ResponseEntity<Map<String, Object>> deleteKnowledgeBase(@PathVariable String id) {
        log.info("删除知识库: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            knowledgeBaseService.deleteKnowledgeBase(id);
            
            response.put("success", true);
            response.put("message", "知识库删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("知识库删除失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
