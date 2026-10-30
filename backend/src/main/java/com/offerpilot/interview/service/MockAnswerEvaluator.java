package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MockAnswerEvaluator implements AnswerEvaluator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                           String promptType, String answer, RoleProfile profile) {
        if ("FOLLOW_UP".equals(promptType)) {
            return evaluateFollowUp(answer);
        }

        List<String> points = readList(question.getReferencePoints());
        String text = answer == null ? "" : answer.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (text.length() < 10 || lower.contains("不知道") || lower.contains("不太清楚")) {
            return new AnswerEvaluationResult(
                    "INCORRECT", 1,
                    "这道题还没有展开，建议先说出核心概念和关键步骤。",
                    points, 0.5);
        }

        if (points.isEmpty()) {
            return new AnswerEvaluationResult(
                    "PARTIAL", 3,
                    "该题暂无参考要点，已记录你的回答。",
                    List.of(), 0.5);
        }

        List<String> missing = new ArrayList<>();
        int matched = 0;
        for (String point : points) {
            if (containsAnyToken(lower, point)) {
                matched++;
            } else {
                missing.add(point);
            }
        }

        double ratio = (double) matched / points.size();
        String correctness;
        int score;
        if (ratio >= 0.75) {
            correctness = "CORRECT";
            score = 5;
        } else if (ratio >= 0.5) {
            correctness = "CORRECT";
            score = 4;
        } else if (ratio >= 0.25) {
            correctness = "PARTIAL";
            score = 3;
        } else {
            correctness = "INSUFFICIENT";
            score = 2;
        }

        String comment = "覆盖了 " + matched + "/" + points.size() + " 个参考要点。";
        double confidence = Math.min(0.9, 0.6 + 0.3 * ratio);
        return new AnswerEvaluationResult(correctness, score, comment, missing, confidence);
    }

    private AnswerEvaluationResult evaluateFollowUp(String answer) {
        String text = answer == null ? "" : answer.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (text.length() < 10 || lower.contains("不知道") || lower.contains("不太清楚")) {
            return new AnswerEvaluationResult(
                    "INCORRECT", 1,
                    "还没有展开，建议先直接回答追问的核心点。",
                    List.of("针对追问的核心点"), 0.5);
        }

        boolean substantive = text.length() >= 40
                || lower.contains("因为")
                || lower.contains("所以")
                || lower.contains("首先")
                || lower.contains("然后")
                || lower.contains("例如")
                || lower.contains("具体");
        if (substantive) {
            return new AnswerEvaluationResult(
                    "CORRECT", 4,
                    "回答有展开，能对应追问的重点。",
                    List.of(), 0.7);
        }
        return new AnswerEvaluationResult(
                "PARTIAL", 3,
                "方向基本对，但还不够具体，建议补充关键步骤或例子。",
                List.of("更具体的步骤或例子"), 0.6);
    }

    private boolean containsAnyToken(String answerLower, String point) {
        for (String token : point.split("[+、,，;；:：\\s/]+")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (trimmed.length() >= 2 && answerLower.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
