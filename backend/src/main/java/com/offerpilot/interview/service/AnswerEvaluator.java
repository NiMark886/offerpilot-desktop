package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.track.domain.RoleProfile;

public interface AnswerEvaluator {

    default AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                            String promptType, String answer) {
        return evaluate(question, promptText, promptType, answer, RoleProfile.tech());
    }

    AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                    String promptType, String answer, RoleProfile profile);
}
