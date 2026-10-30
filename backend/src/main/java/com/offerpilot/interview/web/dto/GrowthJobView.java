package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;

/**
 * One growth-record card. Jobs are grouped by job template, except sales which
 * is grouped as one "销售岗面试" card across all custom sales JDs.
 */
public record GrowthJobView(
        String key,
        Long jobTemplateId,
        String jobTitle,
        String category,
        String trackCode,
        String trackName,
        int totalInterviews,
        int completedInterviews,
        double averageScore,
        int bestScore,
        LocalDateTime lastInterviewAt
) {
}
