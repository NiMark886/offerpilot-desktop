package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.track.domain.TrackQuestion;
import com.offerpilot.track.mapper.TrackQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class KeywordQuestionBankRetriever implements QuestionBankRetriever {

    private static final Logger log = LoggerFactory.getLogger(KeywordQuestionBankRetriever.class);

    private final QuestionBankMapper questionBankMapper;
    private final TrackQuestionMapper trackQuestionMapper;

    public KeywordQuestionBankRetriever(QuestionBankMapper questionBankMapper,
                                        TrackQuestionMapper trackQuestionMapper) {
        this.questionBankMapper = questionBankMapper;
        this.trackQuestionMapper = trackQuestionMapper;
    }

    @Override
    public List<QuestionBankItem> retrieve(String query, List<String> tags, int limit, Long trackId) {
        Map<Long, Integer> weights = new HashMap<>();
        List<QuestionBank> questions = loadQuestions(trackId, weights);
        List<String> tagTokens = tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> queryTokens = new ArrayList<>();
        if (query != null) {
            for (String token : query.toLowerCase(Locale.ROOT).split("[\\s,，、;；/]+")) {
                if (token.length() >= 2) {
                    queryTokens.add(token);
                }
            }
        }
        int size = Math.max(1, limit);
        return questions.stream()
                .map(question -> new Scored(
                        question,
                        score(question, tagTokens, queryTokens),
                        weights.getOrDefault(question.getId(), 100)))
                .sorted(Comparator
                        .comparingInt(Scored::score).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::weight).reversed())
                        .thenComparing(scored -> scored.question().getId()))
                .limit(size)
                .map(scored -> QuestionBankItem.from(scored.question()))
                .toList();
    }

    private List<QuestionBank> loadQuestions(Long trackId, Map<Long, Integer> weights) {
        if (trackId == null) {
            return questionBankMapper.selectList(
                    new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getStatus, "ACTIVE"));
        }

        List<TrackQuestion> bindings = trackQuestionMapper.selectList(
                new LambdaQueryWrapper<TrackQuestion>()
                        .eq(TrackQuestion::getTrackId, trackId)
                        .eq(TrackQuestion::getEnabled, 1));
        if (bindings.isEmpty()) {
            log.warn("No questions bound to track {}, fallback to global question bank", trackId);
            return questionBankMapper.selectList(
                    new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getStatus, "ACTIVE"));
        }

        for (TrackQuestion binding : bindings) {
            weights.put(binding.getQuestionId(), binding.getWeight() == null ? 100 : binding.getWeight());
        }
        List<Long> ids = bindings.stream().map(TrackQuestion::getQuestionId).toList();
        return questionBankMapper.selectBatchIds(ids).stream()
                .filter(question -> "ACTIVE".equals(question.getStatus()))
                .toList();
    }

    private int score(QuestionBank question, List<String> tags, List<String> queryTokens) {
        String text = (question.getQuestion() + " " + question.getReferenceAnswer() + " "
                + question.getTags() + " " + question.getCategory()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String tag : tags) {
            if (text.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 5;
            }
        }
        for (String token : queryTokens) {
            if (text.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private record Scored(QuestionBank question, int score, int weight) {
    }
}
