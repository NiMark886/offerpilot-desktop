package com.offerpilot.track.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.track.service.InterviewTrackService;
import com.offerpilot.track.web.dto.InterviewTrackView;
import com.offerpilot.track.web.dto.TrackQuestionBindRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tracks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrackController {

    private final InterviewTrackService trackService;

    public AdminTrackController(InterviewTrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping
    public ApiResponse<List<InterviewTrackView>> list() {
        return ApiResponse.ok(trackService.listTech());
    }

    @PostMapping("/{trackId}/questions")
    public ApiResponse<Void> bind(@PathVariable Long trackId,
                                  @RequestBody TrackQuestionBindRequest request) {
        trackService.bind(trackId, request.questionIds());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{trackId}/questions/{questionId}")
    public ApiResponse<Void> unbind(@PathVariable Long trackId,
                                    @PathVariable Long questionId) {
        trackService.unbind(trackId, questionId);
        return ApiResponse.ok();
    }
}
