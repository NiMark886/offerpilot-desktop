package com.offerpilot.resume.web.dto;

import java.util.List;

public record ResumeDraftContent(
        Personal personal,
        List<Education> education,
        List<Experience> internships,
        List<Project> projects,
        List<Skill> skills,
        List<String> awards,
        ResumeStyle style
) {

    public record Personal(
            String name,
            String gender,
            String birthDate,
            String ethnicity,
            String politicalStatus,
            String birthplace,
            String currentCity,
            String phone,
            String email,
            String communityUrl,
            String photo
    ) {
    }

    public record Education(
            String school,
            String college,
            String major,
            String degree,
            String startDate,
            String endDate,
            String courses,
            String gpa,
            String ranking
    ) {
    }

    public record Experience(
            String type,
            String company,
            String role,
            String startDate,
            String endDate,
            String techStack,
            List<String> bullets
    ) {
    }

    public record Project(
            String name,
            String startDate,
            String endDate,
            String techStack,
            String repoUrl,
            List<String> bullets
    ) {
    }

    public record Skill(String label, String content) {
    }
}
