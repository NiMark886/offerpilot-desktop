package com.offerpilot.questionbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminQuestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCrudQuestionsAndUserCannot() throws Exception {
        String adminToken = adminToken();

        MvcResult created = mockMvc.perform(post("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "测试题目：HashMap 的负载因子是多少？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .param("keyword", "负载因子"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(patch("/api/admin/questions/" + id + "/status")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/admin/questions/" + id + "/status")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(put("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Java 基础",
                                "tags", "Java,测试",
                                "difficulty", 2,
                                "question", "更新后的题目：HashMap 负载因子？",
                                "referenceAnswer", "默认 0.75。",
                                "source", "测试",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").value("更新后的题目：HashMap 负载因子？"));

        mockMvc.perform(delete("/api/admin/questions/" + id)
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        String userToken = userToken();
        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadOptionsExtractDocumentAndImportSelectedQuestions() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(get("/api/admin/questions/options")
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.sources").isArray());

        String document = """
                1. 文档导入测试题：HashMap 的默认负载因子是多少？
                答：默认 0.75，超过阈值会扩容。
                2. 文档导入测试题：线程池有哪些核心参数？
                答：corePoolSize、maximumPoolSize、keepAliveTime、workQueue 等。
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "import-test.txt", "text/plain",
                document.getBytes(StandardCharsets.UTF_8));

        MvcResult extracted = mockMvc.perform(multipart("/api/admin/questions/import/extract")
                        .file(file)
                        .param("category", "Java 基础")
                        .param("difficulty", "2")
                        .param("source", "测试文档")
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiUsed").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].question").value(
                        "文档导入测试题：HashMap 的默认负载因子是多少？"))
                .andExpect(jsonPath("$.data.items[0].category").value("Java 基础"))
                .andExpect(jsonPath("$.data.items[0].source").value("测试文档 · import-test.txt"))
                .andReturn();

        JsonNode items = objectMapper.readTree(extracted.getResponse().getContentAsString())
                .path("data").path("items");
        Map<String, Object> importRequest = Map.of(
                "questions", List.of(
                        Map.of(
                                "question", items.get(0).path("question").asText(),
                                "referenceAnswer", items.get(0).path("referenceAnswer").asText(),
                                "category", "Java 基础",
                                "difficulty", 2,
                                "tags", List.of("Java", "测试"),
                                "source", "测试文档"),
                        Map.of(
                                "question", items.get(1).path("question").asText(),
                                "referenceAnswer", items.get(1).path("referenceAnswer").asText(),
                                "category", "Java 并发",
                                "difficulty", 2,
                                "tags", List.of("Java", "线程池"),
                                "source", "测试文档")),
                "trackIds", List.of(1));

        MvcResult imported = mockMvc.perform(post("/api/admin/questions/import")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.ids.length()").value(2))
                .andReturn();

        JsonNode ids = objectMapper.readTree(imported.getResponse().getContentAsString())
                .path("data").path("ids");
        mockMvc.perform(get("/api/admin/questions")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .param("keyword", "文档导入测试题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        for (JsonNode id : ids) {
            mockMvc.perform(delete("/api/admin/questions/" + id.asLong())
                            .header(AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        String userToken = userToken();
        mockMvc.perform(get("/api/admin/questions/options")
                        .header(AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@offerpilot.local", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String userToken() throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Normal User"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }
}
