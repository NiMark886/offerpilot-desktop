package com.offerpilot.ai.provider.web.dto;

public record AiProviderStatus(
        boolean aiEnabled,
        String defaultModel
) {
}
