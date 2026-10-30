package com.offerpilot.ai;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import com.offerpilot.ai.service.AiAvailabilityChecker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAvailabilityCheckerTest {

    private final AiProviderService providerService = mock(AiProviderService.class);

    @Test
    void returnsFalseWhenAiModuleDisabled() {
        AiAvailabilityChecker checker = new AiAvailabilityChecker(false, providerService);

        assertThat(checker.isAvailable(1L)).isFalse();
    }

    @Test
    void returnsTrueWhenUserHasEnabledProvider() {
        AiProvider provider = new AiProvider();
        provider.setEnabled(1);
        when(providerService.activeForUser(1L)).thenReturn(provider);
        AiAvailabilityChecker checker = new AiAvailabilityChecker(true, providerService);

        assertThat(checker.isAvailable(1L)).isTrue();
    }

    @Test
    void returnsFalseWhenUserHasNoProvider() {
        when(providerService.activeForUser(1L)).thenReturn(null);
        AiAvailabilityChecker checker = new AiAvailabilityChecker(true, providerService);

        assertThat(checker.isAvailable(1L)).isFalse();
    }

    @Test
    void returnsFalseWhenActiveProviderIsDisabled() {
        AiProvider provider = new AiProvider();
        provider.setEnabled(0);
        when(providerService.activeForUser(1L)).thenReturn(provider);
        AiAvailabilityChecker checker = new AiAvailabilityChecker(true, providerService);

        assertThat(checker.isAvailable(1L)).isFalse();
    }

    @Test
    void returnsFalseForAnonymousUser() {
        AiAvailabilityChecker checker = new AiAvailabilityChecker(true, providerService);

        assertThat(checker.isAvailable(null)).isFalse();
    }
}
