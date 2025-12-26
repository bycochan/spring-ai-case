package com.tes.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 文本嵌入服务
 * 将文本转换为向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final ChatModel chatModel;
    private static final int EMBEDDING_DIM = 1536;

    /**
     * 生成文本嵌入向量
     * 
     * 注意：这里使用模拟向量，实际应该调用真实的嵌入模型
     * 阿里云提供的嵌入模型需要单独配置
     */
    public List<Float> generateEmbedding(String text) {
        log.debug("生成文本嵌入，文本长度: {}", text.length());
        
        // TODO: 替换为真实的嵌入模型调用
        // 目前使用模拟向量，基于文本哈希生成确定性向量
        return generateMockEmbedding(text);
    }

    /**
     * 批量生成嵌入
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        List<List<Float>> embeddings = new ArrayList<>();
        
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        
        return embeddings;
    }

    /**
     * 生成模拟嵌入向量
     * 基于文本哈希生成确定性向量
     */
    private List<Float> generateMockEmbedding(String text) {
        List<Float> embedding = new ArrayList<>(EMBEDDING_DIM);
        
        // 使用文本哈希作为随机种子，确保相同文本生成相同向量
        Random random = new Random(text.hashCode());
        
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding.add(random.nextFloat() * 2 - 1); // 范围 [-1, 1]
        }
        
        // 归一化
        float norm = 0;
        for (float val : embedding) {
            norm += val * val;
        }
        norm = (float) Math.sqrt(norm);
        
        for (int i = 0; i < embedding.size(); i++) {
            embedding.set(i, embedding.get(i) / norm);
        }
        
        return embedding;
    }
}
