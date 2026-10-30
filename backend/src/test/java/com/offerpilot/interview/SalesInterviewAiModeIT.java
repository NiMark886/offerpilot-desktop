package com.offerpilot.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.ai.service.AiAvailabilityChecker;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.service.InterviewBlueprintGenerator;
import com.offerpilot.resume.TestFiles;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sales_ai_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesInterviewAiModeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiAvailabilityChecker aiAvailabilityChecker;

    @MockBean
    private InterviewBlueprintGenerator blueprintGenerator;

    @Test
    void marksAiModeWhenAiGenerationSucceeds() throws Exception {
        when(aiAvailabilityChecker.isAvailable(any())).thenReturn(true);
        when(blueprintGenerator.generate(any())).thenReturn(salesBlueprint());

        createAndExpect("AI");
    }

    @Test
    void fallsBackToBankWhenAiGenerationFails() throws Exception {
        when(aiAvailabilityChecker.isAvailable(any())).thenReturn(true);
        when(blueprintGenerator.generate(any())).thenThrow(new IllegalStateException("AI down"));

        createAndExpect("BANK");
    }

    private void createAndExpect(String generationMode) throws Exception {
        String token = register();
        long resumeId = uploadResume(token);

        mockMvc.perform(post("/api/interviews/custom")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jdText", "负责企业客户开拓、需求挖掘、方案呈现、异议处理和谈判成交，要求结果导向和抗压能力。",
                                "resumeId", resumeId,
                                "difficulty", "JUNIOR",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generationMode").value(generationMode))
                .andExpect(jsonPath("$.data.questions.length()").value(8));
    }

    private InterviewBlueprint salesBlueprint() {
        List<String> competencies = List.of("客户开拓", "需求挖掘", "方案呈现", "异议处理", "谈判成交", "数据复盘");
        List<BlueprintQuestion> questions = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String competency = competencies.get(i % competencies.size());
            questions.add(new BlueprintQuestion(
                    "AI 销售题 " + i,
                    2,
                    List.of(competency),
                    List.of("参考要点"),
                    List.of("追问")));
        }
        return new InterviewBlueprint(
                "销售面试开场",
                List.of(new BlueprintSection("BEHAVIORAL", 100, questions)),
                "销售面试结束");
    }

    private String register() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "sales-ai-" + UUID.randomUUID() + "@example.com",
                                "password123", "Sales AI Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                赵六
                邮箱：zhaoliu@example.com
                3 年企业客户销售经验
                技能：客户开拓, 需求挖掘, 方案呈现, 异议处理, 谈判成交
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
}
