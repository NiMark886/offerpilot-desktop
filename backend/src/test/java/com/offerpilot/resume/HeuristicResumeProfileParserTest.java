package com.offerpilot.resume;

import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.HeuristicResumeProfileParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicResumeProfileParserTest {

    private final HeuristicResumeProfileParser parser = new HeuristicResumeProfileParser();

    @Test
    void parsesBasicProfile() {
        String text = """
                张三
                邮箱：zhangsan@example.com
                手机：13800138000
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis, MyBatis
                项目：中州养老智能物联网平台
                负责设备数据采集、报警规则与 AI 健康评估集成。
                """;

        ResumeProfile profile = parser.parse(text);

        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.email()).isEqualTo("zhangsan@example.com");
        assertThat(profile.phone()).isEqualTo("13800138000");
        assertThat(profile.yearsOfExperience()).isEqualTo(3);
        assertThat(profile.skills()).contains("Java", "Spring Boot", "MySQL", "Redis", "MyBatis");
        assertThat(profile.projects()).isNotEmpty();
        assertThat(profile.projects().get(0).name()).contains("中州养老");
    }
}
