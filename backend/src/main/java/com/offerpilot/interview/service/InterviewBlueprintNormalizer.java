package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Repairs small, predictable deviations in AI-generated blueprints before
 * validation. Structural problems still fall back to the mock generator.
 */
@Component
public class InterviewBlueprintNormalizer {

    public InterviewBlueprint normalize(InterviewBlueprint blueprint) {
        if (blueprint == null || blueprint.sections() == null) {
            return blueprint;
        }
        List<BlueprintSection> sections = blueprint.sections().stream()
                .map(this::normalizeSection)
                .toList();
        return new InterviewBlueprint(blueprint.opening(), sections, blueprint.closing());
    }

    private BlueprintSection normalizeSection(BlueprintSection section) {
        if (section == null) {
            return null;
        }
        List<BlueprintQuestion> questions = section.questions() == null
                ? null
                : section.questions().stream().map(this::normalizeQuestion).toList();
        return new BlueprintSection(section.type(), section.weight(), questions);
    }

    private BlueprintQuestion normalizeQuestion(BlueprintQuestion question) {
        if (question == null) {
            return null;
        }
        return new BlueprintQuestion(
                question.text(),
                clampDifficulty(question.difficulty()),
                question.knowledgeTags(),
                question.referencePoints(),
                question.followUpHints());
    }

    private int clampDifficulty(int difficulty) {
        return Math.max(1, Math.min(3, difficulty));
    }
}
