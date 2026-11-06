package com.tes.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文档解析服务
 * 支持 PDF、Word 和 TXT 文档解析
 */
@Slf4j
@Service
public class DocumentParserService {

    /**
     * 解析文档
     *
     * @param file 上传的文件
     * @return 解析后的文本内容
     */
    public String parseDocument(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        log.info("开始解析文档: {}", fileName);

        String content = parseText(file.getBytes(), fileName);

        log.info("文档解析完成: {}, 内容长度: {}", fileName, content.length());
        return content;
    }

    /**
     * 按文件名后缀把字节内容解析为纯文本（附件转文本的底层入口）。
     * PDF 走 PDFBox，Word 走 POI，TXT 按 UTF-8 直读。
     *
     * @param data     文件字节数据
     * @param fileName 原始文件名（用于按后缀判定格式）
     * @return 解析后的文本内容
     */
    public String parseText(byte[] data, String fileName) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("附件内容为空");
        }
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return parsePDF(new ByteArrayInputStream(data));
        }
        if (name.endsWith(".docx") || name.endsWith(".doc")) {
            return parseWord(new ByteArrayInputStream(data));
        }
        if (name.endsWith(".txt")) {
            return new String(data, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("不支持的文件格式，仅支持 PDF、Word 和 TXT 文档");
    }

    /**
     * 解析 PDF 文档
     */
    private String parsePDF(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 解析 Word 文档
     */
    private String parseWord(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            
            return content.toString();
        }
    }

    /**
     * 将文本分块
     *
     * @param text 原始文本
     * @param chunkSize 每块的大小（字符数）
     * @param overlap 重叠字符数
     * @return 文本块列表
     */
    public List<String> splitTextIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        // 验证参数
        if (chunkSize <= 0) {
            log.warn("chunkSize 必须大于 0，使用默认值 500");
            chunkSize = 500;
        }
        if (overlap < 0 || overlap >= chunkSize) {
            log.warn("overlap 必须在 [0, chunkSize) 范围内，使用默认值 50");
            overlap = Math.min(50, chunkSize / 10);
        }

        int start = 0;
        int textLength = text.length();
        
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            
            // 尝试在句子边界处分割（仅当不是最后一块时）
            if (end < textLength) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastExclamation = text.lastIndexOf('！', end);
                int lastQuestion = text.lastIndexOf('？', end);
                int lastNewline = text.lastIndexOf('\n', end);
                
                int boundary = Math.max(Math.max(lastPeriod, lastExclamation), 
                                       Math.max(lastQuestion, lastNewline));
                
                // 确保边界在当前块范围内
                if (boundary > start && boundary < end) {
                    end = boundary + 1;
                }
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // 关键修复：确保 start 始终向前移动
            // 如果有重叠，下一个起点 = 当前终点 - 重叠大小
            // 否则，下一个起点 = 当前终点
            if (overlap > 0 && end < textLength) {
                start = end - overlap;
            } else {
                start = end;
            }
            
            // 如果已经到达文本末尾，退出循环
            if (start >= textLength) {
                break;
            }
        }
        
        log.info("文本分块完成，共 {} 块", chunks.size());
        return chunks;
    }
}
