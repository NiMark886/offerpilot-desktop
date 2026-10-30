package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import com.offerpilot.track.domain.RoleProfile;

import java.util.List;

public interface InterviewReportGenerator {

    default InterviewReportResult generate(InterviewSession session,
                                           List<InterviewQuestion> questions,
                                           List<InterviewMessage> messages,
                                           List<AnswerEvaluation> evaluations) {
        return generate(session, questions, messages, evaluations, RoleProfile.tech());
    }

    InterviewReportResult generate(InterviewSession session,
                                   List<InterviewQuestion> questions,
                                   List<InterviewMessage> messages,
                                   List<AnswerEvaluation> evaluations,
                                   RoleProfile profile);
}
