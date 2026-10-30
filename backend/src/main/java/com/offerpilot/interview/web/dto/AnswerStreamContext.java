package com.offerpilot.interview.web.dto;

public record AnswerStreamContext(
        Long sessionId,
        Long questionId,
        Long candidateMessageId,
        Long interviewerMessageId,
        String replyType,
        Long nextQuestionId,
        boolean practice,
        String promptText,
        String promptType,
        String answer
) {

    /**
     * 面试官这条回复所针对的题目：追问和收尾仍属于当前题，
     * 提出下一题时必须使用下一题，否则 AI 会继续围绕上一题追问。
     */
    public Long replyQuestionId() {
        if ("QUESTION".equals(replyType) && nextQuestionId != null) {
            return nextQuestionId;
        }
        return questionId;
    }
}
