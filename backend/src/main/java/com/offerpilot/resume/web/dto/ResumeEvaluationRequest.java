package com.offerpilot.resume.web.dto;

import jakarta.validation.constraints.Size;

public record ResumeEvaluationRequest(
        Long jobTemplateId,
        @Size(max = 120) String jobTitle,
        @Size(max = 8000) String jdText
) {
}
