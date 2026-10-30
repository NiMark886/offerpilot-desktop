package com.offerpilot.interview.web.dto;

import java.util.List;

public record QuestionView(
        Long id,
        Integer seq,
        String type,
        Integer difficulty,
        String content,
        List<String> knowledgeTags,
        List<String> referencePoints,
        String status,
        Integer followUpCount,
        Integer maxFollowUps,
        Integer hintCount
) {
}
