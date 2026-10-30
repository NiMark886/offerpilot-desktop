package com.offerpilot.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Wraps every AI call with latency/token/fallback observability.
 *
 * <p>The executor never lets logging failures break the business flow. If a
 * fallback supplier is provided, any AI/parse failure is recorded and the
 * fallback result is returned.</p>
 */
@Component
public class AiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiCallExecutor.class);

    private final AiCallLogService logService;
    private final String defaultModel;

    public AiCallExecutor(AiCallLogService logService,
                          @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String defaultModel) {
        this.logService = logService;
        this.defaultModel = defaultModel;
    }

    public <T> T call(String scene,
                      String promptVersion,
                      Supplier<ChatResponse> aiCall,
                      AiResponseParser<T> parser,
                      Supplier<T> fallback) {
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = aiCall.get();
            T result = parser.parse(response);
            record(scene, promptVersion, response, start, true, false, null);
            return result;
        } catch (Exception ex) {
            record(scene, promptVersion, null, start, false, fallback != null, errorCode(ex));
            log.warn("AI call failed: scene={}, version={}, fallback={}",
                    scene, promptVersion, fallback != null, ex);
            if (fallback != null) {
                return fallback.get();
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("AI call failed", ex);
        }
    }

    public <T> T callOrThrow(String scene,
                             String promptVersion,
                             Supplier<ChatResponse> aiCall,
                             AiResponseParser<T> parser) {
        return call(scene, promptVersion, aiCall, parser, null);
    }

    public Flux<String> stream(String scene,
                               String promptVersion,
                               Supplier<Flux<String>> aiStream,
                               Supplier<String> fallback) {
        long start = System.currentTimeMillis();
        StringBuilder collected = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);
        return Flux.defer(aiStream)
                .doOnNext(collected::append)
                .doOnComplete(() -> {
                    if (!failed.get()) {
                        record(scene, promptVersion, null, start, true, false, null);
                    }
                })
                .onErrorResume(ex -> {
                    failed.set(true);
                    record(scene, promptVersion, null, start, false, fallback != null, errorCode(ex));
                    log.warn("AI stream failed: scene={}, version={}, fallback={}",
                            scene, promptVersion, fallback != null, ex);
                    if (fallback != null && collected.length() == 0) {
                        return Flux.just(fallback.get());
                    }
                    return Flux.empty();
                });
    }

    private void record(String scene,
                        String promptVersion,
                        ChatResponse response,
                        long start,
                        boolean success,
                        boolean fallback,
                        String errorCode) {
        try {
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            String model = response != null && response.getMetadata() != null
                    && response.getMetadata().getModel() != null
                    ? response.getMetadata().getModel() : defaultModel;
            logService.record(new AiCallLogEntry(
                    MDC.get("traceId"),
                    currentUserId(),
                    null,
                    scene,
                    promptVersion,
                    model,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    (int) (System.currentTimeMillis() - start),
                    success,
                    fallback,
                    errorCode));
        } catch (Exception logException) {
            log.warn("Failed to persist AI call log: scene={}", scene, logException);
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private String errorCode(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
}
