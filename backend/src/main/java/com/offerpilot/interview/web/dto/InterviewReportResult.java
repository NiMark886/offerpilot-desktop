package com.offerpilot.interview.web.dto;

import java.util.List;
import java.util.Map;

public record InterviewReportResult(
        Map<String, Integer> dimensions,
        int overallScore,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {
}
