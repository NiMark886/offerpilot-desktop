package com.offerpilot.questionbank.service;

import com.offerpilot.questionbank.web.dto.QuestionBankItem;

import java.util.List;

public interface QuestionBankRetriever {

    default List<QuestionBankItem> retrieve(String query, List<String> tags, int limit) {
        return retrieve(query, tags, limit, null);
    }

    List<QuestionBankItem> retrieve(String query, List<String> tags, int limit, Long trackId);
}
