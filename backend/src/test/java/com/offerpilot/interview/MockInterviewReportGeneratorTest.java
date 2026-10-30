package com.offerpilot.interview;

import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.service.MockInterviewReportGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewReportGeneratorTest {

    private final MockInterviewReportGenerator generator = new MockInterviewReportGenerator();

    @Test
    void generatesFiveDimensionReport() {
        InterviewSession session = new InterviewSession();
        session.setMode("INTERVIEW");

        InterviewQuestion basic = question(1L, 1, "BASIC", "HashMap 扩容机制？");
        InterviewQuestion project = question(2L, 2, "PROJECT", "介绍你的项目");
        InterviewQuestion scenario = question(3L, 3, "SCENARIO", "线上 P99 升高怎么排查？");

        InterviewMessage a1 = answer(101L, 1L, "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍，会重新分布元素。");
        InterviewMessage a2 = answer(102L, 2L, "我负责订单系统，首先做了幂等设计，然后通过压测优化了慢 SQL。");
        InterviewMessage a3 = answer(103L, 3L, "先看监控和日志，再排查慢 SQL 与下游依赖，最后做限流和扩容。");

        AnswerEvaluation e1 = evaluation(1L, 101L, 4, "[\"阈值 8\"]");
        AnswerEvaluation e2 = evaluation(2L, 102L, 3, "[\"量化结果\"]");
        AnswerEvaluation e3 = evaluation(3L, 103L, 5, "[]");

        var result = generator.generate(session,
                List.of(basic, project, scenario),
                List.of(a1, a2, a3),
                List.of(e1, e2, e3));

        assertThat(result.dimensions()).hasSize(5);
        assertThat(result.dimensions()).containsKeys("accuracy", "depth", "project", "expression", "match");
        assertThat(result.overallScore()).isBetween(0, 100);
        assertThat(result.summary()).contains("本场面试");
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.weaknesses()).isNotEmpty();
        assertThat(result.suggestions()).isNotEmpty();
    }

    @Test
    void handlesNoEvaluations() {
        InterviewSession session = new InterviewSession();
        session.setMode("INTERVIEW");

        var result = generator.generate(session, List.of(), List.of(), List.of());

        assertThat(result.overallScore()).isZero();
        assertThat(result.dimensions()).hasSize(5);
        assertThat(result.summary()).contains("没有有效回答");
    }

    private InterviewQuestion question(Long id, int seq, String type, String content) {
        InterviewQuestion q = new InterviewQuestion();
        q.setId(id);
        q.setSeq(seq);
        q.setType(type);
        q.setContent(content);
        q.setStatus("ANSWERED");
        return q;
    }

    private InterviewMessage answer(Long id, Long questionId, String content) {
        InterviewMessage m = new InterviewMessage();
        m.setId(id);
        m.setQuestionId(questionId);
        m.setRole("CANDIDATE");
        m.setMessageType("ANSWER");
        m.setContent(content);
        return m;
    }

    private AnswerEvaluation evaluation(Long questionId, Long candidateMessageId, int score, String missing) {
        AnswerEvaluation e = new AnswerEvaluation();
        e.setQuestionId(questionId);
        e.setCandidateMessageId(candidateMessageId);
        e.setScore(score);
        e.setCorrectness(score >= 4 ? "CORRECT" : "PARTIAL");
        e.setMissingPoints(missing);
        return e;
    }
}
