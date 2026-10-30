# OfferPilot W4 后端：五维报告与统计接口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 面试结束后生成五维评分报告（技术准确性、技术深度、项目真实性、表达逻辑、岗位匹配度），提供逐题复盘、历史面试列表和成长统计接口。

**Architecture:** 新增 `interview_report` 表与 `InterviewReportService`。报告生成时先确保每道已答题都有单题评估：练习模式复用已有 `answer_evaluation`，模拟面试模式在报告生成时用 `AnswerEvaluator` 补评。随后由 `InterviewReportGenerator` 聚合为五维分数与文字报告；默认 Mock，`app.ai.enabled=true` 时优先 Spring AI，失败回退 Mock。历史/统计接口读取会话与报告做聚合。

**Tech Stack:** Java 21、Spring Boot 3.5.16、MyBatis-Plus、MySQL/H2、Spring AI 1.1.8（可选）。

---

## 0. 范围与完成定义

**完成定义：**

- `POST /api/interviews/{id}/report`：为已结束的面试生成（或返回已有）五维报告。
- `GET /api/interviews/{id}/report`：读取报告和逐题复盘。
- `GET /api/interviews/history`：面试历史（含总分、是否有报告）。
- `GET /api/stats/overview`：总场次、完成场次、平均分、最高分、五维平均分、分数趋势。
- 练习模式复用逐题点评；模拟面试模式在生成报告时补评。
- 后端全量测试通过；前端报告页/成长页在后续 W4 前端计划实现。

**本计划不做：** 前端页面、ECharts 图表、报告分享、异步报告任务队列。报告生成同步执行，默认 Mock 足够快；AI 模式会逐题调用模型，后续可改为异步。

**分支策略：** 从 `feat/practice-evaluation` 新建 `feat/p0-week4-report`。

---

## Task 1: 分支、V7 报告表与 DTO

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__interview_report.sql`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/InterviewReport.java`
- Create: `backend/src/main/java/com/offerpilot/interview/mapper/InterviewReportMapper.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportResult.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionReviewView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewHistoryItem.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/ScorePoint.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/StatsOverview.java`
- Test: `backend/src/test/java/com/offerpilot/interview/SchemaV7IT.java`

- [ ] **Step 1: 创建分支**

```bash
cd /e/work
git checkout -b feat/p0-week4-report
```

- [ ] **Step 2: 先写表结构测试**

