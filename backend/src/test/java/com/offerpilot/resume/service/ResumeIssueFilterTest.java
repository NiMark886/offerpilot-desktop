package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ResumeIssue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeIssueFilterTest {

    @Test
    void filtersInternshipAvailabilityIssues() {
        assertThat(ResumeIssueFilter.isInternshipAvailabilityIssue(issue(
                "在读身份与可实习时间未说明",
                "简历未标注当前可实习周期、每周到岗天数，招聘方无法判断到岗可行性。"))).isTrue();
        assertThat(ResumeIssueFilter.isInternshipAvailabilityIssue(issue(
                "实习经历未说明是否可转正",
                "建议补充用工形式和转正机会。"))).isTrue();
        assertThat(ResumeIssueFilter.isInternshipAvailabilityIssue(issue(
                "项目描述缺少量化结果",
                "项目描述没有说明订单量、QPS、转化率等结果。"))).isFalse();
    }

    private ResumeIssue issue(String title, String detail) {
        return new ResumeIssue("岗位匹配", "MEDIUM", title, detail, "补充具体信息。", "");
    }
}
