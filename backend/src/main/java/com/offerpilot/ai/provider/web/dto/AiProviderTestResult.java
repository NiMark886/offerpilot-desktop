package com.offerpilot.ai.provider.web.dto;

public record AiProviderTestResult(
        boolean success,
        long latencyMs,
        String model,
        String message
) {
}
