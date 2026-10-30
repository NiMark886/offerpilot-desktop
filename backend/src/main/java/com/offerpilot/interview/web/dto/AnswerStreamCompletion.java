package com.offerpilot.interview.web.dto;

public record AnswerStreamCompletion(
        String sessionStatus,
        Long currentQuestionId,
        boolean completed,
        AnswerEvaluationView evaluation
) {
}
