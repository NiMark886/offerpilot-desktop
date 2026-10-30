package com.offerpilot.resume.service;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.track.domain.RoleProfile;

/**
 * Evaluates a resume against the job being interviewed. The Spring AI
 * implementation is primary when AI is enabled; the heuristic service is the
 * fallback and the implementation used when AI is disabled.
 */
public interface ResumeEvaluator {

    ResumeAssessment evaluate(Resume resume, JobTemplate job, RoleProfile profile);
}
