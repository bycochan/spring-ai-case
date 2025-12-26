package com.tes.springai.service;

import com.tes.springai.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorStoreService 测试类
 */
@SpringBootTest
class VectorStoreServiceTest {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void testInMemoryInsertAndSearch() {
        // 创建测试文档块
        List<DocumentChunk> chunks = new ArrayList<>();
        
        String doc1Text = "Spring Boot 是一个基于 Spring 框架的快速开发框架";
        String doc2Text = "人工智能是计算机科学的一个分支";
        String doc3Text = "机器学习是人工智能的一个重要领域";
        
        DocumentChunk chunk1 = DocumentChunk.builder()
                .documentId("doc1")
                .content(doc1Text)
                .fileName("spring.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding(doc1Text))
                .build();
        
        DocumentChunk chunk2 = DocumentChunk.builder()
                .documentId("doc2")
                .content(doc2Text)
                .fileName("ai.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding(doc2Text))
                .build();
        
        DocumentChunk chunk3 = DocumentChunk.builder()
                .documentId("doc3")
                .content(doc3Text)
                .fileName("ml.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding(doc3Text))
                .build();
        
        chunks.add(chunk1);
        chunks.add(chunk2);
        chunks.add(chunk3);
        
        // 插入文档块
        vectorStoreService.insertChunks(chunks);
        
        // 搜索测试 - 搜索与"人工智能"相关的内容
        String query = "人工智能和机器学习";
        List<Float> queryVector = embeddingService.generateEmbedding(query);
        List<DocumentChunk> results = vectorStoreService.search(queryVector, 2);
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.size() > 0);
        assertTrue(results.size() <= 2);
        
        // 验证返回的结果包含相关内容
        boolean foundAIContent = results.stream()
                .anyMatch(chunk -> chunk.getContent().contains("人工智能") || chunk.getContent().contains("机器学习"));
        assertTrue(foundAIContent, "搜索结果应包含与人工智能或机器学习相关的内容");
        
        System.out.println("搜索查询: " + query);
        System.out.println("搜索结果数量: " + results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.println("结果 " + (i + 1) + ": " + results.get(i).getContent());
        }
    }

    @Test
    void testInMemorySearchInDocuments() {
        // 创建测试文档块
        List<DocumentChunk> chunks = new ArrayList<>();
        
        DocumentChunk chunk1 = DocumentChunk.builder()
                .documentId("tech-doc")
                .content("Java 是一种面向对象的编程语言")
                .fileName("java.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding("Java 是一种面向对象的编程语言"))
                .build();
        
        DocumentChunk chunk2 = DocumentChunk.builder()
                .documentId("tech-doc")
                .content("Python 是一种简洁易学的编程语言")
                .fileName("python.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding("Python 是一种简洁易学的编程语言"))
                .build();
        
        DocumentChunk chunk3 = DocumentChunk.builder()
                .documentId("other-doc")
                .content("今天天气很好")
                .fileName("weather.txt")
                .pageNumber(1)
                .embedding(embeddingService.generateEmbedding("今天天气很好"))
                .build();
        
        chunks.add(chunk1);
        chunks.add(chunk2);
        chunks.add(chunk3);
        
        vectorStoreService.insertChunks(chunks);
        
        // 在指定文档中搜索
        String query = "编程语言";
        List<Float> queryVector = embeddingService.generateEmbedding(query);
        List<String> documentIds = List.of("tech-doc");
        List<DocumentChunk> results = vectorStoreService.searchInDocuments(queryVector, documentIds, 2);
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.size() > 0);
        
        // 所有结果都应该来自 tech-doc
        boolean allFromTechDoc = results.stream()
                .allMatch(chunk -> "tech-doc".equals(chunk.getDocumentId()));
        assertTrue(allFromTechDoc, "所有结果都应该来自 tech-doc");
        
        System.out.println("在文档 tech-doc 中搜索: " + query);
        System.out.println("搜索结果数量: " + results.size());
        for (DocumentChunk result : results) {
            System.out.println("- " + result.getContent());
        }
    }

    @Test
    void testInMemoryDeleteDocument() {
        // 插入测试数据
        List<DocumentChunk> chunks = new ArrayList<>();
        
        DocumentChunk chunk1 = DocumentChunk.builder()
                .documentId("delete-test")
                .content("这是要删除的文档")
                .embedding(embeddingService.generateEmbedding("这是要删除的文档"))
                .build();
        
        chunks.add(chunk1);
        vectorStoreService.insertChunks(chunks);
        
        // 搜索验证已插入
        List<Float> queryVector = embeddingService.generateEmbedding("删除");
        List<DocumentChunk> beforeDelete = vectorStoreService.search(queryVector, 5);
        long countBefore = beforeDelete.stream()
                .filter(c -> "delete-test".equals(c.getDocumentId()))
                .count();
        assertTrue(countBefore > 0, "删除前应该能找到文档");
        
        // 删除文档
        vectorStoreService.deleteDocument("delete-test");
        
        // 再次搜索验证已删除
        List<DocumentChunk> afterDelete = vectorStoreService.search(queryVector, 5);
        long countAfter = afterDelete.stream()
                .filter(c -> "delete-test".equals(c.getDocumentId()))
                .count();
        assertEquals(0, countAfter, "删除后应该找不到文档");
        
        System.out.println("删除测试通过");
    }
}
