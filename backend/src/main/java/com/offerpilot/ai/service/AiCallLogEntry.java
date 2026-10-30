package com.offerpilot.ai.service;

public record AiCallLogEntry(
        String traceId,
        Long userId,
        Long sessionId,
        String scene,
        String promptVersion,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        int latencyMs,
        boolean success,
        boolean fallback,
        String errorCode
) {
}
