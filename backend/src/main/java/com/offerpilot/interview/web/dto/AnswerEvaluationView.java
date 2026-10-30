package com.offerpilot.interview.web.dto;

import com.offerpilot.interview.domain.AnswerEvaluation;

import java.time.LocalDateTime;
import java.util.List;

public record AnswerEvaluationView(
        Long id,
        Long candidateMessageId,
        Long questionId,
        String promptText,
        String promptType,
        String correctness,
        Integer score,
        String comment,
        List<String> missingPoints,
        Double confidence,
        LocalDateTime createdAt
) {

    public static AnswerEvaluationView from(AnswerEvaluation evaluation, List<String> missingPoints) {
        return new AnswerEvaluationView(
                evaluation.getId(),
                evaluation.getCandidateMessageId(),
                evaluation.getQuestionId(),
                evaluation.getPromptText(),
                evaluation.getPromptType(),
                evaluation.getCorrectness(),
                evaluation.getScore(),
                evaluation.getComment(),
                missingPoints,
                evaluation.getConfidence(),
                evaluation.getCreatedAt());
    }
}
