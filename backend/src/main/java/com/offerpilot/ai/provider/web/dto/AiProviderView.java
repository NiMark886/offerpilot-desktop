package com.offerpilot.ai.provider.web.dto;

import com.offerpilot.ai.provider.domain.AiProvider;

import java.time.LocalDateTime;

public record AiProviderView(
        Long id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String apiKeyHint,
        Double temperature,
        boolean enabled,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AiProviderView from(AiProvider provider) {
        return new AiProviderView(
                provider.getId(),
                provider.getName(),
                provider.getProviderType(),
                provider.getBaseUrl(),
                provider.getModel(),
                provider.getApiKeyHint(),
                provider.getTemperature(),
                integerToBoolean(provider.getEnabled()),
                integerToBoolean(provider.getActive()),
                provider.getCreatedAt(),
                provider.getUpdatedAt());
    }

    private static boolean integerToBoolean(Integer value) {
        return value != null && value == 1;
    }
}
