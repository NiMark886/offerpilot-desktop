package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockInterviewerReplyGenerator implements InterviewerReplyGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                           List<InterviewMessage> history, RoleProfile profile) {
        boolean sales = profile != null && profile.isGeneral();
        return switch (type) {
            case OPENING -> sales
                    ? "你好，我是今天的销售面试官。我们开始第一题：" + question.getContent()
                    : "你好，我是今天的 AI 面试官。我们开始第一题：" + question.getContent();
            case QUESTION -> "好的，我们进入下一题：" + question.getContent();
            case FOLLOW_UP -> followUp(question);
            case CLOSING -> sales
                    ? "今天的销售面试到这里，感谢你的时间。报告会在面试结束后生成。"
                    : "今天的面试到这里，感谢你的时间。报告会在面试结束后生成。";
        };
    }

    private String followUp(InterviewQuestion question) {
        List<String> hints = readList(question.getFollowUpHints());
        if (!hints.isEmpty()) {
            int count = question.getFollowUpCount() == null ? 1 : question.getFollowUpCount();
            int index = Math.max(0, Math.min(count - 1, hints.size() - 1));
            return "我想追问一下：" + hints.get(index);
        }
        return "能结合一个具体例子，再展开说说吗？";
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
