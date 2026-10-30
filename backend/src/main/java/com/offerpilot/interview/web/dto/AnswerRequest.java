package com.offerpilot.interview.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotNull Long questionId,
        @NotBlank @Size(max = 4000) String content
) {
}
