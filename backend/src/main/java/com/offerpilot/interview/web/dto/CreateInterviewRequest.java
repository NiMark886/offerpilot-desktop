package com.offerpilot.interview.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInterviewRequest(
        @NotNull Long jobTemplateId,
        @NotNull Long resumeId,
        @NotBlank String difficulty,
        @NotNull @Min(10) @Max(60) Integer durationMinutes,
        @NotBlank String persona,
        String mode
) {
}
