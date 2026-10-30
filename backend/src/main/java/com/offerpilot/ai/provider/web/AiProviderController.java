package com.offerpilot.ai.provider.web;

import com.offerpilot.ai.provider.service.AiProviderService;
import com.offerpilot.ai.provider.web.dto.AiProviderPreset;
import com.offerpilot.ai.provider.web.dto.AiProviderRequest;
import com.offerpilot.ai.provider.web.dto.AiProviderStatus;
import com.offerpilot.ai.provider.web.dto.AiProviderTestResult;
import com.offerpilot.ai.provider.web.dto.AiProviderView;
import com.offerpilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/providers")
public class AiProviderController {

    private final AiProviderService providerService;

    public AiProviderController(AiProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public ApiResponse<List<AiProviderView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(providerService.list(userId));
    }

    @GetMapping("/presets")
    public ApiResponse<List<AiProviderPreset>> presets() {
        return ApiResponse.ok(providerService.presets());
    }

    @GetMapping("/status")
    public ApiResponse<AiProviderStatus> status() {
        return ApiResponse.ok(providerService.status());
    }

    @PostMapping
    public ApiResponse<AiProviderView> create(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody AiProviderRequest request) {
        return ApiResponse.ok(providerService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiProviderView> update(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody AiProviderRequest request) {
        return ApiResponse.ok(providerService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        providerService.delete(userId, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<AiProviderView> activate(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id) {
        return ApiResponse.ok(providerService.activate(userId, id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AiProviderTestResult> test(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(providerService.test(userId, id));
    }
}
