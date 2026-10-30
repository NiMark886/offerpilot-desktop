package com.offerpilot.resume.domain;

import java.util.List;

public record ResumeProfile(
        String name,
        String email,
        String phone,
        Integer yearsOfExperience,
        List<String> skills,
        List<ProjectExperience> projects,
        String education,
        String summary
) {
}
