package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record QuestionDocumentExtractView(
        String fileName,
        int textLength,
        boolean truncated,
        boolean aiUsed,
        int total,
        List<QuestionBankImportItem> items
) {
}
