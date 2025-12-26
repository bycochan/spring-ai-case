package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {
    
    /**
     * 知识库ID
     */
    private String id;
    
    /**
     * 知识库名称
     */
    private String name;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 文档ID列表
     */
    private List<String> documentIds;
    
    /**
     * 文档数量
     */
    private Integer documentCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
