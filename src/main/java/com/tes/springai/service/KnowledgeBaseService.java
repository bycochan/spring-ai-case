package com.tes.springai.service;

import com.tes.springai.model.KnowledgeBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    // 存储知识库信息
    private final Map<String, KnowledgeBase> knowledgeBases = new ConcurrentHashMap<>();
    
    // 存储文档到知识库的映射
    private final Map<String, String> documentToKnowledgeBase = new ConcurrentHashMap<>();

    /**
     * 创建知识库
     */
    public KnowledgeBase createKnowledgeBase(String name, String description) {
        String id = UUID.randomUUID().toString();
        
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(id)
                .name(name)
                .description(description)
                .documentIds(new ArrayList<>())
                .documentCount(0)
                .createTime(LocalDateTime.now())
                .build();
        
        knowledgeBases.put(id, kb);
        log.info("创建知识库: {}, ID: {}", name, id);
        
        return kb;
    }

    /**
     * 获取所有知识库
     */
    public List<KnowledgeBase> getAllKnowledgeBases() {
        return new ArrayList<>(knowledgeBases.values());
    }

    /**
     * 获取知识库详情
     */
    public KnowledgeBase getKnowledgeBase(String id) {
        return knowledgeBases.get(id);
    }

    /**
     * 添加文档到知识库
     */
    public void addDocumentToKnowledgeBase(String knowledgeBaseId, String documentId) {
        KnowledgeBase kb = knowledgeBases.get(knowledgeBaseId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + knowledgeBaseId);
        }
        
        kb.getDocumentIds().add(documentId);
        kb.setDocumentCount(kb.getDocumentIds().size());
        documentToKnowledgeBase.put(documentId, knowledgeBaseId);
        
        log.info("添加文档 {} 到知识库 {}", documentId, kb.getName());
    }

    /**
     * 从知识库移除文档
     */
    public void removeDocumentFromKnowledgeBase(String documentId) {
        String kbId = documentToKnowledgeBase.remove(documentId);
        if (kbId != null) {
            KnowledgeBase kb = knowledgeBases.get(kbId);
            if (kb != null) {
                kb.getDocumentIds().remove(documentId);
                kb.setDocumentCount(kb.getDocumentIds().size());
            }
        }
    }

    /**
     * 删除知识库
     */
    public void deleteKnowledgeBase(String id) {
        KnowledgeBase kb = knowledgeBases.remove(id);
        if (kb != null) {
            // 清除文档映射
            for (String docId : kb.getDocumentIds()) {
                documentToKnowledgeBase.remove(docId);
            }
            log.info("删除知识库: {}", kb.getName());
        }
    }

    /**
     * 获取知识库的文档列表
     */
    public List<String> getDocumentIds(String knowledgeBaseId) {
        KnowledgeBase kb = knowledgeBases.get(knowledgeBaseId);
        return kb != null ? kb.getDocumentIds() : new ArrayList<>();
    }
}
