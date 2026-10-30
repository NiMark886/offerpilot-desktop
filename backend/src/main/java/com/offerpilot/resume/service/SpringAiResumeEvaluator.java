package com.offerpilot.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.ai.service.AiCallExecutor;
import com.offerpilot.ai.service.ChatClientProvider;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.domain.ResumeIssue;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI resume evaluation with the target job as context. Falls back to the
 * deterministic resume assessment when the provider is unavailable or the
 * response is malformed.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiResumeEvaluator implements ResumeEvaluator {

    private static final String SCENE = "RESUME_ASSESSMENT";
    private static final String PROMPT_VERSION = "resume-assessment-v5";
    private static final int MAX_RESUME_CHARS = 6000;

    private final ChatClientProvider chatClientProvider;
    private final ObjectMapper objectMapper;
    private final AiCallExecutor aiCallExecutor;
    private final ResumeAssessmentService fallback;

    public SpringAiResumeEvaluator(ChatClientProvider chatClientProvider,
                                   ObjectMapper objectMapper,
                                   AiCallExecutor aiCallExecutor,
                                   ResumeAssessmentService fallback) {
        this.chatClientProvider = chatClientProvider;
        this.objectMapper = objectMapper;
        this.aiCallExecutor = aiCallExecutor;
        this.fallback = fallback;
    }

    @Override
    public ResumeAssessment evaluate(Resume resume, JobTemplate job, RoleProfile profile) {
        if (resume == null) {
            return fallback.evaluate(resume, job, profile);
        }
        String jobTitle = job == null ? "销售岗面试" : job.getTitle();
        String jd = job == null || job.getJdText() == null ? "" : job.getJdText();
        String competencies = profile == null ? "" : String.join("、", profile.competencyNames());
        String parsed = resume.getParsedJson() == null ? "{}" : resume.getParsedJson();
        String rawText = truncate(resume.getRawText());
        int layout = fallback.layoutScore(resume);
        List<ResumeIssue> fallbackIssues = fallback.detectIssues(resume, job, profile);

        return aiCallExecutor.call(SCENE, PROMPT_VERSION,
                () -> {
                    ChatClient chatClient = chatClientProvider.get();
                    return chatClient.prompt()
                            .system("""
                                    你是招聘简历评估器。请结合目标岗位的 JD 和能力要求评估候选人简历，
                                    不要脱离岗位只按学校或年限打分。
                                    评分项：学历（权重 25%）、相关技能（权重 25%）、
                                    实习/工作经历（权重 40%）、排版布局（权重 10%，由系统单独评分）。
                                    - 学历：学历层次，同时考虑岗位对学历的要求。
                                    - 相关技能：读取简历中的技术栈/技能，结合岗位 JD 评估匹配度、
                                      技能深度和项目证据；销售等非技术岗位则评估销售相关能力。
                                    - 实习/工作经历：把实习和工作经历合并评估，重点看与岗位的相关性、
                                      项目深度、职责范围和可量化成果。
                                    不要评价在读身份、可实习时间、每周到岗天数、实习周期、
                                    是否可转正、用工形式等到岗安排；简历不需要标注这些信息，
                                    也不要因此扣分或写入 issues。
                                    排版布局由系统的结构检测单独评分，你不需要返回 layoutScore。
                                    还要指出简历的具体缺陷，放在 issues 数组中（最多 8 条），每条包含：
                                    category（如 基本信息/教育背景/技能/项目经历/实习工作/岗位匹配/排版布局）、
                                    severity（HIGH/MEDIUM/LOW）、title（简短标题）、
                                    detail（具体问题，不要泛泛而谈）、suggestion（可执行修改建议）、
                                    evidence（简历中的简短证据或缺失说明）。
                                    只输出 JSON，不要 markdown：
                                    {"educationScore":0-100,"skillsScore":0-100,"experienceScore":0-100,
                                     "comment":"一句中文点评",
                                     "issues":[{"category":"项目经历","severity":"HIGH","title":"项目描述缺少量化结果","detail":"...","suggestion":"...","evidence":"..."}]}
                                    三个分数都必须是 0-100 的整数。
                                    """)
                            .user("""
                                    目标岗位：%s
                                    岗位 JD：%s
                                    能力模型：%s
                                    简历结构化信息：%s
                                    简历原文：%s
                                    """.formatted(jobTitle, jd, competencies, parsed, rawText))
                            .call()
                            .chatResponse();
                },
                response -> parse(response.getResult().getOutput().getText(), layout, fallbackIssues),
                () -> fallback.evaluate(resume, job, profile));
    }

    private ResumeAssessment parse(String content, int layout,
                                   List<ResumeIssue> fallbackIssues) throws Exception {
        JsonNode root = objectMapper.readTree(cleanJson(content));
        if (!root.hasNonNull("educationScore")
                || !root.hasNonNull("skillsScore")
                || !root.hasNonNull("experienceScore")) {
            throw new IllegalArgumentException("AI resume assessment is missing scores");
        }
        int education = clamp(root.get("educationScore").asInt());
        int skills = clamp(root.get("skillsScore").asInt());
        int experience = clamp(root.get("experienceScore").asInt());
        int score = clamp((int) Math.round(
                education * ResumeAssessmentService.EDUCATION_WEIGHT / 100.0
                        + skills * ResumeAssessmentService.SKILLS_WEIGHT / 100.0
                        + experience * ResumeAssessmentService.EXPERIENCE_WEIGHT / 100.0
                        + layout * ResumeAssessmentService.LAYOUT_WEIGHT / 100.0));
        String comment = root.path("comment").asText("AI 已结合目标岗位完成简历评估");
        List<ResumeIssue> issues = mergeIssues(parseIssues(root.path("issues")), fallbackIssues);
        return new ResumeAssessment(score, education, skills, experience, layout,
                ResumeAssessmentService.weightMap(), true, comment, issues);
    }

    private List<ResumeIssue> parseIssues(JsonNode node) {
        List<ResumeIssue> issues = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return issues;
        }
        for (JsonNode item : node) {
            String title = item.path("title").asText("");
            String detail = item.path("detail").asText("");
            if (title.isBlank() || detail.isBlank()) {
                continue;
            }
            String severity = normalizeSeverity(item.path("severity").asText("MEDIUM"));
            ResumeIssue issue = new ResumeIssue(
                    item.path("category").asText("简历内容"),
                    severity,
                    title,
                    detail,
                    item.path("suggestion").asText("请根据问题补充或改写对应内容。"),
                    truncateEvidence(item.path("evidence").asText("")));
            if (ResumeIssueFilter.isInternshipAvailabilityIssue(issue)) {
                continue;
            }
            issues.add(issue);
            if (issues.size() >= 8) {
                break;
            }
        }
        return issues;
    }

    private List<ResumeIssue> mergeIssues(List<ResumeIssue> aiIssues, List<ResumeIssue> fallbackIssues) {
        List<ResumeIssue> merged = new ArrayList<>();
        Set<String> titles = new LinkedHashSet<>();
        for (ResumeIssue issue : aiIssues) {
            if (titles.add(issue.title())) {
                merged.add(issue);
            }
        }
        for (ResumeIssue issue : fallbackIssues) {
            if (ResumeIssueFilter.isInternshipAvailabilityIssue(issue)) {
                continue;
            }
            if (titles.add(issue.title())) {
                merged.add(issue);
            }
            if (merged.size() >= 10) {
                break;
            }
        }
        return merged;
    }

    private String normalizeSeverity(String value) {
        String normalized = value == null ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String truncateEvidence(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_RESUME_CHARS ? value : value.substring(0, MAX_RESUME_CHARS);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
