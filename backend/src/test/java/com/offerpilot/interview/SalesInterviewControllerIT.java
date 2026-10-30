package com.offerpilot.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.TestFiles;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sales_controller_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesInterviewControllerIT {

    private static final Set<String> COMPETENCIES = Set.of(
            "客户开拓", "需求挖掘", "方案呈现", "异议处理", "谈判成交", "数据复盘");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsBankSalesInterviewAndGeneratesSalesReport() throws Exception {
        String token = register("sales-" + UUID.randomUUID() + "@example.com");
        long resumeId = uploadResume(token);

        MvcResult created = mockMvc.perform(post("/api/interviews/custom")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jdText", "负责企业客户开拓、需求挖掘、方案呈现、异议处理和谈判成交，要求有较强的结果导向和抗压能力。",
                                "resumeId", resumeId,
                                "difficulty", "JUNIOR",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generationMode").value("BANK"))
                .andExpect(jsonPath("$.data.questions.length()").value(8))
                .andReturn();

        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        long sessionId = data.get("id").asLong();
        long jobTemplateId = data.get("jobTemplateId").asLong();
        for (JsonNode question : data.get("questions")) {
            String content = question.get("content").asText();
            assertThat(content).doesNotContain("Java", "Spring", "MySQL", "Redis");
            Set<String> tags = new HashSet<>();
            question.get("knowledgeTags").forEach(tag -> tags.add(tag.asText()));
            assertThat(tags).anyMatch(COMPETENCIES::contains);
        }

        mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", jobTemplateId,
                                "resumeId", resumeId,
                                "difficulty", "JUNIOR",
                                "durationMinutes", 20,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generationMode").value("BANK"))
                .andExpect(jsonPath("$.data.questions.length()").value(6));

        JsonNode ownJobs = objectMapper.readTree(mockMvc.perform(get("/api/job-templates")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(jobTitles(ownJobs)).doesNotContain("销售岗面试");

        String otherToken = register("sales-other-" + UUID.randomUUID() + "@example.com");
        JsonNode otherJobs = objectMapper.readTree(mockMvc.perform(get("/api/job-templates")
                        .header(AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(jobTitles(otherJobs)).doesNotContain("销售岗面试");

        long otherResumeId = uploadResume(otherToken);
        mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + otherToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", jobTemplateId,
                                "resumeId", otherResumeId,
                                "difficulty", "JUNIOR",
                                "durationMinutes", 20,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        JsonNode report = objectMapper.readTree(mockMvc.perform(
                        post("/api/interviews/" + sessionId + "/report")
                                .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        for (int i = 0; i < 30 && !"SUCCESS".equals(report.get("status").asText()); i++) {
            Thread.sleep(100);
            report = objectMapper.readTree(mockMvc.perform(
                            get("/api/interviews/" + sessionId + "/report")
                                    .header(AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).get("data");
        }

        assertThat(report.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(report.get("generationMode").asText()).isEqualTo("BANK");
        assertThat(fieldNames(report.get("dimensions"))).containsExactlyInAnyOrder(
                "customerInsight", "communication", "resilience", "resultOrientation", "jobMatch", "resume");
        assertThat(report.get("dimensionLabels").get("customerInsight").asText()).isEqualTo("客户洞察");
        assertThat(report.get("dimensionLabels").get("resume").asText()).isEqualTo("简历评估");
        assertThat(report.get("resumeAssessment").get("educationScore").isNumber()).isTrue();
        assertThat(report.get("resumeAssessment").get("skillsScore").isNumber()).isTrue();
        assertThat(report.get("resumeAssessment").get("layoutScore").isNumber()).isTrue();
        assertThat(report.get("resumeAssessment").get("weights").get("education").asInt()).isEqualTo(25);
        assertThat(report.get("resumeAssessment").get("weights").get("skills").asInt()).isEqualTo(25);
        assertThat(report.get("resumeAssessment").get("weights").get("experience").asInt()).isEqualTo(40);
        assertThat(report.get("resumeAssessment").get("weights").get("layout").asInt()).isEqualTo(10);

        mockMvc.perform(get("/api/stats/jobs")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].key").value("sales"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("销售岗面试"))
                .andExpect(jsonPath("$.data[0].totalInterviews").value(2))
                .andExpect(jsonPath("$.data[0].completedInterviews").value(1));

        mockMvc.perform(get("/api/stats/jobs")
                        .header(AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/stats/overview")
                        .param("trackCode", "sales")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInterviews").value(2))
                .andExpect(jsonPath("$.data.completedInterviews").value(1))
                .andExpect(jsonPath("$.data.dimensionLabels.customerInsight").value("客户洞察"))
                .andExpect(jsonPath("$.data.dimensionLabels.resume").value("简历评估"));

        mockMvc.perform(get("/api/interviews/history")
                        .param("trackCode", "sales")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private String register(String email) throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Sales Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                王五
                邮箱：wangwu@example.com
                3 年企业客户销售经验
                技能：客户开拓, 需求挖掘, 方案呈现, 异议处理, 谈判成交
                业绩：连续两年完成年度销售目标。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Set<String> jobTitles(JsonNode jobs) {
        Set<String> titles = new HashSet<>();
        jobs.forEach(job -> titles.add(job.get("title").asText()));
        return titles;
    }
}
