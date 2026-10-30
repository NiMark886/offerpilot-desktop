package com.offerpilot.interview;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.service.MockAnswerEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAnswerEvaluatorTest {

    private final MockAnswerEvaluator evaluator = new MockAnswerEvaluator();

    @Test
    void marksKnownAnswerAsCorrectOrPartial() {
        InterviewQuestion question = question(
                "[\"数组+链表+红黑树\",\"扩容 2 倍\",\"链表转红黑树阈值 8\"]");

        var result = evaluator.evaluate(question, question.getContent(), "QUESTION",
                "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍");

        assertThat(result.correctness()).isIn("CORRECT", "PARTIAL");
        assertThat(result.score()).isBetween(3, 5);
        assertThat(result.comment()).isNotBlank();
    }

    @Test
    void marksUnknownAnswerAsIncorrect() {
        InterviewQuestion question = question(
                "[\"数组+链表+红黑树\",\"扩容 2 倍\"]");

        var result = evaluator.evaluate(question, question.getContent(), "QUESTION", "不知道");

        assertThat(result.correctness()).isEqualTo("INCORRECT");
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.missingPoints()).isNotEmpty();
    }

    @Test
    void evaluatesFollowUpWithoutOriginalReferencePoints() {
        InterviewQuestion question = question(
                "[\"数组+链表+红黑树\",\"扩容 2 倍\",\"链表转红黑树阈值 8\"]");

        var result = evaluator.evaluate(question,
                "我想追问一下：扩容后元素如何重新分布？",
                "FOLLOW_UP",
                "会重新分布到新的位置");

        assertThat(result.correctness()).isIn("PARTIAL", "CORRECT");
        assertThat(result.missingPoints()).doesNotContain("链表转红黑树阈值 8");
        assertThat(result.comment()).doesNotContain("参考要点");
    }

    private InterviewQuestion question(String referencePoints) {
        InterviewQuestion question = new InterviewQuestion();
        question.setContent("HashMap 的扩容机制？");
        question.setReferencePoints(referencePoints);
        question.setKnowledgeTags("[\"HashMap\"]");
        return question;
    }
}
