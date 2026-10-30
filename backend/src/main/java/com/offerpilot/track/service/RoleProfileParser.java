package com.offerpilot.track.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoleProfileParser {

    private final ObjectMapper objectMapper;

    public RoleProfileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RoleProfile parse(InterviewTrack track) {
        if (track == null) {
            return RoleProfile.tech();
        }
        RoleProfile fallback = new RoleProfile(
                track.getCategory() == null ? "TECH" : track.getCategory(),
                track.getRubricCode(),
                "BANK",
                List.of(),
                RoleProfile.tech().reportDimensions(),
                Map.of());
        if (track.getConfigJson() == null || track.getConfigJson().isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(track.getConfigJson());
            List<RoleProfile.Competency> competencies = new ArrayList<>();
            for (JsonNode node : root.path("competencies")) {
                String name = node.path("name").asText("");
                if (!name.isBlank()) {
                    competencies.add(new RoleProfile.Competency(
                            name,
                            node.path("weight").asInt(0),
                            node.path("description").asText("")));
                }
            }
            Map<String, String> dimensions = new LinkedHashMap<>();
            root.path("reportDimensions").fields()
                    .forEachRemaining(entry -> dimensions.put(entry.getKey(), entry.getValue().asText()));
            Map<String, Integer> mix = new LinkedHashMap<>();
            root.path("questionMix").fields()
                    .forEachRemaining(entry -> mix.put(entry.getKey(), entry.getValue().asInt(0)));
            return new RoleProfile(
                    track.getCategory() == null ? "TECH" : track.getCategory(),
                    track.getRubricCode(),
                    root.path("retrievalMode").asText("BANK"),
                    List.copyOf(competencies),
                    dimensions.isEmpty() ? fallback.reportDimensions() : Map.copyOf(dimensions),
                    Map.copyOf(mix));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
