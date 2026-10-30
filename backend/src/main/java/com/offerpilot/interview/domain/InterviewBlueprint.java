package com.offerpilot.interview.domain;

import java.util.List;

public record InterviewBlueprint(
        String opening,
        List<BlueprintSection> sections,
        String closing
) {
}
