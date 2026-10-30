package com.offerpilot.resume.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResumeDraftRequest(
        @NotBlank(message = "简历名称不能为空")
        @Size(max = 120, message = "简历名称不能超过 120 个字符")
        String title,
        @NotNull(message = "简历内容不能为空")
        ResumeDraftContent content
) {
}
