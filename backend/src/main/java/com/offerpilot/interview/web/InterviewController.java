package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewService;
import com.offerpilot.interview.web.dto.CreateCustomInterviewRequest;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    public ApiResponse<InterviewSessionView> create(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(interviewService.create(userId, request));
    }

    @PostMapping("/custom")
    public ApiResponse<InterviewSessionView> createCustom(@AuthenticationPrincipal Long userId,
                                                          @Valid @RequestBody CreateCustomInterviewRequest request) {
        return ApiResponse.ok(interviewService.createCustom(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSessionView> get(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(interviewService.get(userId, id));
    }

    @GetMapping
    public ApiResponse<List<InterviewSessionView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(interviewService.list(userId));
    }
}
