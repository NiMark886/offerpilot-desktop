package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewReportService;
import com.offerpilot.interview.web.dto.InterviewReportView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews/{id}")
public class InterviewReportController {

    private final InterviewReportService reportService;

    public InterviewReportController(InterviewReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/report")
    public ApiResponse<InterviewReportView> generate(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(reportService.generate(userId, id));
    }

    @GetMapping("/report")
    public ApiResponse<InterviewReportView> get(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id) {
        return ApiResponse.ok(reportService.get(userId, id));
    }
}
