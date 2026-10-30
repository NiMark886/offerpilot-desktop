package com.offerpilot.track.web.dto;

import com.offerpilot.track.domain.InterviewTrack;

public record InterviewTrackView(
        Long id,
        String code,
        String name,
        String category,
        String roleFamily,
        String primaryLanguage,
        String level,
        String description,
        String rubricCode,
        String status,
        Integer sortOrder,
        long questionCount
) {

    public static InterviewTrackView from(InterviewTrack track, long questionCount) {
        return new InterviewTrackView(
                track.getId(),
                track.getCode(),
                track.getName(),
                track.getCategory(),
                track.getRoleFamily(),
                track.getPrimaryLanguage(),
                track.getLevel(),
                track.getDescription(),
                track.getRubricCode(),
                track.getStatus(),
                track.getSortOrder(),
                questionCount);
    }
}
