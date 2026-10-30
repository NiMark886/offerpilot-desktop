package com.offerpilot.job.web.dto;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.track.domain.InterviewTrack;

public record JobTemplateView(
        Long id,
        String title,
        String level,
        String category,
        String jdText,
        Long trackId,
        String trackCode,
        String trackName,
        String roleFamily,
        String primaryLanguage,
        String trackLevel,
        Integer questionCount
) {

    public static JobTemplateView from(JobTemplate jobTemplate) {
        return from(jobTemplate, null, 0);
    }

    public static JobTemplateView from(JobTemplate jobTemplate, InterviewTrack track, long questionCount) {
        return new JobTemplateView(
                jobTemplate.getId(),
                jobTemplate.getTitle(),
                jobTemplate.getLevel(),
                jobTemplate.getCategory(),
                jobTemplate.getJdText(),
                jobTemplate.getTrackId(),
                track == null ? null : track.getCode(),
                track == null ? null : track.getName(),
                track == null ? null : track.getRoleFamily(),
                track == null ? null : track.getPrimaryLanguage(),
                track == null ? jobTemplate.getLevel() : track.getLevel(),
                (int) questionCount);
    }
}
