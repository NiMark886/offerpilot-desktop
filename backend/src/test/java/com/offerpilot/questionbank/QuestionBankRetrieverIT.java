package com.offerpilot.questionbank;

import com.offerpilot.questionbank.service.QuestionBankRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuestionBankRetrieverIT {

    @Autowired
    private QuestionBankRetriever retriever;

    @Test
    void retrievesRelevantQuestionsByTags() {
        var results = retriever.retrieve("Java 集合 HashMap", List.of("Java", "集合"), 5);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(item -> assertThat(item.question()).contains("HashMap"));
    }

    @Test
    void retrievesConcurrencyQuestions() {
        var results = retriever.retrieve("Java 线程池 并发", List.of("Java", "并发"), 5);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(item -> assertThat(item.question()).contains("线程池"));
    }
}
