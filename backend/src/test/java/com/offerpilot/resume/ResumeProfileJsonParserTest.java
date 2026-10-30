package com.offerpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.ResumeProfileJsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeProfileJsonParserTest {

    private final ResumeProfileJsonParser parser = new ResumeProfileJsonParser(new ObjectMapper());

    @Test
    void parsesTypicalAiResponse() {
        String json = """
                {
                  "name": "倪瀚呈",
                  "email": "18715555653@139.com",
                  "phone": "18715555653",
                  "yearsOfExperience": 0,
                  "skills": ["Java", "Spring Boot", "MySQL"],
                  "projects": [
                    {"name": "OfferPilot", "description": "AI 模拟面试平台，负责后端与流式接口"}
                  ],
                  "education": "安徽工业大学 · 人工智能本科（2027 届）",
                  "summary": "Java 后端方向，关注 Spring Boot 与企业级业务系统。"
                }
                """;

        ResumeProfile profile = parser.parse(json);

        assertThat(profile.name()).isEqualTo("倪瀚呈");
        assertThat(profile.email()).isEqualTo("18715555653@139.com");
        assertThat(profile.phone()).isEqualTo("18715555653");
        assertThat(profile.yearsOfExperience()).isZero();
        assertThat(profile.skills()).containsExactly("Java", "Spring Boot", "MySQL");
        assertThat(profile.projects()).hasSize(1);
        assertThat(profile.projects().get(0).name()).isEqualTo("OfferPilot");
        assertThat(profile.education()).contains("安徽工业大学", "人工智能本科");
        assertThat(profile.summary()).contains("Spring Boot");
    }

    @Test
    void toleratesFencedJsonStringYearsAndStringProjects() {
        String json = """
                ```json
                {
                  "name": "倪瀚呈",
                  "yearsOfExperience": "3 年",
                  "skills": "Java，Spring Boot、MySQL",
                  "projects": ["OfferPilot：AI 模拟面试平台", "中州养老：设备数据采集"],
                  "education": {"school": "安徽工业大学", "major": "人工智能", "degree": "本科"},
                  "extra": "ignored"
                }
                ```
                """;

        ResumeProfile profile = parser.parse(json);

        assertThat(profile.name()).isEqualTo("倪瀚呈");
        assertThat(profile.yearsOfExperience()).isEqualTo(3);
        assertThat(profile.skills()).containsExactly("Java", "Spring Boot", "MySQL");
        assertThat(profile.projects()).hasSize(2);
        assertThat(profile.projects().get(0).name()).isEqualTo("OfferPilot");
        assertThat(profile.projects().get(0).description()).isEqualTo("AI 模拟面试平台");
        assertThat(profile.education()).contains("安徽工业大学", "人工智能", "本科");
        assertThat(profile.summary()).isNull();
    }

    @Test
    void parsesProjectsWithAlternateFieldNames() {
        String json = """
                {
                  "projects": [
                    {"projectName": "OfferPilot", "projectDescription": "AI 面试平台"},
                    {"title": "中州养老", "description": "设备数据采集"}
                  ]
                }
                """;

        ResumeProfile profile = parser.parse(json);

        assertThat(profile.projects()).hasSize(2);
        assertThat(profile.projects().get(0).name()).isEqualTo("OfferPilot");
        assertThat(profile.projects().get(0).description()).isEqualTo("AI 面试平台");
        assertThat(profile.projects().get(1).name()).isEqualTo("中州养老");
    }

    @Test
    void rejectsEmptyOrNonJsonResponse() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("not a json object"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("```json\n[1, 2]\n```"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
