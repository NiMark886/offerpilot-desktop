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

import static com.offerpilot.support.SseTestSupport.awaitStreamCompletion;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewConversationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startsAnswersHintsSkipsAndFinishes() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId);

        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.currentQuestionId").isNumber());

        MvcResult sessionResult = mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(sessionResult.getResponse().getContentAsString()).get("data");
        long questionId = session.get("currentQuestionId").asLong();

        MvcResult sse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "不知道"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(awaitStreamCompletion(sse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"replyType\"")))
                .andExpect(content().string(containsString("\"completed\"")));

        mockMvc.perform(post("/api/interviews/" + sessionId + "/hint")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint").isNotEmpty());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/skip")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/interviews/" + sessionId + "/messages")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void practiceModeReturnsEvaluation() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId, "PRACTICE");

        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult sessionResult = mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long questionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("data").get("currentQuestionId").asLong();

        MvcResult firstSse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "不知道"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(awaitStreamCompletion(firstSse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"correctness\"")));

        MvcResult secondSse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "会重新分布到新的位置"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(awaitStreamCompletion(secondSse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"promptType\":\"FOLLOW_UP\"")));

        mockMvc.perform(get("/api/interviews/" + sessionId + "/evaluations")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].promptType").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data[1].promptText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    private String registerAndToken() throws Exception {
        String email = "conv-api-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Conv Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                张三
                邮箱：zhangsan@example.com
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：中州养老智能物联网平台
                负责设备数据采集与报警规则。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult result = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createInterview(String token, long resumeId) throws Exception {
        return createInterview(token, resumeId, "INTERVIEW");
    }

    private long createInterview(String token, long resumeId, String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", 1,
                                "resumeId", resumeId,
                                "difficulty", "STANDARD",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", mode))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
