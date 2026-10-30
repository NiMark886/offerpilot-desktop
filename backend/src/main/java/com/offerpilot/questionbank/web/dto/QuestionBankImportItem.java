package com.offerpilot.questionbank.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionBankImportItem(
        @NotBlank @Size(max = 4000) String question,
        @NotBlank @Size(max = 20000) String referenceAnswer,
        @NotBlank @Size(max = 64) String category,
        @NotNull @Min(1) @Max(3) Integer difficulty,
        @NotEmpty List<@NotBlank @Size(max = 50) String> tags,
        @Size(max = 255) String source
) {
}
