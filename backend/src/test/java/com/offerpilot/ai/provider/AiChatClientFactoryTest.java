package com.offerpilot.ai.provider;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.ApiKeyCipher;
import com.offerpilot.ai.service.AiChatClientFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatClientFactoryTest {

    @Test
    void callsOpenAiCompatibleEndpointAndNormalizesV1BaseUrl() throws Exception {
        HttpServer server = startServer("/v1/chat/completions");
        try {
            ChatClient chatClient = buildClient(server, "/v1");

            assertThat(chatClient.prompt().user("hi").call().content()).isEqualTo("OK");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void appendsV1PathForHostOnlyBaseUrl() throws Exception {
        HttpServer server = startServer("/v1/chat/completions");
        try {
            ChatClient chatClient = buildClient(server, "");

            assertThat(chatClient.prompt().user("hi").call().content()).isEqualTo("OK");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsVersionedBasePathForVendorsLikeZhipuAndVolcengine() throws Exception {
        HttpServer server = startServer("/api/paas/v4/chat/completions");
        try {
            ChatClient chatClient = buildClient(server, "/api/paas/v4");

            assertThat(chatClient.prompt().user("hi").call().content()).isEqualTo("OK");
        } finally {
            server.stop(0);
        }
    }

    private ChatClient buildClient(HttpServer server, String basePath) {
        ApiKeyCipher cipher = new ApiKeyCipher("", "unit-test-jwt-secret");
        AiChatClientFactory factory = new AiChatClientFactory(cipher);
        AiProvider provider = new AiProvider();
        provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + basePath);
        provider.setModel("test-model");
        provider.setTemperature(0.4);
        provider.setApiKeyCipher(cipher.encrypt("sk-test"));
        return factory.build(provider, Duration.ofSeconds(5));
    }

    private HttpServer startServer(String contextPath) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(contextPath, exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer sk-test".equals(authorization)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = """
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "created": 1700000000,
                      "model": "test-model",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "OK"},
                        "finish_reason": "stop"
                      }],
                      "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }
}
