package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.InterviewBlueprintNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewBlueprintNormalizerTest {

    private final InterviewBlueprintNormalizer normalizer = new InterviewBlueprintNormalizer();

    @Test
    void clampsOutOfRangeDifficulty() {
        InterviewBlueprint blueprint = new InterviewBlueprint(
                "opening",
                List.of(new BlueprintSection("BASIC", 30, List.of(
                        question("q1", 0),
                        question("q2", 5),
                        question("q3", 2)))),
                "closing");

        InterviewBlueprint normalized = normalizer.normalize(blueprint);

        assertThat(normalized.sections().get(0).questions())
                .extracting(BlueprintQuestion::difficulty)
                .containsExactly(1, 3, 2);
    }

    @Test
    void keepsStructuralProblemsForValidator() {
        InterviewBlueprint blueprint = new InterviewBlueprint(
                "opening",
                List.of(new BlueprintSection("BASIC", 30, null)),
                "closing");

        assertThat(normalizer.normalize(blueprint).sections().get(0).questions()).isNull();
    }

    private BlueprintQuestion question(String text, int difficulty) {
        return new BlueprintQuestion(text, difficulty, List.of("Java"),
                List.of("point"), List.of("hint"));
    }
}
