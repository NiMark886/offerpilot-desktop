package com.offerpilot.interview.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic sales blueprint used when the user has no AI provider or the
 * AI call fails. It round-robins across the six sales competencies so every
 * interview covers the full competency model.
 */
@Component
public class SalesBankBlueprintGenerator {

    public InterviewBlueprint generateSales(BlueprintRequest request) {
        RoleProfile profile = request.profile();
        if (profile == null || !profile.isGeneral() || profile.competencyNames().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售能力模型未配置");
        }
        int total = expectedCount(request.durationMinutes());
        List<QuestionBankItem> pool = request.retrievedQuestions() == null
                ? List.of() : request.retrievedQuestions();

        Map<String, List<QuestionBankItem>> byCompetency = new LinkedHashMap<>();
        for (String name : profile.competencyNames()) {
            byCompetency.put(name, new ArrayList<>());
        }
        for (QuestionBankItem item : pool) {
            String competency = item.tags().stream()
                    .filter(byCompetency::containsKey)
                    .findFirst()
                    .orElse(null);
            if (competency != null) {
                byCompetency.get(competency).add(item);
            }
        }
        int targetDifficulty = targetDifficulty(request.difficulty());
        byCompetency.values().forEach(list -> list.sort(
                Comparator.comparingInt((QuestionBankItem item) ->
                                Math.abs(item.difficulty() - targetDifficulty))
                        .thenComparingLong(QuestionBankItem::id)));

        List<QuestionBankItem> picked = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        boolean added = true;
        while (picked.size() < total && added) {
            added = false;
            for (String name : profile.competencyNames()) {
                for (QuestionBankItem item : byCompetency.getOrDefault(name, List.of())) {
                    if (used.add(item.id())) {
                        picked.add(item);
                        added = true;
                        break;
                    }
                }
                if (picked.size() >= total) {
                    break;
                }
            }
        }
        if (picked.size() < total) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "销售题库不足，至少需要 " + total + " 道题");
        }

        Map<String, List<BlueprintQuestion>> sections = new LinkedHashMap<>();
        for (QuestionBankItem item : picked) {
            String type = classify(item);
            sections.computeIfAbsent(type, key -> new ArrayList<>()).add(toQuestion(item));
        }
        if (sections.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题型覆盖不足");
        }

        List<BlueprintSection> result = new ArrayList<>();
        for (Map.Entry<String, List<BlueprintQuestion>> entry : sections.entrySet()) {
            int weight = Math.max(1, entry.getValue().size() * 100 / picked.size());
            result.add(new BlueprintSection(entry.getKey(), weight, entry.getValue()));
        }
        return new InterviewBlueprint(
                "你好，我是今天的销售面试官。我们会围绕客户开拓、需求挖掘、方案呈现、异议处理和谈判成交展开，请尽量用真实案例回答。",
                result,
                "今天先聊到这里。感谢你的时间，后续结果会由招聘团队同步。");
    }

    private int targetDifficulty(String difficulty) {
        if (difficulty == null) {
            return 2;
        }
        return switch (difficulty) {
            case "CAMPUS", "EASY" -> 1;
            case "SENIOR", "HARD" -> 3;
            default -> 2;
        };
    }

    private int expectedCount(int durationMinutes) {
        if (durationMinutes <= 20) {
            return 6;
        }
        if (durationMinutes <= 35) {
            return 8;
        }
        return 10;
    }

    private String classify(QuestionBankItem item) {
        String category = item.category() == null ? "" : item.category();
        if (category.contains("销售认知") || category.contains("动机")) {
            return "MOTIVATION";
        }
        if (category.contains("异议") || category.contains("谈判")
                || category.contains("方案呈现")) {
            return "SCENARIO";
        }
        return "BEHAVIORAL";
    }

    private BlueprintQuestion toQuestion(QuestionBankItem item) {
        List<String> points = new ArrayList<>();
        for (String part : item.referenceAnswer().split("[。；;\\n]")) {
            String value = part.trim();
            if (!value.isBlank()) {
                points.add(value);
            }
            if (points.size() >= 5) {
                break;
            }
        }
        if (points.isEmpty()) {
            points.add(item.referenceAnswer());
        }
        String type = classify(item);
        List<String> followUps = switch (type) {
            case "MOTIVATION" -> List.of("能结合一个具体经历说明吗？", "这件事的结果如何量化？");
            case "SCENARIO" -> List.of("你当时的判断依据是什么？", "如果客户仍然拒绝，你会怎么调整？");
            default -> List.of("能具体讲一个客户案例吗？", "这件事的量化结果是什么？");
        };
        return new BlueprintQuestion(
                item.question(),
                item.difficulty(),
                List.copyOf(item.tags()),
                List.copyOf(points),
                followUps);
    }
}
