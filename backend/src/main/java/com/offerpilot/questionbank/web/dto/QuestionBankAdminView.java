package com.offerpilot.questionbank.web.dto;

import com.offerpilot.questionbank.domain.QuestionBank;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record QuestionBankAdminView(
        Long id,
        String category,
        List<String> tags,
        Integer difficulty,
        String question,
        String referenceAnswer,
        String source,
        String status,
        List<Long> trackIds,
        List<String> trackNames,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static QuestionBankAdminView from(QuestionBank bank) {
        return from(bank, List.of(), List.of());
    }

    public static QuestionBankAdminView from(QuestionBank bank, List<Long> trackIds, List<String> trackNames) {
        List<String> tags = bank.getTags() == null ? List.of()
                : Arrays.stream(bank.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new QuestionBankAdminView(
                bank.getId(), bank.getCategory(), tags,
                bank.getDifficulty(), bank.getQuestion(), bank.getReferenceAnswer(),
                bank.getSource(), bank.getStatus(), trackIds, trackNames,
                bank.getCreatedAt(), bank.getUpdatedAt());
    }
}
