package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import com.offerpilot.ai.provider.service.AiProviderType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChatClientProvider {

    private final ChatClient defaultChatClient;
    private final AiProviderService providerService;
    private final AiChatClientFactory chatClientFactory;

    public ChatClientProvider(ChatClient.Builder builder,
                              AiProviderService providerService,
                              AiChatClientFactory chatClientFactory) {
        this.defaultChatClient = builder.build();
        this.providerService = providerService;
        this.chatClientFactory = chatClientFactory;
    }

    public ChatClient get() {
        return get(null);
    }

    public ChatClient get(Duration readTimeout) {
        Long userId = currentUserId();
        if (userId != null) {
            AiProvider provider = providerService.activeForUser(userId);
            if (provider != null) {
                return chatClientFactory.build(provider, readTimeout);
            }
        }
        return defaultChatClient;
    }

    /**
     * DeepSeek supports OpenAI-style JSON object mode. Other presets (MiMo,
     * custom OpenAI-compatible endpoints) may not, so only enable it where it
     * is known to work.
     */
    public boolean supportsJsonMode() {
        Long userId = currentUserId();
        if (userId == null) {
            return true;
        }
        AiProvider provider = providerService.activeForUser(userId);
        return provider == null
                || AiProviderType.from(provider.getProviderType()) == AiProviderType.DEEPSEEK;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
