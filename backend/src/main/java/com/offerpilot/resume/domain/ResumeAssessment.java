package com.offerpilot.resume.domain;

import java.util.Map;

/**
 * Resume assessment: education 25%, relevant skills 25%, internship/work
 * experience 40%, layout/structure 10%.
 */
public record ResumeAssessment(
        int score,
        int educationScore,
        int skillsScore,
        int experienceScore,
        int layoutScore,
        Map<String, Integer> weights,
        boolean aiGenerated,
        String comment
) {
}
