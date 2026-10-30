package com.offerpilot.resume.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
public class ResumeTextExtractor {

    public String extract(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历文件为空");
        }

        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            String text = lower.endsWith(".pdf") ? extractPdf(content) : extractDocx(content);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法从简历中提取文本");
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历解析失败，请确认文件格式");
        }
    }

    private String extractPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
