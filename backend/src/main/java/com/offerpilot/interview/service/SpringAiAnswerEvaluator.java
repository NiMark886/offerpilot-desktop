package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.ai.service.AiCallExecutor;
import com.offerpilot.ai.service.ChatClientProvider;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiAnswerEvaluator implements AnswerEvaluator {

    private static final String SCENE = "EVALUATION";
    private static final String PROMPT_VERSION = "evaluation-v1";

    private final ChatClientProvider chatClientProvider;
    private final ObjectMapper objectMapper;
    private final AiCallExecutor aiCallExecutor;
    private final MockAnswerEvaluator fallback;

    public SpringAiAnswerEvaluator(ChatClientProvider chatClientProvider,
                                   ObjectMapper objectMapper,
                                   AiCallExecutor aiCallExecutor,
                                   MockAnswerEvaluator fallback) {
        this.chatClientProvider = chatClientProvider;
        this.objectMapper = objectMapper;
        this.aiCallExecutor = aiCallExecutor;
        this.fallback = fallback;
    }

    @Override
    public AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                           String promptType, String answer, RoleProfile profile) {
        return aiCallExecutor.call(SCENE, PROMPT_VERSION,
                () -> {
                    ChatClient chatClient = chatClientProvider.get();
                    return chatClient.prompt()
                        .system(systemPrompt(profile))
                        .user("""
                                当前评分对象类型：%s
                                当前评分对象：%s
                                原题：%s
                                参考要点：%s
                                候选人回答：%s
                                """.formatted(
                                promptType == null ? "QUESTION" : promptType,
                                promptText == null ? "无" : promptText,
                                question.getContent(),
                                question.getReferencePoints(),
                                answer))
                        .call()
                        .chatResponse();
                },
                response -> objectMapper.readValue(
                        cleanJson(response.getResult().getOutput().getText()),
                        AnswerEvaluationResult.class),
                () -> fallback.evaluate(question, promptText, promptType, answer, profile));
    }

    private String systemPrompt(RoleProfile profile) {
        if (profile != null && profile.isGeneral()) {
            return """
                    你是销售岗位面试评分器。根据当前评分对象、原题、参考要点和候选人回答判断回答质量。
                    只输出 JSON，不要 markdown。JSON 字段：
                    correctness（CORRECT/PARTIAL/INSUFFICIENT/INCORRECT）、
                    score（1-5）、comment（一句中文点评，不要重复列 missingPoints）、
                    missingPoints（字符串数组）、confidence（0-1）。
                    重点看 STAR 结构、客户洞察、需求挖掘、异议处理、结果量化和表达说服力，
                    不要因为候选人没有技术背景而扣分。如果当前评分对象类型是 FOLLOW_UP，
                    只评价候选人是否针对该追问作答、是否有具体展开。
                    """;
        }
        return """
                你是技术面试评分器。根据当前评分对象、原题、参考要点和候选人回答判断回答正确性。
                只输出 JSON，不要 markdown。JSON 字段：
                correctness（CORRECT/PARTIAL/INSUFFICIENT/INCORRECT）、
                score（1-5）、comment（一句中文点评，不要重复列 missingPoints）、
                missingPoints（字符串数组）、confidence（0-1）。
                如果当前评分对象类型是 FOLLOW_UP，只评价候选人是否针对该追问作答、
                是否有具体展开，不要拿原题的全部参考要点要求候选人。
                """;
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
