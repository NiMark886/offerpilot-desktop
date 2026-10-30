package com.offerpilot.resume.web.dto;

import java.time.LocalDateTime;

public record ResumeDraftView(
        Long id,
        String title,
        ResumeDraftContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
