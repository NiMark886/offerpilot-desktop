package com.offerpilot.quota.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.quota.web.dto.QuotaView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/me")
    public ApiResponse<QuotaView> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(quotaService.me(userId));
    }
}
