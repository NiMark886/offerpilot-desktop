package com.offerpilot.questionbank.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionBankAdminRequest(
        @NotBlank String category,
        @NotBlank String tags,
        @NotNull @Min(1) @Max(3) Integer difficulty,
        @NotBlank String question,
        @NotBlank String referenceAnswer,
        String source,
        String status,
        List<Long> trackIds
) {
}
