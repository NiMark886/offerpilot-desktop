package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.MockBlueprintGenerator;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.track.domain.RoleProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockBlueprintGeneratorTest {

    private final MockBlueprintGenerator generator = new MockBlueprintGenerator();

    @Test
    void generatesEightQuestionsForThirtyMinutes() {
        JobTemplate job = new JobTemplate();
        job.setTitle("Java 后端开发（校招）");
        job.setCategory("Java 后端");
        job.setConfigJson("{\"basic\":30,\"project\":35,\"scenario\":20,\"ai\":15}");

        ResumeProfile profile = new ResumeProfile(
                "张三", "zhangsan@example.com", "13800138000", 3,
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                List.of(new ProjectExperience("中州养老", "负责设备数据采集与报警规则")),
                "某大学本科", "3 年 Java 后端经验");

        InterviewBlueprint blueprint = generator.generate(new BlueprintRequest(
                job, null, RoleProfile.tech(), profile, "STANDARD", 30, "STANDARD", List.of()));

        long count = blueprint.sections().stream()
                .mapToLong(section -> section.questions().size())
                .sum();
        assertThat(count).isEqualTo(8);
        assertThat(blueprint.sections()).isNotEmpty();
        assertThat(blueprint.sections().get(0).questions())
                .allSatisfy(question -> {
                    assertThat(question.text()).isNotBlank();
                    assertThat(question.referencePoints()).isNotEmpty();
                });
    }

    @Test
    void selectsQuestionDifficultyByExperience() {
        JobTemplate job = new JobTemplate();
        job.setTitle("Java 后端");
        job.setCategory("Java 后端");
        job.setConfigJson("{\"basic\":30,\"project\":35,\"scenario\":20,\"ai\":15}");

        ResumeProfile profile = new ResumeProfile(
                "张三", "zhangsan@example.com", "13800138000", 3,
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                List.of(new ProjectExperience("中州养老", "负责设备数据采集与报警规则")),
                "某大学本科", "3 年 Java 后端经验");

        List<QuestionBankItem> retrieved = List.of(
                new QuestionBankItem(1L, "Java 基础", List.of("Java"), 1,
                        "基础题", "基础答案", "测试"),
                new QuestionBankItem(2L, "Java 并发", List.of("Java"), 3,
                        "进阶题", "进阶答案", "测试"));

        InterviewBlueprint campus = generator.generate(new BlueprintRequest(
                job, null, RoleProfile.tech(), profile, "CAMPUS", 20, "STANDARD", retrieved));
        InterviewBlueprint senior = generator.generate(new BlueprintRequest(
                job, null, RoleProfile.tech(), profile, "SENIOR", 20, "STANDARD", retrieved));

        assertThat(firstBasicDifficulty(campus)).isEqualTo(1);
        assertThat(firstBasicDifficulty(senior)).isEqualTo(3);
    }

    private int firstBasicDifficulty(InterviewBlueprint blueprint) {
        return blueprint.sections().stream()
                .filter(section -> "BASIC".equals(section.type()))
                .findFirst()
                .orElseThrow()
                .questions()
                .get(0)
                .difficulty();
    }
}
