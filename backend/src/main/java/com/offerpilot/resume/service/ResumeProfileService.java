package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ResumeProfileService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProfileService.class);

    private final HeuristicResumeProfileParser heuristicParser;
    private final ObjectProvider<AiResumeProfileParser> aiParserProvider;

    public ResumeProfileService(HeuristicResumeProfileParser heuristicParser,
                                ObjectProvider<AiResumeProfileParser> aiParserProvider) {
        this.heuristicParser = heuristicParser;
        this.aiParserProvider = aiParserProvider;
    }

    public ResumeProfile parse(String rawText) {
        ResumeProfile heuristic = heuristicParser.parse(rawText);
        AiResumeProfileParser aiParser = aiParserProvider.getIfAvailable();
        if (aiParser == null) {
            return heuristic;
        }
        try {
            return merge(aiParser.parse(rawText), heuristic);
        } catch (Exception ex) {
            log.warn("AI resume parser failed, using heuristic parser");
            return heuristic;
        }
    }

    private ResumeProfile merge(ResumeProfile ai, ResumeProfile fallback) {
        if (ai == null) {
            return fallback;
        }
        ResumeProfile safeFallback = fallback == null
                ? new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, null)
                : fallback;
        return new ResumeProfile(
                prefer(ai.name(), safeFallback.name()),
                prefer(ai.email(), safeFallback.email()),
                prefer(ai.phone(), safeFallback.phone()),
                preferYears(ai.yearsOfExperience(), safeFallback.yearsOfExperience()),
                mergeSkills(ai.skills(), safeFallback.skills()),
                preferProjects(ai.projects(), safeFallback.projects()),
                prefer(ai.education(), safeFallback.education()),
                prefer(ai.summary(), safeFallback.summary()));
    }

    private String prefer(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private Integer preferYears(Integer primary, Integer fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? 0 : fallback;
    }

    private List<String> mergeSkills(List<String> primary, List<String> fallback) {
        Map<String, String> merged = new LinkedHashMap<>();
        addSkills(merged, primary);
        addSkills(merged, fallback);
        return new ArrayList<>(merged.values());
    }

    private void addSkills(Map<String, String> merged, List<String> skills) {
        if (skills == null) {
            return;
        }
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            String value = skill.trim();
            merged.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }
    }

    private List<ProjectExperience> preferProjects(List<ProjectExperience> primary,
                                                   List<ProjectExperience> fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }
}
