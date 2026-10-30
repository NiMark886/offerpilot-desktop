package com.offerpilot.interview;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.SalesBankBlueprintGenerator;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.track.domain.RoleProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesBankBlueprintGeneratorTest {

    private static final List<String> COMPETENCIES = List.of(
            "客户开拓", "需求挖掘", "方案呈现", "异议处理", "谈判成交", "数据复盘");

    private final SalesBankBlueprintGenerator generator = new SalesBankBlueprintGenerator();

    @Test
    void generatesExpectedQuestionCountByDuration() {
        assertThat(questions(generate(20, pool(2))).size()).isEqualTo(6);
        assertThat(questions(generate(30, pool(2))).size()).isEqualTo(8);
        assertThat(questions(generate(45, pool(2))).size()).isEqualTo(10);
    }

    @Test
    void coversAllCompetenciesWithoutDuplicates() {
        InterviewBlueprint blueprint = generate(45, pool(2));
        List<BlueprintQuestion> questions = questions(blueprint);

        Set<String> texts = new HashSet<>();
        Set<String> covered = new HashSet<>();
        for (BlueprintQuestion question : questions) {
            assertThat(question.referencePoints()).isNotEmpty();
            assertThat(question.knowledgeTags()).isNotEmpty();
            covered.add(question.knowledgeTags().get(0));
            assertThat(texts.add(question.text())).isTrue();
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(COMPETENCIES);
    }

    @Test
    void failsWhenPoolCannotFillRequestedCount() {
        assertThatThrownBy(() -> generate(30, pool(0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售题库不足");
    }

    private InterviewBlueprint generate(int duration, List<QuestionBankItem> pool) {
        JobTemplate job = new JobTemplate();
        job.setTitle("大客户销售");
        job.setJdText("负责企业客户开拓、需求挖掘、方案呈现、异议处理和谈判成交。");
        return generator.generateSales(new BlueprintRequest(
                job, null, salesProfile(), new ResumeProfile(
                        "张三", null, null, 3, List.of(), List.of(), null, ""),
                "JUNIOR", duration, "STANDARD", pool));
    }

    private List<BlueprintQuestion> questions(InterviewBlueprint blueprint) {
        return blueprint.sections().stream()
                .flatMap(section -> section.questions().stream())
                .toList();
    }

    private List<QuestionBankItem> pool(int perCompetency) {
        List<QuestionBankItem> items = new ArrayList<>();
        long id = 1;
        for (String competency : COMPETENCIES) {
            for (int i = 0; i < perCompetency; i++) {
                items.add(new QuestionBankItem(
                        id++,
                        competency,
                        List.of(competency),
                        2,
                        competency + "问题" + i,
                        "第一点。第二点。第三点。",
                        "公开面经整理（改写）"));
            }
        }
        return items;
    }

    private RoleProfile salesProfile() {
        List<RoleProfile.Competency> competencies = COMPETENCIES.stream()
                .map(name -> new RoleProfile.Competency(name, 15, ""))
                .toList();
        return new RoleProfile(
                "GENERAL", "SALES", "BANK_AI", competencies,
                Map.of("customerInsight", "客户洞察"),
                Map.of("behavioral", 50));
    }
}
