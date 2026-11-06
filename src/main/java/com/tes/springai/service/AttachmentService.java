package com.tes.springai.service;

import com.tes.springai.model.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 公共附件服务
 * 参照 RAG 文档上传能力，为多模态聊天提供附件（图片与 PDF/Word/TXT 文档）的存储与校验。
 * 附件保留原始字节、不做向量化，存于内存、重启即清，与会话历史生命周期一致（见 docs/adr/0001）。
 */
@Slf4j
@Service
public class AttachmentService {

    /**
     * 单个附件大小上限：10MB
     */
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * 单条消息附件数量上限：5 个
     */
    public static final int MAX_COUNT_PER_MESSAGE = 5;

    /**
     * 允许的图片 MIME 白名单
     */
    public static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * 允许的文档 MIME 白名单（PDF/Word/TXT）
     */
    public static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain");

    /**
     * 文档扩展名白名单：部分客户端以 application/octet-stream 上传文档，按后缀兜底
     */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "docx", "txt");

    /**
     * 允许的全部 MIME（图片 + 文档）
     */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Stream
            .concat(IMAGE_CONTENT_TYPES.stream(), DOCUMENT_CONTENT_TYPES.stream())
            .collect(Collectors.toUnmodifiableSet());

    private final Map<String, Attachment> attachments = new ConcurrentHashMap<>();

    private final DocumentParserService documentParserService;

    public AttachmentService(DocumentParserService documentParserService) {
        this.documentParserService = documentParserService;
    }

    /**
     * 上传附件：校验类型与大小后存入内存
     */
    public Attachment upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("附件为空");
        }
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        if (!isAllowedType(contentType, fileName)) {
            throw new IllegalArgumentException("不支持的附件格式，仅支持图片（JPG/PNG/GIF/WebP）与文档（PDF/Word/TXT）");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("附件大小超过 10MB 限制");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("读取附件内容失败");
        }

        Attachment attachment = Attachment.builder()
                .fileId(UUID.randomUUID().toString())
                .fileName(fileName)
                .contentType(contentType)
                .size(file.getSize())
                .data(data)
                .uploadedAt(System.currentTimeMillis())
                .build();
        attachments.put(attachment.getFileId(), attachment);
        log.info("附件上传成功: {} ({}, {} bytes)", attachment.getFileName(), attachment.getFileId(), attachment.getSize());
        return attachment;
    }

    /**
     * 判断附件类型是否允许上传：
     * 图片与文档均按 MIME 白名单校验；文档另按扩展名兜底，
     * 但兜底仅对未携带类型或通用二进制类型（application/octet-stream）生效，
     * 防止其他格式文件冒用文档扩展名绕过 MIME 校验
     */
    private boolean isAllowedType(String contentType, String fileName) {
        if (contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        if (contentType != null && DOCUMENT_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return isGenericBinaryType(contentType) && isDocumentExtension(fileName);
    }

    /**
     * 判断是否为未携带类型或通用二进制类型（扩展名兜底仅适用这两类）
     */
    private boolean isGenericBinaryType(String contentType) {
        return contentType == null
                || "application/octet-stream".equals(contentType.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断文件名是否为受支持的文档扩展名
     */
    private boolean isDocumentExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return DOCUMENT_EXTENSIONS.contains(extension);
    }

    /**
     * 获取附件，不存在时抛出异常
     */
    public Attachment getAttachment(String fileId) {
        Attachment attachment = attachments.get(fileId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在: " + fileId);
        }
        return attachment;
    }

    /**
     * 查找附件，不存在时返回 null（历史重建等降级场景使用）
     */
    public Attachment findAttachment(String fileId) {
        return attachments.get(fileId);
    }

    /**
     * 删除附件
     */
    public void delete(String fileId) {
        attachments.remove(fileId);
        log.info("附件已删除: {}", fileId);
    }

    /**
     * 校验聊天请求携带的附件引用：数量不超过上限、逐个存在
     *
     * @return 按传入顺序排列的附件列表
     */
    public List<Attachment> validateForChat(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        if (fileIds.size() > MAX_COUNT_PER_MESSAGE) {
            throw new IllegalArgumentException("单条消息最多携带 " + MAX_COUNT_PER_MESSAGE + " 个附件");
        }
        List<Attachment> result = new ArrayList<>(fileIds.size());
        for (String fileId : fileIds) {
            Attachment attachment = attachments.get(fileId);
            if (attachment == null) {
                throw new IllegalArgumentException("附件不存在或已失效，请重新上传");
            }
            result.add(attachment);
        }
        return result;
    }

    /**
     * 将文档附件转换为模型可识别的纯文本（"附件转文本"唯一入口）。
     * 转换按需进行、不缓存结果：附件仅保留原始字节，提取出的文本不落存储。
     *
     * @param fileId 附件 ID
     * @return 提取出的文本内容
     * @throws IllegalArgumentException 附件不存在、为图片附件或解析失败
     */
    public String extractText(String fileId) {
        Attachment attachment = getAttachment(fileId);
        if (!isDocument(attachment)) {
            throw new IllegalArgumentException("图片附件不支持文本提取: " + fileId);
        }
        try {
            String text = documentParserService.parseText(attachment.getData(), attachment.getFileName());
            if (text == null || text.isBlank()) {
                // 无文本层的扫描件 PDF、纯空白文件等：解析成功但读不到内容，按解析失败处理而非静默返回空文本
                throw new IllegalStateException("未从文档中提取到任何文本");
            }
            return text;
        } catch (Exception e) {
            log.warn("附件文本提取失败: {} ({})", attachment.getFileName(), fileId, e);
            throw new IllegalArgumentException("附件解析失败，文件可能已损坏或无法提取文本: " + attachment.getFileName(), e);
        }
    }

    /**
     * 判断附件是否为文档类型（非图片）：
     * 按 MIME 白名单判定，并按扩展名兜底（部分客户端对文档统一上传为 application/octet-stream）
     */
    private boolean isDocument(Attachment attachment) {
        String contentType = attachment.getContentType();
        if (contentType != null && IMAGE_CONTENT_TYPES.contains(contentType)) {
            return false;
        }
        if (contentType != null && DOCUMENT_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        return isDocumentExtension(attachment.getFileName());
    }
}
