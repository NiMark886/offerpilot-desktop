package com.offerpilot.interview.web.dto;

public record AnswerResult(
        Long candidateMessageId,
        Long interviewerMessageId,
        Long questionId,
        String replyType,
        String replyText,
        String sessionStatus,
        Long currentQuestionId,
        boolean completed,
        AnswerEvaluationView evaluation
) {
}
