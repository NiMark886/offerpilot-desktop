package com.offerpilot.questionbank.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.questionbank.web.dto.QuestionBankImportItem;
import com.offerpilot.questionbank.web.dto.QuestionDocumentExtractView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QuestionDocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(QuestionDocumentImportService.class);
    private static final int MAX_TEXT_CHARS = 60_000;
    private static final int MAX_ITEMS = 100;

    private final QuestionDocumentTextExtractor textExtractor;
    private final AiQuestionDocumentExtractor aiExtractor;
    private final QuestionDocumentFallbackParser fallbackParser;
    private final long maxFileSize;

    public QuestionDocumentImportService(QuestionDocumentTextExtractor textExtractor,
                                         AiQuestionDocumentExtractor aiExtractor,
                                         QuestionDocumentFallbackParser fallbackParser,
                                         @Value("${app.storage.max-size:10485760}") long maxFileSize) {
        this.textExtractor = textExtractor;
        this.aiExtractor = aiExtractor;
        this.fallbackParser = fallbackParser;
        this.maxFileSize = maxFileSize;
    }

    public QuestionDocumentExtractView extract(MultipartFile file,
                                               String category,
                                               Integer difficulty,
                                               String source) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择要导入的文档");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文档不能超过 10MB");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档读取失败");
        }

        String fullText = textExtractor.extract(content, fileName);
        boolean truncated = fullText.length() > MAX_TEXT_CHARS;
        String text = truncated ? fullText.substring(0, MAX_TEXT_CHARS) : fullText;
        QuestionImportDefaults defaults = defaults(category, difficulty, source, fileName);

        boolean aiUsed = false;
        List<QuestionBankImportItem> items = List.of();
        try {
            items = aiExtractor.extract(text, defaults);
            aiUsed = items != null && !items.isEmpty();
        } catch (Exception ex) {
            log.warn("AI question document extraction failed, using rule parser: {}", fileName, ex);
        }

        if (items == null || items.isEmpty()) {
            items = fallbackParser.parse(text, defaults);
            aiUsed = false;
        }
        List<QuestionBankImportItem> normalized = normalize(items, defaults);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "未从文档中识别出题目，请检查文档格式，或先手动录入");
        }

        return new QuestionDocumentExtractView(
                fileName,
                fullText.length(),
                truncated,
                aiUsed,
                normalized.size(),
                normalized);
    }

    private QuestionImportDefaults defaults(String category,
                                            Integer difficulty, String source, String fileName) {
        String safeSource = isBlank(source) ? "文档导入" : source.trim();
        return new QuestionImportDefaults(
                isBlank(category) ? "未分类" : category.trim(),
                difficulty == null ? 1 : Math.max(1, Math.min(3, difficulty)),
                truncate(safeSource + " · " + fileName, 255));
    }

    private List<QuestionBankImportItem> normalize(List<QuestionBankImportItem> items,
                                                   QuestionImportDefaults defaults) {
        Map<String, QuestionBankImportItem> unique = new LinkedHashMap<>();
        for (QuestionBankImportItem item : items) {
            if (item == null || isBlank(item.question())) {
                continue;
            }
            String question = item.question().trim();
            String key = question.replaceAll("\\s+", "")
                    .replaceAll("[？?。.!！]", "")
                    .toLowerCase(Locale.ROOT);
            if (key.length() < 4) {
                continue;
            }
            String category = truncate(firstNonBlank(item.category(), defaults.category(), "未分类"), 64);
            int difficulty = item.difficulty() == null
                    ? defaults.difficulty() : Math.max(1, Math.min(3, item.difficulty()));
            List<String> tags = item.tags() == null ? List.of() : item.tags().stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::trim)
                    .filter(tag -> tag.length() <= 50)
                    .distinct()
                    .limit(8)
                    .toList();
            if (tags.isEmpty()) {
                tags = List.of(category, "文档导入");
            }
            String source = truncate(firstNonBlank(item.source(), defaults.source()), 255);
            QuestionBankImportItem normalized = new QuestionBankImportItem(
                    truncate(question, 4000),
                    truncate(item.referenceAnswer(), 20000),
                    category,
                    difficulty,
                    tags,
                    source);
            unique.putIfAbsent(key, normalized);
            if (unique.size() >= MAX_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String safeFileName(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "document" : originalName.trim();
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return truncate(name, 120);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }
}
