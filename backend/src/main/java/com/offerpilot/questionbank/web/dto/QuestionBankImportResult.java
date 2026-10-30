package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record QuestionBankImportResult(
        int imported,
        List<Long> ids
) {
}
