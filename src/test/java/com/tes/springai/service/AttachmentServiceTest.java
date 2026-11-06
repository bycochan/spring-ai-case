package com.tes.springai.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AttachmentService 测试类
 * 覆盖文档附件（PDF/Word/TXT）的上传校验与"附件转文本"能力。
 * 夹具见 {@link AttachmentFixtures}，在测试内生成最小有效文件。
 */
@SpringBootTest
class AttachmentServiceTest {

    @Autowired
    private AttachmentService attachmentService;

    // ---------- 上传校验：文档类型进入白名单 ----------

    @Test
    void testUploadAcceptsTextDocument() {
        MultipartFile file = AttachmentFixtures.txtFile("note.txt", AttachmentFixtures.TXT_TEXT);
        var attachment = attachmentService.upload(file);

        assertNotNull(attachment.getFileId());
        assertEquals("note.txt", attachment.getFileName());
        assertNotNull(attachmentService.getAttachment(attachment.getFileId()));
    }

    @Test
    void testUploadAcceptsPdfDocument() {
        var attachment = attachmentService.upload(AttachmentFixtures.pdfFile("report.pdf"));

        assertNotNull(attachment.getFileId());
        assertEquals("application/pdf", attachment.getContentType());
    }

    @Test
    void testUploadAcceptsWordDocument() {
        var attachment = attachmentService.upload(AttachmentFixtures.docxFile("spec.docx"));

        assertNotNull(attachment.getFileId());
        assertEquals("spec.docx", attachment.getFileName());
    }

    @Test
    void testUploadRejectsUnsupportedType() {
        MultipartFile file = file("archive.zip", "application/zip", new byte[]{1, 2, 3});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.upload(file));
        assertTrue(ex.getMessage().contains("不支持"));
    }

    @Test
    void testUploadRejectsOversizeDocument() {
        byte[] oversize = new byte[(int) (AttachmentService.MAX_FILE_SIZE + 1)];
        MultipartFile file = file("big.txt", "text/plain", oversize);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.upload(file));
        assertTrue(ex.getMessage().contains("10MB"));
    }

    // ---------- 附件转文本（唯一新缝：extractText） ----------

    @Test
    void testExtractTextFromTxtDocument() {
        String fileId = attachmentService
                .upload(AttachmentFixtures.txtFile("note.txt", AttachmentFixtures.TXT_TEXT))
                .getFileId();

        assertEquals(AttachmentFixtures.TXT_TEXT, attachmentService.extractText(fileId));
    }

    @Test
    void testExtractTextFromPdfDocument() {
        String fileId = attachmentService.upload(AttachmentFixtures.pdfFile("report.pdf")).getFileId();

        String text = attachmentService.extractText(fileId);
        assertTrue(text.contains(AttachmentFixtures.PDF_TEXT), "PDF 提取文本应包含夹具内容，实际为: " + text);
    }

    @Test
    void testExtractTextFromWordDocument() {
        String fileId = attachmentService.upload(AttachmentFixtures.docxFile("spec.docx")).getFileId();

        String text = attachmentService.extractText(fileId);
        assertTrue(text.contains(AttachmentFixtures.DOCX_TEXT), "docx 提取文本应包含夹具内容，实际为: " + text);
    }

    @Test
    void testExtractTextRejectsImageAttachment() {
        String fileId = attachmentService.upload(AttachmentFixtures.pngFile("pic.png")).getFileId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.extractText(fileId));
        assertTrue(ex.getMessage().contains("图片"));
    }

    @Test
    void testExtractTextRejectsMissingFileId() {
        assertThrows(IllegalArgumentException.class, () -> attachmentService.extractText("not-exist-id"));
    }

    @Test
    void testExtractTextRejectsCorruptedPdf() {
        String fileId = attachmentService.upload(AttachmentFixtures.corruptedPdfFile("broken.pdf")).getFileId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.extractText(fileId));
        assertTrue(ex.getMessage().contains("解析失败"));
    }

    // ---------- 聊天引用：文档与图片共用数量额度 ----------

    @Test
    void testValidateForChatAcceptsDocumentAttachments() {
        List<String> fileIds = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> attachmentService
                        .upload(AttachmentFixtures.txtFile("doc-" + i + ".txt", "内容" + i))
                        .getFileId())
                .collect(Collectors.toList());

        assertEquals(5, attachmentService.validateForChat(fileIds).size());
    }

    @Test
    void testValidateForChatRejectsSixthAttachment() {
        List<String> fileIds = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> attachmentService
                        .upload(AttachmentFixtures.txtFile("doc-" + i + ".txt", "内容" + i))
                        .getFileId())
                .collect(Collectors.toList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.validateForChat(fileIds));
        assertTrue(ex.getMessage().contains("最多"));
    }

    private MultipartFile file(String name, String contentType, byte[] data) {
        return new MockMultipartFile("file", name, contentType, data);
    }
}
