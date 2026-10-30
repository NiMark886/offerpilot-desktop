# OfferPilot 练习模式实时点评实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加“练习模式”：每回答一题后，系统立即判断回答正确性，给出正确/部分正确/需补充/不正确、1–5 分、一句点评和缺失要点，并通过 SSE 推送到前端展示；普通模拟面试模式不显示即时点评。

**Architecture:** 在 `interview` 域新增 `mode`（INTERVIEW / PRACTICE）和 `answer_evaluation` 表。`InterviewConversationService` 在练习模式下调用 `AnswerEvaluator`，默认使用基于参考要点的 `MockAnswerEvaluator`，`app.ai.enabled=true` 时使用 Spring AI 评估器并失败回退 Mock。评估结果随 SSE 的 `evaluation` 事件返回，同时提供 `GET /api/interviews/{id}/evaluations` 供页面刷新后恢复。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring AI 1.1.8（可选）、MyBatis-Plus、MySQL/H2、SSE、Vue3 + Element Plus。

---

## 0. 范围与完成定义

**完成定义：**

- 创建面试时可选 `模拟面试` 或 `练习模式（实时点评）`。
- 练习模式下，每次提交回答后：
  - SSE 事件顺序：`meta → delta… → evaluation → done`。
  - 前端在候选人回答下方显示点评卡片：正确性、分数、点评、缺失要点。
  - 页面刷新后，通过 `GET /evaluations` 恢复点评。
- 模拟面试模式不产生评估，不显示点评卡片。
- 后端测试、前端构建、浏览器端到端验收通过。

**本计划不做：**

- W4 的五维报告、历史成长曲线；本功能只做单题即时点评。
- 语音、RAG、额度等。

**分支策略：** 从 `feat/p0-week3` 新建 `feat/practice-evaluation`。W3 的 PR #3 合并后可将本分支 rebase 到 `main`。

---

## Task 1: 分支、V5 迁移与 mode 字段

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__practice_evaluation.sql`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/CreateInterviewRequest.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Test: `backend/src/test/java/com/offerpilot/interview/SchemaV5IT.java`
- Modify: `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java`

- [ ] **Step 1: 创建分支**

```bash
cd /e/work
git checkout -b feat/practice-evaluation
```

- [ ] **Step 2: 先写 V5 表结构测试**

Create `backend/src/test/java/com/offerpilot/interview/SchemaV5IT.java`:
```java
package com.offerpilot.interview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV5IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPracticeEvaluationSchema() {
        assertThat(tableCount("answer_evaluation")).isEqualTo(1);
        assertThat(columnCount("interview_session", "mode")).isEqualTo(1);
    }

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                Integer.class, table, column);
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV5IT test
```

Expected: FAIL，`answer_evaluation` 表或 `mode` 字段不存在。

- [ ] **Step 4: 创建 V5 迁移**

Create `backend/src/main/resources/db/migration/V5__practice_evaluation.sql`:
```sql
ALTER TABLE interview_session ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'INTERVIEW';

CREATE TABLE answer_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    candidate_message_id BIGINT NOT NULL,
    correctness VARCHAR(20) NOT NULL,
    score INT NOT NULL,
    comment TEXT NOT NULL,
    missing_points TEXT NULL,
    confidence DOUBLE NOT NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_answer_evaluation_session ON answer_evaluation(session_id);
CREATE INDEX idx_answer_evaluation_candidate_msg ON answer_evaluation(candidate_message_id);
```

- [ ] **Step 5: 增加 `mode` 字段**

在 `InterviewSession` 的 `private String persona;` 后加入：
```java
    private String mode;
```

在 `CreateInterviewRequest` 的 `persona` 后加入：
```java
        String mode
```

将 `CreateInterviewRequest` 改为：
```java
public record CreateInterviewRequest(
        @NotNull Long jobTemplateId,
        @NotNull Long resumeId,
        @NotBlank String difficulty,
        @NotNull @Min(10) @Max(60) Integer durationMinutes,
        @NotBlank String persona,
        String mode
) {
}
```

在 `InterviewSessionView` 的 `persona` 后加入：
```java
        String mode,
```

- [ ] **Step 6: 更新 `InterviewService`**

在 `create` 方法中设置模式（放在 `session.setPersona(...)` 后）：
```java
        String mode = "PRACTICE".equalsIgnoreCase(request.mode()) ? "PRACTICE" : "INTERVIEW";
        session.setMode(mode);
