package com.offerpilot.resume.domain;

/**
 * A concrete resume defect with an actionable suggestion.
 * severity is HIGH, MEDIUM or LOW.
 */
public record ResumeIssue(
        String category,
        String severity,
        String title,
        String detail,
        String suggestion,
        String evidence
) {
}
