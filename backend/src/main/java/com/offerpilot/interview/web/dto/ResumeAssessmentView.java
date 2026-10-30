package com.offerpilot.interview.web.dto;

import com.offerpilot.resume.domain.ResumeAssessment;

import java.util.Map;

public record ResumeAssessmentView(
        int score,
        int educationScore,
        int skillsScore,
        int experienceScore,
        int layoutScore,
        Map<String, Integer> weights,
        boolean aiGenerated,
        String comment
) {

    public static ResumeAssessmentView from(ResumeAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        return new ResumeAssessmentView(
                assessment.score(),
                assessment.educationScore(),
                assessment.skillsScore(),
                assessment.experienceScore(),
                assessment.layoutScore(),
                assessment.weights(),
                assessment.aiGenerated(),
                assessment.comment());
    }
}