```

在 `toView` 中，`session.getPersona()` 后加入：
```java
                session.getMode(),
```

- [ ] **Step 7: 更新 W3 测试构造**

将 `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java` 中的：
```java
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD"));
```
改为：
```java
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "INTERVIEW"));
```

- [ ] **Step 8: 运行测试，确认通过**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV5IT,InterviewControllerIT,InterviewConversationServiceTest test
```

Expected: PASS。

- [ ] **Step 9: 提交**

```bash
cd /e/work
git add backend/src/main/resources/db/migration/V5__practice_evaluation.sql \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/CreateInterviewRequest.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewService.java \
  backend/src/test/java/com/offerpilot/interview/SchemaV5IT.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java
git commit -m "feat(interview): add practice mode and evaluation schema"
```

---

## Task 2: 评估领域与 Mock/AI 评估器

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/domain/AnswerEvaluation.java`
- Create: `backend/src/main/java/com/offerpilot/interview/mapper/AnswerEvaluationMapper.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationResult.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/AnswerEvaluator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/MockAnswerEvaluator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/SpringAiAnswerEvaluator.java`
- Test: `backend/src/test/java/com/offerpilot/interview/MockAnswerEvaluatorTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/MockAnswerEvaluatorTest.java`:
```java
package com.offerpilot.interview;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.service.MockAnswerEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAnswerEvaluatorTest {

    private final MockAnswerEvaluator evaluator = new MockAnswerEvaluator();

    @Test
    void marksKnownAnswerAsCorrectOrPartial() {
        InterviewQuestion question = question(
                "[\"数组+链表+红黑树\",\"扩容 2 倍\",\"链表转红黑树阈值 8\"]");

        var result = evaluator.evaluate(question,
                "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍");

        assertThat(result.correctness()).isIn("CORRECT", "PARTIAL");
        assertThat(result.score()).isBetween(3, 5);
        assertThat(result.comment()).isNotBlank();
    }

    @Test
    void marksUnknownAnswerAsIncorrect() {
        InterviewQuestion question = question(
                "[\"数组+链表+红黑树\",\"扩容 2 倍\"]");

        var result = evaluator.evaluate(question, "不知道");

        assertThat(result.correctness()).isEqualTo("INCORRECT");
        assertThat(result.score()).isEqualTo(1);
        assertThat(result.missingPoints()).isNotEmpty();
    }

    private InterviewQuestion question(String referencePoints) {
        InterviewQuestion question = new InterviewQuestion();
        question.setContent("HashMap 的扩容机制？");
        question.setReferencePoints(referencePoints);
        question.setKnowledgeTags("[\"HashMap\"]");
        return question;
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockAnswerEvaluatorTest test
```

Expected: 编译失败，相关类不存在。

- [ ] **Step 3: 创建评估实体、Mapper 与 DTO**

Create `backend/src/main/java/com/offerpilot/interview/domain/AnswerEvaluation.java`:
```java
package com.offerpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("answer_evaluation")
public class AnswerEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private Long candidateMessageId;

    private String correctness;

    private Integer score;

    private String comment;

    private String missingPoints;

    private Double confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

Create `backend/src/main/java/com/offerpilot/interview/mapper/AnswerEvaluationMapper.java`:
```java
package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnswerEvaluationMapper extends BaseMapper<AnswerEvaluation> {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationResult.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;

public record AnswerEvaluationResult(
        String correctness,
        int score,
        String comment,
        List<String> missingPoints,
        double confidence
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationView.java`:
```java
package com.offerpilot.interview.web.dto;

import com.offerpilot.interview.domain.AnswerEvaluation;

import java.time.LocalDateTime;
import java.util.List;

public record AnswerEvaluationView(
        Long id,
        Long candidateMessageId,
        Long questionId,
        String correctness,
        Integer score,
        String comment,
        List<String> missingPoints,
        Double confidence,
        LocalDateTime createdAt
) {

    public static AnswerEvaluationView from(AnswerEvaluation evaluation, List<String> missingPoints) {
        return new AnswerEvaluationView(
                evaluation.getId(),
                evaluation.getCandidateMessageId(),
                evaluation.getQuestionId(),
                evaluation.getCorrectness(),
                evaluation.getScore(),
                evaluation.getComment(),
                missingPoints,
                evaluation.getConfidence(),
                evaluation.getCreatedAt());
    }
}
```

- [ ] **Step 4: 创建评估器接口与 Mock 实现**

Create `backend/src/main/java/com/offerpilot/interview/service/AnswerEvaluator.java`:
```java
package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;

public interface AnswerEvaluator {

    AnswerEvaluationResult evaluate(InterviewQuestion question, String answer);
}
```

Create `backend/src/main/java/com/offerpilot/interview/service/MockAnswerEvaluator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MockAnswerEvaluator implements AnswerEvaluator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AnswerEvaluationResult evaluate(InterviewQuestion question, String answer) {
        List<String> points = readList(question.getReferencePoints());
        String text = answer == null ? "" : answer.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (text.length() < 10 || lower.contains("不知道") || lower.contains("不太清楚")) {
            return new AnswerEvaluationResult(
                    "INCORRECT", 1,
                    "这道题还没有展开，建议先说出核心概念和关键步骤。",
                    points, 0.5);
        }

        if (points.isEmpty()) {
            return new AnswerEvaluationResult(
                    "PARTIAL", 3,
                    "该题暂无参考要点，已记录你的回答。",
                    List.of(), 0.5);
        }

        List<String> missing = new ArrayList<>();
        int matched = 0;
        for (String point : points) {
            if (containsAnyToken(lower, point)) {
                matched++;
            } else {
                missing.add(point);
            }
        }

        double ratio = (double) matched / points.size();
        String correctness;
        int score;
        if (ratio >= 0.75) {
            correctness = "CORRECT";
            score = 5;
        } else if (ratio >= 0.5) {
            correctness = "CORRECT";
            score = 4;
        } else if (ratio >= 0.25) {
            correctness = "PARTIAL";
            score = 3;
        } else {
            correctness = "INSUFFICIENT";
            score = 2;
        }

        String comment = "覆盖了 " + matched + "/" + points.size() + " 个参考要点。";
        if (!missing.isEmpty()) {
            comment += " 建议补充：" + String.join("、", missing.subList(0, Math.min(2, missing.size()))) + "。";
        }
        double confidence = Math.min(0.9, 0.6 + 0.3 * ratio);
        return new AnswerEvaluationResult(correctness, score, comment, missing, confidence);
    }

    private boolean containsAnyToken(String answerLower, String point) {
        for (String token : point.split("[+、,，;；:：\\s/]+")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (trimmed.length() >= 2 && answerLower.contains(trimmed)) {
                return true;
            }
        }
        return false;
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
```

- [ ] **Step 5: 创建 Spring AI 评估器**

Create `backend/src/main/java/com/offerpilot/interview/service/SpringAiAnswerEvaluator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiAnswerEvaluator implements AnswerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAnswerEvaluator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockAnswerEvaluator fallback;

    public SpringAiAnswerEvaluator(ChatClient.Builder builder,
                                   ObjectMapper objectMapper,
                                   MockAnswerEvaluator fallback) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public AnswerEvaluationResult evaluate(InterviewQuestion question, String answer) {
        try {
            String content = chatClient.prompt()
                    .system("""
                            你是技术面试评分器。根据题目、参考要点和候选人回答判断回答正确性。
                            只输出 JSON，不要 markdown。JSON 字段：
                            correctness（CORRECT/PARTIAL/INSUFFICIENT/INCORRECT）、
                            score（1-5）、comment（一句中文点评）、
                            missingPoints（字符串数组）、confidence（0-1）。
                            """)
                    .user("""
                            题目：%s
                            参考要点：%s
                            候选人回答：%s
                            """.formatted(
                            question.getContent(),
                            question.getReferencePoints(),
                            answer))
                    .call()
                    .content();
            return objectMapper.readValue(cleanJson(content), AnswerEvaluationResult.class);
        } catch (Exception ex) {
            log.warn("AI answer evaluation failed, falling back to mock", ex);
            return fallback.evaluate(question, answer);
        }
    }

    private String cleanJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockAnswerEvaluatorTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/domain/AnswerEvaluation.java \
  backend/src/main/java/com/offerpilot/interview/mapper/AnswerEvaluationMapper.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationResult.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/AnswerEvaluationView.java \
  backend/src/main/java/com/offerpilot/interview/service/AnswerEvaluator.java \
  backend/src/main/java/com/offerpilot/interview/service/MockAnswerEvaluator.java \
  backend/src/main/java/com/offerpilot/interview/service/SpringAiAnswerEvaluator.java \
  backend/src/test/java/com/offerpilot/interview/MockAnswerEvaluatorTest.java
git commit -m "feat(interview): add answer evaluators for practice mode"
```

---

## Task 3: 对话服务集成与 SSE 点评事件

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerResult.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/InterviewConversationController.java`
- Modify: `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java`
- Modify: `backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java`

- [ ] **Step 1: 扩展 `AnswerResult`**

将 `AnswerResult` 改为：
```java
package com.offerpilot.interview.web.dto;

public record AnswerResult(
        Long candidateMessageId,
        Long interviewerMessageId,
        Long questionId,
        String replyType,
        String replyText,
        String sessionStatus,
        Long currentQuestionId,
        boolean completed,
        AnswerEvaluationView evaluation
) {
}
```

- [ ] **Step 2: 修改 `InterviewConversationService`**

在构造函数中增加依赖：
```java
    private final AnswerEvaluator answerEvaluator;
    private final AnswerEvaluationMapper evaluationMapper;
```

完整构造函数：
```java
    public InterviewConversationService(InterviewSessionMapper sessionMapper,
                                        InterviewQuestionMapper questionMapper,
                                        InterviewMessageMapper messageMapper,
                                        InterviewService interviewService,
                                        InterviewerReplyGenerator replyGenerator,
                                        AnswerEvaluator answerEvaluator,
                                        AnswerEvaluationMapper evaluationMapper,
                                        ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.interviewService = interviewService;
        this.replyGenerator = replyGenerator;
        this.answerEvaluator = answerEvaluator;
        this.evaluationMapper = evaluationMapper;
        this.objectMapper = objectMapper;
    }
```

导入：
```java
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.web.dto.AnswerEvaluationResult;
import com.offerpilot.interview.web.dto.AnswerEvaluationView;
```

在 `answer` 中，插入候选人消息后加入：
```java
        AnswerEvaluationView evaluation = evaluateIfPractice(session, question, candidate, request.content());
```

将三个 `new AnswerResult(...)` 的最后一个参数改为 `evaluation`：
```java
            return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                    "FOLLOW_UP", reply, session.getStatus(), question.getId(), false, evaluation);
```
```java
            return new AnswerResult(candidate.getId(), interviewer.getId(), next.getId(),
                    "QUESTION", reply, session.getStatus(), next.getId(), false, evaluation);
```
```java
        return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                "CLOSING", reply, "COMPLETED", null, true, evaluation);
