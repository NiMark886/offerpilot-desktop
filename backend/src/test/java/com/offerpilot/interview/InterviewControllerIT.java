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

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndReadsInterview() throws Exception {
        String email = "interview-" + UUID.randomUUID() + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Interview Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        byte[] docx = TestFiles.docx("""
                李四
                邮箱：lisi@example.com
                2 年 Java 开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：电商订单系统
                负责订单创建、库存扣减和幂等设计。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult uploaded = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long resumeId = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createBody = objectMapper.writeValueAsString(Map.of(
                "jobTemplateId", 1,
                "resumeId", resumeId,
                "difficulty", "STANDARD",
                "durationMinutes", 30,
                "persona", "STANDARD"));

        MvcResult created = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.questions.length()").value(8))
                .andReturn();

        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        long sessionId = data.get("id").asLong();

        mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(8));

        mockMvc.perform(get("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
