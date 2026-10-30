package com.offerpilot.job.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.job.service.JobTemplateService;
import com.offerpilot.job.web.dto.JobTemplateView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job-templates")
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;

    public JobTemplateController(JobTemplateService jobTemplateService) {
        this.jobTemplateService = jobTemplateService;
    }

    @GetMapping
    public ApiResponse<List<JobTemplateView>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(jobTemplateService.listActive(userId));
    }
}
