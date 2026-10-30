package com.offerpilot.questionbank.web.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionStatusRequest(@NotBlank String status) {
}
