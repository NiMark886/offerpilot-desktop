package com.offerpilot.interview.service;

import com.offerpilot.ai.service.AiCallExecutor;
import com.offerpilot.ai.service.ChatClientProvider;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiInterviewerReplyGenerator implements InterviewerReplyGenerator {

    private static final String SCENE = "REPLY";
    private static final String PROMPT_VERSION = "reply-v1";

    private final ChatClientProvider chatClientProvider;
    private final AiCallExecutor aiCallExecutor;
    private final MockInterviewerReplyGenerator fallback;

    public SpringAiInterviewerReplyGenerator(ChatClientProvider chatClientProvider,
                                             AiCallExecutor aiCallExecutor,
                                             MockInterviewerReplyGenerator fallback) {
        this.chatClientProvider = chatClientProvider;
        this.aiCallExecutor = aiCallExecutor;
        this.fallback = fallback;
    }

    @Override
    public String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                           List<InterviewMessage> history, RoleProfile profile) {
        return aiCallExecutor.call(SCENE, PROMPT_VERSION,
                () -> {
                    ChatClient chatClient = chatClientProvider.get();
                    return chatClient.prompt()
                            .system(systemPrompt(profile))
                            .user(userPrompt(question, type, candidateAnswer, history))
                            .call()
                            .chatResponse();
                },
                response -> {
                    String content = response.getResult().getOutput().getText();
                    if (content == null || content.isBlank()) {
                        throw new IllegalStateException("AI interviewer reply is empty");
                    }
                    return content.trim();
                },
                () -> fallback.generate(question, type, candidateAnswer, history, profile));
    }

    @Override
    public Flux<String> stream(InterviewQuestion question, ReplyType type, String candidateAnswer,
                               List<InterviewMessage> history, RoleProfile profile) {
        return aiCallExecutor.stream(SCENE, PROMPT_VERSION,
                () -> {
                    ChatClient chatClient = chatClientProvider.get();
                    return chatClient.prompt()
                            .system(systemPrompt(profile))
                            .user(userPrompt(question, type, candidateAnswer, history))
                            .stream()
                            .content();
                },
                () -> fallback.generate(question, type, candidateAnswer, history, profile));
    }

    private String systemPrompt(RoleProfile profile) {
        if (profile != null && profile.isGeneral()) {
            return """
                    你是一名资深销售面试官。只输出面试官要说的话，不要解释，不要 markdown。
                    控制在一到三句话，语气专业、克制。
                    当前类型为 QUESTION 时，必须提出「当前题目」中的问题；
                    不要继续追问上一题，也不要自行追加新的追问。
                    如果候选人回答空泛，追问要围绕具体客户、动作、数字和决策过程。
                    """;
        }
        return """
                你是一名资深技术面试官。只输出面试官要说的话，不要解释，不要 markdown。
                控制在一到三句话，语气专业、自然。
                当前类型为 QUESTION 时，必须提出「当前题目」中的问题；
                不要继续追问上一题，也不要自行追加新的追问。
                """;
    }

    private String userPrompt(InterviewQuestion question, ReplyType type, String candidateAnswer,
                               List<InterviewMessage> history) {
        String historyText = history.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));
        String instruction = switch (type) {
            case OPENING -> "请用面试官口吻开场，并自然地提出第一题。";
            case QUESTION -> "请用面试官口吻用一句话承接上一轮，然后明确提出「当前题目」中的问题，不要继续追问上一题。";
            case FOLLOW_UP -> "请针对候选人的回答追问一个更具体的问题，不要重复原题。";
            case CLOSING -> "请用面试官口吻结束面试，感谢候选人。";
        };
        return """
                当前类型：%s
                指令：%s
                当前题目：%s
                候选人回答：%s
                最近对话：
                %s
                """.formatted(
                type.name(),
                instruction,
                question == null ? "无" : question.getContent(),
                candidateAnswer == null ? "无" : candidateAnswer,
                historyText);
    }
}
