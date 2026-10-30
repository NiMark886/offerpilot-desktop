package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.InterviewBlueprint;

public interface InterviewBlueprintGenerator {

    InterviewBlueprint generate(BlueprintRequest request);
}
