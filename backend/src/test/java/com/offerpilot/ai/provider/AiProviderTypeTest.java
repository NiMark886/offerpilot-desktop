package com.offerpilot.ai.provider;

import com.offerpilot.ai.provider.service.AiProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderTypeTest {

    @Test
    void everyPresetHasConsistentDefaults() {
        for (AiProviderType type : AiProviderType.values()) {
            if (type == AiProviderType.CUSTOM) {
                continue;
            }
            assertThat(type.defaultBaseUrl()).as(type.name()).startsWith("https://");
            assertThat(type.defaultModel()).as(type.name()).isNotBlank();
            assertThat(type.models()).as(type.name()).isNotEmpty().contains(type.defaultModel());
            assertThat(type.group()).as(type.name()).isNotBlank();
        }
    }
}
