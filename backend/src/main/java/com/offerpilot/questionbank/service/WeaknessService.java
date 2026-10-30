package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.questionbank.web.dto.WeaknessView;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeaknessService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final QuestionBankRetriever questionBankRetriever;
    private final InterviewTrackMapper trackMapper;
    private final ObjectMapper objectMapper;

    public WeaknessService(InterviewSessionMapper sessionMapper,
                           InterviewQuestionMapper questionMapper,
                           AnswerEvaluationMapper evaluationMapper,
                           QuestionBankRetriever questionBankRetriever,
                           InterviewTrackMapper trackMapper,
                           ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.evaluationMapper = evaluationMapper;
        this.questionBankRetriever = questionBankRetriever;
        this.trackMapper = trackMapper;
        this.objectMapper = objectMapper;
    }

    public List<WeaknessView> weaknesses(Long userId) {
        return weaknesses(userId, null, null);
    }

    public List<WeaknessView> weaknesses(Long userId, Long jobTemplateId) {
        return weaknesses(userId, jobTemplateId, null);
    }

    public List<WeaknessView> weaknesses(Long userId, Long jobTemplateId, String trackCode) {
        LambdaQueryWrapper<InterviewSession> sessionQuery = new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .orderByDesc(InterviewSession::getCreatedAt)
                .last("LIMIT 100");
        if (trackCode != null && !trackCode.isBlank()) {
            InterviewTrack track = trackMapper.selectOne(new LambdaQueryWrapper<InterviewTrack>()
                    .eq(InterviewTrack::getCode, trackCode)
                    .last("LIMIT 1"));
            if (track == null) {
                return List.of();
            }
            sessionQuery.eq(InterviewSession::getTrackId, track.getId());
        } else if (jobTemplateId != null) {
            sessionQuery.eq(InterviewSession::getJobTemplateId, jobTemplateId);
        }
        List<InterviewSession> sessions = sessionMapper.selectList(sessionQuery);
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getSessionId, sessionIds));
        Map<Long, List<String>> tagsByQuestion = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            tagsByQuestion.put(question.getId(), readList(question.getKnowledgeTags()));
        }
        Map<Long, LocalDateTime> sessionTimes = new LinkedHashMap<>();
        Map<Long, InterviewSession> sessionById = new LinkedHashMap<>();
        Map<Long, InterviewTrack> trackById = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            sessionTimes.put(session.getId(), session.getCreatedAt());
            sessionById.put(session.getId(), session);
            if (session.getTrackId() != null) {
                trackById.computeIfAbsent(session.getTrackId(), trackMapper::selectById);
            }
        }

        List<AnswerEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .in(AnswerEvaluation::getSessionId, sessionIds));

        Map<String, Accumulator> counts = new LinkedHashMap<>();
        for (AnswerEvaluation evaluation : evaluations) {
            boolean weak = !"CORRECT".equals(evaluation.getCorrectness())
                    || (evaluation.getMissingPoints() != null && !evaluation.getMissingPoints().isBlank());
            if (!weak) {
                continue;
            }
            List<String> tags = tagsByQuestion.getOrDefault(evaluation.getQuestionId(), List.of());
            LocalDateTime time = sessionTimes.get(evaluation.getSessionId());
            InterviewSession session = sessionById.get(evaluation.getSessionId());
            InterviewTrack track = session == null || session.getTrackId() == null
                    ? null : trackById.get(session.getTrackId());
            boolean sales = track != null && ("GENERAL".equalsIgnoreCase(track.getCategory())
                    || "SALES".equalsIgnoreCase(track.getRubricCode()));
            for (String tag : tags) {
                Accumulator accumulator = counts.computeIfAbsent(tag, key -> new Accumulator());
                accumulator.count++;
                if (sales) {
                    accumulator.sales = true;
                    accumulator.trackId = track.getId();
                }
                if (time != null && (accumulator.lastSeen == null || time.isAfter(accumulator.lastSeen))) {
                    accumulator.lastSeen = time;
                }
            }
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                .limit(6)
                .map(entry -> new WeaknessView(
                        entry.getKey(),
                        entry.getValue().count,
                        entry.getValue().lastSeen == null ? "" : entry.getValue().lastSeen.toLocalDate().toString(),
                        entry.getValue().sales
                                ? questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3,
                                        entry.getValue().trackId)
                                : questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3)))
                .toList();
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static class Accumulator {
        private int count;
        private LocalDateTime lastSeen;
        private boolean sales;
        private Long trackId;
    }
}
