package com.offerpilot.interview.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomInterviewRequest(
        @NotBlank @Size(min = 30, max = 8000) String jdText,
        @NotNull Long resumeId,
        @NotBlank String difficulty,
        @NotNull @Min(10) @Max(60) Integer durationMinutes,
        @NotBlank String persona,
        String mode
) {
}
