package com.offerpilot.ai;

import com.offerpilot.ai.service.AiCallExecutor;
import com.offerpilot.ai.service.AiCallLogEntry;
import com.offerpilot.ai.service.AiCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCallExecutorTest {

    private final AiCallLogService logService = mock(AiCallLogService.class);
    private final AiCallExecutor executor = new AiCallExecutor(logService, "deepseek-chat");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void recordsSuccessWithTokens() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getModel()).thenReturn("deepseek-chat");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(12);
        when(usage.getCompletionTokens()).thenReturn(34);

        String result = executor.call("BLUEPRINT", "blueprint-v1",
                () -> response, ignored -> "ok", () -> "fallback");

        assertThat(result).isEqualTo("ok");
        AiCallLogEntry entry = captureEntry();
        assertThat(entry.success()).isTrue();
        assertThat(entry.fallback()).isFalse();
        assertThat(entry.model()).isEqualTo("deepseek-chat");
        assertThat(entry.promptTokens()).isEqualTo(12);
        assertThat(entry.completionTokens()).isEqualTo(34);
        assertThat(entry.errorCode()).isNull();
    }

    @Test
    void recordsFallbackWhenAiCallFails() {
        String result = executor.call("REPLY", "reply-v1",
                () -> {
                    throw new IllegalStateException("boom");
                },
                ignored -> "ok",
                () -> "fallback");

        assertThat(result).isEqualTo("fallback");
        AiCallLogEntry entry = captureEntry();
        assertThat(entry.success()).isFalse();
        assertThat(entry.fallback()).isTrue();
        assertThat(entry.errorCode()).isEqualTo("IllegalStateException");
    }

    @Test
    void rethrowsWhenNoFallbackIsProvided() {
        assertThatThrownBy(() -> executor.callOrThrow("RESUME", "resume-v1",
                () -> {
                    throw new IllegalStateException("boom");
                },
                ignored -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        AiCallLogEntry entry = captureEntry();
        assertThat(entry.success()).isFalse();
        assertThat(entry.fallback()).isFalse();
    }

    private AiCallLogEntry captureEntry() {
        ArgumentCaptor<AiCallLogEntry> captor = ArgumentCaptor.forClass(AiCallLogEntry.class);
        verify(logService).record(captor.capture());
        return captor.getValue();
    }
}
