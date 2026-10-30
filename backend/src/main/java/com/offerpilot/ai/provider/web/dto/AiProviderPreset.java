package com.offerpilot.ai.provider.web.dto;

import com.offerpilot.ai.provider.service.AiProviderType;

import java.util.List;

public record AiProviderPreset(
        String type,
        String label,
        String baseUrl,
        String model,
        List<String> models,
        String group
) {

    public static AiProviderPreset from(AiProviderType type) {
        return new AiProviderPreset(
                type.name(),
                type.label(),
                type.defaultBaseUrl(),
                type.defaultModel(),
                type.models(),
                type.group()
        );
    }
}
