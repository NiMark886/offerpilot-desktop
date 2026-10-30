package com.offerpilot.resume.web.dto;

public record ResumeDraftExportView(
        String fileName,
        String contentBase64
) {
}
