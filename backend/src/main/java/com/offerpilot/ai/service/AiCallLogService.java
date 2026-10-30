package com.offerpilot.ai.service;

import com.offerpilot.ai.domain.AiCallLog;
import com.offerpilot.ai.mapper.AiCallLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCallLogService {

    private final AiCallLogMapper mapper;

    public AiCallLogService(AiCallLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiCallLogEntry entry) {
        AiCallLog log = new AiCallLog();
        log.setTraceId(entry.traceId());
        log.setUserId(entry.userId());
        log.setSessionId(entry.sessionId());
        log.setScene(entry.scene());
        log.setPromptVersion(entry.promptVersion());
        log.setModel(entry.model());
        log.setPromptTokens(entry.promptTokens());
        log.setCompletionTokens(entry.completionTokens());
        log.setLatencyMs(entry.latencyMs());
        log.setSuccess(entry.success() ? 1 : 0);
        log.setFallback(entry.fallback() ? 1 : 0);
        log.setErrorCode(entry.errorCode());
        mapper.insert(log);
    }
}
