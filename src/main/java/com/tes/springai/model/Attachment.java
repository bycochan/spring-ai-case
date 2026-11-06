package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附件模型
 * 经公共附件上传接口存入的文件，发送聊天消息时通过 fileId 引用。
 * 与 RAG 的文档（解析入向量库、不留原文件）是两套体系，见 docs/adr/0001。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    /**
     * 附件唯一标识（fileId）
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * MIME 类型，如 image/png
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private long size;

    /**
     * 文件字节数据
     */
    private byte[] data;

    /**
     * 上传时间戳
     */
    private Long uploadedAt;
}