```

在类中增加：
```java
    public java.util.List<AnswerEvaluationView> evaluations(Long userId, Long sessionId) {
        loadOwned(userId, sessionId);
        return evaluationMapper.selectList(new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId))
                .stream()
                .map(evaluation -> AnswerEvaluationView.from(evaluation, readList(evaluation.getMissingPoints())))
                .toList();
    }

    private AnswerEvaluationView evaluateIfPractice(InterviewSession session,
                                                    InterviewQuestion question,
                                                    InterviewMessage candidate,
                                                    String answer) {
        if (!"PRACTICE".equals(session.getMode())) {
            return null;
        }
        AnswerEvaluationResult result = answerEvaluator.evaluate(question, answer);
        AnswerEvaluation evaluation = new AnswerEvaluation();
        evaluation.setSessionId(session.getId());
        evaluation.setQuestionId(question.getId());
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
```

- [ ] **Step 3: 修改 SSE 控制器**

在 `stream` 的 `meta` map 中加入：
```java
            meta.put("candidateMessageId", result.candidateMessageId());
```

在 delta 循环之后、`done` 之前加入：
```java
            if (result.evaluation() != null) {
                emitter.send(SseEmitter.event().name("evaluation").data(toJson(result.evaluation())));
            }
```

增加 `GET /evaluations`：
```java
    @GetMapping("/evaluations")
    public ApiResponse<java.util.List<com.offerpilot.interview.web.dto.AnswerEvaluationView>> evaluations(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.evaluations(userId, id));
    }