Create `backend/src/test/java/com/offerpilot/interview/SchemaV7IT.java`:
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
class SchemaV7IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsInterviewReportTable() {
        assertThat(tableCount("interview_report")).isEqualTo(1);
        assertThat(columnCount("interview_report", "overall_score")).isEqualTo(1);
        assertThat(columnCount("interview_report", "dimensions_json")).isEqualTo(1);
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
mvn -f backend/pom.xml -Dtest=SchemaV7IT test
```

Expected: FAIL，`interview_report` 不存在。

- [ ] **Step 4: 创建 V7 迁移**

Create `backend/src/main/resources/db/migration/V7__interview_report.sql`:
```sql
CREATE TABLE interview_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    overall_score INT NOT NULL,
    dimensions_json TEXT NOT NULL,
    summary TEXT NOT NULL,
    strengths_json TEXT NULL,
    weaknesses_json TEXT NULL,
    suggestions_json TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_interview_report_session ON interview_report(session_id);
CREATE INDEX idx_interview_report_user ON interview_report(user_id);
```

- [ ] **Step 5: 创建实体与 Mapper**

Create `backend/src/main/java/com/offerpilot/interview/domain/InterviewReport.java`:
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
@TableName("interview_report")
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private Integer overallScore;

    private String dimensionsJson;

    private String summary;

    private String strengthsJson;

    private String weaknessesJson;

    private String suggestionsJson;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

Create `backend/src/main/java/com/offerpilot/interview/mapper/InterviewReportMapper.java`:
```java
package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewReportMapper extends BaseMapper<InterviewReport> {
}
```

- [ ] **Step 6: 创建 DTO**

Create `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportResult.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;
import java.util.Map;

public record InterviewReportResult(
        Map<String, Integer> dimensions,
        int overallScore,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionReviewView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;

public record QuestionReviewView(
        Long questionId,
        Integer seq,
        String type,
        String question,
        String answer,
        String promptType,
        String promptText,
        String correctness,
        Integer score,
        String comment,
        List<String> missingPoints
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InterviewReportView(
        Long id,
        Long sessionId,
        String jobTitle,
        String mode,
        int overallScore,
        Map<String, Integer> dimensions,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions,
        String status,
        LocalDateTime createdAt,
        List<QuestionReviewView> questionReviews
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewHistoryItem.java`:
```java
package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;

public record InterviewHistoryItem(
        Long sessionId,
        String jobTitle,
        String mode,
        String status,
        Integer totalScore,
        LocalDateTime createdAt,
        boolean hasReport
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/ScorePoint.java`:
```java
package com.offerpilot.interview.web.dto;

public record ScorePoint(String date, int score, String jobTitle) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/StatsOverview.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;
import java.util.Map;

public record StatsOverview(
        int totalInterviews,
        int completedInterviews,
        double averageScore,
        int bestScore,
        Map<String, Integer> dimensionAverages,
        List<ScorePoint> scoreTrend
) {
}
```

- [ ] **Step 7: 运行测试，确认通过**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV7IT test
```

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
cd /e/work
git add backend/src/main/resources/db/migration/V7__interview_report.sql \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewReport.java \
  backend/src/main/java/com/offerpilot/interview/mapper/InterviewReportMapper.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportResult.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportView.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/QuestionReviewView.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/InterviewHistoryItem.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/ScorePoint.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/StatsOverview.java \
  backend/src/test/java/com/offerpilot/interview/SchemaV7IT.java
git commit -m "feat(report): add interview report schema and DTOs"
```

---

## Task 2: 五维报告生成器（Mock + Spring AI）

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewReportGenerator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/MockInterviewReportGenerator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewReportGenerator.java`
- Test: `backend/src/test/java/com/offerpilot/interview/MockInterviewReportGeneratorTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/MockInterviewReportGeneratorTest.java`:
```java
package com.offerpilot.interview;

import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.service.MockInterviewReportGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewReportGeneratorTest {

    private final MockInterviewReportGenerator generator = new MockInterviewReportGenerator();

    @Test
    void generatesFiveDimensionReport() {
        InterviewSession session = new InterviewSession();
        session.setMode("INTERVIEW");

        InterviewQuestion basic = question(1L, 1, "BASIC", "HashMap 扩容机制？");
        InterviewQuestion project = question(2L, 2, "PROJECT", "介绍你的项目");
        InterviewQuestion scenario = question(3L, 3, "SCENARIO", "线上 P99 升高怎么排查？");

        InterviewMessage a1 = answer(101L, 1L, "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍，会重新分布元素。");
        InterviewMessage a2 = answer(102L, 2L, "我负责订单系统，首先做了幂等设计，然后通过压测优化了慢 SQL。");
        InterviewMessage a3 = answer(103L, 3L, "先看监控和日志，再排查慢 SQL 与下游依赖，最后做限流和扩容。");

        AnswerEvaluation e1 = evaluation(1L, 101L, 4, "[\"阈值 8\"]");
        AnswerEvaluation e2 = evaluation(2L, 102L, 3, "[\"量化结果\"]");
        AnswerEvaluation e3 = evaluation(3L, 103L, 5, "[]");

        var result = generator.generate(session,
                List.of(basic, project, scenario),
                List.of(a1, a2, a3),
                List.of(e1, e2, e3));

        assertThat(result.dimensions()).hasSize(5);
        assertThat(result.dimensions()).containsKeys("accuracy", "depth", "project", "expression", "match");
        assertThat(result.overallScore()).isBetween(0, 100);
        assertThat(result.summary()).contains("本场面试");
        assertThat(result.strengths()).isNotEmpty();
        assertThat(result.weaknesses()).isNotEmpty();
        assertThat(result.suggestions()).isNotEmpty();
    }

    @Test
    void handlesNoEvaluations() {
        InterviewSession session = new InterviewSession();
        session.setMode("INTERVIEW");

        var result = generator.generate(session, List.of(), List.of(), List.of());

        assertThat(result.overallScore()).isZero();
        assertThat(result.dimensions()).hasSize(5);
        assertThat(result.summary()).contains("没有有效回答");
    }

    private InterviewQuestion question(Long id, int seq, String type, String content) {
        InterviewQuestion q = new InterviewQuestion();
        q.setId(id);
        q.setSeq(seq);
        q.setType(type);
        q.setContent(content);
        q.setStatus("ANSWERED");
        return q;
    }

    private InterviewMessage answer(Long id, Long questionId, String content) {
        InterviewMessage m = new InterviewMessage();
        m.setId(id);
        m.setQuestionId(questionId);
        m.setRole("CANDIDATE");
        m.setMessageType("ANSWER");
        m.setContent(content);
        return m;
    }

    private AnswerEvaluation evaluation(Long questionId, Long candidateMessageId, int score, String missing) {
        AnswerEvaluation e = new AnswerEvaluation();
        e.setQuestionId(questionId);
        e.setCandidateMessageId(candidateMessageId);
        e.setScore(score);
        e.setCorrectness(score >= 4 ? "CORRECT" : "PARTIAL");
        e.setMissingPoints(missing);
        return e;
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockInterviewReportGeneratorTest test
```

Expected: 编译失败，`MockInterviewReportGenerator` 不存在。

- [ ] **Step 3: 创建生成器接口**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewReportGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;

import java.util.List;

public interface InterviewReportGenerator {

    InterviewReportResult generate(InterviewSession session,
                                   List<InterviewQuestion> questions,
                                   List<InterviewMessage> messages,
                                   List<AnswerEvaluation> evaluations);
}
```

- [ ] **Step 4: 创建 Mock 生成器**

Create `backend/src/main/java/com/offerpilot/interview/service/MockInterviewReportGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MockInterviewReportGenerator implements InterviewReportGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public InterviewReportResult generate(InterviewSession session,
                                           List<InterviewQuestion> questions,
                                           List<InterviewMessage> messages,
                                           List<AnswerEvaluation> evaluations) {
        Map<String, Integer> empty = emptyDimensions();
        if (evaluations == null || evaluations.isEmpty()) {
            return new InterviewReportResult(empty, 0,
                    "本场面试没有有效回答，无法生成评分报告。",
                    List.of(), List.of("没有有效回答"),
                    List.of("建议重新完成一场面试，至少回答 3 道题。"));
        }

        List<InterviewMessage> answers = messages.stream()
                .filter(message -> "CANDIDATE".equals(message.getRole()))
                .toList();
        Map<Long, String> typeByQuestion = questions.stream()
                .collect(Collectors.toMap(InterviewQuestion::getId, InterviewQuestion::getType, (a, b) -> a));

        double avgScore = evaluations.stream()
                .mapToInt(evaluation -> evaluation.getScore() == null ? 0 : evaluation.getScore())
                .average().orElse(0);
        double avgLength = answers.stream()
                .mapToInt(message -> message.getContent() == null ? 0 : message.getContent().length())
                .average().orElse(0);

        int accuracy = percent(avgScore);
        int depth = clamp((int) Math.round(accuracy * 0.75 + Math.min(20, avgLength / 15.0)));

        double projectAvg = evaluations.stream()
                .filter(evaluation -> "PROJECT".equals(typeByQuestion.get(evaluation.getQuestionId())))
                .mapToInt(evaluation -> evaluation.getScore() == null ? 0 : evaluation.getScore())
                .average().orElse(avgScore);
        int project = percent(projectAvg);

        String joinedAnswers = answers.stream()
                .map(InterviewMessage::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        int markers = 0;
        for (String marker : List.of("首先", "然后", "最后", "因为", "所以", "例如", "第一", "第二")) {
            if (joinedAnswers.contains(marker)) {
                markers++;
            }
        }
        int expression = joinedAnswers.isBlank()
                ? 0
                : clamp((int) Math.round(55 + markers * 5 + Math.min(20, avgLength / 8.0)));

        int match = clamp((int) Math.round(accuracy * 0.4 + depth * 0.35 + project * 0.25));
        int overall = clamp((int) Math.round(
                accuracy * 0.30 + depth * 0.25 + project * 0.20 + expression * 0.15 + match * 0.10));

        long answeredQuestions = evaluations.stream()
                .map(AnswerEvaluation::getQuestionId)
                .distinct()
                .count();

        String summary = "本场面试共完成 %d 道题，整体得分 %d 分。技术准确性 %d 分，技术深度 %d 分，项目表现 %d 分，表达逻辑 %d 分，岗位匹配度 %d 分。"
                .formatted(answeredQuestions, overall, accuracy, depth, project, expression, match);

        List<String> strengths = new ArrayList<>();
        if (accuracy >= 75) strengths.add("技术准确性较好");
        if (depth >= 75) strengths.add("回答有一定技术深度");
        if (project >= 75) strengths.add("项目经历表达清晰");
        if (expression >= 75) strengths.add("表达逻辑清晰");
        if (match >= 75) strengths.add("与目标岗位匹配度较高");
        if (strengths.isEmpty()) strengths.add("完成了本场面试，具备继续训练的基础。");

        List<String> weaknesses = new ArrayList<>();
        if (accuracy < 60) weaknesses.add("技术准确性不足");
        if (depth < 60) weaknesses.add("回答深度不够");
        if (project < 60) weaknesses.add("项目经历讲得不够清楚");
        if (expression < 60) weaknesses.add("表达逻辑需要加强");
        if (match < 60) weaknesses.add("岗位匹配度偏低");

        Map<String, Long> missingCounts = evaluations.stream()
                .flatMap(evaluation -> readList(evaluation.getMissingPoints()).stream())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        missingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> weaknesses.add("知识点缺口：" + entry.getKey()));
        if (weaknesses.isEmpty()) {
            weaknesses.add("暂无明显短板，建议继续提升回答深度和项目细节。");
        }

        List<String> suggestions = new ArrayList<>();
        if (accuracy < 60) suggestions.add("先补齐高频八股，再做下一场模拟面试。");
        if (depth < 60) suggestions.add("回答时补充原理、取舍和边界条件，避免只讲结论。");
        if (project < 60) suggestions.add("用 STAR 结构重写项目经历，突出个人贡献和量化结果。");
        if (expression < 60) suggestions.add("练习“总-分-总”表达，先给结论再展开。");
        if (suggestions.isEmpty()) suggestions.add("保持训练节奏，下一场可以挑战更高难度。");

        return new InterviewReportResult(
                dimensions(accuracy, depth, project, expression, match),
                overall, summary, strengths, weaknesses, suggestions);
    }

    private Map<String, Integer> emptyDimensions() {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("accuracy", 0);
        dimensions.put("depth", 0);
        dimensions.put("project", 0);
        dimensions.put("expression", 0);
        dimensions.put("match", 0);
        return dimensions;
    }

    private Map<String, Integer> dimensions(int accuracy, int depth, int project, int expression, int match) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("accuracy", accuracy);
        dimensions.put("depth", depth);
        dimensions.put("project", project);
        dimensions.put("expression", expression);
        dimensions.put("match", match);
        return dimensions;
    }

    private int percent(double score) {
        return clamp((int) Math.round(score / 5.0 * 100));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
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

- [ ] **Step 5: 创建 Spring AI 生成器**

Create `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewReportGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiInterviewReportGenerator implements InterviewReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiInterviewReportGenerator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MockInterviewReportGenerator fallback;

    public SpringAiInterviewReportGenerator(ChatClient.Builder builder,
                                            ObjectMapper objectMapper,
                                            MockInterviewReportGenerator fallback) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public InterviewReportResult generate(InterviewSession session,
                                           List<InterviewQuestion> questions,
                                           List<InterviewMessage> messages,
                                           List<AnswerEvaluation> evaluations) {
        try {
            String context = objectMapper.writeValueAsString(Map.of(
                    "mode", session.getMode(),
                    "questions", questions,
                    "messages", messages,
                    "evaluations", evaluations));
            String content = chatClient.prompt()
                    .system("""
                            你是技术面试报告生成器。根据面试模式、题目、候选人回答和单题评估，
                            输出五维评分报告。只输出 JSON，不要 markdown。JSON 字段：
                            dimensions（accuracy/depth/project/expression/match，0-100 整数）、
                            overallScore（0-100）、summary（中文总结）、
                            strengths（字符串数组）、weaknesses（字符串数组）、suggestions（字符串数组）。
                            """)
                    .user("面试上下文：\n" + context)
                    .call()
                    .content();
            return objectMapper.readValue(cleanJson(content), InterviewReportResult.class);
        } catch (Exception ex) {
            log.warn("AI report generation failed, falling back to mock", ex);
            return fallback.generate(session, questions, messages, evaluations);
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
mvn -f backend/pom.xml -Dtest=MockInterviewReportGeneratorTest test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/service/InterviewReportGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/MockInterviewReportGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewReportGenerator.java \
  backend/src/test/java/com/offerpilot/interview/MockInterviewReportGeneratorTest.java
git commit -m "feat(report): add five-dimension report generators"
```

---

## Task 3: 报告服务、报告/历史/统计接口

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewReportService.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/InterviewReportController.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/InterviewStatsController.java`
- Test: `backend/src/test/java/com/offerpilot/interview/InterviewReportControllerIT.java`

- [ ] **Step 1: 先写接口测试**

Create `backend/src/test/java/com/offerpilot/interview/InterviewReportControllerIT.java`:
```java
package com.offerpilot.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.resume.TestFiles;
import com.offerpilot.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewReportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatesReportHistoryAndStats() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId);

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
                                "content", "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍，会重新分布元素。我会结合一次线上扩容导致的性能抖动，说明如何通过预设容量和监控 GC 来优化。"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(sse)).andExpect(status().isOk());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/interviews/" + sessionId + "/report")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").isNumber())
                .andExpect(jsonPath("$.data.dimensions.accuracy").isNumber())
                .andExpect(jsonPath("$.data.dimensions.depth").isNumber())
                .andExpect(jsonPath("$.data.dimensions.project").isNumber())
                .andExpect(jsonPath("$.data.dimensions.expression").isNumber())
                .andExpect(jsonPath("$.data.dimensions.match").isNumber())
                .andExpect(jsonPath("$.data.questionReviews").isArray());

