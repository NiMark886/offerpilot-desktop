package com.offerpilot.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts an AI resume response into {@link ResumeProfile}.
 *
 * <p>LLMs are not reliable JSON serializers: values such as
 * {@code yearsOfExperience} may come back as strings, projects may be plain
 * strings, or optional fields may be omitted. This parser tolerates those
 * variants instead of failing the whole resume upload.</p>
 */
@Component
public class ResumeProfileJsonParser {

    private static final Pattern INTEGER = Pattern.compile("(\\d+)");
    private static final Pattern SKILL_SEPARATOR = Pattern.compile("[,，、;；/\\n]");
    private static final int MAX_SKILLS = 30;
    private static final int MAX_PROJECTS = 5;
    private static final int MAX_SUMMARY_LENGTH = 500;

    private final ObjectMapper objectMapper;

    public ResumeProfileJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResumeProfile parse(String content) {
        JsonNode root = readRoot(content);
        return new ResumeProfile(
                text(root, "name", "candidateName", "fullName", "姓名"),
                text(root, "email", "mail", "邮箱"),
                text(root, "phone", "mobile", "telephone", "电话", "手机"),
                yearsOfExperience(root),
                skills(root),
                projects(root),
                education(root),
                summary(root));
    }

    private JsonNode readRoot(String content) {
        String json = cleanJson(content);
        if (json.isBlank()) {
            throw new IllegalArgumentException("AI resume response is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null && root.isArray() && root.size() > 0) {
                root = root.get(0);
            }
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("AI resume response is not a JSON object");
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI resume response is not valid JSON", ex);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String text(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            String value = nodeText(node);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String value = nodeText(item);
                if (value != null && !value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            return parts.isEmpty() ? null : String.join("；", parts);
        }
        if (node.isObject()) {
            for (String key : List.of("name", "value", "text", "title", "label")) {
                String value = nodeText(node.get(key));
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private Integer yearsOfExperience(JsonNode root) {
        JsonNode node = firstField(root,
                "yearsOfExperience", "yearsExperience", "years", "experienceYears",
                "years_of_experience", "工作年限", "经验年限");
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return clampYears(node.asInt());
        }
        if (node.isNumber()) {
            return clampYears((int) Math.round(node.asDouble()));
        }
        String value = nodeText(node);
        if (value == null) {
            return null;
        }
        Matcher matcher = INTEGER.matcher(value);
        return matcher.find() ? clampYears(Integer.parseInt(matcher.group(1))) : null;
    }

    private int clampYears(int years) {
        return Math.max(0, Math.min(60, years));
    }

    private List<String> skills(JsonNode root) {
        JsonNode node = firstField(root,
                "skills", "technicalSkills", "skillSet", "technologies", "技能");
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                addSkill(result, nodeText(item));
            }
        } else if (node != null) {
            String value = nodeText(node);
            if (value != null) {
                for (String part : SKILL_SEPARATOR.split(value)) {
                    addSkill(result, part);
                }
            }
        }
        return result.values().stream().limit(MAX_SKILLS).toList();
    }

    private void addSkill(Map<String, String> result, String value) {
        if (value == null) {
            return;
        }
        String skill = value.replaceFirst("^[·•\\-*\\s]+", "").trim();
        if (skill.isEmpty() || skill.length() > 50) {
            return;
        }
        result.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill);
    }

    private List<ProjectExperience> projects(JsonNode root) {
        JsonNode node = firstField(root,
                "projects", "projectExperiences", "projectExperience", "项目经历", "项目");
        List<ProjectExperience> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addProject(result, project(item));
                if (result.size() >= MAX_PROJECTS) {
                    break;
                }
            }
        } else if (node.isObject()) {
            if (node.has("name") || node.has("projectName") || node.has("title")) {
                addProject(result, project(node));
            } else {
                node.fields().forEachRemaining(entry -> {
                    if (result.size() < MAX_PROJECTS) {
                        String description = nodeText(entry.getValue());
                        addProject(result, new ProjectExperience(
                                trim(entry.getKey(), 100),
                                trim(description == null ? entry.getKey() : description, 1000)));
                    }
                });
            }
        } else {
            addProject(result, projectFromText(nodeText(node)));
        }
        return result;
    }

    private void addProject(List<ProjectExperience> result, ProjectExperience project) {
        if (project != null && project.name() != null && !project.name().isBlank()) {
            result.add(project);
        }
    }

    private ProjectExperience project(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return projectFromText(node.asText());
        }
        if (!node.isObject()) {
            return null;
        }
        String name = text(node, "name", "projectName", "title", "project", "项目名称");
        String description = text(node,
                "description", "desc", "detail", "projectDescription", "projectDesc",
                "responsibility", "responsibilities", "content", "summary",
                "项目描述", "职责");
        if (name == null && description == null) {
            return null;
        }
        if (name == null) {
            name = trim(description, 40);
        }
        if (description == null) {
            description = name;
        }
        return new ProjectExperience(trim(name, 100), trim(description, 1000));
    }

    private ProjectExperience projectFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        int separator = Math.max(text.indexOf('：'), text.indexOf(':'));
        if (separator > 0 && separator < text.length() - 1) {
            return new ProjectExperience(
                    trim(text.substring(0, separator), 100),
                    trim(text.substring(separator + 1), 1000));
        }
        return new ProjectExperience(trim(text, 40), trim(text, 1000));
    }

    private String education(JsonNode root) {
        JsonNode node = firstField(root,
                "education", "educationBackground", "edu", "教育背景", "教育经历");
        if (node == null || node.isNull()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        collectEducation(node, parts);
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private void collectEducation(JsonNode node, List<String> parts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectEducation(item, parts);
            }
            return;
        }
        if (node.isObject()) {
            int before = parts.size();
            for (String key : List.of("school", "university", "college", "major",
                    "degree", "time", "period", "description", "学校", "专业", "学历")) {
                String value = nodeText(node.get(key));
                if (value != null && !value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            if (parts.size() == before) {
                node.fields().forEachRemaining(entry -> {
                    String value = nodeText(entry.getValue());
                    if (value != null && !value.isBlank()) {
                        parts.add(value.trim());
                    }
                });
            }
            return;
        }
        String value = nodeText(node);
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private String summary(JsonNode root) {
        String value = text(root, "summary", "profile", "about", "个人总结", "一句话总结");
        return value == null ? null : trim(value, MAX_SUMMARY_LENGTH);
    }

    private JsonNode firstField(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }
}
