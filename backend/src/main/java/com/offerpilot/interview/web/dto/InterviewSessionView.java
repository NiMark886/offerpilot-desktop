package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionView(
        Long id,
        Long jobTemplateId,
        String jobTitle,
        Long resumeId,
        String difficulty,
        Integer durationMinutes,
        String persona,
        String mode,
        String status,
        String generationMode,
        Long currentQuestionId,
        LocalDateTime createdAt,
        List<QuestionView> questions
) {
}
