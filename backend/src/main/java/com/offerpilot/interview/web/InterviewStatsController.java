package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewReportService;
import com.offerpilot.interview.web.dto.GrowthJobView;
import com.offerpilot.interview.web.dto.InterviewHistoryItem;
import com.offerpilot.interview.web.dto.StatsOverview;
import com.offerpilot.questionbank.service.WeaknessService;
import com.offerpilot.questionbank.web.dto.WeaknessView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InterviewStatsController {

    private final InterviewReportService reportService;
    private final WeaknessService weaknessService;

    public InterviewStatsController(InterviewReportService reportService, WeaknessService weaknessService) {
        this.reportService = reportService;
        this.weaknessService = weaknessService;
    }

    @GetMapping("/interviews/history")
    public ApiResponse<List<InterviewHistoryItem>> history(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long jobTemplateId,
            @RequestParam(required = false) String trackCode) {
        return ApiResponse.ok(reportService.history(userId, jobTemplateId, trackCode));
    }

    @GetMapping("/stats/jobs")
    public ApiResponse<List<GrowthJobView>> jobs(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reportService.jobs(userId));
    }

    @GetMapping("/stats/overview")
    public ApiResponse<StatsOverview> overview(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long jobTemplateId,
            @RequestParam(required = false) String trackCode) {
        return ApiResponse.ok(reportService.stats(userId, jobTemplateId, trackCode));
    }

    @GetMapping("/stats/weaknesses")
    public ApiResponse<List<WeaknessView>> weaknesses(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long jobTemplateId,
            @RequestParam(required = false) String trackCode) {
        return ApiResponse.ok(weaknessService.weaknesses(userId, jobTemplateId, trackCode));
    }
}
