package com.offerpilot.questionbank.web.dto;

import java.util.List;

/**
 * Dropdown values collected from the existing question bank. The admin UI
 * allows creating new values on top of these options, so this is a
 * convenience list rather than a closed enum.
 */
public record QuestionBankOptionsView(
        List<String> categories,
        List<String> tags,
        List<String> sources
) {
}
