package com.offerpilot.resume.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.resume.service.ResumeDraftService;
import com.offerpilot.resume.web.dto.ResumeDraftExportView;
import com.offerpilot.resume.web.dto.ResumeDraftRequest;
import com.offerpilot.resume.web.dto.ResumeDraftView;
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
@RequestMapping("/api/resume-drafts")
public class ResumeDraftController {

    private final ResumeDraftService resumeDraftService;

    public ResumeDraftController(ResumeDraftService resumeDraftService) {
        this.resumeDraftService = resumeDraftService;
    }

    @GetMapping
    public ApiResponse<List<ResumeDraftView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(resumeDraftService.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDraftView> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(resumeDraftService.get(userId, id));
    }

    @PostMapping
    public ApiResponse<ResumeDraftView> create(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody ResumeDraftRequest request) {
        return ApiResponse.ok(resumeDraftService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeDraftView> update(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ResumeDraftRequest request) {
        return ApiResponse.ok(resumeDraftService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        resumeDraftService.delete(userId, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/export/docx")
    public ApiResponse<ResumeDraftExportView> exportDocx(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(resumeDraftService.exportDocx(userId, id));
    }
}
