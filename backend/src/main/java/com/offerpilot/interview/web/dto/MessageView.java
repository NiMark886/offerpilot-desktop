package com.offerpilot.interview.web.dto;

import com.offerpilot.interview.domain.InterviewMessage;

import java.time.LocalDateTime;

public record MessageView(
        Long id,
        Long sessionId,
        Long questionId,
        String role,
        String messageType,
        String content,
        LocalDateTime createdAt
) {

    public static MessageView from(InterviewMessage message) {
        return new MessageView(
                message.getId(),
                message.getSessionId(),
                message.getQuestionId(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt());
    }
}
