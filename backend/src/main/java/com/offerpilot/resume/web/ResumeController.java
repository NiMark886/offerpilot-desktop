package com.offerpilot.resume.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.resume.service.ResumeService;
import com.offerpilot.resume.web.dto.ResumeView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ApiResponse<ResumeView> upload(@AuthenticationPrincipal Long userId,
                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(userId, file));
    }

    @GetMapping
    public ApiResponse<List<ResumeView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(resumeService.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeView> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(resumeService.get(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        resumeService.delete(userId, id);
        return ApiResponse.ok();
    }
}
