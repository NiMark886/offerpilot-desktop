package com.offerpilot.interview;

import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.InterviewBlueprintValidator;
import com.offerpilot.track.domain.RoleProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewBlueprintValidatorTest {

    private final InterviewBlueprintValidator validator = new InterviewBlueprintValidator();

    @Test
    void acceptsValidBlueprint() {
        assertThatCode(() -> validator.validate(validBlueprint())).doesNotThrowAnyException();
    }

    @Test
    void rejectsQuestionWithoutReferencePoints() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"), List.of(), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 100,
                        List.of(question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referencePoints");
    }

    @Test
    void rejectsUnsupportedSectionType() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"),
                List.of("要点"), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("UNKNOWN", 100,
                        List.of(question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported section type");
    }

    @Test
    void rejectsTooFewQuestions() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("Java"),
                List.of("要点"), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 100,
                        List.of(question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question count");
    }

    @Test
    void rejectsEmptySections() {
        InterviewBlueprint blueprint = new InterviewBlueprint("opening", List.of(), "closing");

        assertThatThrownBy(() -> validator.validate(blueprint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one section");
    }

    @Test
    void acceptsValidSalesBlueprint() {
        assertThatCode(() -> validator.validate(salesBlueprint(), salesProfile(), 6))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSalesBlueprintMissingCompetency() {
        BlueprintQuestion question = new BlueprintQuestion("销售问题", 2, List.of("客户开拓"),
                List.of("要点"), List.of("追问"));
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BEHAVIORAL", 100,
                        List.of(question, question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint, salesProfile(), 6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("覆盖不足");
    }

    @Test
    void rejectsSalesBlueprintWithWrongCount() {
        assertThatThrownBy(() -> validator.validate(salesBlueprint(), salesProfile(), 8))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售题目数量");
    }

    @Test
    void rejectsTechSectionTypeForSales() {
        BlueprintQuestion question = new BlueprintQuestion("问题", 2, List.of("需求挖掘"),
                List.of("要点"), List.of());
        InterviewBlueprint blueprint = new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 100,
                        List.of(question, question, question, question, question, question))),
                "closing");

        assertThatThrownBy(() -> validator.validate(blueprint, salesProfile(), 6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售题型不支持");
    }

    private RoleProfile salesProfile() {
        return new RoleProfile("GENERAL", "SALES", "BANK_AI",
                List.of(
                        new RoleProfile.Competency("客户开拓", 20, ""),
                        new RoleProfile.Competency("需求挖掘", 20, ""),
                        new RoleProfile.Competency("方案呈现", 15, ""),
                        new RoleProfile.Competency("异议处理", 15, ""),
                        new RoleProfile.Competency("谈判成交", 15, ""),
                        new RoleProfile.Competency("数据复盘", 15, "")),
                java.util.Map.of("customerInsight", "客户洞察"),
                java.util.Map.of());
    }

    private InterviewBlueprint salesBlueprint() {
        List<String> competencies = List.of("客户开拓", "需求挖掘", "方案呈现", "异议处理", "谈判成交", "数据复盘");
        List<BlueprintQuestion> questions = competencies.stream()
                .map(name -> new BlueprintQuestion("销售问题-" + name, 2, List.of(name),
                        List.of("要点"), List.of("追问")))
                .toList();
        return new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BEHAVIORAL", 100, questions)),
                "closing");
    }

    private InterviewBlueprint validBlueprint() {
        BlueprintQuestion question = new BlueprintQuestion("请介绍 HashMap", 2, List.of("Java"),
                List.of("数组", "链表", "红黑树"), List.of("扩容过程"));
        return new InterviewBlueprint("opening",
                List.of(new BlueprintSection("BASIC", 50,
                                List.of(question, question, question)),
                        new BlueprintSection("PROJECT", 50,
                                List.of(question, question))),
                "closing");
    }
}
