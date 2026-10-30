package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.interview.web.dto.AnswerEvaluationView;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.AnswerStreamCompletion;
import com.offerpilot.interview.web.dto.AnswerStreamContext;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import com.offerpilot.track.domain.RoleProfile;
import com.offerpilot.track.service.InterviewTrackService;
import com.offerpilot.track.service.RoleProfileParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewConversationService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewService interviewService;
    private final InterviewerReplyGenerator replyGenerator;
    private final AnswerEvaluator answerEvaluator;
    private final AnswerEvaluationMapper evaluationMapper;
    private final InterviewTrackService trackService;
    private final RoleProfileParser roleProfileParser;
    private final ObjectMapper objectMapper;

    public InterviewConversationService(InterviewSessionMapper sessionMapper,
                                        InterviewQuestionMapper questionMapper,
                                        InterviewMessageMapper messageMapper,
                                        InterviewService interviewService,
                                        InterviewerReplyGenerator replyGenerator,
                                        AnswerEvaluator answerEvaluator,
                                        AnswerEvaluationMapper evaluationMapper,
                                        InterviewTrackService trackService,
                                        RoleProfileParser roleProfileParser,
                                        ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.interviewService = interviewService;
        this.replyGenerator = replyGenerator;
        this.answerEvaluator = answerEvaluator;
        this.evaluationMapper = evaluationMapper;
        this.trackService = trackService;
        this.roleProfileParser = roleProfileParser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionView start(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        RoleProfile profile = profile(session);
        if ("COMPLETED".equals(session.getStatus())) {
            return interviewService.get(userId, sessionId);
        }
        if ("READY".equals(session.getStatus())) {
            LocalDateTime startedAt = LocalDateTime.now();
            int started = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .eq(InterviewSession::getUserId, userId)
                    .eq(InterviewSession::getStatus, "READY")
                    .set(InterviewSession::getStatus, "IN_PROGRESS")
                    .set(InterviewSession::getStartedAt, startedAt));
            if (started == 1) {
                session.setStatus("IN_PROGRESS");
                session.setStartedAt(startedAt);
                InterviewQuestion first = nextPending(sessionId);
                if (first == null) {
                    complete(session);
                    addMessage(sessionId, null, "INTERVIEWER", "CLOSING",
                            replyGenerator.generate(null, ReplyType.CLOSING, null, List.of(), profile));
                    sessionMapper.updateById(session);
                    return interviewService.get(userId, sessionId);
                }
                ask(session, first);
                addMessage(sessionId, first.getId(), "INTERVIEWER", "OPENING",
                        replyGenerator.generate(first, ReplyType.OPENING, null, List.of(), profile));
                sessionMapper.updateById(session);
            }
        }
        return interviewService.get(userId, sessionId);
    }

    public List<MessageView> messages(Long userId, Long sessionId) {
        loadOwned(userId, sessionId);
        return history(sessionId).stream()
                .map(MessageView::from)
                .toList();
    }

    public List<AnswerEvaluationView> evaluations(Long userId, Long sessionId) {
        loadOwned(userId, sessionId);
        return evaluationMapper.selectList(new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId))
                .stream()
                .map(evaluation -> AnswerEvaluationView.from(evaluation,
                        readList(evaluation.getMissingPoints())))
                .toList();
    }

    @Transactional
    public AnswerResult answer(Long userId, Long sessionId, AnswerRequest request) {
        InterviewSession session = loadOwned(userId, sessionId);
        RoleProfile profile = profile(session);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }

        InterviewQuestion question = questionMapper.selectById(request.questionId());
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }
        int claimed = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, question.getId())
                .eq(InterviewQuestion::getSessionId, sessionId)
                .eq(InterviewQuestion::getStatus, "ASKED")
                .set(InterviewQuestion::getStatus, "ANSWERING"));
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }
        question.setStatus("ANSWERING");

        InterviewMessage candidate = addMessage(sessionId, question.getId(), "CANDIDATE", "ANSWER", request.content());
        InterviewMessage prompt = lastInterviewerMessage(sessionId, question.getId());
        String promptType = prompt != null && "FOLLOW_UP".equals(prompt.getMessageType())
                ? "FOLLOW_UP" : "QUESTION";
        String promptText = "FOLLOW_UP".equals(promptType) && prompt != null
                ? prompt.getContent() : question.getContent();
        AnswerEvaluationView evaluation = evaluateIfPractice(
                session, question, candidate, promptText, promptType, request.content(), profile);
        List<InterviewMessage> historyMessages = history(sessionId);

        if (shouldFollowUp(question, request.content())) {
            int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
            question.setFollowUpCount(count + 1);
            questionMapper.updateById(question);
            questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getId, question.getId())
                    .eq(InterviewQuestion::getStatus, "ANSWERING")
                    .set(InterviewQuestion::getStatus, "ASKED"));
            String reply = replyGenerator.generate(question, ReplyType.FOLLOW_UP, request.content(), historyMessages, profile);
            InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "FOLLOW_UP", reply);
            return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                    "FOLLOW_UP", reply, session.getStatus(), question.getId(), false, evaluation);
        }

        int answered = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, question.getId())
                .eq(InterviewQuestion::getStatus, "ANSWERING")
                .set(InterviewQuestion::getStatus, "ANSWERED")
                .set(InterviewQuestion::getAnsweredAt, LocalDateTime.now()));
        if (answered == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "题目状态已发生变化");
        }

        InterviewQuestion next = nextPending(sessionId);
        if (next != null) {
            ask(session, next);
            String reply = replyGenerator.generate(next, ReplyType.QUESTION, request.content(), historyMessages, profile);
            InterviewMessage interviewer = addMessage(sessionId, next.getId(), "INTERVIEWER", "QUESTION", reply);
            sessionMapper.updateById(session);
            return new AnswerResult(candidate.getId(), interviewer.getId(), next.getId(),
                    "QUESTION", reply, session.getStatus(), next.getId(), false, evaluation);
        }

        complete(session);
        sessionMapper.updateById(session);
        String reply = replyGenerator.generate(question, ReplyType.CLOSING, request.content(), historyMessages, profile);
        InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "CLOSING", reply);
        return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                "CLOSING", reply, "COMPLETED", null, true, evaluation);
    }

    @Transactional
    public AnswerStreamContext beginAnswerStream(Long userId, Long sessionId, AnswerRequest request) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }

        InterviewQuestion question = questionMapper.selectById(request.questionId());
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }
        int claimed = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, question.getId())
                .eq(InterviewQuestion::getSessionId, sessionId)
                .eq(InterviewQuestion::getStatus, "ASKED")
                .set(InterviewQuestion::getStatus, "ANSWERING"));
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }
        question.setStatus("ANSWERING");

        InterviewMessage candidate = addMessage(sessionId, question.getId(), "CANDIDATE", "ANSWER", request.content());
        InterviewMessage prompt = lastInterviewerMessage(sessionId, question.getId());
        String promptType = prompt != null && "FOLLOW_UP".equals(prompt.getMessageType())
                ? "FOLLOW_UP" : "QUESTION";
        String promptText = "FOLLOW_UP".equals(promptType) && prompt != null
                ? prompt.getContent() : question.getContent();

        String replyType;
        Long nextQuestionId = null;
        if (shouldFollowUp(question, request.content())) {
            int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
            question.setFollowUpCount(count + 1);
            questionMapper.updateById(question);
            replyType = "FOLLOW_UP";
        } else {
            InterviewQuestion next = nextPending(sessionId);
            if (next != null) {
                replyType = "QUESTION";
                nextQuestionId = next.getId();
            } else {
                replyType = "CLOSING";
            }
        }

        Long interviewerQuestionId = "QUESTION".equals(replyType) && nextQuestionId != null
                ? nextQuestionId : question.getId();
        InterviewMessage interviewer = addMessage(sessionId, interviewerQuestionId, "INTERVIEWER", replyType, "");
        return new AnswerStreamContext(sessionId, question.getId(), candidate.getId(), interviewer.getId(),
                replyType, nextQuestionId, "PRACTICE".equals(session.getMode()),
                promptText, promptType, request.content());
    }

    public Flux<String> streamReply(AnswerStreamContext context) {
        InterviewQuestion question = questionMapper.selectById(context.replyQuestionId());
        if (question == null) {
            return Flux.error(new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        }
        InterviewSession session = sessionMapper.selectById(context.sessionId());
        RoleProfile profile = profile(session);
        ReplyType type = ReplyType.valueOf(context.replyType());
        List<InterviewMessage> history = history(context.sessionId()).stream()
                .filter(message -> !context.interviewerMessageId().equals(message.getId()))
                .toList();
        return replyGenerator.stream(question, type, context.answer(), history, profile);
    }

    public AnswerEvaluationResult evaluateStreamAnswer(Long userId, AnswerStreamContext context) {
        if (!context.practice()) {
            return null;
        }
        InterviewSession session = loadOwned(userId, context.sessionId());
        RoleProfile profile = profile(session);
        InterviewQuestion question = questionMapper.selectById(context.questionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return answerEvaluator.evaluate(question, context.promptText(), context.promptType(),
                context.answer(), profile);
    }

    @Transactional
    public AnswerStreamCompletion completeAnswerStream(Long userId, AnswerStreamContext context,
                                                        String replyText,
                                                        AnswerEvaluationResult evaluationResult) {
        InterviewSession session = loadOwned(userId, context.sessionId());
        InterviewQuestion question = questionMapper.selectById(context.questionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        if (!"ANSWERING".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "题目状态已发生变化");
        }
        InterviewMessage interviewer = messageMapper.selectById(context.interviewerMessageId());
        if (interviewer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试官消息不存在");
        }
        interviewer.setContent(replyText);
        messageMapper.updateById(interviewer);

        AnswerEvaluationView evaluation = null;
        if (evaluationResult != null) {
            InterviewMessage candidate = messageMapper.selectById(context.candidateMessageId());
            if (candidate != null) {
                evaluation = persistEvaluation(session, question, candidate,
                        context.promptText(), context.promptType(), evaluationResult);
            }
        }

        boolean completed = false;
        if ("FOLLOW_UP".equals(context.replyType())) {
            int reset = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getId, question.getId())
                    .eq(InterviewQuestion::getStatus, "ANSWERING")
                    .set(InterviewQuestion::getStatus, "ASKED"));
            if (reset == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "题目状态已发生变化");
            }
        } else {
            int answered = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getId, question.getId())
                    .eq(InterviewQuestion::getStatus, "ANSWERING")
                    .set(InterviewQuestion::getStatus, "ANSWERED")
                    .set(InterviewQuestion::getAnsweredAt, LocalDateTime.now()));
            if (answered == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "题目状态已发生变化");
            }

            if (context.nextQuestionId() != null) {
                InterviewQuestion next = questionMapper.selectById(context.nextQuestionId());
                if (next == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "下一题状态已发生变化");
                }
                ask(session, next);
            } else {
                complete(session);
                completed = true;
            }
        }
        sessionMapper.updateById(session);
        return new AnswerStreamCompletion(session.getStatus(), session.getCurrentQuestionId(),
                completed, evaluation);
    }

    @Transactional
    public HintResult hint(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus()) || session.getCurrentQuestionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前没有可提示的题目");
        }
        InterviewQuestion question = questionMapper.selectById(session.getCurrentQuestionId());
        if (question == null || !"ASKED".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前没有可提示的题目");
        }
        int count = question.getHintCount() == null ? 0 : question.getHintCount();
        if (count >= 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "本题已使用过提示");
        }
        question.setHintCount(count + 1);
        questionMapper.updateById(question);

        List<String> points = readList(question.getReferencePoints());
        String hint = points.isEmpty() ? "先讲清楚背景、你的职责和最终结果。" : "可以从「" + points.get(0) + "」入手。";
        InterviewMessage message = addMessage(sessionId, question.getId(), "SYSTEM", "HINT", "提示：" + hint);
        return new HintResult(message.getId(), hint);
    }

    @Transactional
    public InterviewSessionView skip(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        RoleProfile profile = profile(session);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }
        InterviewQuestion current = session.getCurrentQuestionId() == null
                ? null : questionMapper.selectById(session.getCurrentQuestionId());
        if (current != null && "ASKED".equals(current.getStatus())) {
            current.setStatus("SKIPPED");
            current.setAnsweredAt(LocalDateTime.now());
            questionMapper.updateById(current);
        }

        InterviewQuestion next = nextPending(sessionId);
        if (next != null) {
            ask(session, next);
            addMessage(sessionId, next.getId(), "INTERVIEWER", "QUESTION",
                    replyGenerator.generate(next, ReplyType.QUESTION, null, history(sessionId), profile));
            sessionMapper.updateById(session);
            return interviewService.get(userId, sessionId);
        }

        complete(session);
        sessionMapper.updateById(session);
        addMessage(sessionId, current == null ? null : current.getId(), "INTERVIEWER", "CLOSING",
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId), profile));
        return interviewService.get(userId, sessionId);
    }

    @Transactional
    public InterviewSessionView finish(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        RoleProfile profile = profile(session);
        if ("COMPLETED".equals(session.getStatus())) {
            return interviewService.get(userId, sessionId);
        }
        InterviewQuestion current = session.getCurrentQuestionId() == null
                ? null : questionMapper.selectById(session.getCurrentQuestionId());
        if (current != null && "ASKED".equals(current.getStatus())) {
            current.setStatus("SKIPPED");
            current.setAnsweredAt(LocalDateTime.now());
            questionMapper.updateById(current);
        }
        complete(session);
        sessionMapper.updateById(session);
        addMessage(sessionId, current == null ? null : current.getId(), "INTERVIEWER", "CLOSING",
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId), profile));
        return interviewService.get(userId, sessionId);
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试不存在");
        }
        return session;
    }

    private List<InterviewMessage> history(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getId));
    }

    private InterviewMessage lastInterviewerMessage(Long sessionId, Long questionId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getQuestionId, questionId)
                .eq(InterviewMessage::getRole, "INTERVIEWER")
                .orderByDesc(InterviewMessage::getId)
                .last("LIMIT 1"));
    }

    private InterviewQuestion nextPending(Long sessionId) {
        return questionMapper.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .eq(InterviewQuestion::getStatus, "PENDING")
                .orderByAsc(InterviewQuestion::getSeq)
                .last("LIMIT 1"));
    }

    private void ask(InterviewSession session, InterviewQuestion question) {
        LocalDateTime askedAt = LocalDateTime.now();
        int updated = questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, question.getId())
                .eq(InterviewQuestion::getStatus, "PENDING")
                .set(InterviewQuestion::getStatus, "ASKED")
                .set(InterviewQuestion::getAskedAt, askedAt));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "题目状态已发生变化");
        }
        question.setStatus("ASKED");
        question.setAskedAt(askedAt);
        session.setCurrentQuestionId(question.getId());
    }

    private void complete(InterviewSession session) {
        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        session.setCurrentQuestionId(null);
    }

    private boolean shouldFollowUp(InterviewQuestion question, String answer) {
        List<String> hints = readList(question.getFollowUpHints());
        if (hints.isEmpty()) {
            return false;
        }
        int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
        int maxFollowUps = Math.min(2, hints.size());
        if (count >= maxFollowUps) {
            return false;
        }
        String text = answer == null ? "" : answer.trim();
        return text.length() < 60
                || text.contains("不知道")
                || text.contains("不太清楚")
                || text.contains("没做过")
                || text.contains("大概")
                || text.contains("可能");
    }

    private InterviewMessage addMessage(Long sessionId, Long questionId, String role,
                                        String messageType, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setQuestionId(questionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
    }

    private AnswerEvaluationView evaluateIfPractice(InterviewSession session,
                                                    InterviewQuestion question,
                                                    InterviewMessage candidate,
                                                    String promptText,
                                                    String promptType,
                                                    String answer,
                                                    RoleProfile profile) {
        if (!"PRACTICE".equals(session.getMode())) {
            return null;
        }
        AnswerEvaluationResult result = answerEvaluator.evaluate(question, promptText, promptType, answer, profile);
        return persistEvaluation(session, question, candidate, promptText, promptType, result);
    }

    private RoleProfile profile(InterviewSession session) {
        if (session == null || session.getTrackId() == null) {
            return RoleProfile.tech();
        }
        return roleProfileParser.parse(trackService.require(session.getTrackId()));
    }

    private AnswerEvaluationView persistEvaluation(InterviewSession session,
                                                   InterviewQuestion question,
                                                   InterviewMessage candidate,
                                                   String promptText,
                                                   String promptType,
                                                   AnswerEvaluationResult result) {
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
        return AnswerEvaluationView.from(evaluation, result.missingPoints());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "评估结果序列化失败");
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
