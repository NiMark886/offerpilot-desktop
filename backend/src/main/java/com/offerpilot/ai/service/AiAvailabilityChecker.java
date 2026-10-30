package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a user can use AI generation. A user without an active
 * provider is routed to the deterministic bank mode even when the AI module is
 * enabled globally.
 */
@Component
public class AiAvailabilityChecker {

    private final boolean aiEnabled;
    private final AiProviderService providerService;

    public AiAvailabilityChecker(@Value("${app.ai.enabled:false}") boolean aiEnabled,
                                 AiProviderService providerService) {
        this.aiEnabled = aiEnabled;
        this.providerService = providerService;
    }

    public boolean isAvailable(Long userId) {
        if (!aiEnabled || userId == null) {
            return false;
        }
        AiProvider provider = providerService.activeForUser(userId);
        return provider != null && provider.getEnabled() != null && provider.getEnabled() == 1;
    }
}
