package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.track.domain.RoleProfile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public interface InterviewerReplyGenerator {

    default String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                            List<InterviewMessage> history) {
        return generate(question, type, candidateAnswer, history, RoleProfile.tech());
    }

    String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                    List<InterviewMessage> history, RoleProfile profile);

    default Flux<String> stream(InterviewQuestion question, ReplyType type, String candidateAnswer,
                                List<InterviewMessage> history) {
        return stream(question, type, candidateAnswer, history, RoleProfile.tech());
    }

    default Flux<String> stream(InterviewQuestion question, ReplyType type, String candidateAnswer,
                                List<InterviewMessage> history, RoleProfile profile) {
        return Flux.fromIterable(chunks(generate(question, type, candidateAnswer, history, profile)));
    }

    private static List<String> chunks(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += 24) {
            chunks.add(text.substring(i, Math.min(text.length(), i + 24)));
        }
        return chunks;
    }
}
