package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.ApiKeyCipher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

@Component
public class AiChatClientFactory {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String V1_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private final ApiKeyCipher apiKeyCipher;

    public AiChatClientFactory(ApiKeyCipher apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public ChatClient build(AiProvider provider) {
        return build(provider, DEFAULT_READ_TIMEOUT);
    }

    public ChatClient build(AiProvider provider, Duration readTimeout) {
        Endpoint endpoint = resolveEndpoint(provider.getBaseUrl());
        String apiKey = apiKeyCipher.decrypt(provider.getApiKeyCipher());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl())
                .apiKey(apiKey)
                .completionsPath(endpoint.completionsPath())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(provider.getModel())
                .temperature(provider.getTemperature() == null ? 0.4 : provider.getTemperature())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /**
     * Host-only base URLs (for example {@code https://api.deepseek.com}) use
     * the standard {@code /v1/chat/completions} path. When the user or preset
     * already supplied a path ({@code /v1}, {@code /api/paas/v4},
     * {@code /v1beta/openai}, ...), append only {@code /chat/completions} so
     * version segments are not duplicated.
     */
    private Endpoint resolveEndpoint(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            return new Endpoint(
                    normalized.substring(0, normalized.length() - CHAT_COMPLETIONS_SUFFIX.length()),
                    CHAT_COMPLETIONS_SUFFIX
            );
        }
        if (hasPath(normalized)) {
            return new Endpoint(normalized, CHAT_COMPLETIONS_SUFFIX);
        }
        return new Endpoint(normalized, V1_CHAT_COMPLETIONS_PATH);
    }

    private boolean hasPath(String baseUrl) {
        try {
            String path = URI.create(baseUrl).getPath();
            return path != null && !path.isBlank() && !"/".equals(path);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private record Endpoint(String baseUrl, String completionsPath) {
    }
}