```

- [ ] **Step 4: 扩展服务测试**

在 `InterviewConversationServiceTest` 中增加：
```java
    @Test
    void practiceModeReturnsEvaluation() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD", "PRACTICE"));

        InterviewSessionView started = conversationService.start(user.getId(), session.id());
        AnswerResult result = conversationService.answer(user.getId(), session.id(),
                new AnswerRequest(started.currentQuestionId(),
                        "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍"));

        assertThat(result.evaluation()).isNotNull();
        assertThat(result.evaluation().correctness()).isIn("CORRECT", "PARTIAL");
        assertThat(conversationService.evaluations(user.getId(), session.id())).hasSize(1);
    }
```

- [ ] **Step 5: 扩展控制器测试**

在 `InterviewConversationControllerIT` 中增加 `practiceModeReturnsEvaluation`：
```java
    @Test
    void practiceModeReturnsEvaluation() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId, "PRACTICE");

        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult sessionResult = mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long questionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("data").get("currentQuestionId").asLong();

        MvcResult sse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(sse))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"correctness\"")))
                .andExpect(content().string(containsString("\"comment\"")));

        mockMvc.perform(get("/api/interviews/" + sessionId + "/evaluations")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
```

将现有 `createInterview` 改为带 mode 参数：
```java
    private long createInterview(String token, long resumeId) throws Exception {
        return createInterview(token, resumeId, "INTERVIEW");
    }

    private long createInterview(String token, long resumeId, String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", 1,
                                "resumeId", resumeId,
                                "difficulty", "STANDARD",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", mode))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
```

- [ ] **Step 6: 运行测试**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewConversationServiceTest,InterviewConversationControllerIT test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/web/dto/AnswerResult.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java \
  backend/src/main/java/com/offerpilot/interview/web/InterviewConversationController.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java
git commit -m "feat(interview): stream practice mode evaluations"
```

---

## Task 4: 前端模式选择、点评卡片与验收

**Files:**
- Modify: `frontend/src/api/interview.ts`
- Modify: `frontend/src/components/InterviewSetupDialog.vue`
- Modify: `frontend/src/views/InterviewChatView.vue`
- Modify: `README.md`

- [ ] **Step 1: 更新前端类型与 API**

在 `frontend/src/api/interview.ts` 中：

`InterviewSession` 增加：
```ts
  mode: string
```

`CreateInterviewRequest` 增加：
```ts
  mode: string
```

增加：
```ts
export interface AnswerEvaluationView {
  id: number
  candidateMessageId: number
  questionId: number
  correctness: 'CORRECT' | 'PARTIAL' | 'INSUFFICIENT' | 'INCORRECT'
  score: number
  comment: string
  missingPoints: string[]
  confidence: number
  createdAt: string
}
```

`StreamHandlers` 改为：
```ts
export interface StreamHandlers {
  onMeta?: (meta: {
    candidateMessageId: number
    interviewerMessageId: number
    questionId: number
    replyType: string
  }) => void
  onDelta?: (text: string) => void
  onEvaluation?: (evaluation: AnswerEvaluationView) => void
  onDone?: (done: { sessionStatus: string; currentQuestionId: number | null; completed: boolean }) => void
  onError?: (message: string) => void
}
```

`interviewApi` 增加：
```ts
  evaluations: (id: number) => http.get<never, AnswerEvaluationView[]>(`/interviews/${id}/evaluations`),
```

`streamAnswer` 解析事件时增加：
```ts
      if (event === 'meta') handlers.onMeta?.(payload)
      else if (event === 'delta') handlers.onDelta?.(payload.text)
      else if (event === 'evaluation') handlers.onEvaluation?.(payload)
      else if (event === 'done') handlers.onDone?.(payload)
      else if (event === 'error') handlers.onError?.(payload.message)
```

- [ ] **Step 2: 修改创建面试弹窗**

在 `frontend/src/components/InterviewSetupDialog.vue` 的 `form` 中增加：
```ts
  mode: 'INTERVIEW'
```

在模板的“难度”区块前加入：
```vue
      <section class="field-block">
        <div class="field-title"><span>模式</span></div>
        <el-radio-group v-model="form.mode">
          <el-radio-button value="INTERVIEW">模拟面试</el-radio-button>
          <el-radio-button value="PRACTICE">练习模式（实时点评）</el-radio-button>
        </el-radio-group>
        <p v-if="form.mode === 'PRACTICE'" class="mode-hint">
          每题回答后会立即显示正确性点评，适合刷题练习。
        </p>
      </section>
```

在 `submit` 的 `interviewApi.create` 参数中加入：
```ts
      mode: form.mode,
```

在样式末尾增加：
```css
.mode-hint {
  margin: 8px 0 0;
  color: #8a97a8;
  font-size: 12px;
}
```

- [ ] **Step 3: 修改聊天页**

在 `<script setup>` 中：
- 导入 `AnswerEvaluationView` 类型。
- 增加：
```ts
const evaluations = ref<Record<number, AnswerEvaluationView>>({})

const correctnessLabels: Record<string, string> = {
  CORRECT: '正确',
  PARTIAL: '部分正确',
  INSUFFICIENT: '需补充',
  INCORRECT: '不正确'
}

function correctnessLabel(value: string) {
  return correctnessLabels[value] ?? value
}
```
- `load` 中，将 `messages.value = await interviewApi.messages(id)` 改为：
```ts
    const [messageList, evaluationList] = await Promise.all([
      interviewApi.messages(id),
      interviewApi.evaluations(id)
    ])
    messages.value = messageList
    evaluations.value = Object.fromEntries(
      evaluationList.map((evaluation) => [evaluation.candidateMessageId, evaluation])
    )
```
- `send` 中，在创建临时消息前记录 `tempCandidateId`；`onMeta` 中先把候选人临时消息 ID 替换为真实 ID：
```ts
      onMeta: (meta) => {
        const candidate = messages.value.find((m) => m.id === tempCandidateId)
        if (candidate) candidate.id = meta.candidateMessageId
        const message = messages.value.find((m) => m.id === streamingMessageId.value)
        if (message) {
          message.id = meta.interviewerMessageId
          message.questionId = meta.questionId
          message.messageType = meta.replyType
          streamingMessageId.value = meta.interviewerMessageId
        }
      },
```
- 增加 `onEvaluation`：
```ts
      onEvaluation: (evaluation) => {
        evaluations.value[evaluation.candidateMessageId] = evaluation
      },
```

模板中，将消息行改为：
```vue
          <div v-else class="message-row" :class="message.role === 'CANDIDATE' ? 'right' : 'left'">
            <div class="avatar">{{ message.role === 'CANDIDATE' ? '你' : 'AI' }}</div>
            <div class="message-content">
              <div class="bubble">
                <span v-if="message.id === streamingMessageId && !message.content" class="typing">AI 正在思考…</span>
                <span v-else class="bubble-text">{{ message.content }}</span>
              </div>
              <div
                v-if="message.role === 'CANDIDATE' && evaluations[message.id]"
                class="evaluation-card"
                :class="evaluations[message.id].correctness.toLowerCase()"
              >
                <div class="evaluation-head">
                  <span class="evaluation-label">
                    {{ correctnessLabel(evaluations[message.id].correctness) }}
                  </span>
                  <span class="evaluation-score">{{ evaluations[message.id].score }}/5</span>
                </div>
                <p class="evaluation-comment">{{ evaluations[message.id].comment }}</p>
                <p
                  v-if="evaluations[message.id].missingPoints?.length"
                  class="evaluation-missing"
                >
                  建议补充：{{ evaluations[message.id].missingPoints.join('、') }}
                </p>
              </div>
            </div>
          </div>
```

样式调整：
- 将 `.bubble` 的 `max-width: 72%` 改为 `max-width: 100%`。
- 增加 `.message-content`：
```css
.message-content {
  max-width: 78%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-row.right .message-content {
  align-items: flex-end;
}
```
- 增加点评卡片样式：
```css
.evaluation-card {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e4ecf5;
  background: #fbfdff;
  font-size: 13px;
}

.evaluation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.evaluation-label {
  font-weight: 700;
}

.evaluation-score {
  color: #8a97a8;
}

.evaluation-card.correct {
  border-color: #c2e7b0;
  background: #f0f9eb;
}

.evaluation-card.correct .evaluation-label {
  color: #529b2e;
}

.evaluation-card.partial {
  border-color: #f3d19e;
  background: #fdf6ec;
}

.evaluation-card.partial .evaluation-label {
  color: #b88230;
}

.evaluation-card.insufficient {
  border-color: #f3d19e;
  background: #fff7e6;
}

.evaluation-card.insufficient .evaluation-label {
  color: #d48806;
}

.evaluation-card.incorrect {
  border-color: #f0b4b4;
  background: #fef0f0;
}

.evaluation-card.incorrect .evaluation-label {
  color: #c45656;
}

.evaluation-comment {
  margin: 0;
  color: #5b6b7f;
  line-height: 1.7;
}

.evaluation-missing {
  margin: 6px 0 0;
  color: #8a97a8;
  line-height: 1.6;
}
```

- [ ] **Step 4: 构建验证**

```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 5: 浏览器端验收**

1. 创建面试时选择“练习模式（实时点评）”。
2. 开始答题后，输入：
   ```text
   HashMap 底层是数组、链表和红黑树，扩容时容量翻倍
   ```
3. 发送后应看到面试官追问，并在回答下方出现点评卡片：
   - 正确性：正确 / 部分正确
   - 分数：3–5
   - 点评和缺失要点
4. 刷新页面，点评卡片仍然存在（`GET /evaluations` 恢复）。
5. 再创建一个“模拟面试”模式的面试，回答后不应出现点评卡片。

- [ ] **Step 6: 更新 README**

在“当前进度”中补充：
```markdown
当前进度：P0 Week 3 + 练习模式实时点评（多轮对话、SSE、追问/提示/跳过、逐题即时点评）。
```

在 API 表格中追加：
```markdown
| GET | `/api/interviews/{id}/evaluations` | 练习模式逐题点评 |
```

在“开发约定”前补充：
```markdown
## 练习模式

创建面试时选择“练习模式（实时点评）”，每回答一题会立即返回：
正确性（正确/部分正确/需补充/不正确）、1–5 分、一句点评和缺失要点。
默认 Mock 评估；`APP_AI_ENABLED=true` 时使用 Spring AI 评估，失败回退 Mock。
```

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add frontend/src/api/interview.ts frontend/src/components/InterviewSetupDialog.vue \
  frontend/src/views/InterviewChatView.vue README.md
git commit -m "feat: add practice mode real-time answer evaluation"
```

---

## 自检结果

- **Spec coverage:** 练习模式选择、逐题评估、SSE `evaluation` 事件、前端点评卡片、刷新恢复、普通模式不显示，均已覆盖。
- **Placeholder scan:** 没有 TBD/TODO；代码完整。
- **Type consistency:** `mode`、`AnswerEvaluationResult`、`AnswerEvaluationView`、`AnswerResult.evaluation`、SSE `evaluation` 事件、前端 `AnswerEvaluationView` 字段一致。
- **默认可用性:** `app.ai.enabled=false` 时 Mock 评估可用；开启 AI 后优先 Spring AI，失败回退 Mock。

---

## 执行记录（2026-10-27）

- 分支：`feat/practice-evaluation`（基于 `feat/p0-week3`）。
- 后端测试：`mvn -f backend/pom.xml test` → 通过（含 V5 迁移、Mock 评估器、练习模式 SSE、点评持久化）。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 浏览器端到端（Playwright + Chrome）：
  - 创建面试时选择“练习模式（实时点评）”。
  - 回答「HashMap 底层是数组、链表和红黑树，扩容时容量翻倍」。
  - 点评卡片显示：正确、4/5、覆盖了 2/3 个参考要点、建议补充「链表转红黑树阈值 8」。
  - 刷新页面后点评仍然存在（`GET /evaluations` 恢复）。
- 实施中的调整：
  - Mock 评估器的 `comment` 只输出覆盖情况，缺失要点由前端单独展示，避免“建议补充”重复。
  - `meta` SSE 事件增加 `candidateMessageId`，前端把临时候选人消息 ID 替换为真实 ID，点评卡片能即时挂到对应回答下。
  - 普通模拟面试模式不产生 `answer_evaluation`，不显示点评卡片。
- 追问点评修复（2026-10-27）：
  - V6 迁移为 `answer_evaluation` 增加 `prompt_text` / `prompt_type`。
  - 评估器增加当前评分对象上下文；追问回答按“是否针对追问作答”评估，不再套用原题参考要点。
  - 点评卡片显示“针对追问”和追问内容；原题点评显示“针对本题”。
  - 新增回归测试：追问回答的 `missingPoints` 不得包含原题参考要点。
- 追问轮换优化（2026-10-27）：
  - Mock 面试官按 `followUpHints` 顺序选择追问，不再重复第一条。
  - 追问次数上限改为 `min(2, followUpHints.size())`，没有更多追问时直接进入下一题。
  - 新增测试覆盖追问轮换与追问次数限制。
- 待推送：分支提交在本地，W3 的 PR #3 仍未合并。
