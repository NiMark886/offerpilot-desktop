package com.offerpilot.questionbank.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class QuestionDocumentTextExtractor {

    public String extract(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档内容为空");
        }

        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (lower.endsWith(".pdf")) {
                text = extractPdf(content);
            } else if (lower.endsWith(".docx")) {
                text = extractDocx(content);
            } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
                text = decodeText(content);
            } else {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "仅支持 PDF、DOCX、TXT、Markdown 文档");
            }
            String normalized = normalize(text);
            if (normalized.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "无法从文档中提取文字；扫描版 PDF 请先 OCR 或改用可复制文字的文档");
            }
            return normalized;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "文档读取失败，请确认文件未损坏且格式受支持");
        }
    }

    private String extractPdf(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String decodeText(byte[] content) {
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (!utf8.contains("�")) {
            return utf8;
        }
        return new String(content, Charset.forName("GB18030"));
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
