package com.offerpilot.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.questionbank.service.QuestionBankRetriever;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.TrackQuestion;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import com.offerpilot.track.mapper.TrackQuestionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TrackQuestionRetrieverIT {

    @Autowired
    private QuestionBankRetriever retriever;

    @Autowired
    private InterviewTrackMapper trackMapper;

    @Autowired
    private TrackQuestionMapper trackQuestionMapper;

    @Test
    void retrievesOnlyQuestionsBoundToJavaBackendTrack() {
        InterviewTrack track = track("java-backend-campus");
        Set<Long> boundIds = boundQuestionIds(track.getId());

        List<QuestionBankItem> results = retriever.retrieve(
                "Vue 前端", List.of("Vue", "前端"), 5, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(item -> assertThat(boundIds).contains(item.id()));
        assertThat(results).noneMatch(item -> "Vue 和前端基础".equals(item.category()));
    }

    @Test
    void fullStackTrackCanRetrieveFrontendQuestions() {
        InterviewTrack track = track("java-fullstack-junior");
        Set<Long> boundIds = boundQuestionIds(track.getId());

        List<QuestionBankItem> results = retriever.retrieve(
                "Vue 前端", List.of("Vue", "前端"), 10, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(item -> assertThat(boundIds).contains(item.id()));
        assertThat(results).anyMatch(item -> "Vue 和前端基础".equals(item.category()));
    }

    @Test
    void retrievesSpringCircularDependencyQuestionForJavaBackend() {
        InterviewTrack track = track("java-backend-campus");

        List<QuestionBankItem> results = retriever.retrieve(
                "Spring 循环依赖 三级缓存", List.of("Spring", "循环依赖"), 5, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(item -> item.question().contains("循环依赖"));
    }

    @Test
    void retrievesRagQuestionForJavaAiTrack() {
        InterviewTrack track = track("java-ai");

        List<QuestionBankItem> results = retriever.retrieve(
                "RAG 召回 重排", List.of("RAG", "召回"), 5, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(item ->
                "Java + AI".equals(item.category()) || item.question().contains("RAG"));
    }

    @Test
    void retrievesJavaScriptEventLoopQuestionForFrontendTrack() {
        InterviewTrack track = track("frontend");

        List<QuestionBankItem> results = retriever.retrieve(
                "JavaScript 事件循环 微任务", List.of("JavaScript", "事件循环"), 5, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(item -> item.question().contains("事件循环"));
    }

    @Test
    void retrievesNewHeimaQuestionsForJavaBackendTrack() {
        InterviewTrack track = track("java-backend-campus");
        Set<Long> boundIds = boundQuestionIds(track.getId());

        List<QuestionBankItem> results = retriever.retrieve(
                "TCP 三次握手 四次挥手", List.of("TCP", "三次握手"), 5, track.getId());

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(item -> assertThat(boundIds).contains(item.id()));
        assertThat(results).anyMatch(item -> item.question().contains("三次握手"));
    }

    @Test
    void legacyRetrievalStillWorksWithoutTrack() {
        List<QuestionBankItem> results = retriever.retrieve(
                "Java 集合 HashMap", List.of("Java", "集合"), 5);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(item -> assertThat(item.question()).contains("HashMap"));
    }

    private InterviewTrack track(String code) {
        return trackMapper.selectOne(new LambdaQueryWrapper<InterviewTrack>()
                .eq(InterviewTrack::getCode, code));
    }

    private Set<Long> boundQuestionIds(Long trackId) {
        return trackQuestionMapper.selectList(new LambdaQueryWrapper<TrackQuestion>()
                        .eq(TrackQuestion::getTrackId, trackId)
                        .eq(TrackQuestion::getEnabled, 1))
                .stream()
                .map(TrackQuestion::getQuestionId)
                .collect(Collectors.toSet());
    }
}
