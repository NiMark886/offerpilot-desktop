package com.offerpilot.track.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.TrackQuestion;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import com.offerpilot.track.mapper.TrackQuestionMapper;
import com.offerpilot.track.web.dto.InterviewTrackView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class InterviewTrackService {

    private final InterviewTrackMapper trackMapper;
    private final TrackQuestionMapper trackQuestionMapper;
    private final QuestionBankMapper questionBankMapper;

    public InterviewTrackService(InterviewTrackMapper trackMapper,
                                 TrackQuestionMapper trackQuestionMapper,
                                 QuestionBankMapper questionBankMapper) {
        this.trackMapper = trackMapper;
        this.trackQuestionMapper = trackQuestionMapper;
        this.questionBankMapper = questionBankMapper;
    }

    public List<InterviewTrackView> listAll() {
        return trackMapper.selectList(new LambdaQueryWrapper<InterviewTrack>()
                        .orderByAsc(InterviewTrack::getSortOrder))
                .stream()
                .map(track -> InterviewTrackView.from(track, countQuestions(track.getId())))
                .toList();
    }

    public List<InterviewTrackView> listTech() {
        return listAll().stream()
                .filter(view -> "TECH".equalsIgnoreCase(view.category()))
                .toList();
    }

    public List<InterviewTrack> listActive() {
        return trackMapper.selectList(new LambdaQueryWrapper<InterviewTrack>()
                .eq(InterviewTrack::getStatus, "ACTIVE")
                .orderByAsc(InterviewTrack::getSortOrder));
    }

    public InterviewTrack requireByCode(String code) {
        InterviewTrack track = trackMapper.selectOne(new LambdaQueryWrapper<InterviewTrack>()
                .eq(InterviewTrack::getCode, code)
                .eq(InterviewTrack::getStatus, "ACTIVE"));
        if (track == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位题库不存在");
        }
        return track;
    }

    public InterviewTrack require(Long trackId) {
        InterviewTrack track = trackMapper.selectById(trackId);
        if (track == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位题库不存在");
        }
        return track;
    }

    public InterviewTrack requireActive(Long trackId) {
        InterviewTrack track = require(trackId);
        if (!"ACTIVE".equals(track.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "岗位题库尚未开放");
        }
        return track;
    }

    /**
     * 旧岗位模板没有 track_id 时返回 null，保持原有全局题库检索行为。
     * 新岗位模板的赛道不存在或未启用时拒绝创建面试。
     */
    public InterviewTrack resolveForJob(JobTemplate job) {
        if (job.getTrackId() == null) {
            return null;
        }
        InterviewTrack track = trackMapper.selectById(job.getTrackId());
        if (track == null || !"ACTIVE".equals(track.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该岗位题库正在维护，请选择其他岗位");
        }
        return track;
    }

    public long countQuestions(Long trackId) {
        return trackQuestionMapper.countActiveQuestions(trackId);
    }

    public List<Long> questionIds(Long trackId) {
        return trackQuestionMapper.selectList(new LambdaQueryWrapper<TrackQuestion>()
                        .eq(TrackQuestion::getTrackId, trackId)
                        .eq(TrackQuestion::getEnabled, 1))
                .stream()
                .map(TrackQuestion::getQuestionId)
                .toList();
    }

    @Transactional
    public void bind(Long trackId, List<Long> questionIds) {
        require(trackId);
        if (questionIds == null) {
            return;
        }
        questionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(questionId -> {
                    QuestionBank question = questionBankMapper.selectById(questionId);
                    if (question == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在: " + questionId);
                    }
                    Long existing = trackQuestionMapper.selectCount(new LambdaQueryWrapper<TrackQuestion>()
                            .eq(TrackQuestion::getTrackId, trackId)
                            .eq(TrackQuestion::getQuestionId, questionId));
                    if (existing != null && existing > 0) {
                        return;
                    }
                    TrackQuestion binding = new TrackQuestion();
                    binding.setTrackId(trackId);
                    binding.setQuestionId(questionId);
                    binding.setSortOrder(0);
                    binding.setWeight(100);
                    binding.setEnabled(1);
                    trackQuestionMapper.insert(binding);
                });
    }

    @Transactional
    public void unbind(Long trackId, Long questionId) {
        require(trackId);
        trackQuestionMapper.delete(new LambdaQueryWrapper<TrackQuestion>()
                .eq(TrackQuestion::getTrackId, trackId)
                .eq(TrackQuestion::getQuestionId, questionId));
    }
}
