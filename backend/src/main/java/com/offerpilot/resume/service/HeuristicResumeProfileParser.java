package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HeuristicResumeProfileParser {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern YEARS = Pattern.compile("(\\d+)\\s*(年|years?)", Pattern.CASE_INSENSITIVE);

    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "Spring", "MyBatis", "MySQL", "Redis",
            "JVM", "并发", "微服务", "Docker", "Kubernetes", "Linux", "RabbitMQ", "Kafka",
            "RAG", "Agent", "大模型", "提示词", "向量数据库", "Embedding", "Python"
    );

    public ResumeProfile parse(String rawText) {
        String text = rawText == null ? "" : rawText.replace("\r\n", "\n");
        String email = findFirst(EMAIL, text);
        String phone = findFirst(PHONE, text);
        Integer years = findYears(text);
        List<String> skills = findSkills(text);
        List<ProjectExperience> projects = findProjects(text);
        String education = findEducation(text);
        String name = findName(text);
        String summary = text.length() > 220 ? text.substring(0, 220).trim() : text.trim();

        return new ResumeProfile(name, email, phone, years, skills, projects, education, summary);
    }

    private String findFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private Integer findYears(String text) {
        Matcher matcher = YEARS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private List<String> findSkills(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> skills = new LinkedHashSet<>();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                skills.add(skill);
            }
        }
        return new ArrayList<>(skills);
    }

    private List<ProjectExperience> findProjects(String text) {
        List<ProjectExperience> projects = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("项目") && trimmed.length() >= 6) {
                String name = trimmed;
                int separator = Math.max(trimmed.indexOf('：'), trimmed.indexOf(':'));
                if (separator > 0 && separator < trimmed.length() - 1) {
                    name = trimmed.substring(separator + 1).trim();
                }
                if (name.length() > 40) {
                    name = name.substring(0, 40);
                }
                projects.add(new ProjectExperience(name, trimmed));
                if (projects.size() >= 3) {
                    break;
                }
            }
        }
        return projects;
    }

    private String findEducation(String text) {
        for (String line : text.split("\n")) {
            if (line.contains("大学") || line.contains("学院") || line.contains("本科") || line.contains("硕士")) {
                return line.trim();
            }
        }
        return null;
    }

    private String findName(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 20
                    && !trimmed.contains("@") && !trimmed.matches(".*\\d{5,}.*")
                    && !trimmed.contains("简历")) {
                return trimmed;
            }
        }
        return "候选人";
    }
}
