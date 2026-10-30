package com.offerpilot.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.domain.ResumeIssue;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.track.domain.RoleProfile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scores education (25%), relevant skills (25%), merged internship/work
 * experience (40%) and layout/structure (10%). The experience part uses a
 * 30/25 split internally.
 */
@Service
public class ResumeAssessmentService implements ResumeEvaluator {

    public static final int EDUCATION_WEIGHT = 25;
    public static final int SKILLS_WEIGHT = 25;
    public static final int EXPERIENCE_WEIGHT = 40;
    public static final int LAYOUT_WEIGHT = 10;
    private static final int INTERNSHIP_PART_WEIGHT = 30;
    private static final int WORK_EXPERIENCE_PART_WEIGHT = 25;
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private final ObjectMapper objectMapper;

    public ResumeAssessmentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeAssessment evaluate(Resume resume, JobTemplate job, RoleProfile profile) {
        return assess(resume, job, profile);
    }

    public ResumeAssessment assess(Resume resume) {
        return assess(resume, null, null);
    }

    public ResumeAssessment assess(Resume resume, JobTemplate job, RoleProfile roleProfile) {
        if (resume == null) {
            return new ResumeAssessment(0, 0, 0, 0, 0, weightMap(), false, "暂无简历", List.of());
        }
        ResumeProfile profile = readProfile(resume.getParsedJson());
        String text = buildText(resume, profile);
        int education = educationScore(profile.education(), text);
        int skills = skillsScore(profile, text, job, roleProfile);
        int internship = internshipScore(profile, text);
        int workExperience = workExperienceScore(profile, text);
        int layout = layoutScore(resume);
        int experience = clamp((int) Math.round(
                internship * INTERNSHIP_PART_WEIGHT / 55.0
                        + workExperience * WORK_EXPERIENCE_PART_WEIGHT / 55.0));
        int score = clamp((int) Math.round(
                education * EDUCATION_WEIGHT / 100.0
                        + skills * SKILLS_WEIGHT / 100.0
                        + experience * EXPERIENCE_WEIGHT / 100.0
                        + layout * LAYOUT_WEIGHT / 100.0));
        List<ResumeIssue> issues = buildIssues(
                resume, profile, text, job, roleProfile, education, skills, experience, layout);
        return new ResumeAssessment(score, education, skills, experience, layout,
                weightMap(), false, "基于简历规则的评估", issues);
    }

    public List<ResumeIssue> detectIssues(Resume resume, JobTemplate job, RoleProfile roleProfile) {
        if (resume == null) {
            return List.of();
        }
        ResumeProfile profile = readProfile(resume.getParsedJson());
        String text = buildText(resume, profile);
        int education = educationScore(profile.education(), text);
        int skills = skillsScore(profile, text, job, roleProfile);
        int internship = internshipScore(profile, text);
        int workExperience = workExperienceScore(profile, text);
        int experience = clamp((int) Math.round(
                internship * INTERNSHIP_PART_WEIGHT / 55.0
                        + workExperience * WORK_EXPERIENCE_PART_WEIGHT / 55.0));
        return buildIssues(resume, profile, text, job, roleProfile,
                education, skills, experience, layoutScore(resume));
    }

