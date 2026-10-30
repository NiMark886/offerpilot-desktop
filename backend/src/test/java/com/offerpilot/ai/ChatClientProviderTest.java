package com.offerpilot.ai;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import com.offerpilot.ai.service.AiChatClientFactory;
import com.offerpilot.ai.service.ChatClientProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientProviderTest {

    private final ChatClient defaultClient = mock(ChatClient.class);
    private final ChatClient userClient = mock(ChatClient.class);
    private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
    private final AiProviderService providerService = mock(AiProviderService.class);
    private final AiChatClientFactory factory = mock(AiChatClientFactory.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fallsBackToDefaultWhenUserHasNoProvider() {
        when(builder.build()).thenReturn(defaultClient);
        ChatClientProvider provider = new ChatClientProvider(builder, providerService, factory);
        authenticateAs(42L);
        when(providerService.activeForUser(42L)).thenReturn(null);

        assertThat(provider.get()).isSameAs(defaultClient);
    }

    @Test
    void usesUserProviderWhenActive() {
        when(builder.build()).thenReturn(defaultClient);
        ChatClientProvider provider = new ChatClientProvider(builder, providerService, factory);
        authenticateAs(42L);
        AiProvider userProvider = new AiProvider();
        userProvider.setId(1L);
        when(providerService.activeForUser(42L)).thenReturn(userProvider);
        when(factory.build(userProvider, null)).thenReturn(userClient);

        assertThat(provider.get()).isSameAs(userClient);
    }

    @Test
    void fallsBackToDefaultWithoutAuthentication() {
        when(builder.build()).thenReturn(defaultClient);
        ChatClientProvider provider = new ChatClientProvider(builder, providerService, factory);

        assertThat(provider.get()).isSameAs(defaultClient);
        assertThat(provider.supportsJsonMode()).isTrue();
    }

    @Test
    void enablesJsonModeForDeepSeekProvider() {
        when(builder.build()).thenReturn(defaultClient);
        ChatClientProvider provider = new ChatClientProvider(builder, providerService, factory);
        authenticateAs(42L);
        AiProvider deepSeek = new AiProvider();
        deepSeek.setProviderType("DEEPSEEK");
        when(providerService.activeForUser(42L)).thenReturn(deepSeek);

        assertThat(provider.supportsJsonMode()).isTrue();
    }

    @Test
    void disablesJsonModeForMimoProvider() {
        when(builder.build()).thenReturn(defaultClient);
        ChatClientProvider provider = new ChatClientProvider(builder, providerService, factory);
        authenticateAs(42L);
        AiProvider mimo = new AiProvider();
        mimo.setProviderType("MIMO");
        when(providerService.activeForUser(42L)).thenReturn(mimo);

        assertThat(provider.supportsJsonMode()).isFalse();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
