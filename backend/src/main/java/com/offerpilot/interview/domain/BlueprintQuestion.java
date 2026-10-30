package com.offerpilot.interview.domain;

import java.util.List;

public record BlueprintQuestion(
        String text,
        int difficulty,
        List<String> knowledgeTags,
        List<String> referencePoints,
        List<String> followUpHints
) {
}
