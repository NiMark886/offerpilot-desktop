package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewReport;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewReportMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.interview.web.dto.GrowthJobView;
import com.offerpilot.interview.web.dto.InterviewHistoryItem;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import com.offerpilot.interview.web.dto.InterviewReportView;
import com.offerpilot.interview.web.dto.QuestionReviewView;
import com.offerpilot.interview.web.dto.ResumeAssessmentView;
import com.offerpilot.interview.web.dto.ResumeIssueView;
import com.offerpilot.interview.web.dto.ScorePoint;
import com.offerpilot.interview.web.dto.StatsOverview;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeAssessment;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.resume.service.ResumeAssessmentService;
import com.offerpilot.resume.service.ResumeEvaluator;
import com.offerpilot.resume.service.ResumeIssueFilter;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import com.offerpilot.track.mapper.InterviewTrackMapper;
import com.offerpilot.track.service.RoleProfileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewReportService {

    private static final Logger log = LoggerFactory.getLogger(InterviewReportService.class);

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final InterviewTrackMapper trackMapper;
    private final RoleProfileParser roleProfileParser;
    private final ResumeMapper resumeMapper;
    private final ResumeAssessmentService resumeAssessmentService;
    private final ResumeEvaluator resumeEvaluator;
    private final AnswerEvaluator answerEvaluator;
    private final InterviewReportGenerator reportGenerator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor reportExecutor;

    public InterviewReportService(InterviewReportMapper reportMapper,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewQuestionMapper questionMapper,
                                  InterviewMessageMapper messageMapper,
                                  AnswerEvaluationMapper evaluationMapper,
                                  JobTemplateMapper jobTemplateMapper,
                                  InterviewTrackMapper trackMapper,
                                  RoleProfileParser roleProfileParser,
                                  ResumeMapper resumeMapper,
                                  ResumeAssessmentService resumeAssessmentService,
                                  ResumeEvaluator resumeEvaluator,
                                  AnswerEvaluator answerEvaluator,
                                  InterviewReportGenerator reportGenerator,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate,
                                  @Qualifier("reportExecutor") TaskExecutor reportExecutor) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.evaluationMapper = evaluationMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.trackMapper = trackMapper;
        this.roleProfileParser = roleProfileParser;
        this.resumeMapper = resumeMapper;
        this.resumeAssessmentService = resumeAssessmentService;
        this.resumeEvaluator = resumeEvaluator;
        this.answerEvaluator = answerEvaluator;
        this.reportGenerator = reportGenerator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.reportExecutor = reportExecutor;
    }

    public InterviewReportView generate(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "面试尚未结束");
        }

        List<InterviewQuestion> questions = loadQuestions(sessionId);
        List<InterviewMessage> messages = loadMessages(sessionId);
        List<AnswerEvaluation> evaluations = loadEvaluations(sessionId);

        InterviewReport existing = findReport(sessionId);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return buildView(existing, session, questions, messages, evaluations);
        }

        boolean submit = false;
        if (existing == null) {
            InterviewReport created = new InterviewReport();
            created.setSessionId(sessionId);
            created.setUserId(userId);
            created.setOverallScore(0);
            created.setDimensionsJson("{}");
            created.setSummary("");
            created.setStatus("GENERATING");
            try {
                reportMapper.insert(created);
                existing = created;
                submit = true;
            } catch (DuplicateKeyException ex) {
                existing = findReport(sessionId);
            }
        } else if ("FAILED".equals(existing.getStatus())) {
            existing.setStatus("GENERATING");
            existing.setSummary("");
            reportMapper.updateById(existing);
            submit = true;
        }

        if (existing == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报告任务创建失败");
        }
        if (submit) {
            Long reportId = existing.getId();
            reportExecutor.execute(() -> runAsUser(userId,
                    () -> generateAsync(userId, sessionId, reportId)));
        }
        return buildView(existing, session, questions, messages, evaluations);
    }

    public InterviewReportView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        InterviewReport report = findReport(sessionId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告尚未生成");
        }
        return buildView(report, session,
                loadQuestions(sessionId), loadMessages(sessionId), loadEvaluations(sessionId));
    }

    private void generateAsync(Long userId, Long sessionId, Long reportId) {
        try {
            InterviewSession session = loadOwned(userId, sessionId);
            List<InterviewQuestion> questions = loadQuestions(sessionId);
            List<InterviewMessage> messages = loadMessages(sessionId);
            List<AnswerEvaluation> evaluations = new ArrayList<>(loadEvaluations(sessionId));
            RoleProfile profile = profile(session);
            ensureEvaluations(session, questions, messages, evaluations, profile);

            ResumeAssessment resumeAssessment = evaluateResume(session);
            InterviewReportResult result = withResumeDimension(
                    reportGenerator.generate(session, questions, messages, evaluations, profile),
                    resumeAssessment);
            InterviewReport report = reportMapper.selectById(reportId);
            if (report == null) {
                log.warn("Report row disappeared while generating: reportId={}", reportId);
                return;
            }
            report.setOverallScore(result.overallScore());
            report.setDimensionsJson(writeJson(result.dimensions()));
            report.setResumeAssessmentJson(writeJson(ResumeAssessmentView.from(resumeAssessment)));
            report.setSummary(result.summary());
            report.setStrengthsJson(writeJson(result.strengths()));
            report.setWeaknessesJson(writeJson(result.weaknesses()));
            report.setSuggestionsJson(writeJson(result.suggestions()));
            report.setStatus("SUCCESS");
            transactionTemplate.executeWithoutResult(status -> {
                reportMapper.updateById(report);
                session.setTotalScore(result.overallScore());
                sessionMapper.updateById(session);
            });
        } catch (Exception ex) {
            log.warn("Async report generation failed: sessionId={}", sessionId, ex);
            InterviewReport report = reportMapper.selectById(reportId);
            if (report != null) {
                report.setStatus("FAILED");
                report.setSummary("报告生成失败，请重试");
                reportMapper.updateById(report);
            }
        }
    }

    private void runAsUser(Long userId, Runnable action) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private InterviewReport findReport(Long sessionId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId));
    }

    private List<InterviewQuestion> loadQuestions(Long sessionId) {
        return questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByAsc(InterviewQuestion::getSeq));
    }

    private List<InterviewMessage> loadMessages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getId));
    }

    private List<AnswerEvaluation> loadEvaluations(Long sessionId) {
        return evaluationMapper.selectList(new LambdaQueryWrapper<AnswerEvaluation>()
                .eq(AnswerEvaluation::getSessionId, sessionId)
                .orderByAsc(AnswerEvaluation::getId));
    }

    public List<GrowthJobView> jobs(Long userId) {
        List<InterviewSession> sessions = sessions(userId, null);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> jobIds = sessions.stream()
                .map(InterviewSession::getJobTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> trackIds = sessions.stream()
                .map(InterviewSession::getTrackId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, JobTemplate> jobById = jobIds.isEmpty() ? Map.of()
                : jobTemplateMapper.selectBatchIds(jobIds).stream()
                        .collect(Collectors.toMap(JobTemplate::getId, job -> job, (a, b) -> a));
        Map<Long, InterviewTrack> trackById = trackIds.isEmpty() ? Map.of()
                : trackMapper.selectBatchIds(trackIds).stream()
                        .collect(Collectors.toMap(InterviewTrack::getId, track -> track, (a, b) -> a));

        Map<String, List<InterviewSession>> byKey = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            InterviewTrack track = session.getTrackId() == null
                    ? null : trackById.get(session.getTrackId());
            boolean sales = track != null
                    && ("GENERAL".equalsIgnoreCase(track.getCategory())
                    || "SALES".equalsIgnoreCase(track.getRubricCode()));
            String key = sales
                    ? "sales"
                    : session.getJobTemplateId() == null ? null : String.valueOf(session.getJobTemplateId());
            if (key != null) {
                byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(session);
            }
        }
        byKey.entrySet().removeIf(entry -> entry.getValue().stream()
                .allMatch(session -> "READY".equals(session.getStatus())));
        if (byKey.isEmpty()) {
            return List.of();
        }

        InterviewTrack salesTrack = trackById.values().stream()
                .filter(track -> "sales".equalsIgnoreCase(track.getCode()))
                .findFirst()
                .orElse(null);

        List<GrowthJobView> result = new ArrayList<>();
        for (Map.Entry<String, List<InterviewSession>> entry : byKey.entrySet()) {
            List<InterviewSession> jobSessions = entry.getValue();
            List<InterviewSession> completed = jobSessions.stream()
                    .filter(session -> "COMPLETED".equals(session.getStatus()))
                    .toList();
            List<Integer> scores = completed.stream()
                    .map(InterviewSession::getTotalScore)
                    .filter(Objects::nonNull)
                    .toList();
            double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            int best = scores.stream().mapToInt(Integer::intValue).max().orElse(0);

            boolean salesGroup = "sales".equals(entry.getKey());
            JobTemplate job = salesGroup ? null
                    : jobById.get(jobSessions.get(0).getJobTemplateId());
            InterviewTrack track = salesGroup ? salesTrack
                    : job == null || job.getTrackId() == null ? null : trackById.get(job.getTrackId());
            result.add(new GrowthJobView(
                    entry.getKey(),
                    salesGroup ? null : jobSessions.get(0).getJobTemplateId(),
                    salesGroup ? "销售岗面试" : job == null ? "未知岗位" : job.getTitle(),
                    salesGroup ? "销售岗位" : job == null ? null : job.getCategory(),
                    track == null ? null : track.getCode(),
                    track == null ? null : track.getName(),
                    jobSessions.size(),
                    completed.size(),
                    Math.round(average * 10.0) / 10.0,
                    best,
                    jobSessions.stream()
                            .map(InterviewSession::getCreatedAt)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(null)));
        }
        result.sort(Comparator.comparing(
                GrowthJobView::lastInterviewAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public List<InterviewHistoryItem> history(Long userId) {
        return history(userId, null, null);
    }

    public List<InterviewHistoryItem> history(Long userId, Long jobTemplateId) {
        return history(userId, jobTemplateId, null);
    }

    public List<InterviewHistoryItem> history(Long userId, Long jobTemplateId, String trackCode) {
        List<InterviewSession> sessions = sessions(userId, jobTemplateId, trackCode);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> jobIds = sessions.stream()
                .map(InterviewSession::getJobTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> jobTitles = jobIds.isEmpty() ? Map.of()
                : jobTemplateMapper.selectBatchIds(jobIds).stream()
                        .collect(Collectors.toMap(JobTemplate::getId, JobTemplate::getTitle, (first, second) -> first));
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        Map<Long, InterviewReport> reports = reportMapper.selectList(
                        new LambdaQueryWrapper<InterviewReport>()
                                .in(InterviewReport::getSessionId, sessionIds))
                .stream()
                .collect(Collectors.toMap(InterviewReport::getSessionId, report -> report, (first, second) -> first));
        return sessions.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(session -> {
                    InterviewReport report = reports.get(session.getId());
                    return new InterviewHistoryItem(
                            session.getId(),
                            jobTitles.getOrDefault(session.getJobTemplateId(), "未知岗位"),
                            session.getMode(),
                            session.getStatus(),
                            session.getTotalScore(),
                            session.getCreatedAt(),
                            report != null && "SUCCESS".equals(report.getStatus()));
                })
                .toList();
    }

    public StatsOverview stats(Long userId) {
        return stats(userId, null, null);
    }

    public StatsOverview stats(Long userId, Long jobTemplateId) {
        return stats(userId, jobTemplateId, null);
    }

    public StatsOverview stats(Long userId, Long jobTemplateId, String trackCode) {
        List<InterviewSession> sessions = sessions(userId, jobTemplateId, trackCode);
        List<InterviewSession> completed = sessions.stream()
                .filter(session -> "COMPLETED".equals(session.getStatus()))
                .toList();
        List<Integer> scores = completed.stream()
                .map(InterviewSession::getTotalScore)
                .filter(Objects::nonNull)
                .toList();
        double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int best = scores.stream().mapToInt(Integer::intValue).max().orElse(0);

        List<ScorePoint> trend = completed.stream()
                .filter(session -> session.getTotalScore() != null)
                .map(session -> new ScorePoint(
                        session.getCreatedAt().toLocalDate().toString(),
                        session.getTotalScore(),
                        jobTitle(session.getJobTemplateId())))
                .toList();

        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        List<InterviewReport> reports = sessionIds.isEmpty() ? List.of()
                : reportMapper.selectList(new LambdaQueryWrapper<InterviewReport>()
                        .in(InterviewReport::getSessionId, sessionIds)
                        .eq(InterviewReport::getStatus, "SUCCESS"));
        Map<Long, InterviewSession> sessionById = sessions.stream()
                .collect(Collectors.toMap(InterviewSession::getId, session -> session, (a, b) -> a));
        Map<Long, InterviewTrack> trackById = new HashMap<>();
        for (InterviewSession session : sessions) {
            if (session.getTrackId() != null) {
                trackById.putIfAbsent(session.getTrackId(), trackMapper.selectById(session.getTrackId()));
            }
        }
        Map<Long, ResumeAssessment> assessmentByResume = new HashMap<>();

        Set<String> keys = new LinkedHashSet<>();
        Map<Long, Map<String, Integer>> dimensionsByReport = new LinkedHashMap<>();
        for (InterviewReport report : reports) {
            Map<String, Integer> dimensions = new LinkedHashMap<>(readMap(report.getDimensionsJson()));
            if (!dimensions.containsKey("resume")) {
                InterviewSession session = sessionById.get(report.getSessionId());
                ResumeAssessment assessment = session == null ? null
                        : resumeAssessment(session.getResumeId(), assessmentByResume);
                if (assessment != null) {
                    dimensions.put("resume", assessment.score());
                }
            }
            dimensionsByReport.put(report.getId(), dimensions);
            keys.addAll(dimensions.keySet());
        }

        Map<String, Integer> dimensionAverages = new LinkedHashMap<>();
        Map<String, String> dimensionLabels = new LinkedHashMap<>();
        for (String key : keys) {
            double avg = dimensionsByReport.values().stream()
                    .filter(map -> map.containsKey(key))
                    .mapToInt(map -> map.get(key))
                    .average().orElse(0);
            dimensionAverages.put(key, (int) Math.round(avg));
            dimensionLabels.put(key, labelFor(key, reports, dimensionsByReport, sessionById, trackById));
        }

        return new StatsOverview(
                sessions.size(),
                completed.size(),
                Math.round(average * 10.0) / 10.0,
                best,
                dimensionAverages,
                dimensionLabels,
                trend);
    }

    private List<InterviewSession> sessions(Long userId, Long jobTemplateId) {
        return sessions(userId, jobTemplateId, null);
    }

    private List<InterviewSession> sessions(Long userId, Long jobTemplateId, String trackCode) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .orderByAsc(InterviewSession::getCreatedAt);
        if (trackCode != null && !trackCode.isBlank()) {
            InterviewTrack track = trackMapper.selectOne(new LambdaQueryWrapper<InterviewTrack>()
                    .eq(InterviewTrack::getCode, trackCode)
                    .last("LIMIT 1"));
            if (track == null) {
                return List.of();
            }
            wrapper.eq(InterviewSession::getTrackId, track.getId());
        } else if (jobTemplateId != null) {
            wrapper.eq(InterviewSession::getJobTemplateId, jobTemplateId);
        }
        return sessionMapper.selectList(wrapper);
    }

    private String labelFor(String key,
                            List<InterviewReport> reports,
                            Map<Long, Map<String, Integer>> dimensionsByReport,
                            Map<Long, InterviewSession> sessionById,
                            Map<Long, InterviewTrack> trackById) {
        for (InterviewReport report : reports) {
            if (!dimensionsByReport.getOrDefault(report.getId(), Map.of()).containsKey(key)) {
                continue;
            }
            InterviewSession session = sessionById.get(report.getSessionId());
            if (session == null || session.getTrackId() == null) {
                continue;
            }
            InterviewTrack track = trackById.get(session.getTrackId());
            RoleProfile profile = roleProfileParser.parse(track);
            if (profile.reportDimensions().containsKey(key)) {
                return profile.dimensionLabel(key);
            }
        }
        return switch (key) {
            case "accuracy" -> "技术准确性";
            case "depth" -> "技术深度";
            case "project" -> "项目表现";
            case "expression" -> "表达逻辑";
            case "match" -> "岗位匹配";
            case "resume" -> "简历评估";
            default -> key;
        };
    }

    private void ensureEvaluations(InterviewSession session,
                                   List<InterviewQuestion> questions,
                                   List<InterviewMessage> messages,
                                   List<AnswerEvaluation> evaluations,
                                   RoleProfile profile) {
        Set<Long> evaluatedMessageIds = evaluations.stream()
                .map(AnswerEvaluation::getCandidateMessageId)
                .collect(Collectors.toSet());
        Map<Long, InterviewMessage> latestCandidate = new LinkedHashMap<>();
        for (InterviewMessage message : messages) {
            if ("CANDIDATE".equals(message.getRole()) && message.getQuestionId() != null) {
                latestCandidate.put(message.getQuestionId(), message);
            }
        }

        for (InterviewQuestion question : questions) {
            if (!"ANSWERED".equals(question.getStatus())) {
                continue;
            }
            InterviewMessage candidate = latestCandidate.get(question.getId());
            if (candidate == null || evaluatedMessageIds.contains(candidate.getId())) {
                continue;
            }

            String promptType = "QUESTION";
            String promptText = question.getContent();
            for (int i = messages.size() - 1; i >= 0; i--) {
                InterviewMessage message = messages.get(i);
                if ("INTERVIEWER".equals(message.getRole())
                        && question.getId().equals(message.getQuestionId())) {
                    if ("FOLLOW_UP".equals(message.getMessageType())) {
                        promptType = "FOLLOW_UP";
                        promptText = message.getContent();
                    }
                    break;
                }
            }

            AnswerEvaluationResult result = answerEvaluator.evaluate(
                    question, promptText, promptType, candidate.getContent(), profile);
            AnswerEvaluation evaluation = new AnswerEvaluation();
            evaluation.setSessionId(session.getId());
            evaluation.setQuestionId(question.getId());
            evaluation.setPromptText(promptText);
            evaluation.setPromptType(promptType);
            evaluation.setCandidateMessageId(candidate.getId());
            evaluation.setCorrectness(result.correctness());
            evaluation.setScore(result.score());
            evaluation.setComment(result.comment());
            evaluation.setMissingPoints(writeJson(result.missingPoints()));
            evaluation.setConfidence(result.confidence());
            evaluationMapper.insert(evaluation);
            evaluations.add(evaluation);
            evaluatedMessageIds.add(candidate.getId());
        }
    }

    private InterviewReportView buildView(InterviewReport report,
                                          InterviewSession session,
                                          List<InterviewQuestion> questions,
                                          List<InterviewMessage> messages,
                                          List<AnswerEvaluation> evaluations) {
        JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
        RoleProfile profile = profile(session);
        ResumeAssessmentView assessmentView = readResumeAssessment(report.getResumeAssessmentJson());
        if (assessmentView == null) {
            assessmentView = ResumeAssessmentView.from(
                    resumeAssessment(session.getResumeId(), new HashMap<>()));
        }
        Map<String, Integer> dimensions = new LinkedHashMap<>(readMap(report.getDimensionsJson()));
        if (assessmentView != null && !dimensions.containsKey("resume")) {
            dimensions.put("resume", assessmentView.score());
        }
        Map<String, String> labels = new LinkedHashMap<>();
        dimensions.keySet().forEach(key -> labels.put(
                key, "resume".equals(key) ? "简历评估" : profile.dimensionLabel(key)));
        return new InterviewReportView(
                report.getId(),
                session.getId(),
                job == null ? "未知岗位" : job.getTitle(),
                session.getMode(),
                report.getOverallScore(),
                dimensions,
                labels,
                assessmentView,
                session.getGenerationMode(),
                report.getSummary(),
                readList(report.getStrengthsJson()),
                readList(report.getWeaknessesJson()),
                readList(report.getSuggestionsJson()),
                report.getStatus(),
                report.getCreatedAt(),
                buildQuestionReviews(questions, messages, evaluations));
    }

    private InterviewReportResult withResumeDimension(InterviewReportResult result, ResumeAssessment assessment) {
        if (assessment == null) {
            return result;
        }
        Map<String, Integer> dimensions = new LinkedHashMap<>(result.dimensions());
        dimensions.put("resume", assessment.score());
        return new InterviewReportResult(
                dimensions,
                result.overallScore(),
                result.summary(),
                result.strengths(),
                result.weaknesses(),
                result.suggestions());
    }

    private ResumeAssessment evaluateResume(InterviewSession session) {
        if (session.getResumeId() == null) {
            return null;
        }
        Resume resume = resumeMapper.selectById(session.getResumeId());
        if (resume == null) {
            return null;
        }
        try {
            JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
            return resumeEvaluator.evaluate(resume, job, profile(session));
        } catch (Exception ex) {
            log.warn("AI resume assessment failed, fallback to heuristic: sessionId={}",
                    session.getId(), ex);
            return resumeAssessmentService.assess(resume);
        }
    }

    private ResumeAssessmentView readResumeAssessment(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return null;
            }
            int education = root.path("educationScore").asInt(0);
            int skills = root.path("skillsScore").asInt(50);
            int layout = root.path("layoutScore").asInt(50);
            int experience;
            if (root.hasNonNull("experienceScore")) {
                experience = root.path("experienceScore").asInt(0);
            } else {
                int internship = root.path("internshipScore").asInt(0);
                int work = root.path("workExperienceScore").asInt(0);
                experience = (int) Math.round((internship * 30 + work * 25) / 55.0);
            }
            boolean completeNewFormat = root.hasNonNull("skillsScore")
                    && root.hasNonNull("experienceScore")
                    && root.hasNonNull("layoutScore");
            int score = completeNewFormat
                    ? root.path("score").asInt(0)
                    : (int) Math.round(
                            education * 25 / 100.0
                                    + skills * 25 / 100.0
                                    + experience * 40 / 100.0
                                    + layout * 10 / 100.0);
            List<ResumeIssueView> issues = new ArrayList<>();
            JsonNode issueNodes = root.path("issues");
            if (issueNodes.isArray()) {
                for (JsonNode item : issueNodes) {
                    String title = item.path("title").asText("");
                    if (title.isBlank()) {
                        continue;
                    }
                    ResumeIssueView issue = new ResumeIssueView(
                            item.path("category").asText("简历内容"),
                            item.path("severity").asText("MEDIUM"),
                            title,
                            item.path("detail").asText(""),
                            item.path("suggestion").asText(""),
                            item.path("evidence").asText(""));
                    if (ResumeIssueFilter.isInternshipAvailabilityIssue(
                            issue.category(), issue.title(), issue.detail(),
                            issue.suggestion(), issue.evidence())) {
                        continue;
                    }
                    issues.add(issue);
                    if (issues.size() >= 10) {
                        break;
                    }
                }
            }
            return new ResumeAssessmentView(
                    score,
                    education,
                    skills,
                    experience,
                    layout,
                    ResumeAssessmentService.weightMap(),
                    root.path("aiGenerated").asBoolean(false),
                    root.path("comment").asText(""),
                    issues);
        } catch (Exception ex) {
            return null;
        }
    }

    private ResumeAssessment resumeAssessment(Long resumeId, Map<Long, ResumeAssessment> cache) {
        if (resumeId == null) {
            return null;
        }
        if (cache.containsKey(resumeId)) {
            return cache.get(resumeId);
        }
        Resume resume = resumeMapper.selectById(resumeId);
        ResumeAssessment assessment = resume == null ? null : resumeAssessmentService.assess(resume);
        cache.put(resumeId, assessment);
        return assessment;
    }

    private List<QuestionReviewView> buildQuestionReviews(List<InterviewQuestion> questions,
                                                          List<InterviewMessage> messages,
                                                          List<AnswerEvaluation> evaluations) {
        Map<Long, InterviewMessage> latestCandidate = new LinkedHashMap<>();
        for (InterviewMessage message : messages) {
            if ("CANDIDATE".equals(message.getRole()) && message.getQuestionId() != null) {
                latestCandidate.put(message.getQuestionId(), message);
            }
        }
        Map<Long, AnswerEvaluation> evaluationByMessage = evaluations.stream()
                .collect(Collectors.toMap(
                        AnswerEvaluation::getCandidateMessageId,
                        evaluation -> evaluation,
                        (first, second) -> second));

        List<QuestionReviewView> reviews = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (!"ANSWERED".equals(question.getStatus())) {
                continue;
            }
            InterviewMessage candidate = latestCandidate.get(question.getId());
            if (candidate == null) {
                continue;
            }
            AnswerEvaluation evaluation = evaluationByMessage.get(candidate.getId());
            if (evaluation == null) {
                continue;
            }
            reviews.add(new QuestionReviewView(
                    question.getId(),
                    question.getSeq(),
                    question.getType(),
                    question.getContent(),
                    candidate.getContent(),
                    evaluation.getPromptType(),
                    evaluation.getPromptText(),
                    evaluation.getCorrectness(),
                    evaluation.getScore(),
                    evaluation.getComment(),
                    readList(evaluation.getMissingPoints())));
        }
        return reviews;
    }

    private RoleProfile profile(InterviewSession session) {
        if (session == null || session.getTrackId() == null) {
            return RoleProfile.tech();
        }
        return roleProfileParser.parse(trackMapper.selectById(session.getTrackId()));
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private String jobTitle(Long jobTemplateId) {
        JobTemplate job = jobTemplateMapper.selectById(jobTemplateId);
        return job == null ? "未知岗位" : job.getTitle();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报告序列化失败");
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Integer> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
