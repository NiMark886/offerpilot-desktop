package com.offerpilot.questionbank.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionBankImportRequest(
        @NotEmpty @Size(max = 100) List<@Valid QuestionBankImportItem> questions,
        List<Long> trackIds
) {
}
