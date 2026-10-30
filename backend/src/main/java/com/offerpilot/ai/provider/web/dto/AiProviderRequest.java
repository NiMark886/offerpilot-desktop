package com.offerpilot.ai.provider.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiProviderRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 32) String providerType,
        @Size(max = 512) String baseUrl,
        @Size(max = 128) String model,
        String apiKey,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        Boolean enabled
) {
}
