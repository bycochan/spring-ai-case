package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档片段模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    
    /**
     * 文档ID
     */
    private String documentId;
    
    /**
     * 片段ID
     */
    private String chunkId;
    
    /**
     * 文本内容
     */
    private String content;
    
    /**
     * 向量嵌入
     */
    private List<Float> embedding;
    
    /**
     * 元数据
     */
    private String metadata;
    
    /**
     * 页码（用于PDF）
     */
    private Integer pageNumber;
    
    /**
     * 文件名
     */
    private String fileName;
}