    private List<ResumeIssue> buildIssues(Resume resume, ResumeProfile profile, String text,
                                          JobTemplate job, RoleProfile roleProfile,
                                          int education, int skills, int experience, int layout) {
        List<ResumeIssue> issues = new ArrayList<>();
        if (!EMAIL.matcher(text).find()) {
            issues.add(issue("基本信息", "HIGH", "缺少邮箱",
                    "简历中未检测到可用的邮箱地址，招聘方无法通过邮件联系你。",
                    "在页眉或个人信息区补充常用邮箱，并检查格式是否正确。",
                    "未找到邮箱地址"));
        }
        if (!PHONE.matcher(text).find()) {
            issues.add(issue("基本信息", "HIGH", "缺少手机号",
                    "简历中未检测到手机号，HR 无法快速电话联系。",
                    "补充 11 位手机号，并放在姓名和邮箱附近。",
                    "未找到手机号"));
        }
        if (profile.education() == null || profile.education().isBlank()) {
            issues.add(issue("教育背景", "HIGH", "缺少教育背景",
                    "简历未识别到学校、学历或专业信息。",
                    "补充学校、学历、专业和毕业时间，校招简历尤其重要。",
                    "教育字段为空"));
        }
        List<String> skillList = profile.skills() == null ? List.of() : profile.skills();
        if (skillList.isEmpty()) {
            issues.add(issue("技能", "HIGH", "缺少技能/技术栈",
                    "简历没有可识别的技能列表，无法判断你的技术栈和岗位匹配度。",
                    "增加技能栏，按语言、框架、数据库、工具分组列出掌握程度。",
                    "技能字段为空"));
        }

        List<ProjectExperience> projects = profile.projects() == null ? List.of() : profile.projects();
        if (projects.isEmpty()) {
            issues.add(issue("项目经历", "MEDIUM", "缺少项目经历",
                    "简历未识别到项目名称或项目描述，面试官难以判断你的实际动手能力。",
                    "补充 1-2 个代表项目，写清业务背景、你的职责、技术方案和结果。",
                    "项目数量为 0"));
        } else {
            for (ProjectExperience project : projects) {
                if (project.description() == null || project.description().trim().length() < 20) {
                    issues.add(issue("项目经历", "MEDIUM", "项目描述过短",
                            "项目「" + project.name() + "」的描述不足 20 字，信息量过低。",
                            "按“背景-任务-行动-结果”补充你的具体贡献和技术细节。",
                            "项目描述长度不足"));
                    break;
                }
            }
        }

        if (!containsAny(text, List.of("实习", "工作", "在职", "项目经历"))) {
            issues.add(issue("实习/工作经历", "MEDIUM", "工作或实习经历不明确",
                    "简历中缺少实习、工作或项目经历的有效描述。",
                    "补充实习/工作时间、公司、岗位、职责和量化成果。",
                    "未找到实习/工作关键词"));
        }
        boolean hasDigit = text.chars().anyMatch(Character::isDigit);
        if (!hasDigit && (!projects.isEmpty() || containsAny(text, List.of("实习", "工作")))) {
            issues.add(issue("成果量化", "MEDIUM", "缺少量化结果",
                    "简历几乎没有数字，无法判断你的工作规模和实际产出。",
                    "补充金额、订单量、用户量、性能指标、转化率等可量化结果。",
                    "未找到数字型成果"));
        }
        if (text.length() > 0 && text.length() < 300) {
            issues.add(issue("内容长度", "HIGH", "简历内容过少",
                    "当前简历可读文本不足 300 字，信息密度过低。",
                    "补充教育、技能、项目和实习/工作经历，至少形成一页完整简历。",
                    "可读文本约 " + text.length() + " 字"));
        } else if (text.length() > 5000) {
            issues.add(issue("内容长度", "LOW", "简历内容偏长",
                    "简历可读文本超过 5000 字，重点不够集中。",
                    "压缩到 1-2 页，删除重复描述和与目标岗位无关的内容。",
                    "可读文本约 " + text.length() + " 字"));
        }
        if (profile.summary() == null || profile.summary().isBlank()) {
            issues.add(issue("个人简介", "LOW", "缺少个人简介",
                    "简历没有简短的自我概述，HR 需要更快理解你的定位。",
                    "用 2-3 句话概括经验年限、核心技能和代表性成果。",
                    "个人简介为空"));
        }

        int sections = 0;
        for (String keyword : List.of("教育", "技能", "项目", "实习", "工作", "证书", "奖项")) {
            if (text.contains(keyword)) {
                sections++;
            }
        }
        if (sections < 3) {
            issues.add(issue("排版布局", "MEDIUM", "模块结构不完整",
                    "简历缺少常见的教育、技能、项目或经历模块，阅读时难以快速定位信息。",
                    "使用清晰的小标题组织信息，建议包含教育、技能、项目、实习/工作。",
                    "检测到的模块数：" + sections));
        }
        if (layout < 60) {
            issues.add(issue("排版布局", "MEDIUM", "结构/排版得分偏低",
                    "文件格式、联系方式、段落或时间线结构存在明显缺口。",
                    "统一要点符号，控制单行长度，补齐联系方式和时间信息。",
                    "结构排版得分：" + layout));
        }

        boolean jobContext = job != null
                && ((job.getJdText() != null && !job.getJdText().isBlank())
                || (job.getTitle() != null && !job.getTitle().isBlank()));
        if (jobContext && skills < 50) {
            issues.add(issue("岗位匹配", "HIGH", "技能与目标岗位匹配度低",
                    "简历技能与目标岗位 JD 的重合度较低，HR 可能认为你不匹配。",
                    "对照 JD 补充相关技术栈，并用项目证明这些技能的实际使用。",
                    "技能匹配得分：" + skills));
        }
        if (jobContext && experience < 50) {
            issues.add(issue("岗位匹配", "MEDIUM", "经历与目标岗位相关性不足",
                    "实习/工作经历与目标岗位的业务或职责关联较弱。",
                    "优先展示与岗位最相关的项目，写清可迁移的能力和成果。",
                    "经历匹配得分：" + experience));
        }

        return issues.stream()
                .sorted(Comparator.comparingInt(issue -> severityRank(issue.severity())))
                .limit(10)
                .toList();
    }

