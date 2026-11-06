package com.tes.springai.service;

import com.tes.springai.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG (检索增强生成) 服务
 * 整合文档解析、向量存储和AI对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final DocumentParserService documentParserService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ChatModel chatModel;
    private final KnowledgeBaseService knowledgeBaseService;

    // 文本分块参数
    private static final int CHUNK_SIZE = 500;  // 每块500字符
    private static final int CHUNK_OVERLAP = 50;  // 重叠50字符

    /**
     * 上传并索引文档
     */
    public String uploadAndIndexDocument(MultipartFile file) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        
        log.info("开始处理文档: {}, ID: {}", fileName, documentId);

        // 1. 解析文档
        String content = documentParserService.parseDocument(file);
        
        // 2. 文本分块
        List<String> textChunks = documentParserService.splitTextIntoChunks(
                content, CHUNK_SIZE, CHUNK_OVERLAP);
        
        // 3. 生成嵌入向量
        List<List<Float>> embeddings = embeddingService.generateEmbeddings(textChunks);
        
        // 4. 构建文档块
        List<DocumentChunk> documentChunks = new ArrayList<>();
        for (int i = 0; i < textChunks.size(); i++) {
            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .chunkId(UUID.randomUUID().toString())
                    .content(textChunks.get(i))
                    .embedding(embeddings.get(i))
                    .fileName(fileName)
                    .pageNumber(i / 2)  // 粗略估计页码
                    .build();
            documentChunks.add(chunk);
        }
        
        // 5. 存储到向量数据库
        vectorStoreService.insertChunks(documentChunks);
        
        log.info("文档索引完成: {}, 共 {} 个块", fileName, textChunks.size());
        return documentId;
    }

    /**
     * RAG 问答
     */
    public String ragQuery(String question, int topK) {
        log.info("RAG 查询: {}", question);

        // 1. 将问题转换为向量
        List<Float> queryVector = embeddingService.generateEmbedding(question);
        
        // 2. 向量搜索相关文档
        List<DocumentChunk> relevantChunks = vectorStoreService.search(queryVector, topK);
        
        if (relevantChunks.isEmpty()) {
            return "抱歉，没有找到相关文档信息。";
        }
        
        // 3. 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("以下是相关的文档片段：\n\n");
        
        for (int i = 0; i < relevantChunks.size(); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            context.append(String.format("[片段 %d - 来自文件: %s]\n", i + 1, chunk.getFileName()));
            context.append(chunk.getContent()).append("\n\n");
        }
        
        // 4. 构建提示词
        String systemPrompt = "你是一个智能文档助手。请基于提供的文档片段回答用户的问题。" +
                "如果文档中没有相关信息，请明确告知用户。回答要准确、简洁。";
        
        String userPrompt = context.toString() + 
                "\n问题：" + question + 
                "\n\n请基于以上文档片段回答问题：";
        
        // 5. 调用 AI 模型
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        Prompt prompt = new Prompt(messages);
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        String answer = ((org.springframework.ai.chat.messages.AssistantMessage) response.getResult().getOutput()).getText();

        log.info("RAG 查询完成");
        return answer;
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) {
        log.info("删除文档: {}", documentId);
        vectorStoreService.deleteDocument(documentId);
    }

    /**
     * 基于知识库的 RAG 问答
     */
    public String ragQueryWithKnowledgeBase(String question, String knowledgeBaseId, int topK) {
        log.info("RAG 知识库查询: {}, 知识库: {}", question, knowledgeBaseId);

        // 获取知识库的文档列表
        List<String> documentIds = knowledgeBaseService.getDocumentIds(knowledgeBaseId);
        
        if (documentIds.isEmpty()) {
            return "抱歉，该知识库中还没有文档。";
        }

        // 将问题转换为向量
        List<Float> queryVector = embeddingService.generateEmbedding(question);
        
        // 向量搜索相关文档（需要过滤知识库内的文档）
        List<DocumentChunk> relevantChunks = vectorStoreService.searchInDocuments(queryVector, documentIds, topK);
        
        if (relevantChunks.isEmpty()) {
            return "抱歉，在知识库中没有找到相关信息。";
        }
        
        // 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("以下是从知识库中找到的相关文档片段：\n\n");
        
        for (int i = 0; i < relevantChunks.size(); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            context.append(String.format("[片段 %d - 来自文件: %s]\n", i + 1, chunk.getFileName()));
            context.append(chunk.getContent()).append("\n\n");
        }
        
        // 构建提示词
        String systemPrompt = "你是一个智能文档助手。请基于提供的文档片段回答用户的问题。" +
                "如果文档中没有相关信息，请明确告知用户。回答要准确、简洁。";
        
        String userPrompt = context.toString() + 
                "\n问题：" + question + 
                "\n\n请基于以上文档片段回答问题：";
        
        // 调用 AI 模型
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        Prompt prompt = new Prompt(messages);
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        String answer = ((org.springframework.ai.chat.messages.AssistantMessage) response.getResult().getOutput()).getText();

        log.info("RAG 知识库查询完成");
        return answer;
    }
}
