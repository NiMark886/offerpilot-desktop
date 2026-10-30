package com.offerpilot.interview.web.dto;

import java.util.List;

public record QuestionReviewView(
        Long questionId,
        Integer seq,
        String type,
        String question,
        String answer,
        String promptType,
        String promptText,
        String correctness,
        Integer score,
        String comment,
        List<String> missingPoints
) {
}
