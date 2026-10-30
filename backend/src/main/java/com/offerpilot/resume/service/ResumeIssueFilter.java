package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ResumeIssue;

import java.util.List;

/**
 * Drops suggestions about internship availability. A resume does not need to
 * state the internship period, weekly attendance, return-offer options or
 * employment form, so these are not resume defects.
 */
public final class ResumeIssueFilter {

    private static final List<String> AVAILABILITY_KEYWORDS = List.of(
            "可实习", "实习时间", "到岗", "出勤", "每周", "转正",
            "用工形式", "实习周期", "实习时长", "在读身份");

    private ResumeIssueFilter() {
    }

    public static boolean isInternshipAvailabilityIssue(ResumeIssue issue) {
        if (issue == null) {
            return false;
        }
        return isInternshipAvailabilityIssue(
                issue.category(), issue.title(), issue.detail(), issue.suggestion(), issue.evidence());
    }

    public static boolean isInternshipAvailabilityIssue(String category, String title, String detail,
                                                        String suggestion, String evidence) {
        String text = String.join(" ",
                nullSafe(category), nullSafe(title), nullSafe(detail),
                nullSafe(suggestion), nullSafe(evidence));
        for (String keyword : AVAILABILITY_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
