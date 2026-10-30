package com.offerpilot.interview.web.dto;

import com.offerpilot.resume.domain.ResumeIssue;

public record ResumeIssueView(
        String category,
        String severity,
        String title,
        String detail,
        String suggestion,
        String evidence
) {

    public static ResumeIssueView from(ResumeIssue issue) {
        if (issue == null) {
            return null;
        }
        return new ResumeIssueView(
                issue.category(),
                issue.severity(),
                issue.title(),
                issue.detail(),
                issue.suggestion(),
                issue.evidence());
    }
}
