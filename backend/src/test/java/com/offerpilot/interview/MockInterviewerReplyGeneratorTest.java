package com.offerpilot.interview;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.interview.service.MockInterviewerReplyGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewerReplyGeneratorTest {

    private final MockInterviewerReplyGenerator generator = new MockInterviewerReplyGenerator();

    @Test
    void generatesOpeningAndQuestion() {
        InterviewQuestion question = question("HashMap 的扩容机制？", "[\"扩容 2 倍\"]", "[\"扩容后如何分布？\"]");

        String opening = generator.generate(question, ReplyType.OPENING, null, List.of());
        String next = generator.generate(question, ReplyType.QUESTION, null, List.of());

        assertThat(opening).contains("HashMap");
        assertThat(next).contains("HashMap");
    }

    @Test
    void generatesFollowUpFromHint() {
        InterviewQuestion question = question("HashMap 的扩容机制？", "[\"扩容 2 倍\"]", "[\"扩容后如何分布？\"]");

        String reply = generator.generate(question, ReplyType.FOLLOW_UP, "不知道", List.of());

        assertThat(reply).contains("追问");
        assertThat(reply).isNotBlank();
    }

    @Test
    void rotatesFollowUpHints() {
        InterviewQuestion question = question(
                "HashMap 的扩容机制？",
                "[\"扩容 2 倍\"]",
                "[\"追问一？\",\"追问二？\"]");

        question.setFollowUpCount(1);
        String first = generator.generate(question, ReplyType.FOLLOW_UP, null, List.of());
        question.setFollowUpCount(2);
        String second = generator.generate(question, ReplyType.FOLLOW_UP, null, List.of());

        assertThat(first).contains("追问一");
        assertThat(second).contains("追问二");
    }

    @Test
    void generatesClosing() {
        String reply = generator.generate(null, ReplyType.CLOSING, null, List.of());

        assertThat(reply).contains("面试到这里");
    }

    private InterviewQuestion question(String text, String referencePoints, String followUps) {
        InterviewQuestion question = new InterviewQuestion();
        question.setContent(text);
        question.setKnowledgeTags("[\"HashMap\"]");
        question.setReferencePoints(referencePoints);
        question.setFollowUpHints(followUps);
        return question;
    }
}
