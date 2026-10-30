package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;

public record InterviewHistoryItem(
        Long sessionId,
        String jobTitle,
        String mode,
        String status,
        Integer totalScore,
        LocalDateTime createdAt,
        boolean hasReport
) {
}
