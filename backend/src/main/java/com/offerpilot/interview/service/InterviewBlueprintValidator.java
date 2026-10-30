package com.offerpilot.interview.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects structurally invalid AI-generated blueprints so the caller can fall
 * back to the deterministic mock generator instead of persisting bad data.
 */
@Component
public class InterviewBlueprintValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("BASIC", "PROJECT", "SCENARIO", "AI");
    private static final Set<String> GENERAL_TYPES = Set.of("BEHAVIORAL", "SCENARIO", "ROLE_PLAY", "MOTIVATION");
    private static final int MIN_QUESTIONS = 5;
    private static final int MAX_QUESTIONS = 12;

    public void validate(InterviewBlueprint blueprint) {
        validate(blueprint, RoleProfile.tech(), 0);
    }

    public void validate(InterviewBlueprint blueprint, RoleProfile profile, int expectedCount) {
        if (profile != null && profile.isGeneral()) {
            validateGeneral(blueprint, profile, expectedCount);
        } else {
            validateTech(blueprint);
        }
    }

    private void validateTech(InterviewBlueprint blueprint) {
        if (blueprint == null || blueprint.sections() == null || blueprint.sections().isEmpty()) {
            throw new IllegalArgumentException("Blueprint must contain at least one section");
        }

        int totalQuestions = 0;
        for (BlueprintSection section : blueprint.sections()) {
            if (section == null) {
                throw new IllegalArgumentException("Blueprint section cannot be null");
            }
            if (section.type() == null || !ALLOWED_TYPES.contains(section.type())) {
                throw new IllegalArgumentException("Unsupported section type: " + section.type());
            }
            if (section.questions() == null || section.questions().isEmpty()) {
                throw new IllegalArgumentException("Section must contain at least one question: " + section.type());
            }
            for (BlueprintQuestion question : section.questions()) {
                validateQuestion(question);
                totalQuestions++;
            }
        }

        if (totalQuestions < MIN_QUESTIONS || totalQuestions > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Blueprint question count out of range: " + totalQuestions);
        }
    }

    private void validateGeneral(InterviewBlueprint blueprint, RoleProfile profile, int expectedCount) {
        if (blueprint == null || blueprint.sections() == null || blueprint.sections().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售蓝图至少需要一个部分");
        }
        List<String> competencies = profile.competencyNames();
        if (competencies.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售能力模型未配置");
        }

        Set<String> covered = new LinkedHashSet<>();
        int totalQuestions = 0;
        for (BlueprintSection section : blueprint.sections()) {
            if (section == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售蓝图部分不能为空");
            }
            if (section.type() == null || !GENERAL_TYPES.contains(section.type())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题型不支持: " + section.type());
            }
            if (section.questions() == null || section.questions().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题型不能为空: " + section.type());
            }
            for (BlueprintQuestion question : section.questions()) {
                validateQuestion(question, competencies, covered);
                totalQuestions++;
            }
        }

        if (expectedCount > 0 && totalQuestions != expectedCount) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "销售题目数量应为 " + expectedCount + "，实际为 " + totalQuestions);
        }
        if (totalQuestions >= 6 && !covered.containsAll(competencies)) {
            List<String> missing = competencies.stream().filter(name -> !covered.contains(name)).toList();
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "销售能力覆盖不足: " + String.join("、", missing));
        }
    }

    private void validateQuestion(BlueprintQuestion question) {
        if (question == null || question.text() == null || question.text().isBlank()) {
            throw new IllegalArgumentException("Question text cannot be blank");
        }
        if (question.difficulty() < 1 || question.difficulty() > 3) {
            throw new IllegalArgumentException("Question difficulty must be between 1 and 3");
        }
        validatePoints(question.referencePoints(), "referencePoints", question.text());
    }

    private void validateQuestion(BlueprintQuestion question, List<String> competencies, Set<String> covered) {
        validateQuestion(question);
        if (question.knowledgeTags() == null || question.knowledgeTags().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题目缺少能力标签: " + question.text());
        }
        String matched = question.knowledgeTags().stream()
                .filter(competencies::contains)
                .findFirst()
                .orElse(null);
        if (matched == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "销售题目必须包含能力标签: " + question.text());
        }
        covered.add(matched);
    }

    private void validatePoints(List<String> points, String field, String questionText) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty: " + questionText);
        }
        for (String point : points) {
            if (point == null || point.isBlank()) {
                throw new IllegalArgumentException(field + " contains a blank item: " + questionText);
            }
        }
    }
}
