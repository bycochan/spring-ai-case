package com.tes.springai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 附件测试夹具
 * 用解析同款库（PDFBox/POI）在测试内生成最小有效文件，不依赖二进制资源。
 */
final class AttachmentFixtures {

    static final String PDF_TEXT = "SPRING_AI_PDF_FIXTURE_12345";
    static final String DOCX_TEXT = "SPRING_AI_DOCX_FIXTURE_67890";
    static final String TXT_TEXT = "SPRING_AI_TXT_FIXTURE_你好世界";

    private AttachmentFixtures() {
    }

    static MultipartFile txtFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    static MultipartFile docxFile(String name) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", minimalDocx());
    }

    static MultipartFile pdfFile(String name) {
        return new MockMultipartFile("file", name, "application/pdf", minimalPdf());
    }

    /**
     * 名义上是 PDF 的损坏文件：上传可通过（类型校验只看类型），解析必然失败
     */
    static MultipartFile corruptedPdfFile(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "这不是有效的PDF内容".getBytes(StandardCharsets.UTF_8));
    }

    static MultipartFile pngFile(String name) {
        return new MockMultipartFile("file", name, "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    /**
     * 用 PDFBox 生成包含固定文本的最小有效 PDF
     */
    private static byte[] minimalPdf() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(PDF_TEXT);
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 PDF 夹具失败", e);
        }
    }

    /**
     * 用 POI 生成包含固定文本的最小有效 docx
     */
    private static byte[] minimalDocx() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(DOCX_TEXT);
            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 docx 夹具失败", e);
        }
    }
}
