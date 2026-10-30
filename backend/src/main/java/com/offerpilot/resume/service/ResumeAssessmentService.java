package com.offerpilot.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.track.domain.RoleProfile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.File;
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
            return new ResumeAssessment(0, 0, 0, 0, 0, weightMap(), false, "暂无简历");
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
        return new ResumeAssessment(score, education, skills, experience, layout,
                weightMap(), false, "基于简历规则的评估");
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
