package com.offerpilot.track.domain;

import java.util.List;
import java.util.Map;

/**
 * Parsed interview track configuration. It keeps the role-specific vocabulary
 * (competencies, report dimensions, question mix) out of the AI services.
 */
public record RoleProfile(
        String category,
        String rubricCode,
        String retrievalMode,
        List<Competency> competencies,
        Map<String, String> reportDimensions,
        Map<String, Integer> questionMix) {

    public record Competency(String name, int weight, String description) {
    }

    public static RoleProfile tech() {
        return new RoleProfile(
                "TECH",
                "BACKEND",
                "BANK",
                List.of(),
                Map.of(
                        "accuracy", "技术准确性",
                        "depth", "技术深度",
                        "project", "项目表现",
                        "expression", "表达逻辑",
                        "match", "岗位匹配"),
                Map.of());
    }

    public boolean isGeneral() {
        return "GENERAL".equalsIgnoreCase(category) || "SALES".equalsIgnoreCase(rubricCode);
    }

    public List<String> competencyNames() {
        return competencies.stream().map(Competency::name).toList();
    }

    public String dimensionLabel(String key) {
        return reportDimensions.getOrDefault(key, key);
    }
}
