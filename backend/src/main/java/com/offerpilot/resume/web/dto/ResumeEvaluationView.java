package com.offerpilot.resume.web.dto;

import com.offerpilot.interview.web.dto.ResumeAssessmentView;

public record ResumeEvaluationView(
        Long resumeId,
        String fileName,
        String jobTitle,
        ResumeAssessmentView assessment
) {
}
