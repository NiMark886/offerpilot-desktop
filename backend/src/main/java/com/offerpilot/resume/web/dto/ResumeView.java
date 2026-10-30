package com.offerpilot.resume.web.dto;

import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;

import java.time.LocalDateTime;

public record ResumeView(
        Long id,
        String fileName,
        Long fileSize,
        String parseStatus,
        ResumeProfile profile,
        LocalDateTime createdAt
) {

    public static ResumeView from(Resume resume, ResumeProfile profile) {
        return new ResumeView(
                resume.getId(),
                resume.getFileName(),
                resume.getFileSize(),
                resume.getParseStatus(),
                profile,
                resume.getCreatedAt());
    }
}
