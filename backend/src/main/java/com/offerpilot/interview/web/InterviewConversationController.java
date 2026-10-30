package com.offerpilot.interview.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewConversationService;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.interview.web.dto.AnswerEvaluationView;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerStreamCompletion;
import com.offerpilot.interview.web.dto.AnswerStreamContext;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews/{id}")
public class InterviewConversationController {

    private static final Logger log = LoggerFactory.getLogger(InterviewConversationController.class);

    private final InterviewConversationService conversationService;
    private final TaskExecutor sseExecutor;
    private final ObjectMapper objectMapper;

    public InterviewConversationController(InterviewConversationService conversationService,
                                           @Qualifier("interviewSseExecutor") TaskExecutor sseExecutor,
                                           ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.sseExecutor = sseExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ApiResponse<InterviewSessionView> start(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.start(userId, id));
    }

    @GetMapping("/messages")
    public ApiResponse<List<MessageView>> messages(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(userId, id));
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<AnswerEvaluationView>> evaluations(@AuthenticationPrincipal Long userId,
                                                               @PathVariable Long id) {
        return ApiResponse.ok(conversationService.evaluations(userId, id));
    }

    @PostMapping(value = "/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@AuthenticationPrincipal Long userId,
                             @PathVariable Long id,
                             @Valid @RequestBody AnswerRequest request) {
        AnswerStreamContext context = conversationService.beginAnswerStream(userId, id, request);
        SseEmitter emitter = new SseEmitter(120_000L);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        sseExecutor.execute(() -> {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            try {
                stream(emitter, userId, context);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }

    @PostMapping("/hint")
    public ApiResponse<HintResult> hint(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.hint(userId, id));
    }

    @PostMapping("/skip")
    public ApiResponse<InterviewSessionView> skip(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.skip(userId, id));
    }

    @PostMapping("/finish")
    public ApiResponse<InterviewSessionView> finish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.finish(userId, id));
    }

    private void stream(SseEmitter emitter, Long userId, AnswerStreamContext context) {
        StringBuilder reply = new StringBuilder();
        boolean finalized = false;
        AnswerEvaluationResult evaluation = null;
        boolean evaluationAttempted = false;
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("candidateMessageId", context.candidateMessageId());
            meta.put("interviewerMessageId", context.interviewerMessageId());
            meta.put("questionId", context.replyQuestionId());
            meta.put("replyType", context.replyType());
            emitter.send(SseEmitter.event().name("meta").data(toJson(meta)));

            conversationService.streamReply(context)
                    .doOnNext(chunk -> {
                        reply.append(chunk);
                        try {
                            emitter.send(SseEmitter.event().name("delta")
                                    .data(toJson(Map.of("text", chunk))));
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    })
                    .blockLast();

            evaluationAttempted = true;
            try {
                evaluation = conversationService.evaluateStreamAnswer(userId, context);
            } catch (Exception ex) {
                log.warn("Streaming answer evaluation failed: sessionId={}", context.sessionId(), ex);
            }

            AnswerStreamCompletion completion = conversationService.completeAnswerStream(
                    userId, context, reply.toString(), evaluation);
            finalized = true;
            if (completion.evaluation() != null) {
                emitter.send(SseEmitter.event().name("evaluation")
                        .data(toJson(completion.evaluation())));
            }
            sendDone(emitter, completion);
            emitter.complete();
        } catch (Exception ex) {
            log.warn("Streaming answer failed: sessionId={}, replyType={}, finalized={}",
                    context.sessionId(), context.replyType(), finalized, ex);
            if (!finalized) {
                if (!evaluationAttempted && context.practice()) {
                    evaluationAttempted = true;
                    try {
                        evaluation = conversationService.evaluateStreamAnswer(userId, context);
                    } catch (Exception evaluationEx) {
                        log.warn("Streaming answer evaluation failed during finalization: sessionId={}",
                                context.sessionId(), evaluationEx);
                    }
                }
                try {
                    AnswerStreamCompletion completion = conversationService.completeAnswerStream(
                            userId, context,
                            reply.length() == 0 ? "抱歉，面试官回复生成失败，请继续回答。" : reply.toString(),
                            evaluation);
                    finalized = true;
                    if (completion.evaluation() != null) {
                        try {
                            emitter.send(SseEmitter.event().name("evaluation")
                                    .data(toJson(completion.evaluation())));
                        } catch (IOException sendEx) {
                            log.debug("Failed to send evaluation event: sessionId={}",
                                    context.sessionId(), sendEx);
                        }
                    }
                    sendError(emitter);
                    sendDone(emitter, completion);
                    emitter.complete();
                    return;
                } catch (Exception completionEx) {
                    log.warn("Failed to finalize streaming answer: sessionId={}",
                            context.sessionId(), completionEx);
                }
            }
            sendError(emitter);
            emitter.completeWithError(ex);
        }
    }

    private void sendDone(SseEmitter emitter, AnswerStreamCompletion completion) throws IOException {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("sessionStatus", completion.sessionStatus());
        done.put("currentQuestionId", completion.currentQuestionId());
        done.put("completed", completion.completed());
        emitter.send(SseEmitter.event().name("done").data(toJson(done)));
    }

    private void sendError(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(toJson(Map.of("message", "面试官回复失败"))));
        } catch (IOException ignored) {
            // client already disconnected
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
