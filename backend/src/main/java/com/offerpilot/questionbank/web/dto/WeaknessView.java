package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record WeaknessView(
        String tag,
        int count,
        String lastSeen,
        List<QuestionBankItem> recommendedQuestions
) {
}
