package com.offerpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.job.web.dto.JobTemplateView;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.service.InterviewTrackService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JobTemplateService {

    private final JobTemplateMapper jobTemplateMapper;
    private final InterviewTrackService trackService;

    public JobTemplateService(JobTemplateMapper jobTemplateMapper,
                              InterviewTrackService trackService) {
        this.jobTemplateMapper = jobTemplateMapper;
        this.trackService = trackService;
    }

    public List<JobTemplateView> listActive(Long userId) {
        Map<Long, InterviewTrack> activeTracks = trackService.listActive().stream()
                .collect(Collectors.toMap(InterviewTrack::getId, track -> track));

        LambdaQueryWrapper<JobTemplate> wrapper = new LambdaQueryWrapper<JobTemplate>()
                .eq(JobTemplate::getStatus, "ACTIVE")
                .orderByAsc(JobTemplate::getSortOrder);
        if (userId == null) {
            wrapper.isNull(JobTemplate::getUserId);
        } else {
            wrapper.and(w -> w.isNull(JobTemplate::getUserId).or().eq(JobTemplate::getUserId, userId));
        }

        return jobTemplateMapper.selectList(wrapper)
                .stream()
                .filter(job -> job.getTrackId() == null || activeTracks.containsKey(job.getTrackId()))
                .filter(job -> !isUserSalesJob(job, activeTracks))
                .map(job -> {
                    InterviewTrack track = job.getTrackId() == null ? null : activeTracks.get(job.getTrackId());
                    long count = track == null ? 0 : trackService.countQuestions(track.getId());
                    return JobTemplateView.from(job, track, count);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isUserSalesJob(JobTemplate job, Map<Long, InterviewTrack> activeTracks) {
        if (job.getUserId() == null || job.getTrackId() == null) {
            return false;
        }
        InterviewTrack track = activeTracks.get(job.getTrackId());
        return track != null
                && ("GENERAL".equalsIgnoreCase(track.getCategory())
                || "SALES".equalsIgnoreCase(track.getRubricCode()));
    }
}
