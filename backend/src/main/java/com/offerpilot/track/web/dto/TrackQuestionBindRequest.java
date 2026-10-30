package com.offerpilot.track.web.dto;

import java.util.List;

public record TrackQuestionBindRequest(List<Long> questionIds) {
}
