package com.offerpilot.resume;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeEvaluationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evaluatesResumeWithDetailedIssues() throws Exception {
        String token = register("resume-review-" + UUID.randomUUID() + "@example.com");
        long resumeId = uploadResume(token);

        mockMvc.perform(post("/api/resumes/" + resumeId + "/evaluation")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeId").value(resumeId))
                .andExpect(jsonPath("$.data.assessment.score").isNumber())
                .andExpect(jsonPath("$.data.assessment.weights.education").value(25))
                .andExpect(jsonPath("$.data.assessment.weights.skills").value(25))
                .andExpect(jsonPath("$.data.assessment.weights.experience").value(40))
                .andExpect(jsonPath("$.data.assessment.weights.layout").value(10))
                .andExpect(jsonPath("$.data.assessment.issues").isArray())
                .andExpect(jsonPath("$.data.assessment.issues.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.assessment.issues[0].title").isNotEmpty());

        mockMvc.perform(post("/api/resumes/" + resumeId + "/evaluation")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("jobTemplateId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").isNotEmpty());

        String otherToken = register("resume-review-other-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(post("/api/resumes/" + resumeId + "/evaluation")
                        .header(AUTHORIZATION, "Bearer " + otherToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Resume Reviewer"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                张三
                技能：Java
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("data");
        assertThat(data.get("parseStatus").asText()).isNotBlank();
        return data.get("id").asLong();
    }
}
