package com.offerpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.service.ResumeAssessmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeAssessmentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeAssessmentService service = new ResumeAssessmentService(objectMapper);

    @Test
    void weightsEducationSkillsExperienceAndLayout() {
        ResumeAssessment assessment = service.assess(resume(
                "安徽工业大学 本科", 3, "负责订单系统开发", "有实习经历"));

        assertThat(assessment.weights()).containsEntry("education", 25);
        assertThat(assessment.weights()).containsEntry("skills", 25);
        assertThat(assessment.weights()).containsEntry("experience", 40);
        assertThat(assessment.weights()).containsEntry("layout", 10);
        assertThat(assessment.weights()).doesNotContainKeys("internship", "workExperience");
        assertThat(assessment.educationScore()).isEqualTo(80);
        assertThat(assessment.skillsScore()).isEqualTo(58);
        assertThat(assessment.experienceScore()).isEqualTo(82);
        assertThat(assessment.layoutScore()).isEqualTo(36);
        assertThat(assessment.score()).isEqualTo(71);
        assertThat(assessment.aiGenerated()).isFalse();
        assertThat(assessment.comment()).isNotBlank();
    }

    @Test
    void masterEducationScoresHigherThanBachelor() {
        int bachelor = service.assess(resume("本科", 3, "项目", "")).score();
        int master = service.assess(resume("硕士", 3, "项目", "")).score();

        assertThat(master).isGreaterThan(bachelor);
    }

    @Test
    void treatsNullResumeAsZero() {
        ResumeAssessment assessment = service.assess(null);

        assertThat(assessment.score()).isZero();
    }

    @Test
    void blankProfileGetsNeutralBaseline() throws Exception {
        Resume resume = new Resume();
        resume.setParsedJson(objectMapper.writeValueAsString(
                new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "")));

        ResumeAssessment assessment = service.assess(resume);

        assertThat(assessment.educationScore()).isEqualTo(50);
        assertThat(assessment.skillsScore()).isEqualTo(50);
        assertThat(assessment.experienceScore()).isEqualTo(50);
        assertThat(assessment.layoutScore()).isEqualTo(18);
        assertThat(assessment.score()).isEqualTo(47);
    }

    private Resume resume(String education, int years, String projectName, String rawText) {
        try {
            Resume resume = new Resume();
            resume.setFileName("resume.docx");
            resume.setRawText(rawText);
            resume.setParsedJson(objectMapper.writeValueAsString(new ResumeProfile(
                    "候选人", null, null, years, List.of("Java"),
                    List.of(new ProjectExperience(projectName, projectName)),
                    education, "summary")));
            return resume;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