    private ResumeIssue issue(String category, String severity, String title,
                              String detail, String suggestion, String evidence) {
        return new ResumeIssue(category, severity, title, detail, suggestion, evidence);
    }

    private int severityRank(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) {
            return 0;
        }
        if ("MEDIUM".equalsIgnoreCase(severity)) {
            return 1;
        }
        return 2;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Integer> weightMap() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("education", EDUCATION_WEIGHT);
        weights.put("skills", SKILLS_WEIGHT);
        weights.put("experience", EXPERIENCE_WEIGHT);
        weights.put("layout", LAYOUT_WEIGHT);
        return weights;
    }

    /**
     * Structural layout score. This does not claim to judge visual aesthetics;
     * it checks file format, contact completeness, section headings, length,
     * bullet consistency and date evidence.
     */
    public int layoutScore(Resume resume) {
        if (resume == null) {
            return 0;
        }
        String fileName = resume.getFileName() == null ? "" : resume.getFileName().toLowerCase(Locale.ROOT);
        String text = resume.getRawText() == null ? "" : resume.getRawText();
        int score = fileName.endsWith(".pdf") || fileName.endsWith(".docx") ? 10 : 5;
        if (EMAIL.matcher(text).find()) {
            score += 8;
        }
        if (PHONE.matcher(text).find()) {
            score += 7;
        }

        int sections = 0;
        for (String keyword : List.of("教育", "技能", "项目", "实习", "工作", "证书", "奖项")) {
            if (text.contains(keyword)) {
                sections++;
            }
        }
        score += Math.min(25, sections * 5);

        int pages = pdfPages(resume, fileName);
        if (pages > 0) {
            if (pages <= 2) {
                score += 20;
            } else if (pages <= 3) {
                score += 12;
            } else {
                score += 6;
            }
        } else {
            int length = text.length();
            if (length >= 400 && length <= 2500) {
                score += 18;
            } else if (length >= 200) {
                score += 12;
            } else if (length > 0) {
                score += 8;
            }
        }

        int bulletLines = 0;
        int nonBlankLines = 0;
        int totalChars = 0;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonBlankLines++;
            totalChars += trimmed.length();
            if (trimmed.matches("^([-*•·]|\\d+[.、]).*")) {
                bulletLines++;
            }
        }
        if (bulletLines >= 3) {
            score += 15;
        } else if (bulletLines > 0) {
            score += 10;
        } else {
            score += 6;
        }

        int years = 0;
        var matcher = YEAR.matcher(text);
        while (matcher.find()) {
            years++;
        }
        if (years >= 2) {
            score += 10;
        } else if (years == 1) {
            score += 7;
        } else {
            score += 5;
        }

        int averageLineLength = nonBlankLines == 0 ? 0 : totalChars / nonBlankLines;
        score += nonBlankLines >= 6 && averageLineLength <= 90 ? 5 : 2;
        return clamp(score);
    }

    private int pdfPages(Resume resume, String fileName) {
        if (!fileName.endsWith(".pdf") || resume.getFilePath() == null) {
            return 0;
        }
        try (PDDocument document = Loader.loadPDF(new File(resume.getFilePath()))) {
            return document.getNumberOfPages();
        } catch (Exception ex) {
            return 0;
        }
    }

    private int educationScore(String education, String text) {
        String value = education == null ? "" : education;
        if (value.isBlank()) {
            value = text == null ? "" : text;
        }
        if (value.contains("博士")) {
            return 95;
        }
        if (value.contains("硕士") || value.contains("研究生")) {
            return 88;
        }
        if (value.contains("本科") || value.contains("学士")) {
            return 80;
        }
        if (value.contains("大专") || value.contains("专科")) {
            return 68;
        }
        if (value.contains("高中") || value.contains("中专")) {
            return 52;
        }
        return value.isBlank() ? 50 : 60;
    }

    private int skillsScore(ResumeProfile profile, String text,
                            JobTemplate job, RoleProfile roleProfile) {
        List<String> skills = profile.skills() == null
                ? List.of()
                : profile.skills().stream()
                        .filter(skill -> skill != null && !skill.isBlank())
                        .toList();
        String jobContext = ((job == null ? "" : job.getTitle() + " " + job.getJdText() + " ")
                + (roleProfile == null ? "" : String.join(" ", roleProfile.competencyNames())))
                .toLowerCase(Locale.ROOT);

        if (skills.isEmpty()) {
            int hits = keywordHits(text, List.of(
                    "销售", "客户", "谈判", "需求", "沟通", "数据分析",
                    "Java", "Spring", "MySQL", "前端", "后端"));
            return clamp(50 + Math.min(20, hits * 4));
        }

        int matched = 0;
        for (String skill : skills) {
            if (!jobContext.isBlank()
                    && jobContext.contains(skill.toLowerCase(Locale.ROOT))) {
                matched++;
            }
        }
        int score = 55 + Math.min(20, skills.size() * 3) + Math.min(20, matched * 6);
        if (profile.projects() != null && !profile.projects().isEmpty() && text.contains("项目")) {
            score += 5;
        }
        return clamp(score);
    }

    private int keywordHits(String text, List<String> keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    private int internshipScore(ResumeProfile profile, String text) {
        if (text.contains("实习")) {
            return 85;
        }
        int projectCount = profile.projects() == null ? 0 : profile.projects().size();
        if (projectCount >= 2) {
            return 70;
        }
        if (projectCount == 1) {
            return 65;
        }
        return text.contains("项目") ? 60 : 50;
    }

    private int workExperienceScore(ResumeProfile profile, String text) {
        int years = profile.yearsOfExperience() == null ? 0 : profile.yearsOfExperience();
        int base;
        if (years >= 8) {
            base = 92;
        } else if (years >= 5) {
            base = 85;
        } else if (years >= 3) {
            base = 75;
        } else if (years >= 2) {
            base = 68;
        } else if (years >= 1) {
            base = 62;
        } else {
            base = text.contains("工作经验") || text.contains("在职") ? 60 : 50;
        }
        int projectCount = profile.projects() == null ? 0 : profile.projects().size();
        return clamp(base + Math.min(8, projectCount * 3));
    }

    private String buildText(Resume resume, ResumeProfile profile) {
        StringBuilder builder = new StringBuilder();
        append(builder, resume.getRawText());
        append(builder, profile.education());
        append(builder, profile.summary());
        if (profile.skills() != null) {
            profile.skills().forEach(skill -> append(builder, skill));
        }
        List<ProjectExperience> projects = profile.projects() == null ? List.of() : profile.projects();
        for (ProjectExperience project : projects) {
            append(builder, project.name());
            append(builder, project.description());
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append(' ');
        }
    }

    private ResumeProfile readProfile(String json) {
        if (json == null || json.isBlank()) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
        try {
            return objectMapper.readValue(json, ResumeProfile.class);
        } catch (Exception ex) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
