package com.offerpilot.resume.storage;

public record StoredFile(
        String relativePath,
        String originalName,
        String contentType,
        long size
) {
}
