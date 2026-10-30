package com.offerpilot.questionbank.web.dto;

import com.offerpilot.questionbank.domain.QuestionBank;

import java.util.Arrays;
import java.util.List;

public record QuestionBankItem(
        Long id,
        String category,
        List<String> tags,
        int difficulty,
        String question,
        String referenceAnswer,
        String source
) {

    public static QuestionBankItem from(QuestionBank bank) {
        List<String> tags = bank.getTags() == null ? List.of()
                : Arrays.stream(bank.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new QuestionBankItem(
                bank.getId(),
                bank.getCategory(),
                tags,
                bank.getDifficulty() == null ? 2 : bank.getDifficulty(),
                bank.getQuestion(),
                bank.getReferenceAnswer(),
                bank.getSource());
    }
}
