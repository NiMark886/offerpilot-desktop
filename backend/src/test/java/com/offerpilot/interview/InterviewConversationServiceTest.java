package com.offerpilot.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.service.InterviewConversationService;
import com.offerpilot.interview.service.InterviewService;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.AnswerStreamContext;
import com.offerpilot.interview.web.dto.CreateInterviewRequest;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.job.mapper.JobTemplateMapper;
import com.offerpilot.resume.domain.ProjectExperience;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterviewConversationServiceTest {

    @Autowired
    private InterviewConversationService conversationService;

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobTemplateMapper jobTemplateMapper;

    @Autowired
    private InterviewQuestionMapper questionMapper;

    @Autowired
    private InterviewMessageMapper messageMapper;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runsConversationStateMachine() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "INTERVIEW"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        assertThat(started.status()).isEqualTo("IN_PROGRESS");
        assertThat(started.currentQuestionId()).isNotNull();
        assertThat(conversationService.messages(user.getId(), session.id())).hasSize(1);

        Long firstQuestionId = started.currentQuestionId();
        AnswerResult followUp = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(firstQuestionId, "不知道"));
        assertThat(followUp.replyType()).isEqualTo("FOLLOW_UP");
        assertThat(followUp.replyText()).isNotBlank();

        AnswerResult next = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(firstQuestionId,
                        "我会先讲清楚 HashMap 的数组、链表和红黑树结构，再说明扩容时容量翻倍、元素重新分布的过程，以及负载因子和树化阈值。然后结合一次线上扩容导致的性能抖动，说明如何通过预设容量和监控 GC 来优化。"));
        assertThat(next.replyType()).isIn("QUESTION", "CLOSING");

        var hint = conversationService.hint(user.getId(), session.id());
        assertThat(hint.hint()).isNotBlank();
        assertThatThrownBy(() -> conversationService.hint(user.getId(), session.id()))
                .isInstanceOf(BusinessException.class);

        InterviewSessionView skipped = conversationService.skip(user.getId(), session.id());
        assertThat(skipped.status()).isIn("IN_PROGRESS", "COMPLETED");

        InterviewSessionView finished = conversationService.finish(user.getId(), session.id());
        assertThat(finished.status()).isEqualTo("COMPLETED");
    }

    @Test
    void followUpEvaluationDoesNotReuseOriginalQuestionReferencePoints() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "PRACTICE"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        AnswerResult first = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(started.currentQuestionId(), "不知道"));
        assertThat(first.replyType()).isEqualTo("FOLLOW_UP");

        AnswerResult second = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(started.currentQuestionId(), "会重新分布到新的位置"));

        assertThat(second.evaluation()).isNotNull();
        assertThat(second.evaluation().promptType()).isEqualTo("FOLLOW_UP");
        assertThat(second.evaluation().promptText()).isEqualTo(first.replyText());
        assertThat(second.evaluation().missingPoints())
                .doesNotContain("链表转红黑树阈值 8");
    }

    @Test
    void limitsFollowUpsToAvailableHints() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "INTERVIEW"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        AnswerResult first = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(started.currentQuestionId(), "不知道"));
        assertThat(first.replyType()).isEqualTo("FOLLOW_UP");

        AnswerResult second = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(started.currentQuestionId(), "不知道"));
        assertThat(second.replyType()).isIn("QUESTION", "CLOSING");
    }

    @Test
    void streamingNextQuestionUsesNextQuestionContext() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "PRACTICE"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        Long currentQuestionId = started.currentQuestionId();
        InterviewQuestion current = questionMapper.selectById(currentQuestionId);
        current.setFollowUpHints("[\"能再展开讲讲这道题的边界条件吗？\"]");
        questionMapper.updateById(current);

        InterviewSessionView reloaded = interviewService.get(user.getId(), session.id());
        assertThat(reloaded.questions().stream()
                .filter(question -> question.id().equals(currentQuestionId))
                .findFirst()
                .orElseThrow()
                .maxFollowUps()).isEqualTo(1);

        AnswerStreamContext first = conversationService.beginAnswerStream(user.getId(), session.id(),
                new AnswerRequest(currentQuestionId, "不知道"));
        assertThat(first.replyType()).isEqualTo("FOLLOW_UP");
        conversationService.completeAnswerStream(user.getId(), first, "请再展开讲讲", null);

        AnswerStreamContext second = conversationService.beginAnswerStream(user.getId(), session.id(),
                new AnswerRequest(currentQuestionId, "不知道"));
        assertThat(second.replyType()).isEqualTo("QUESTION");
        assertThat(second.nextQuestionId()).isNotNull();

        InterviewMessage nextPromptMessage = messageMapper.selectById(second.interviewerMessageId());
        assertThat(nextPromptMessage.getQuestionId()).isEqualTo(second.nextQuestionId());

        String reply = conversationService.streamReply(second)
                .collectList()
                .block()
                .stream()
                .collect(Collectors.joining());
        InterviewQuestion next = questionMapper.selectById(second.nextQuestionId());
        assertThat(reply).contains(next.getContent());
        assertThat(reply).doesNotContain(current.getContent());
    }

    @Test
    void practiceModeReturnsEvaluation() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "PRACTICE"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        var firstQuestion = started.questions().stream()
                .filter(question -> question.id().equals(started.currentQuestionId()))
                .findFirst().orElseThrow();
        String answer = String.join("，", firstQuestion.referencePoints());
        AnswerResult result = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(firstQuestion.id(), answer));

        assertThat(result.evaluation()).isNotNull();
        assertThat(result.evaluation().correctness()).isIn("CORRECT", "PARTIAL");
        assertThat(conversationService.evaluations(user.getId(), session.id())).hasSize(1);
    }

    @Test
    void storesTrackIdFromJobTemplate() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        var job = jobTemplateMapper.selectList(null).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> item.getTrackId() != null)
                .findFirst()
                .orElseThrow();

        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(job.getId(), resume.getId(), "STANDARD", 30, "STANDARD", "INTERVIEW"));

        InterviewSession saved = sessionMapper.selectById(session.id());
        assertThat(saved.getTrackId()).isEqualTo(job.getTrackId());
    }

    private User createUser() {
        User user = new User();
        user.setEmail("conv-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Conversation Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user;
    }

    private Resume createResume(User user) throws Exception {
        ResumeProfile profile = new ResumeProfile(
                "张三", "zhangsan@example.com", "13800138000", 3,
                List.of("Java", "Spring Boot", "MySQL"),
                List.of(new ProjectExperience("中州养老", "设备数据采集")),
                "某大学本科", "3 年 Java 经验");
        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setFileName("resume.docx");
        resume.setFilePath("test/resume.docx");
        resume.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        resume.setFileSize(100L);
        resume.setParseStatus("PARSED");
        resume.setParsedJson(objectMapper.writeValueAsString(profile));
        resumeMapper.insert(resume);
        return resume;
    }
}
