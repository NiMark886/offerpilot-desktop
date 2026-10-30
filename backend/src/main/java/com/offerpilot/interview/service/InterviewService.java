package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.ai.service.AiAvailabilityChecker;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.BlueprintQuestion;
import com.offerpilot.interview.domain.BlueprintRequest;
import com.offerpilot.interview.domain.BlueprintSection;
import com.offerpilot.interview.domain.InterviewBlueprint;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.CreateCustomInterviewRequest;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.QuestionView;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.questionbank.service.QuestionBankRetriever;
import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import com.offerpilot.track.service.InterviewTrackService;
import com.offerpilot.track.service.RoleProfileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewBlueprintGenerator blueprintGenerator;
    private final SalesBankBlueprintGenerator salesBankBlueprintGenerator;
    private final InterviewBlueprintValidator blueprintValidator;
    private final QuestionBankRetriever questionBankRetriever;
    private final InterviewTrackService trackService;
    private final RoleProfileParser roleProfileParser;
    private final AiAvailabilityChecker aiAvailabilityChecker;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public InterviewService(InterviewSessionMapper sessionMapper,
                            InterviewQuestionMapper questionMapper,
                            JobTemplateMapper jobTemplateMapper,
                            ResumeMapper resumeMapper,
                            InterviewBlueprintGenerator blueprintGenerator,
                            SalesBankBlueprintGenerator salesBankBlueprintGenerator,
                            InterviewBlueprintValidator blueprintValidator,
                            QuestionBankRetriever questionBankRetriever,
                            InterviewTrackService trackService,
                            RoleProfileParser roleProfileParser,
                            AiAvailabilityChecker aiAvailabilityChecker,
                            QuotaService quotaService,
                            ObjectMapper objectMapper,
                            TransactionTemplate transactionTemplate) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.resumeMapper = resumeMapper;
        this.blueprintGenerator = blueprintGenerator;
        this.salesBankBlueprintGenerator = salesBankBlueprintGenerator;
        this.blueprintValidator = blueprintValidator;
        this.questionBankRetriever = questionBankRetriever;
        this.trackService = trackService;
        this.roleProfileParser = roleProfileParser;
        this.aiAvailabilityChecker = aiAvailabilityChecker;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public InterviewSessionView create(Long userId, CreateInterviewRequest request) {
        JobTemplate job = jobTemplateMapper.selectById(request.jobTemplateId());
        if (job == null || !"ACTIVE".equals(job.getStatus())
                || (job.getUserId() != null && !job.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位模板不存在");
        }

        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }

        InterviewTrack track = trackService.resolveForJob(job);
        Long trackId = track == null ? null : track.getId();
        RoleProfile roleProfile = roleProfileParser.parse(track);
        ResumeProfile profile = readProfile(resume);
        List<String> tags = new ArrayList<>();
        tags.add(job.getCategory());
        if (roleProfile.isGeneral()) {
            tags.addAll(roleProfile.competencyNames());
        }
        if (profile != null && profile.skills() != null) {
            tags.addAll(profile.skills());
        }
        var retrieved = questionBankRetriever.retrieve(
                job.getJdText(), tags, roleProfile.isGeneral() ? 50 : 8, trackId);
        quotaService.checkAvailable(userId);

        BlueprintRequest blueprintRequest = new BlueprintRequest(
                job, track, roleProfile, profile, request.difficulty(), request.durationMinutes(),
                request.persona(), retrieved);
        InterviewBlueprint blueprint;
        String generationMode;
        if (roleProfile.isGeneral()) {
            int expectedCount = expectedCount(request.durationMinutes());
            if (aiAvailabilityChecker.isAvailable(userId)) {
                try {
                    blueprint = blueprintGenerator.generate(blueprintRequest);
                    generationMode = "AI";
                } catch (Exception ex) {
                    log.warn("Sales AI blueprint failed, fallback to bank: {}", ex.getMessage());
                    blueprint = salesBankBlueprintGenerator.generateSales(blueprintRequest);
                    generationMode = "BANK";
                }
            } else {
                blueprint = salesBankBlueprintGenerator.generateSales(blueprintRequest);
                generationMode = "BANK";
            }
            blueprintValidator.validate(blueprint, roleProfile, expectedCount);
        } else {
            blueprint = blueprintGenerator.generate(blueprintRequest);
            generationMode = "AI";
        }
        InterviewBlueprint finalBlueprint = blueprint;
        String finalGenerationMode = generationMode;

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            quotaService.checkAndConsumeSession(userId);

            InterviewSession session = new InterviewSession();
            session.setUserId(userId);
            session.setJobTemplateId(job.getId());
            session.setTrackId(trackId);
            session.setResumeId(resume.getId());
            session.setDifficulty(request.difficulty());
            session.setDurationMinutes(request.durationMinutes());
            session.setPersona(request.persona());
            String mode = "PRACTICE".equalsIgnoreCase(request.mode()) ? "PRACTICE" : "INTERVIEW";
            session.setMode(mode);
            session.setStatus("READY");
            session.setGenerationMode(finalGenerationMode);
            session.setBlueprintJson(writeJson(finalBlueprint));
            sessionMapper.insert(session);

            List<QuestionView> questions = saveQuestions(session.getId(), finalBlueprint);
            return toView(session, job.getTitle(), questions);
        }));
    }

    public InterviewSessionView createCustom(Long userId, CreateCustomInterviewRequest request) {
        Resume resume = resumeMapper.selectById(request.resumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }

        InterviewTrack track = trackService.requireByCode("sales");
        RoleProfile roleProfile = roleProfileParser.parse(track);
        quotaService.checkAvailable(userId);

        List<String> tags = new ArrayList<>(roleProfile.competencyNames());
        String query = "销售岗面试 " + request.jdText();
        var retrieved = questionBankRetriever.retrieve(query, tags, 50, track.getId());

        JobTemplate job = new JobTemplate();
        job.setTitle("销售岗面试");
        job.setLevel("通用");
        job.setCategory("销售岗位");
        job.setJdText(request.jdText());
        job.setConfigJson("{}");
        job.setTrackId(track.getId());
        job.setStatus("ACTIVE");
        job.setSortOrder(0);
        job.setUserId(userId);
        job.setSource("USER");

        BlueprintRequest blueprintRequest = new BlueprintRequest(
                job, track, roleProfile, readProfile(resume), request.difficulty(),
                request.durationMinutes(), request.persona(), retrieved);
        int expectedCount = expectedCount(request.durationMinutes());

        InterviewBlueprint blueprint;
        String generationMode;
        if (aiAvailabilityChecker.isAvailable(userId)) {
            try {
                blueprint = blueprintGenerator.generate(blueprintRequest);
                generationMode = "AI";
            } catch (Exception ex) {
                log.warn("Sales AI blueprint failed, fallback to bank: {}", ex.getMessage());
                blueprint = salesBankBlueprintGenerator.generateSales(blueprintRequest);
                generationMode = "BANK";
            }
        } else {
            blueprint = salesBankBlueprintGenerator.generateSales(blueprintRequest);
            generationMode = "BANK";
        }
        blueprintValidator.validate(blueprint, roleProfile, expectedCount);

        String finalMode = generationMode;
        InterviewBlueprint finalBlueprint = blueprint;
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            quotaService.checkAndConsumeSession(userId);
            jobTemplateMapper.insert(job);

            InterviewSession session = new InterviewSession();
            session.setUserId(userId);
            session.setJobTemplateId(job.getId());
            session.setTrackId(track.getId());
            session.setResumeId(resume.getId());
            session.setDifficulty(request.difficulty());
            session.setDurationMinutes(request.durationMinutes());
            session.setPersona(request.persona());
            session.setMode("PRACTICE".equalsIgnoreCase(request.mode()) ? "PRACTICE" : "INTERVIEW");
            session.setStatus("READY");
            session.setGenerationMode(finalMode);
            session.setBlueprintJson(writeJson(finalBlueprint));
            sessionMapper.insert(session);

            List<QuestionView> questions = saveQuestions(session.getId(), finalBlueprint);
            return toView(session, job.getTitle(), questions);
        }));
    }

    private int expectedCount(int durationMinutes) {
        if (durationMinutes <= 20) {
            return 6;
        }
        if (durationMinutes <= 35) {
            return 8;
        }
        return 10;
    }

    public InterviewSessionView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
        List<QuestionView> questions = questionMapper.selectList(
                        new LambdaQueryWrapper<InterviewQuestion>()
                                .eq(InterviewQuestion::getSessionId, sessionId)
                                .orderByAsc(InterviewQuestion::getSeq))
                .stream()
                .map(this::toQuestionView)
                .toList();
        return toView(session, job == null ? "未知岗位" : job.getTitle(), questions);
    }

    public List<InterviewSessionView> list(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
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
        return sessions.stream()
                .map(session -> toView(session,
                        jobTitles.getOrDefault(session.getJobTemplateId(), "未知岗位"), List.of()))
                .toList();
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private List<QuestionView> saveQuestions(Long sessionId, InterviewBlueprint blueprint) {
        List<QuestionView> result = new ArrayList<>();
        int seq = 1;
        for (BlueprintSection section : blueprint.sections()) {
            for (BlueprintQuestion question : section.questions()) {
                InterviewQuestion entity = new InterviewQuestion();
                entity.setSessionId(sessionId);
                entity.setSeq(seq++);
                entity.setType(section.type());
                entity.setDifficulty(question.difficulty());
                entity.setContent(question.text());
                entity.setKnowledgeTags(writeJson(question.knowledgeTags()));
                entity.setReferencePoints(writeJson(question.referencePoints()));
                entity.setFollowUpHints(writeJson(question.followUpHints()));
                entity.setStatus("PENDING");
                questionMapper.insert(entity);
                result.add(toQuestionView(entity));
            }
        }
        return result;
    }

    private QuestionView toQuestionView(InterviewQuestion question) {
        return new QuestionView(
                question.getId(),
                question.getSeq(),
                question.getType(),
                question.getDifficulty(),
                question.getContent(),
                readList(question.getKnowledgeTags()),
                readList(question.getReferencePoints()),
                question.getStatus(),
                question.getFollowUpCount(),
                Math.min(2, readList(question.getFollowUpHints()).size()),
                question.getHintCount());
    }

    private InterviewSessionView toView(InterviewSession session, String jobTitle,
                                        List<QuestionView> questions) {
        return new InterviewSessionView(
                session.getId(),
                session.getJobTemplateId(),
                jobTitle,
                session.getResumeId(),
                session.getDifficulty(),
                session.getDurationMinutes(),
                session.getPersona(),
                session.getMode(),
                session.getStatus(),
                session.getGenerationMode(),
                session.getCurrentQuestionId(),
                session.getCreatedAt(),
                questions);
    }

    private ResumeProfile readProfile(Resume resume) {
        if (resume.getParsedJson() == null) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeProfile.class);
        } catch (Exception ex) {
            return new ResumeProfile(null, null, null, 0, List.of(), List.of(), null, "");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "面试蓝图序列化失败");
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
}
