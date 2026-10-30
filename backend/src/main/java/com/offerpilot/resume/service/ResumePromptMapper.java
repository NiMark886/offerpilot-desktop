package com.offerpilot.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the resume payload sent to AI prompts.
 *
 * <p>Only interview-relevant fields are included. Contact details and any
 * email/phone occurrences inside free text are masked so resumes do not leak
 * PII to the model provider.</p>
 */
@Component
public class ResumePromptMapper {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final String MASK = "[已脱敏]";

    private final ObjectMapper objectMapper;

    public ResumePromptMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toPromptJson(ResumeProfile profile) {
        if (profile == null) {
            return "{}";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("yearsOfExperience", profile.yearsOfExperience());
        payload.put("skills", profile.skills() == null ? List.of() : profile.skills());
        payload.put("projects", profile.projects() == null
                ? List.of()
                : profile.projects().stream().map(this::projectPayload).toList());
        payload.put("education", sanitize(profile.education()));
        payload.put("summary", sanitize(profile.summary()));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "简历画像序列化失败");
        }
    }

    private Map<String, Object> projectPayload(ProjectExperience project) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", sanitize(project.name()));
        payload.put("description", sanitize(project.description()));
        return payload;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String masked = EMAIL.matcher(value).replaceAll(MASK);
        return PHONE.matcher(masked).replaceAll(MASK);
    }
}
