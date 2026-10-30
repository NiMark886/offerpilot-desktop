package com.offerpilot.interview.web.dto;

import java.util.List;

public record AnswerEvaluationResult(
        String correctness,
        int score,
        String comment,
        List<String> missingPoints,
        double confidence
) {
}
