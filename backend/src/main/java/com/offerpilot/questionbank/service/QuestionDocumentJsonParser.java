package com.offerpilot.questionbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.questionbank.web.dto.QuestionBankImportItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tolerates the small JSON variations that chat models produce (code fences,
 * a top-level array or an object containing {@code questions}, string tags,
 * Chinese difficulty labels, missing optional fields).
 */
@Component
public class QuestionDocumentJsonParser {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_TAGS = 8;

    private final ObjectMapper objectMapper;

    public QuestionDocumentJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<QuestionBankImportItem> parse(String content, QuestionImportDefaults defaults) {
        JsonNode root = readRoot(content);
        JsonNode items = root;
        if (root.isObject()) {
            items = firstItems(root, "questions", "items", "data", "题目列表");
            if (items == null && hasQuestionField(root)) {
                items = objectMapper.createArrayNode().add(root);
            }
        }
        if (items == null || !items.isArray()) {
            throw new IllegalArgumentException("AI question response does not contain a question array");
        }

        Map<String, QuestionBankImportItem> unique = new LinkedHashMap<>();
        for (JsonNode node : items) {
            QuestionBankImportItem item = toItem(node, defaults);
            if (item == null) {
                continue;
            }
            String key = normalizeKey(item.question());
            if (key.length() < 4) {
                continue;
            }
            unique.putIfAbsent(key, item);
            if (unique.size() >= MAX_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private JsonNode readRoot(String content) {
        String json = cleanJson(content);
        if (json.isBlank()) {
            throw new IllegalArgumentException("AI question response is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || (!root.isArray() && !root.isObject())) {
                throw new IllegalArgumentException("AI question response is not JSON");
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI question response is not valid JSON", ex);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;
        char endChar;
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            endChar = ']';
        } else if (objectStart >= 0) {
            start = objectStart;
            endChar = '}';
        } else {
            return trimmed;
        }
        int end = trimmed.lastIndexOf(endChar);
        return end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private JsonNode firstItems(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && node.isArray()) {
                return node;
            }
            if (node != null && node.isObject() && hasQuestionField(node)) {
                return objectMapper.createArrayNode().add(node);
            }
        }
        return null;
    }

    private boolean hasQuestionField(JsonNode node) {
        return node.has("question") || node.has("content")
                || node.has("题目") || node.has("问题");
    }

    private QuestionBankImportItem toItem(JsonNode node, QuestionImportDefaults defaults) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String question = stripLeadingNumber(text(node, "question", "content", "title", "题目", "问题"));
        if (question.isBlank()) {
            return null;
        }
        String answer = text(node,
                "referenceAnswer", "reference_answer", "answer", "答", "答案", "参考答案");
        String category = firstNonBlank(text(node, "category", "categoryName", "分类"), defaults.category(), "未分类");
        int difficulty = difficulty(node);
        if (difficulty == 0) {
            difficulty = defaults.difficulty() == null ? 1 : clamp(defaults.difficulty());
        }
        List<String> tags = tags(node);
        if (tags.isEmpty()) {
            tags = List.of(category, "文档导入");
        }
        String source = firstNonBlank(defaults.source(), text(node, "source", "来源"), "文档导入");
        return new QuestionBankImportItem(
                truncate(question, 4000),
                truncate(answer, 20000),
                truncate(category, 64),
                difficulty,
                tags.stream().distinct().limit(MAX_TAGS).toList(),
                truncate(source, 255));
    }

    private String text(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            String value = nodeText(node);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String value = nodeText(item);
                if (value != null && !value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            return String.join(",", parts);
        }
        if (node.isObject()) {
            for (String key : List.of("text", "value", "name", "content")) {
                String value = nodeText(node.get(key));
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private int difficulty(JsonNode node) {
        JsonNode value = node.get("difficulty");
        if (value == null) {
            value = node.get("难度");
        }
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isIntegralNumber()) {
            return clamp(value.asInt());
        }
        String text = value.asText("").toLowerCase(Locale.ROOT);
        if (text.contains("难") || text.contains("hard") || text.contains("高")) {
            return 3;
        }
        if (text.contains("中") || text.contains("medium")) {
            return 2;
        }
        if (text.contains("简") || text.contains("easy") || text.contains("低")) {
            return 1;
        }
        for (int i = 1; i <= 3; i++) {
            if (text.contains(String.valueOf(i))) {
                return i;
            }
        }
        return 0;
    }

    private List<String> tags(JsonNode node) {
        JsonNode value = node.get("tags");
        if (value == null) {
            value = node.get("标签");
        }
        List<String> result = new ArrayList<>();
        if (value == null || value.isNull()) {
            return result;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                addTag(result, nodeText(item));
            }
        } else {
            String text = nodeText(value);
            if (text != null) {
                for (String part : text.split("[,，、;；/|]")) {
                    addTag(result, part);
                }
            }
        }
        return result.stream().distinct().limit(MAX_TAGS).toList();
    }

    private void addTag(List<String> tags, String value) {
        if (value == null) {
            return;
        }
        String tag = value.replaceFirst("^[·•\\-*\\s]+", "").trim();
        if (!tag.isBlank() && tag.length() <= 50) {
            tags.add(tag);
        }
    }

    private String stripLeadingNumber(String value) {
        return value == null ? "" : value.replaceFirst(
                "^\\s*(?:[（(]?\\d{1,3}[）).、．]|第\\s*\\d{1,3}\\s*题\\s*[：:、.．]?)\\s*",
                "").trim();
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "")
                .replaceAll("[？?。.!！]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(3, value));
    }

    private String truncate(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength).trim();
    }
}
