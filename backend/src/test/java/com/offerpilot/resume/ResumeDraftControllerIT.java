package com.offerpilot.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.web.dto.ResumeDraftContent;
import com.offerpilot.resume.web.dto.ResumeDraftRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeDraftControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsUpdatesExportsAndDeletesDraft() throws Exception {
        String token = register("draft-" + UUID.randomUUID() + "@example.com");
        long draftId = createDraft(token);

        mockMvc.perform(get("/api/resume-drafts")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content.personal.name").value("张三"));

        mockMvc.perform(put("/api/resume-drafts/" + draftId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ResumeDraftRequest(
                                "张三的简历（Java 后端）", content()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("张三的简历（Java 后端）"));

        MvcResult exported = mockMvc.perform(post("/api/resume-drafts/" + draftId + "/export/docx")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("张三_简历.docx"))
                .andReturn();
        JsonNode exportData = objectMapper.readTree(
                exported.getResponse().getContentAsString()).get("data");
        byte[] docx = Base64.getDecoder().decode(exportData.get("contentBase64").asText());
        assertThat(docx.length).isGreaterThan(1000);
        assertThat(docx[0]).isEqualTo((byte) 'P');
        assertThat(docx[1]).isEqualTo((byte) 'K');
        assertThat(documentXml(docx))
                .contains("张三")
                .contains("教育背景")
                .contains("工作经历")
                .contains("实习经历")
                .contains("项目经历")
                .contains("github.com/example")
                .contains("github.com/example/edu-admin")
                .contains("3.8 / 4.0")
                .contains("5 / 120");
    }

    @Test
    void hidesDraftFromOtherUsers() throws Exception {
        String owner = register("draft-owner-" + UUID.randomUUID() + "@example.com");
        String other = register("draft-other-" + UUID.randomUUID() + "@example.com");
        long draftId = createDraft(owner);

        mockMvc.perform(get("/api/resume-drafts/" + draftId)
                        .header(AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/resume-drafts/" + draftId + "/export/docx")
                        .header(AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/resume-drafts/" + draftId)
                        .header(AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/resume-drafts/" + draftId)
                        .header(AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/resume-drafts")
                        .header(AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private long createDraft(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/resume-drafts")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new ResumeDraftRequest("我的简历", content()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.personal.name").value("张三"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Draft Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private ResumeDraftContent content() {
        return new ResumeDraftContent(
                new ResumeDraftContent.Personal(
                        "张三", "男", "2000.01.01", "汉族", "群众",
                        "示例省示例市", "示例市", "13800138000",
                        "zhangsan@example.com", "github.com/example", ""),
                List.of(new ResumeDraftContent.Education(
                        "示例大学", "计算机学院", "软件工程", "本科",
                        "2023.09", "2027.06", "数据结构、数据库原理、计算机网络",
                        "3.8 / 4.0", "5 / 120")),
                List.of(
                        new ResumeDraftContent.Experience(
                                "INTERNSHIP", "示例科技有限公司", "后端开发实习生", "2026.06", "2026.09",
                                "Vue3 · Spring Boot", List.of("参与业务系统接口开发", "完成数据字段映射与接口联调")),
                        new ResumeDraftContent.Experience(
                                "WORK", "示例数据科技有限公司", "Java 开发工程师", "2024.07", "2025.08",
                                "Spring Boot · MySQL", List.of("负责订单查询接口开发", "优化慢查询与缓存"))),
                List.of(new ResumeDraftContent.Project(
                        "在线教育后台管理系统", "2025.09", "2025.11",
                        "Spring Boot · MyBatis · MySQL",
                        "github.com/example/edu-admin",
                        List.of("完成员工、部门等后台管理接口", "基于 JWT 实现无状态登录"))),
                List.of(new ResumeDraftContent.Skill(
                        "Java / Spring", "熟悉 Java 基础、集合、异常处理")),
                List.of("校级一等奖学金"),
                null);
    }

    private String documentXml(byte[] docx) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
