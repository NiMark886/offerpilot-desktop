package com.offerpilot.interview.domain;

import java.util.List;

public record BlueprintSection(String type, int weight, List<BlueprintQuestion> questions) {
}
