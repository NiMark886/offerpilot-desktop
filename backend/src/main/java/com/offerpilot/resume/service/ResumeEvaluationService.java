package com.offerpilot.resume.service;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.web.dto.ResumeAssessmentView;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.resume.web.dto.ResumeEvaluationRequest;
import com.offerpilot.resume.web.dto.ResumeEvaluationView;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import com.offerpilot.track.service.RoleProfileParser;
import org.springframework.stereotype.Service;

@Service
public class ResumeEvaluationService {

    private final ResumeMapper resumeMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final InterviewTrackMapper trackMapper;
    private final RoleProfileParser roleProfileParser;
    private final ResumeEvaluator resumeEvaluator;

    public ResumeEvaluationService(ResumeMapper resumeMapper,
                                   JobTemplateMapper jobTemplateMapper,
                                   InterviewTrackMapper trackMapper,
                                   RoleProfileParser roleProfileParser,
                                   ResumeEvaluator resumeEvaluator) {
        this.resumeMapper = resumeMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.trackMapper = trackMapper;
        this.roleProfileParser = roleProfileParser;
        this.resumeEvaluator = resumeEvaluator;
    }

    public ResumeEvaluationView evaluate(Long userId, Long resumeId, ResumeEvaluationRequest request) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }

        JobTemplate job = null;
        RoleProfile profile = RoleProfile.tech();
        if (request != null && request.jobTemplateId() != null) {
            job = jobTemplateMapper.selectById(request.jobTemplateId());
            if (job == null || !"ACTIVE".equals(job.getStatus())
                    || (job.getUserId() != null && !job.getUserId().equals(userId))) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "岗位模板不存在");
            }
            if (job.getTrackId() != null) {
                InterviewTrack track = trackMapper.selectById(job.getTrackId());
                profile = roleProfileParser.parse(track);
            }
        } else if (request != null && request.jdText() != null && !request.jdText().isBlank()) {
            job = new JobTemplate();
            job.setTitle(request.jobTitle() == null || request.jobTitle().isBlank()
                    ? "目标岗位" : request.jobTitle().trim());
            job.setJdText(request.jdText().trim());
        }

        ResumeAssessment assessment = resumeEvaluator.evaluate(resume, job, profile);
        return new ResumeEvaluationView(
                resume.getId(),
                resume.getFileName(),
                job == null ? "通用简历评估" : job.getTitle(),
                ResumeAssessmentView.from(assessment));
    }
}
