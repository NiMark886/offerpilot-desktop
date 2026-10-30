package com.offerpilot.resume;

import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.AiResumeProfileParser;
import com.offerpilot.resume.service.HeuristicResumeProfileParser;
import com.offerpilot.resume.service.ResumeProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumeProfileServiceTest {

    private static final String RESUME_TEXT = """
            张三
            邮箱：zhangsan@example.com
            电话：13800138000
            3 年 Java 后端开发经验
            技能：Java, MySQL
            项目：中州养老智能物联网平台
            负责设备数据采集与报警规则。
            """;

    private final HeuristicResumeProfileParser heuristicParser = new HeuristicResumeProfileParser();
    private final AiResumeProfileParser aiParser = mock(AiResumeProfileParser.class);
    private final ObjectProvider<AiResumeProfileParser> aiParserProvider = mock(ObjectProvider.class);
    private final ResumeProfileService service = new ResumeProfileService(heuristicParser, aiParserProvider);

    @Test
    void aiFieldsWinAndHeuristicFillsMissingOnes() {
        when(aiParserProvider.getIfAvailable()).thenReturn(aiParser);
        when(aiParser.parse(anyString())).thenReturn(new ResumeProfile(
                "张三（AI）",
                null,
                null,
                1,
                List.of("Java", "Spring Boot"),
                List.of(new ProjectExperience("OfferPilot", "AI 项目描述")),
                "安徽工业大学 本科",
                "AI 总结"));

        ResumeProfile profile = service.parse(RESUME_TEXT);

        assertThat(profile.name()).isEqualTo("张三（AI）");
        assertThat(profile.email()).isEqualTo("zhangsan@example.com");
        assertThat(profile.phone()).isEqualTo("13800138000");
        assertThat(profile.yearsOfExperience()).isEqualTo(1);
        assertThat(profile.skills()).containsExactly("Java", "Spring Boot", "MySQL");
        assertThat(profile.projects()).containsExactly(
                new ProjectExperience("OfferPilot", "AI 项目描述"));
        assertThat(profile.education()).isEqualTo("安徽工业大学 本科");
        assertThat(profile.summary()).isEqualTo("AI 总结");
    }

    @Test
    void keepsAiZeroYearsInsteadOfHeuristicGuess() {
        when(aiParserProvider.getIfAvailable()).thenReturn(aiParser);
        when(aiParser.parse(anyString())).thenReturn(new ResumeProfile(
                "张三", null, null, 0, List.of(), List.of(), null, null));

        ResumeProfile profile = service.parse(RESUME_TEXT);

        assertThat(profile.yearsOfExperience()).isZero();
        assertThat(profile.skills()).contains("Java", "MySQL");
        assertThat(profile.projects()).hasSize(1);
    }

    @Test
    void fallsBackToHeuristicWhenAiFails() {
        when(aiParserProvider.getIfAvailable()).thenReturn(aiParser);
        when(aiParser.parse(anyString())).thenThrow(new IllegalStateException("AI unavailable"));

        ResumeProfile profile = service.parse(RESUME_TEXT);

        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.email()).isEqualTo("zhangsan@example.com");
        assertThat(profile.yearsOfExperience()).isEqualTo(3);
    }

    @Test
    void usesHeuristicWhenAiBeanIsMissing() {
        when(aiParserProvider.getIfAvailable()).thenReturn(null);

        ResumeProfile profile = service.parse(RESUME_TEXT);

        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.skills()).contains("Java", "MySQL");
    }
}
