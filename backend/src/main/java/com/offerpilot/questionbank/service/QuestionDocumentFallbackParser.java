package com.offerpilot.questionbank.service;

import com.offerpilot.questionbank.web.dto.QuestionBankImportItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parser for documents that use numbered questions and answers.
 * It is intentionally conservative: the admin reviews every row before import.
 */
@Component
public class QuestionDocumentFallbackParser {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_QUESTION_LENGTH = 4000;
    private static final int MAX_ANSWER_LENGTH = 20000;

    private static final Pattern QUESTION_LINE = Pattern.compile(
            "^\\s*(?:"
                    + "[（(]?\\d{1,3}[）).、．]"
                    + "|第\\s*\\d{1,3}\\s*题\\s*[：:、.．]?"
                    + "|(?:Q|Question)\\s*\\d{1,3}\\s*[.、:：)）]?"
                    + "|问(?:题)?\\s*\\d{1,3}\\s*[.、:：)）]?"
                    + "|(?:题目|问题)\\s*[：:]"
                    + ")\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ANSWER_LINE = Pattern.compile(
            "^\\s*(?:参考答案|答案要点|答案|答|A|Answer)\\s*[：:.、)）]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INLINE_ANSWER = Pattern.compile(
            "\\s*(?:参考答案|答案要点|答案|答|Answer|A)\\s*[：:]\\s*",
            Pattern.CASE_INSENSITIVE);

    public List<QuestionBankImportItem> parse(String text, QuestionImportDefaults defaults) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Draft> drafts = parseNumbered(text);
        if (drafts.isEmpty()) {
            drafts = parseParagraphs(text);
        }

        Map<String, QuestionBankImportItem> unique = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            add(unique, toItem(draft.question(), draft.answer(), defaults));
            if (unique.size() >= MAX_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<Draft> parseNumbered(String text) {
        List<Draft> drafts = new ArrayList<>();
        String question = null;
        StringBuilder answer = new StringBuilder();
        boolean answerStarted = false;

        for (String rawLine : text.split("\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher questionMatcher = QUESTION_LINE.matcher(line);
            if (questionMatcher.matches()) {
                flush(drafts, question, answer);
                String questionText = questionMatcher.group(1).trim();
                String inlineAnswer = null;
                Matcher inlineMatcher = INLINE_ANSWER.matcher(questionText);
                if (inlineMatcher.find() && inlineMatcher.start() > 0) {
                    inlineAnswer = questionText.substring(inlineMatcher.end()).trim();
                    questionText = questionText.substring(0, inlineMatcher.start()).trim();
                }
                question = questionText;
                answer = new StringBuilder(inlineAnswer == null ? "" : inlineAnswer);
                answerStarted = inlineAnswer != null && !inlineAnswer.isBlank();
                continue;
            }

            if (question == null) {
                continue;
            }

            Matcher answerMatcher = ANSWER_LINE.matcher(line);
            if (answerMatcher.matches()) {
                appendLine(answer, answerMatcher.group(1));
                answerStarted = true;
                continue;
            }

            if (!answerStarted && looksLikeQuestionContinuation(line)) {
                question = question + " " + line;
            } else {
                appendLine(answer, line);
                answerStarted = true;
            }
        }
        flush(drafts, question, answer);
        return drafts;
    }

    private List<Draft> parseParagraphs(String text) {
        List<Draft> drafts = new ArrayList<>();
        for (String block : text.split("\\n\\s*\\n")) {
            String[] lines = block.strip().split("\\n");
            if (lines.length == 0) {
                continue;
            }
            String question = lines[0].trim();
            if (!looksLikeQuestion(question)) {
                continue;
            }
            StringBuilder answer = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                appendLine(answer, lines[i]);
            }
            drafts.add(new Draft(question, answer.toString()));
            if (drafts.size() >= MAX_ITEMS) {
                break;
            }
        }
        return drafts;
    }

    private boolean looksLikeQuestionContinuation(String line) {
        return line.endsWith("？") || line.endsWith("?")
                || line.startsWith("（") || line.startsWith("(");
    }

    private boolean looksLikeQuestion(String line) {
        return line.endsWith("？") || line.endsWith("?")
                || line.startsWith("Q") || line.startsWith("问");
    }

    private void flush(List<Draft> drafts, String question, StringBuilder answer) {
        if (question == null || question.isBlank()) {
            return;
        }
        drafts.add(new Draft(question.trim(), answer.toString().trim()));
    }

    private void appendLine(StringBuilder builder, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line.trim());
    }

    private QuestionBankImportItem toItem(String question, String answer,
                                           QuestionImportDefaults defaults) {
        String category = defaultValue(defaults.category(), "未分类");
        int difficulty = defaults.difficulty() == null
                ? 1 : Math.max(1, Math.min(3, defaults.difficulty()));
        String source = defaultValue(defaults.source(), "文档导入");
        List<String> tags = distinctTags(List.of(category, "文档导入"));
        return new QuestionBankImportItem(
                truncate(question, MAX_QUESTION_LENGTH),
                truncate(answer, MAX_ANSWER_LENGTH),
                category,
                difficulty,
                tags,
                source);
    }

    private void add(Map<String, QuestionBankImportItem> unique, QuestionBankImportItem item) {
        if (item.question() == null || item.question().isBlank()) {
            return;
        }
        String key = item.question().replaceAll("\\s+", "")
                .replaceAll("[？?。.!！]", "")
                .toLowerCase(Locale.ROOT);
        if (key.length() < 4) {
            return;
        }
        unique.putIfAbsent(key, item);
    }

    private List<String> distinctTags(List<String> tags) {
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength).trim();
    }

    private record Draft(String question, String answer) {
    }
}
