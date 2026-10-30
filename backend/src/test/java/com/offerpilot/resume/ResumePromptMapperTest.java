package com.offerpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.ResumePromptMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumePromptMapperTest {

    private final ResumePromptMapper mapper = new ResumePromptMapper(new ObjectMapper());

    @Test
    void excludesContactFieldsAndMasksPiiInFreeText() {
        ResumeProfile profile = new ResumeProfile(
                "张三",
                "zhangsan@example.com",
                "13800000000",
                3,
                List.of("Java", "Spring Boot"),
                List.of(new ProjectExperience("OfferPilot", "联系方式 13900000000 或 test@example.com")),
                "某大学",
                "邮箱 zhangsan@example.com，电话 13800000000");

        String json = mapper.toPromptJson(profile);

        assertThat(json)
                .doesNotContain("张三")
                .doesNotContain("zhangsan@example.com")
                .doesNotContain("13800000000")
                .doesNotContain("13900000000")
                .doesNotContain("test@example.com")
                .contains("[已脱敏]")
                .contains("Java")
                .contains("OfferPilot")
                .contains("某大学");
    }

    @Test
    void returnsEmptyJsonForNullProfile() {
        assertThat(mapper.toPromptJson(null)).isEqualTo("{}");
    }
}