        mockMvc.perform(get("/api/interviews/" + sessionId + "/report")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").isNumber());

        mockMvc.perform(get("/api/interviews/history")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hasReport").value(true));

        mockMvc.perform(get("/api/stats/overview")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInterviews").value(1))
                .andExpect(jsonPath("$.data.completedInterviews").value(1))
                .andExpect(jsonPath("$.data.averageScore").isNumber())
                .andExpect(jsonPath("$.data.dimensionAverages.accuracy").isNumber());
    }

    private String registerAndToken() throws Exception {
        String email = "report-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Report Tester"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long uploadResume(String token) throws Exception {
        byte[] docx = TestFiles.docx("""
                张三
                邮箱：zhangsan@example.com
                3 年 Java 后端开发经验
                技能：Java, Spring Boot, MySQL, Redis
                项目：中州养老智能物联网平台
                负责设备数据采集与报警规则。
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        MvcResult result = mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createInterview(String token, long resumeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobTemplateId", 1,
                                "resumeId", resumeId,
                                "difficulty", "STANDARD",
                                "durationMinutes", 30,
                                "persona", "STANDARD",
                                "mode", "INTERVIEW"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewReportControllerIT test
```

Expected: FAIL，`/report`、`/history`、`/stats/overview` 端点不存在。

- [ ] **Step 3: 创建 `InterviewReportService`**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewReportService.java`:
```java
package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.offerpilot.interview.web.dto.InterviewHistoryItem;
import com.offerpilot.interview.web.dto.InterviewReportResult;
import com.offerpilot.interview.web.dto.InterviewReportView;
import com.offerpilot.interview.web.dto.QuestionReviewView;
import com.offerpilot.interview.web.dto.ScorePoint;
import com.offerpilot.interview.web.dto.StatsOverview;
import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.job.mapper.JobTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewReportService {

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final JobTemplateMapper jobTemplateMapper;
    private final AnswerEvaluator answerEvaluator;
    private final InterviewReportGenerator reportGenerator;
    private final ObjectMapper objectMapper;

    public InterviewReportService(InterviewReportMapper reportMapper,
                                   InterviewSessionMapper sessionMapper,
                                   InterviewQuestionMapper questionMapper,
                                   InterviewMessageMapper messageMapper,
                                   AnswerEvaluationMapper evaluationMapper,
                                   JobTemplateMapper jobTemplateMapper,
                                   AnswerEvaluator answerEvaluator,
                                   InterviewReportGenerator reportGenerator,
                                   ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.evaluationMapper = evaluationMapper;
        this.jobTemplateMapper = jobTemplateMapper;
        this.answerEvaluator = answerEvaluator;
        this.reportGenerator = reportGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewReportView generate(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "面试尚未结束");
        }

        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getSeq));
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));
        List<AnswerEvaluation> evaluations = new ArrayList<>(evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId)));

        ensureEvaluations(session, questions, messages, evaluations);

        InterviewReport existing = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId));
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return buildView(existing, session, questions, messages, evaluations);
        }

        InterviewReportResult result = reportGenerator.generate(session, questions, messages, evaluations);
        InterviewReport report = existing == null ? new InterviewReport() : existing;
        if (existing == null) {
            report.setSessionId(sessionId);
            report.setUserId(userId);
        }
        report.setOverallScore(result.overallScore());
        report.setDimensionsJson(writeJson(result.dimensions()));
        report.setSummary(result.summary());
        report.setStrengthsJson(writeJson(result.strengths()));
        report.setWeaknessesJson(writeJson(result.weaknesses()));
        report.setSuggestionsJson(writeJson(result.suggestions()));
        report.setStatus("SUCCESS");
        if (existing == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        session.setTotalScore(result.overallScore());
        sessionMapper.updateById(session);

        return buildView(report, session, questions, messages, evaluations);
    }

    public InterviewReportView get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        InterviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告尚未生成");
        }
        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getSeq));
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));
        List<AnswerEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .eq(AnswerEvaluation::getSessionId, sessionId)
                        .orderByAsc(AnswerEvaluation::getId));
        return buildView(report, session, questions, messages, evaluations);
    }

    public List<InterviewHistoryItem> history(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt))
                .stream()
                .map(session -> {
                    JobTemplate job = jobTemplateMapper.selectById(session.getJobTemplateId());
                    InterviewReport report = reportMapper.selectOne(
                            new LambdaQueryWrapper<InterviewReport>()
                                    .eq(InterviewReport::getSessionId, session.getId()));
                    return new InterviewHistoryItem(
                            session.getId(),
                            job == null ? "未知岗位" : job.getTitle(),
                            session.getMode(),
                            session.getStatus(),
                            session.getTotalScore(),
                            session.getCreatedAt(),
                            report != null && "SUCCESS".equals(report.getStatus()));
                })
                .toList();
    }

    public StatsOverview stats(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByAsc(InterviewSession::getCreatedAt));
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

        List<InterviewReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getUserId, userId)
                        .eq(InterviewReport::getStatus, "SUCCESS"));
        Map<String, Integer> dimensionAverages = new LinkedHashMap<>();
        for (String key : List.of("accuracy", "depth", "project", "expression", "match")) {
            double avg = reports.stream()
                    .map(report -> readMap(report.getDimensionsJson()).getOrDefault(key, 0))
                    .mapToInt(Integer::intValue)
                    .average().orElse(0);
            dimensionAverages.put(key, (int) Math.round(avg));
        }

        return new StatsOverview(
                sessions.size(),
                completed.size(),
                Math.round(average * 10.0) / 10.0,
                best,
                dimensionAverages,
                trend);
    }

    private void ensureEvaluations(InterviewSession session,
                                    List<InterviewQuestion> questions,
                                    List<InterviewMessage> messages,
                                    List<AnswerEvaluation> evaluations) {
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
                    question, promptText, promptType, candidate.getContent());
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
        return new InterviewReportView(
                report.getId(),
                session.getId(),
                job == null ? "未知岗位" : job.getTitle(),
                session.getMode(),
                report.getOverallScore(),
                readMap(report.getDimensionsJson()),
                report.getSummary(),
                readList(report.getStrengthsJson()),
                readList(report.getWeaknessesJson()),
                readList(report.getSuggestionsJson()),
                report.getStatus(),
                report.getCreatedAt(),
                buildQuestionReviews(questions, messages, evaluations));
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
```

- [ ] **Step 4: 创建报告与统计控制器**

Create `backend/src/main/java/com/offerpilot/interview/web/InterviewReportController.java`:
```java
package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewReportService;
import com.offerpilot.interview.web.dto.InterviewReportView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews/{id}")
public class InterviewReportController {

    private final InterviewReportService reportService;

    public InterviewReportController(InterviewReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/report")
    public ApiResponse<InterviewReportView> generate(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(reportService.generate(userId, id));
    }

    @GetMapping("/report")
    public ApiResponse<InterviewReportView> get(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id) {
        return ApiResponse.ok(reportService.get(userId, id));
    }
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/InterviewStatsController.java`:
```java
package com.offerpilot.interview.web;

import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewReportService;
import com.offerpilot.interview.web.dto.InterviewHistoryItem;
import com.offerpilot.interview.web.dto.StatsOverview;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InterviewStatsController {

    private final InterviewReportService reportService;

    public InterviewStatsController(InterviewReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/interviews/history")
    public ApiResponse<List<InterviewHistoryItem>> history(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reportService.history(userId));
    }

    @GetMapping("/stats/overview")
    public ApiResponse<StatsOverview> overview(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reportService.stats(userId));
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewReportControllerIT test
```

Expected: PASS。若 `/api/interviews/history` 与 `/{id}` 冲突，Spring 会优先匹配静态路径；如失败，检查 `InterviewController` 的 `@GetMapping("/{id}")` 和 `InterviewStatsController` 的 `/interviews/history`。

- [ ] **Step 6: 运行全量后端测试**

```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/service/InterviewReportService.java \
  backend/src/main/java/com/offerpilot/interview/web/InterviewReportController.java \
  backend/src/main/java/com/offerpilot/interview/web/InterviewStatsController.java \
  backend/src/test/java/com/offerpilot/interview/InterviewReportControllerIT.java
git commit -m "feat(report): add report, history and stats APIs"
```

---

## 自检结果

- **Spec coverage:** 五维报告、逐题复盘、历史列表、统计接口均覆盖；练习模式复用逐题点评，模拟面试模式补评。
- **Placeholder scan:** 没有 TBD/TODO；代码完整。
- **Type consistency:** `InterviewReportResult`、`InterviewReportView`、`QuestionReviewView`、`InterviewHistoryItem`、`StatsOverview` 字段前后一致；`dimensions` 使用 `accuracy/depth/project/expression/match` 五个 key。
- **默认可用性:** 默认 Mock 生成器不依赖 AI；`app.ai.enabled=true` 时优先 Spring AI，失败回退 Mock。
