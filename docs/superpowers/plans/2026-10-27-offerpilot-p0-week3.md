# OfferPilot P0 Week 3 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一场可完成的多轮文字面试：开始面试、逐题提问、根据回答自动追问或进入下一题、提示、跳过、结束面试，并通过 SSE 把面试官回复流式推送到前端。

**Architecture:** 在 `interview` 域新增 `InterviewConversationService`，用 Java 状态机控制流程（题目状态、追问次数、提示次数、会话状态），LLM 只负责生成面试官措辞。默认使用确定性 `MockInterviewerReplyGenerator`，`app.ai.enabled=true` 时优先使用 `SpringAiInterviewerReplyGenerator`，失败自动回退 Mock。SSE 使用 Spring MVC `SseEmitter` + 独立线程池，前端用 `fetch` 读取流式响应。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring AI 1.1.8（可选）、MyBatis-Plus 3.5.18、H2/MySQL、Vue3 + Element Plus、SSE。

---

## 0. 范围与完成定义

**本计划覆盖 P0 第 3 周（W3）：**

- 开始面试：`READY → IN_PROGRESS`，发送开场白 + 第一题。
- 多轮回答：提交回答后由状态机决定“追问”还是“下一题”。
- 追问规则：每题最多 2 次；回答过短或含模糊表达时触发追问。
- 提示：每题最多 1 次，提示内容来自题目的参考要点。
- 跳过：当前题标记 `SKIPPED`，进入下一题；无下一题则结束。
- 结束：会话 `COMPLETED`，写入结束语。
- 消息历史：`interview_message` 表持久化面试官/候选人/系统提示消息。
- SSE：面试官回复通过 `POST /api/interviews/{id}/answers` 流式返回。
- 前端：聊天页 `InterviewChatView`，支持流式渲染、提示、跳过、结束。
- 后端测试、前端构建、浏览器端到端验收通过。

**本计划不做：**

- 评分、五维报告、历史成长曲线（W4）。
- RAG 题库检索、额度限流、运营后台（W5）。
- 语音面试（P2）。
- 真实 AI 不是默认路径：默认 Mock；开启 AI 后失败自动回退 Mock。

**完成定义：**

- `mvn -f backend/pom.xml test` 全部通过（W1+W2+W3）。
- `npm --prefix frontend run build` 通过。
- 浏览器端：创建面试 → 开始答题 → 提交回答 → 看到面试官回复（SSE 流式）→ 使用提示 → 跳过一题 → 结束面试 → 状态变为已完成。
- 工作区干净，提交到 `feat/p0-week3` 分支。

**分支策略：** W2 的 PR #2 尚未合并，因此从 `feat/p0-week2` 新建 `feat/p0-week3`。PR #2 合并后可将 W3 rebase 到 `main`。

---

## 1. 文件结构（W3 新增/修改）

```text
backend/
├── src/main/java/com/offerpilot/
│   ├── common/config/AsyncConfig.java                 # 新增：SSE 线程池
│   ├── interview/
│   │   ├── domain/InterviewMessage.java               # 新增
│   │   ├── domain/InterviewSession.java               # 修改：currentQuestionId
│   │   ├── domain/InterviewQuestion.java              # 修改：followUpCount/hintCount/askedAt/answeredAt
│   │   ├── domain/ReplyType.java                      # 新增
│   │   ├── mapper/InterviewMessageMapper.java         # 新增
│   │   ├── service/InterviewerReplyGenerator.java     # 新增
│   │   ├── service/MockInterviewerReplyGenerator.java # 新增
│   │   ├── service/SpringAiInterviewerReplyGenerator.java # 新增，条件启用
│   │   ├── service/InterviewConversationService.java  # 新增：状态机
│   │   ├── web/InterviewConversationController.java   # 新增：start/messages/answers/hint/skip/finish
│   │   ├── web/dto/AnswerRequest.java                 # 新增
│   │   ├── web/dto/AnswerResult.java                  # 新增
│   │   ├── web/dto/HintResult.java                    # 新增
│   │   ├── web/dto/MessageView.java                   # 新增
│   │   ├── web/dto/QuestionView.java                  # 修改：status/followUpCount/hintCount
│   │   └── web/dto/InterviewSessionView.java          # 修改：currentQuestionId
│   └── ...
├── src/main/resources/db/migration/V4__interview_conversation.sql
└── src/test/java/com/offerpilot/interview/
    ├── SchemaV4IT.java
    ├── MockInterviewerReplyGeneratorTest.java
    ├── InterviewConversationServiceTest.java
    └── InterviewConversationControllerIT.java

frontend/
├── src/api/interview.ts                               # 修改：消息/start/hint/skip/finish/SSE
├── src/views/InterviewChatView.vue                    # 新增
├── src/views/InterviewSessionView.vue                 # 修改：开始答题按钮
└── src/router/index.ts                                # 修改：/interviews/:id/chat
```

---

## Task 1: 分支、V4 迁移与领域字段

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__interview_conversation.sql`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/InterviewQuestion.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionView.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Test: `backend/src/test/java/com/offerpilot/interview/SchemaV4IT.java`

- [ ] **Step 1: 创建 W3 分支**

Run:
```bash
cd /e/work
git checkout -b feat/p0-week3
```

Expected: `Switched to a new branch 'feat/p0-week3'`。

- [ ] **Step 2: 先写 V4 表结构测试**

Create `backend/src/test/java/com/offerpilot/interview/SchemaV4IT.java`:
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
class SchemaV4IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsConversationTableAndColumns() {
        assertThat(tableCount("interview_message")).isEqualTo(1);
        assertThat(columnCount("interview_question", "follow_up_count")).isEqualTo(1);
        assertThat(columnCount("interview_question", "hint_count")).isEqualTo(1);
        assertThat(columnCount("interview_question", "asked_at")).isEqualTo(1);
        assertThat(columnCount("interview_question", "answered_at")).isEqualTo(1);
        assertThat(columnCount("interview_session", "current_question_id")).isEqualTo(1);
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

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV4IT test
```

Expected: FAIL，`interview_message` 表或字段不存在。

- [ ] **Step 4: 创建 V4 迁移**

Create `backend/src/main/resources/db/migration/V4__interview_conversation.sql`:
```sql
ALTER TABLE interview_session ADD COLUMN current_question_id BIGINT NULL;

ALTER TABLE interview_question ADD COLUMN follow_up_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_question ADD COLUMN hint_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_question ADD COLUMN asked_at DATETIME(3) NULL;
ALTER TABLE interview_question ADD COLUMN answered_at DATETIME(3) NULL;

CREATE TABLE interview_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NULL,
    role VARCHAR(20) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tokens_in INT NULL,
    tokens_out INT NULL,
    model VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_message_session_id ON interview_message(session_id);
```

- [ ] **Step 5: 修改实体字段**

在 `InterviewSession` 中 `private String status;` 后加入：
```java
    private Long currentQuestionId;
```

在 `InterviewQuestion` 中 `private String status;` 前加入：
```java
    private Integer followUpCount;

    private Integer hintCount;

    private LocalDateTime askedAt;

    private LocalDateTime answeredAt;
```

- [ ] **Step 6: 修改 `QuestionView`**

Create/overwrite `backend/src/main/java/com/offerpilot/interview/web/dto/QuestionView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.util.List;

public record QuestionView(
        Long id,
        Integer seq,
        String type,
        Integer difficulty,
        String content,
        List<String> knowledgeTags,
        List<String> referencePoints,
        String status,
        Integer followUpCount,
        Integer hintCount
) {
}
```

- [ ] **Step 7: 修改 `InterviewSessionView`**

Create/overwrite `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`:
```java
package com.offerpilot.interview.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionView(
        Long id,
        Long jobTemplateId,
        String jobTitle,
        Long resumeId,
        String difficulty,
        Integer durationMinutes,
        String persona,
        String status,
        Long currentQuestionId,
        LocalDateTime createdAt,
        List<QuestionView> questions
) {
}
```

- [ ] **Step 8: 更新 `InterviewService` 的映射**

将 `toQuestionView` 改为：
```java
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
                question.getHintCount());
    }
```

将 `toView` 改为：
```java
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
                session.getStatus(),
                session.getCurrentQuestionId(),
                session.getCreatedAt(),
                questions);
    }
```

- [ ] **Step 9: 更新 W2 测试与前端类型以适配新字段**

后端 W2 测试只断言已有字段，不需要修改。前端 `frontend/src/api/interview.ts` 在 Task 5 统一更新；本任务先不动前端。

- [ ] **Step 10: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV4IT,InterviewControllerIT test
```

Expected: PASS。

- [ ] **Step 11: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/resources/db/migration/V4__interview_conversation.sql \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewQuestion.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/QuestionView.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewService.java \
  backend/src/test/java/com/offerpilot/interview/SchemaV4IT.java
git commit -m "feat(interview): add conversation schema and state fields"
```

---

## Task 2: 消息领域与面试官回复生成器

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/domain/InterviewMessage.java`
- Create: `backend/src/main/java/com/offerpilot/interview/domain/ReplyType.java`
- Create: `backend/src/main/java/com/offerpilot/interview/mapper/InterviewMessageMapper.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/MessageView.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewerReplyGenerator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/MockInterviewerReplyGenerator.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewerReplyGenerator.java`
- Test: `backend/src/test/java/com/offerpilot/interview/MockInterviewerReplyGeneratorTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/MockInterviewerReplyGeneratorTest.java`:
```java
package com.offerpilot.interview;

import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.interview.service.MockInterviewerReplyGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewerReplyGeneratorTest {

    private final MockInterviewerReplyGenerator generator = new MockInterviewerReplyGenerator();

    @Test
    void generatesOpeningAndQuestion() {
        InterviewQuestion question = question("HashMap 的扩容机制？", "[\"扩容 2 倍\"]", "[\"扩容后如何分布？\"]");

        String opening = generator.generate(question, ReplyType.OPENING, null, List.of());
        String next = generator.generate(question, ReplyType.QUESTION, null, List.of());

        assertThat(opening).contains("HashMap");
        assertThat(next).contains("HashMap");
    }

    @Test
    void generatesFollowUpFromHint() {
        InterviewQuestion question = question("HashMap 的扩容机制？", "[\"扩容 2 倍\"]", "[\"扩容后如何分布？\"]");

        String reply = generator.generate(question, ReplyType.FOLLOW_UP, "不知道", List.of());

        assertThat(reply).contains("追问");
        assertThat(reply).isNotBlank();
    }

    @Test
    void generatesClosing() {
        String reply = generator.generate(null, ReplyType.CLOSING, null, List.of());

        assertThat(reply).contains("面试到这里");
    }

    private InterviewQuestion question(String text, String referencePoints, String followUps) {
        InterviewQuestion question = new InterviewQuestion();
        question.setContent(text);
        question.setKnowledgeTags("[\"HashMap\"]");
        question.setReferencePoints(referencePoints);
        question.setFollowUpHints(followUps);
        return question;
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockInterviewerReplyGeneratorTest test
```

Expected: 编译失败，相关类不存在。

- [ ] **Step 3: 创建 `ReplyType`**

Create `backend/src/main/java/com/offerpilot/interview/domain/ReplyType.java`:
```java
package com.offerpilot.interview.domain;

public enum ReplyType {
    OPENING,
    QUESTION,
    FOLLOW_UP,
    CLOSING
}
```

- [ ] **Step 4: 创建 `InterviewMessage` 与 Mapper**

Create `backend/src/main/java/com/offerpilot/interview/domain/InterviewMessage.java`:
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
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private String role;

    private String messageType;

    private String content;

    private Integer tokensIn;

    private Integer tokensOut;

    private String model;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

Create `backend/src/main/java/com/offerpilot/interview/mapper/InterviewMessageMapper.java`:
```java
package com.offerpilot.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.interview.domain.InterviewMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewMessageMapper extends BaseMapper<InterviewMessage> {
}
```

- [ ] **Step 5: 创建 `MessageView`**

Create `backend/src/main/java/com/offerpilot/interview/web/dto/MessageView.java`:
```java
package com.offerpilot.interview.web.dto;

import com.offerpilot.interview.domain.InterviewMessage;

import java.time.LocalDateTime;

public record MessageView(
        Long id,
        Long sessionId,
        Long questionId,
        String role,
        String messageType,
        String content,
        LocalDateTime createdAt
) {

    public static MessageView from(InterviewMessage message) {
        return new MessageView(
                message.getId(),
                message.getSessionId(),
                message.getQuestionId(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt());
    }
}
```

- [ ] **Step 6: 创建生成器接口与 Mock**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewerReplyGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;

import java.util.List;

public interface InterviewerReplyGenerator {

    String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                    List<InterviewMessage> history);
}
```

Create `backend/src/main/java/com/offerpilot/interview/service/MockInterviewerReplyGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockInterviewerReplyGenerator implements InterviewerReplyGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                           List<InterviewMessage> history) {
        return switch (type) {
            case OPENING -> "你好，我是今天的 AI 面试官。我们开始第一题：" + question.getContent();
            case QUESTION -> "好的，我们进入下一题：" + question.getContent();
            case FOLLOW_UP -> followUp(question);
            case CLOSING -> "今天的面试到这里，感谢你的时间。报告会在面试结束后生成。";
        };
    }

    private String followUp(InterviewQuestion question) {
        List<String> hints = readList(question.getFollowUpHints());
        if (!hints.isEmpty()) {
            return "我想追问一下：" + hints.get(0);
        }
        return "能结合一个具体例子，再展开说说吗？";
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

- [ ] **Step 7: 创建 Spring AI 生成器（可选启用）**

Create `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewerReplyGenerator.java`:
```java
package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpringAiInterviewerReplyGenerator implements InterviewerReplyGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiInterviewerReplyGenerator.class);

    private final ChatClient chatClient;
    private final MockInterviewerReplyGenerator fallback;

    public SpringAiInterviewerReplyGenerator(ChatClient.Builder builder,
                                             MockInterviewerReplyGenerator fallback) {
        this.chatClient = builder.build();
        this.fallback = fallback;
    }

    @Override
    public String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                           List<InterviewMessage> history) {
        try {
            String historyText = history.stream()
                    .map(message -> message.getRole() + ": " + message.getContent())
                    .collect(Collectors.joining("\n"));
            String instruction = switch (type) {
                case OPENING -> "请用面试官口吻开场，并自然地提出第一题。";
                case QUESTION -> "请用面试官口吻简短过渡，然后提出下一题。";
                case FOLLOW_UP -> "请针对候选人的回答追问一个更具体的问题，不要重复原题。";
                case CLOSING -> "请用面试官口吻结束面试，感谢候选人。";
            };
            String content = chatClient.prompt()
                    .system("""
                            你是一名资深技术面试官。只输出面试官要说的话，不要解释，不要 markdown。
                            控制在一到三句话，语气专业、自然。
                            """)
                    .user("""
                            当前类型：%s
                            指令：%s
                            当前题目：%s
                            候选人回答：%s
                            最近对话：
                            %s
                            """.formatted(
                            type.name(),
                            instruction,
                            question == null ? "无" : question.getContent(),
                            candidateAnswer == null ? "无" : candidateAnswer,
                            historyText))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return fallback.generate(question, type, candidateAnswer, history);
            }
            return content.trim();
        } catch (Exception ex) {
            log.warn("AI interviewer reply failed, falling back to mock", ex);
            return fallback.generate(question, type, candidateAnswer, history);
        }
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=MockInterviewerReplyGeneratorTest test
```

Expected: PASS。

- [ ] **Step 9: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/domain/ReplyType.java \
  backend/src/main/java/com/offerpilot/interview/domain/InterviewMessage.java \
  backend/src/main/java/com/offerpilot/interview/mapper/InterviewMessageMapper.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/MessageView.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewerReplyGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/MockInterviewerReplyGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewerReplyGenerator.java \
  backend/src/test/java/com/offerpilot/interview/MockInterviewerReplyGeneratorTest.java
git commit -m "feat(interview): add messages and interviewer reply generators"
```

---

## Task 3: 面试对话状态机

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerRequest.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerResult.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/HintResult.java`
- Create: `backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java`
- Test: `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java`

- [ ] **Step 1: 先写失败测试**

Create `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java`:
```java
package com.offerpilot.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.interview.service.InterviewConversationService;
import com.offerpilot.interview.service.InterviewService;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
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
    private ObjectMapper objectMapper;

    @Test
    void runsConversationStateMachine() throws Exception {
        User user = createUser();
        Resume resume = createResume(user);
        Long jobId = jobTemplateMapper.selectList(null).get(0).getId();
        InterviewSessionView session = interviewService.create(user.getId(),
                new CreateInterviewRequest(jobId, resume.getId(), "STANDARD", 30, "STANDARD"));

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

        HintResultHolder hint = new HintResultHolder(
                conversationService.hint(user.getId(), session.id()).hint());
        assertThat(hint.hint()).isNotBlank();
        assertThatThrownBy(() -> conversationService.hint(user.getId(), session.id()))
                .isInstanceOf(BusinessException.class);

        InterviewSessionView skipped = conversationService.skip(user.getId(), session.id());
        assertThat(skipped.status()).isIn("IN_PROGRESS", "COMPLETED");

        InterviewSessionView finished = conversationService.finish(user.getId(), session.id());
        assertThat(finished.status()).isEqualTo("COMPLETED");
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

    private record HintResultHolder(String hint) {
    }
}
```

> 说明：测试中的 `HintResultHolder` 只是为了避免直接依赖 DTO 的完整类型；实现后可以直接断言 `conversationService.hint(...).hint()`。如果编译正常，可简化为直接断言。计划保留该写法以确保测试可编译。

- [ ] **Step 2: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewConversationServiceTest test
```

Expected: 编译失败，`InterviewConversationService` / DTO 不存在。

- [ ] **Step 3: 创建 DTO**

Create `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerRequest.java`:
```java
package com.offerpilot.interview.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotNull Long questionId,
        @NotBlank @Size(max = 4000) String content
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/AnswerResult.java`:
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
        boolean completed
) {
}
```

Create `backend/src/main/java/com/offerpilot/interview/web/dto/HintResult.java`:
```java
package com.offerpilot.interview.web.dto;

public record HintResult(Long messageId, String hint) {
}
```

- [ ] **Step 4: 创建 `InterviewConversationService`**

Create `backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java`:
```java
package com.offerpilot.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.domain.ReplyType;
import com.offerpilot.interview.mapper.InterviewMessageMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewConversationService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewService interviewService;
    private final InterviewerReplyGenerator replyGenerator;
    private final ObjectMapper objectMapper;

    public InterviewConversationService(InterviewSessionMapper sessionMapper,
                                         InterviewQuestionMapper questionMapper,
                                         InterviewMessageMapper messageMapper,
                                         InterviewService interviewService,
                                         InterviewerReplyGenerator replyGenerator,
                                         ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.interviewService = interviewService;
        this.replyGenerator = replyGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionView start(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return interviewService.get(userId, sessionId);
        }
        if ("READY".equals(session.getStatus())) {
            session.setStatus("IN_PROGRESS");
            session.setStartedAt(LocalDateTime.now());
            InterviewQuestion first = nextPending(sessionId);
            if (first == null) {
                complete(session);
                addMessage(sessionId, null, "INTERVIEWER", "CLOSING",
                        replyGenerator.generate(null, ReplyType.CLOSING, null, List.of()));
                sessionMapper.updateById(session);
                return interviewService.get(userId, sessionId);
            }
            ask(session, first);
            addMessage(sessionId, first.getId(), "INTERVIEWER", "OPENING",
                    replyGenerator.generate(first, ReplyType.OPENING, null, history(sessionId)));
            sessionMapper.updateById(session);
        }
        return interviewService.get(userId, sessionId);
    }

    public List<MessageView> messages(Long userId, Long sessionId) {
        loadOwned(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId))
                .stream()
                .map(MessageView::from)
                .toList();
    }

    @Transactional
    public AnswerResult answer(Long userId, Long sessionId, AnswerRequest request) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前面试不在进行中");
        }

        InterviewQuestion question = questionMapper.selectById(request.questionId());
        if (question == null || !sessionId.equals(question.getSessionId())
                || !"ASKED".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前题目不可回答");
        }

        InterviewMessage candidate = addMessage(sessionId, question.getId(), "CANDIDATE", "ANSWER", request.content());
        List<InterviewMessage> historyMessages = history(sessionId);

        if (shouldFollowUp(question, request.content())) {
            int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
            question.setFollowUpCount(count + 1);
            questionMapper.updateById(question);
            String reply = replyGenerator.generate(question, ReplyType.FOLLOW_UP, request.content(), historyMessages);
            InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "FOLLOW_UP", reply);
            return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                    "FOLLOW_UP", reply, session.getStatus(), question.getId(), false);
        }

        question.setStatus("ANSWERED");
        question.setAnsweredAt(LocalDateTime.now());
        questionMapper.updateById(question);

        InterviewQuestion next = nextPending(sessionId);
        if (next != null) {
            ask(session, next);
            String reply = replyGenerator.generate(next, ReplyType.QUESTION, request.content(), historyMessages);
            InterviewMessage interviewer = addMessage(sessionId, next.getId(), "INTERVIEWER", "QUESTION", reply);
            sessionMapper.updateById(session);
            return new AnswerResult(candidate.getId(), interviewer.getId(), next.getId(),
                    "QUESTION", reply, session.getStatus(), next.getId(), false);
        }

        complete(session);
        sessionMapper.updateById(session);
        String reply = replyGenerator.generate(question, ReplyType.CLOSING, request.content(), historyMessages);
        InterviewMessage interviewer = addMessage(sessionId, question.getId(), "INTERVIEWER", "CLOSING", reply);
        return new AnswerResult(candidate.getId(), interviewer.getId(), question.getId(),
                "CLOSING", reply, "COMPLETED", null, true);
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
                    replyGenerator.generate(next, ReplyType.QUESTION, null, history(sessionId)));
            sessionMapper.updateById(session);
            return interviewService.get(userId, sessionId);
        }

        complete(session);
        sessionMapper.updateById(session);
        addMessage(sessionId, current == null ? null : current.getId(), "INTERVIEWER", "CLOSING",
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId)));
        return interviewService.get(userId, sessionId);
    }

    @Transactional
    public InterviewSessionView finish(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
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
                replyGenerator.generate(current, ReplyType.CLOSING, null, history(sessionId)));
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

    private InterviewQuestion nextPending(Long sessionId) {
        return questionMapper.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .eq(InterviewQuestion::getStatus, "PENDING")
                .orderByAsc(InterviewQuestion::getSeq)
                .last("LIMIT 1"));
    }

    private void ask(InterviewSession session, InterviewQuestion question) {
        question.setStatus("ASKED");
        question.setAskedAt(LocalDateTime.now());
        questionMapper.updateById(question);
        session.setCurrentQuestionId(question.getId());
    }

    private void complete(InterviewSession session) {
        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        session.setCurrentQuestionId(null);
    }

    private boolean shouldFollowUp(InterviewQuestion question, String answer) {
        int count = question.getFollowUpCount() == null ? 0 : question.getFollowUpCount();
        if (count >= 2) {
            return false;
        }
        if (readList(question.getFollowUpHints()).isEmpty()) {
            return false;
        }
        String text = answer == null ? "" : answer.trim();
        boolean vague = text.length() < 60
                || text.contains("不知道")
                || text.contains("不太清楚")
                || text.contains("没做过")
                || text.contains("大概")
                || text.contains("可能");
        return vague;
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

- [ ] **Step 5: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewConversationServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/interview/web/dto/AnswerRequest.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/AnswerResult.java \
  backend/src/main/java/com/offerpilot/interview/web/dto/HintResult.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java
git commit -m "feat(interview): add conversation state machine"
```

---

## Task 4: SSE 控制器与对话 API

**Files:**
- Create: `backend/src/main/java/com/offerpilot/common/config/AsyncConfig.java`
- Create: `backend/src/main/java/com/offerpilot/interview/web/InterviewConversationController.java`
- Test: `backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java`

- [ ] **Step 1: 创建 SSE 线程池**

Create `backend/src/main/java/com/offerpilot/common/config/AsyncConfig.java`:
```java
package com.offerpilot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "interviewSseExecutor")
    public ThreadPoolTaskExecutor interviewSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("interview-sse-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: 先写控制器测试**

Create `backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java`:
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewConversationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startsAnswersHintsSkipsAndFinishes() throws Exception {
        String token = registerAndToken();
        long resumeId = uploadResume(token);
        long sessionId = createInterview(token, resumeId);

        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.currentQuestionId").isNumber());

        MvcResult sessionResult = mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(sessionResult.getResponse().getContentAsString()).get("data");
        long questionId = session.get("currentQuestionId").asLong();

        MvcResult sse = mockMvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", questionId,
                                "content", "不知道"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(sse))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"replyType\"")))
                .andExpect(content().string(containsString("\"completed\"")));

        mockMvc.perform(post("/api/interviews/" + sessionId + "/hint")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint").isNotEmpty());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/skip")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/interviews/" + sessionId + "/finish")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/interviews/" + sessionId + "/messages")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private String registerAndToken() throws Exception {
        String email = "conv-api-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Conv Tester"))))
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
                                "persona", "STANDARD"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewConversationControllerIT test
```

Expected: FAIL，`/start` 等端点不存在（404）。

- [ ] **Step 4: 创建 `InterviewConversationController`**

Create `backend/src/main/java/com/offerpilot/interview/web/InterviewConversationController.java`:
```java
package com.offerpilot.interview.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.api.ApiResponse;
import com.offerpilot.interview.service.InterviewConversationService;
import com.offerpilot.interview.web.dto.AnswerRequest;
import com.offerpilot.interview.web.dto.AnswerResult;
import com.offerpilot.interview.web.dto.HintResult;
import com.offerpilot.interview.web.dto.InterviewSessionView;
import com.offerpilot.interview.web.dto.MessageView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews/{id}")
public class InterviewConversationController {

    private final InterviewConversationService conversationService;
    private final ThreadPoolTaskExecutor sseExecutor;
    private final ObjectMapper objectMapper;

    public InterviewConversationController(InterviewConversationService conversationService,
                                           @Qualifier("interviewSseExecutor") ThreadPoolTaskExecutor sseExecutor,
                                           ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.sseExecutor = sseExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ApiResponse<InterviewSessionView> start(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.start(userId, id));
    }

    @GetMapping("/messages")
    public ApiResponse<List<MessageView>> messages(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(userId, id));
    }

    @PostMapping(value = "/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@AuthenticationPrincipal Long userId,
                             @PathVariable Long id,
                             @Valid @RequestBody AnswerRequest request) {
        AnswerResult result = conversationService.answer(userId, id, request);
        SseEmitter emitter = new SseEmitter(60_000L);
        sseExecutor.execute(() -> stream(emitter, result));
        return emitter;
    }

    @PostMapping("/hint")
    public ApiResponse<HintResult> hint(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.hint(userId, id));
    }

    @PostMapping("/skip")
    public ApiResponse<InterviewSessionView> skip(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.skip(userId, id));
    }

    @PostMapping("/finish")
    public ApiResponse<InterviewSessionView> finish(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(conversationService.finish(userId, id));
    }

    private void stream(SseEmitter emitter, AnswerResult result) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("interviewerMessageId", result.interviewerMessageId());
            meta.put("questionId", result.questionId());
            meta.put("replyType", result.replyType());
            emitter.send(SseEmitter.event().name("meta").data(toJson(meta)));

            for (String chunk : chunks(result.replyText())) {
                emitter.send(SseEmitter.event().name("delta").data(toJson(Map.of("text", chunk))));
                Thread.sleep(25);
            }

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionStatus", result.sessionStatus());
            done.put("currentQuestionId", result.currentQuestionId());
            done.put("completed", result.completed());
            emitter.send(SseEmitter.event().name("done").data(toJson(done)));
            emitter.complete();
        } catch (Exception ex) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(toJson(Map.of("message", "面试官回复失败"))));
            } catch (IOException ignored) {
                // client already disconnected
            }
            emitter.completeWithError(ex);
        }
    }

    private List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        for (int i = 0; i < text.length(); i += 24) {
            chunks.add(text.substring(i, Math.min(text.length(), i + 24)));
        }
        return chunks;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=InterviewConversationControllerIT test
```

Expected: PASS。若 SSE 测试超时，检查 `ThreadPoolTaskExecutor` 是否初始化、`SseEmitter` 是否在 60 秒内 complete；`asyncDispatch` 默认等待 10 秒，流式文本约 10 个 chunk × 25ms，足够。

- [ ] **Step 6: 运行全量后端测试**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [ ] **Step 7: 提交**

Run:
```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/common/config/AsyncConfig.java \
  backend/src/main/java/com/offerpilot/interview/web/InterviewConversationController.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java
git commit -m "feat(interview): add SSE conversation APIs"
```

---

## Task 5: 前端对话 API 与 SSE 客户端

**Files:**
- Modify: `frontend/src/api/interview.ts`

- [ ] **Step 1: 更新 `interview.ts`**

将 `frontend/src/api/interview.ts` 改为：
```ts
import http from './http'

export interface QuestionView {
  id: number
  seq: number
  type: string
  difficulty: number
  content: string
  knowledgeTags: string[]
  referencePoints: string[]
  status: string
  followUpCount: number
  hintCount: number
}

export interface InterviewSession {
  id: number
  jobTemplateId: number
  jobTitle: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  status: string
  currentQuestionId: number | null
  createdAt: string
  questions: QuestionView[]
}

export interface MessageView {
  id: number
  sessionId: number
  questionId: number | null
  role: string
  messageType: string
  content: string
  createdAt: string
}

export interface CreateInterviewRequest {
  jobTemplateId: number
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
}

export interface HintResult {
  messageId: number
  hint: string
}

export interface StreamHandlers {
  onMeta?: (meta: { interviewerMessageId: number; questionId: number; replyType: string }) => void
  onDelta?: (text: string) => void
  onDone?: (done: { sessionStatus: string; currentQuestionId: number | null; completed: boolean }) => void
  onError?: (message: string) => void
}

export const interviewApi = {
  create: (data: CreateInterviewRequest) =>
    http.post<never, InterviewSession>('/interviews', data),

  get: (id: number) => http.get<never, InterviewSession>(`/interviews/${id}`),

  list: () => http.get<never, InterviewSession[]>('/interviews'),

  start: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/start`),

  messages: (id: number) => http.get<never, MessageView[]>(`/interviews/${id}/messages`),

  hint: (id: number) => http.post<never, HintResult>(`/interviews/${id}/hint`),

  skip: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/skip`),

  finish: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/finish`)
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

export async function streamAnswer(
  sessionId: number,
  questionId: number,
  content: string,
  handlers: StreamHandlers
): Promise<void> {
  const token = localStorage.getItem(ACCESS_KEY)
  const response = await fetch(`/api/interviews/${sessionId}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ questionId, content })
  })

  if (response.status === 401) {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
    window.location.assign('/login')
    throw new Error('登录已过期')
  }

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || '提交回答失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const raw of events) {
      const lines = raw.split('\n')
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n')
      if (!event || !data) continue

      const payload = JSON.parse(data)
      if (event === 'meta') handlers.onMeta?.(payload)
      else if (event === 'delta') handlers.onDelta?.(payload.text)
      else if (event === 'done') handlers.onDone?.(payload)
      else if (event === 'error') handlers.onError?.(payload.message)
    }
  }
}
```

- [ ] **Step 2: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功（W2 的页面会因 `QuestionView` 新增字段而自动兼容）。

- [ ] **Step 3: 提交**

Run:
```bash
cd /e/work
git add frontend/src/api/interview.ts
git commit -m "feat(frontend): add conversation API and SSE client"
```

---

## Task 6: 前端聊天页

**Files:**
- Create: `frontend/src/views/InterviewChatView.vue`
- Modify: `frontend/src/views/InterviewSessionView.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 创建聊天页**

Create `frontend/src/views/InterviewChatView.vue`:
```vue
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  interviewApi,
  streamAnswer,
  type InterviewSession,
  type MessageView,
  type QuestionView
} from '@/api/interview'

const route = useRoute()
const router = useRouter()

const session = ref<InterviewSession | null>(null)
const messages = ref<MessageView[]>([])
const loading = ref(true)
const sending = ref(false)
const input = ref('')
const streamingMessageId = ref<number | null>(null)
const messagesEl = ref<HTMLElement | null>(null)

const typeLabels: Record<string, string> = {
  BASIC: '基础',
  PROJECT: '项目',
  SCENARIO: '场景',
  AI: 'AI 工程'
}

const currentQuestion = computed<QuestionView | null>(() => {
  if (!session.value || session.value.currentQuestionId == null) return null
  return session.value.questions.find((q) => q.id === session.value?.currentQuestionId) ?? null
})

const completed = computed(() => session.value?.status === 'COMPLETED')
const canSend = computed(() =>
  Boolean(currentQuestion.value) && !sending.value && !completed.value && input.value.trim().length > 0
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    session.value = await interviewApi.get(id)
    if (session.value.status === 'READY') {
      session.value = await interviewApi.start(id)
    }
    messages.value = await interviewApi.messages(id)
    await scrollToBottom()
  } catch {
    router.replace('/')
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!canSend.value || !session.value || !currentQuestion.value) return
  const answer = input.value.trim()
  const id = session.value.id
  const questionId = currentQuestion.value.id
  input.value = ''

  const tempCandidateId = -Date.now()
  const tempInterviewerId = tempCandidateId - 1
  messages.value.push({
    id: tempCandidateId,
    sessionId: id,
    questionId,
    role: 'CANDIDATE',
    messageType: 'ANSWER',
    content: answer,
    createdAt: new Date().toISOString()
  })
  messages.value.push({
    id: tempInterviewerId,
    sessionId: id,
    questionId,
    role: 'INTERVIEWER',
    messageType: 'QUESTION',
    content: '',
    createdAt: new Date().toISOString()
  })
  streamingMessageId.value = tempInterviewerId
  sending.value = true
  await scrollToBottom()

  try {
    await streamAnswer(id, questionId, answer, {
      onMeta: (meta) => {
        const message = messages.value.find((m) => m.id === streamingMessageId.value)
        if (message) {
          message.id = meta.interviewerMessageId
          message.questionId = meta.questionId
          message.messageType = meta.replyType
          streamingMessageId.value = meta.interviewerMessageId
        }
      },
      onDelta: (text) => {
        const message = messages.value.find((m) => m.id === streamingMessageId.value)
        if (message) message.content += text
        scrollToBottom()
      },
      onDone: async (done) => {
        if (session.value) {
          session.value.status = done.sessionStatus
          session.value.currentQuestionId = done.currentQuestionId
        }
        sending.value = false
        streamingMessageId.value = null
        await load()
      },
      onError: async (message) => {
        ElMessage.error(message)
        sending.value = false
        streamingMessageId.value = null
        await load()
      }
    })
    if (sending.value) {
      sending.value = false
      streamingMessageId.value = null
      await load()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交回答失败')
    sending.value = false
    streamingMessageId.value = null
    await load()
  }
}

async function handleHint() {
  if (!session.value) return
  try {
    const result = await interviewApi.hint(session.value.id)
    ElMessage.success(`提示：${result.hint}`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function handleSkip() {
  if (!session.value) return
  try {
    await interviewApi.skip(session.value.id)
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function handleFinish() {
  if (!session.value) return
  try {
    await ElMessageBox.confirm('确定要结束这场面试吗？', '结束面试', {
      type: 'warning',
      confirmButtonText: '结束',
      cancelButtonText: '继续面试'
    })
    await interviewApi.finish(session.value.id)
    await load()
  } catch {
    // 用户取消或接口失败
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

watch(messages, scrollToBottom, { deep: true })

function backToPreview() {
  router.push(`/interviews/${route.params.id}`)
}
</script>

<template>
  <div class="chat-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="backToPreview">← 返回蓝图</button>
      <div class="brand">OfferPilot</div>
      <span class="status" :class="{ done: completed }">{{ completed ? '面试已结束' : '面试进行中' }}</span>
    </header>

    <main v-loading="loading" class="chat-main">
      <section ref="messagesEl" class="messages">
        <template v-for="message in messages" :key="message.id">
          <div v-if="message.role === 'SYSTEM'" class="system-message">
            {{ message.content }}
          </div>
          <div v-else class="message-row" :class="message.role === 'CANDIDATE' ? 'right' : 'left'">
            <div class="avatar">{{ message.role === 'CANDIDATE' ? '你' : 'AI' }}</div>
            <div class="bubble">
              <span v-if="message.id === streamingMessageId && !message.content" class="typing">AI 正在思考…</span>
              <span v-else class="bubble-text">{{ message.content }}</span>
            </div>
          </div>
        </template>
      </section>

      <section v-if="!completed" class="composer">
        <div v-if="currentQuestion" class="question-bar">
          <span class="q-label">当前 Q{{ currentQuestion.seq }}</span>
          <el-tag size="small" effect="plain">{{ typeLabels[currentQuestion.type] || currentQuestion.type }}</el-tag>
          <span class="q-meta">追问 {{ currentQuestion.followUpCount }}/2 · 提示 {{ currentQuestion.hintCount }}/1</span>
        </div>
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入你的回答，Enter 发送，Shift + Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send"
        />
        <div class="actions">
          <div class="actions-left">
            <el-button
              size="small"
              :disabled="sending || !currentQuestion || currentQuestion.hintCount >= 1"
              @click="handleHint"
            >
              提示
            </el-button>
            <el-button size="small" :disabled="sending" @click="handleSkip">跳过</el-button>
            <el-button size="small" type="danger" plain :disabled="sending" @click="handleFinish">
              结束面试
            </el-button>
          </div>
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </section>

      <section v-else class="finished">
        <el-result icon="success" title="面试已结束" sub-title="评分报告与薄弱点分析将在 W4 开放">
          <template #extra>
            <el-button type="primary" @click="router.push('/')">返回岗位</el-button>
          </template>
        </el-result>
      </section>
    </main>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
  flex: none;
}

.brand {
  color: #17375e;
  font-size: 20px;
  font-weight: 800;
}

.back-link {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}

.status {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.status.done {
  background: #f0f9eb;
  color: #529b2e;
}

.chat-main {
  flex: 1;
  min-height: 0;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 24px 24px;
  display: flex;
  flex-direction: column;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 4px 24px;
  scroll-behavior: smooth;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  align-items: flex-start;
}

.message-row.right {
  flex-direction: row-reverse;
}

.avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #1f4e79;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.message-row.right .avatar {
  background: #2e75b6;
}

.bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e4ecf5;
  color: #33475b;
  font-size: 15px;
  line-height: 1.8;
  box-shadow: 0 4px 14px rgba(31, 78, 121, 0.05);
}

.message-row.right .bubble {
  background: #2e75b6;
  border-color: #2e75b6;
  color: #fff;
}

.bubble-text {
  white-space: pre-wrap;
}

.typing {
  color: #8a97a8;
}

.message-row.right .typing {
  color: rgba(255, 255, 255, 0.8);
}

.system-message {
  max-width: 76%;
  margin: 0 auto 18px;
  padding: 9px 14px;
  border-radius: 999px;
  background: #fff7e6;
  border: 1px solid #f3d19e;
  color: #b88230;
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
}

.composer {
  flex: none;
  padding-top: 12px;
  border-top: 1px solid #e4ecf5;
}

.question-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.q-label {
  color: #17375e;
  font-size: 13px;
  font-weight: 700;
}

.q-meta {
  color: #8a97a8;
  font-size: 12px;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.actions-left {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.finished {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .chat-main {
    padding: 12px 14px 18px;
  }

  .bubble {
    max-width: 82%;
  }

  .actions {
    align-items: flex-end;
    flex-direction: column;
  }
}
</style>
```

- [ ] **Step 2: 修改预览页的开始按钮**

在 `frontend/src/views/InterviewSessionView.vue` 的 `<script setup>` 中加入：
```ts
const starting = ref(false)

async function startInterview() {
  if (!session.value) return
  starting.value = true
  try {
    await interviewApi.start(session.value.id)
    router.push(`/interviews/${session.value.id}/chat`)
  } finally {
    starting.value = false
  }
}
```

将模板中的：
```vue
<el-button type="primary" size="large" disabled>开始答题（W3 开放）</el-button>
```
改为：
```vue
<el-button type="primary" size="large" :loading="starting" @click="startInterview">
  开始答题
</el-button>
```

- [ ] **Step 3: 添加路由**

在 `frontend/src/router/index.ts` 的 routes 中加入：
```ts
    {
      path: '/interviews/:id/chat',
      name: 'interview-chat',
      component: () => import('@/views/InterviewChatView.vue'),
      meta: { requiresAuth: true }
    },
```

> 注意：`/interviews/:id` 和 `/interviews/:id/chat` 不冲突；路由顺序不影响匹配。

- [ ] **Step 4: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add frontend/src/views/InterviewChatView.vue \
  frontend/src/views/InterviewSessionView.vue \
  frontend/src/router/index.ts
git commit -m "feat(frontend): add interview chat page with streaming"
```

---

## Task 7: 端到端验收与文档

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 运行后端全量测试**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [ ] **Step 2: 构建前端**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 3: 启动服务并做浏览器端验收**

启动后端（H2）与前端：
```bash
cd /e/work
mvn -f backend/pom.xml spring-boot:run
```
另一个终端：
```bash
cd /e/work/frontend
npm run dev
```

浏览器验收步骤：

1. 注册/登录，选择岗位，上传简历（可复用 W2 的 `sample-resume.docx`），创建 30 分钟面试。
2. 在蓝图预览页点击“开始答题”，进入聊天页。
3. 应看到面试官开场白 + 第一题。
4. 输入“不知道”并发送，应看到 SSE 流式追问。
5. 再次输入一段较长的回答，应进入下一题或结束。
6. 点击“提示”，应出现系统提示消息；再次点击应被禁用。
7. 点击“跳过”，应进入下一题。
8. 点击“结束面试”，确认后显示“面试已结束”。
9. 刷新页面，消息历史应保留。

- [ ] **Step 4: 更新 README**

将“当前进度”改为：
```markdown
当前进度：P0 Week 3 已完成（多轮对话、SSE 流式、追问/提示/跳过、面试状态机）。

下一步：P0 Week 4（单题评分、五维报告、历史成长曲线）。
```

在 API 表格中追加：
```markdown
| POST | `/api/interviews/{id}/start` | 开始面试 |
| GET | `/api/interviews/{id}/messages` | 面试消息历史 |
| POST | `/api/interviews/{id}/answers` | 提交回答（SSE 流式返回） |
| POST | `/api/interviews/{id}/hint` | 请求提示（每题 1 次） |
| POST | `/api/interviews/{id}/skip` | 跳过当前题 |
| POST | `/api/interviews/{id}/finish` | 结束面试 |
```

- [ ] **Step 5: 提交**

Run:
```bash
cd /e/work
git add README.md
git commit -m "docs: mark P0 week 3 complete"
git status --short
```

Expected: 工作区干净。

---

## 自检结果

- **Spec coverage:** W3 的多轮对话、SSE、追问、提示、跳过、状态机均已覆盖；评分/报告/RAG 明确留到 W4-W5。
- **Placeholder scan:** 没有 TBD/TODO；所有代码步骤都有完整代码。
- **Type consistency:** `InterviewSession.currentQuestionId`、`QuestionView.status/followUpCount/hintCount`、`AnswerRequest`、`AnswerResult`、`HintResult`、`MessageView` 在后端与前端保持一致；SSE 事件名 `meta/delta/done/error` 与前端解析一致。
- **默认可用性:** `app.ai.enabled=false` 时全部功能可用；开启 AI 后 `SpringAiInterviewerReplyGenerator` 优先，失败自动回退 Mock。

---

## 执行记录（2026-10-27）

- 分支：`feat/p0-week3`（基于 `feat/p0-week2`）。
- 后端测试：`mvn -f backend/pom.xml test` → **28 tests, 0 failures, 0 errors**。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 浏览器端到端（Playwright + Chrome）：
  - 注册 → 上传 DOCX → 创建面试 → 蓝图预览 → 点击“开始答题”进入聊天页。
  - 首条消息为面试官开场白 + 第一题。
  - 输入“不知道”提交 → SSE 流式返回追问「扩容后元素如何重新分布？」。
  - 点击“提示” → 出现系统提示消息「可以从数组+链表+红黑树入手」。
  - 点击“跳过” → 进入下一题「线程池的核心参数有哪些？」。
  - 点击“结束面试”并确认 → 状态变为“面试已结束”，显示 W4 报告占位。
  - 消息历史共 6 条（面试官开场、候选人回答、追问、系统提示、下一题、结束语）。
- 实施中的调整：
  - `InterviewConversationService` 内部消息历史使用 `history(sessionId)`，避免与对外 `messages(userId, sessionId)` 混淆。
  - `SecurityConfig` 增加 `DispatcherType.ASYNC` / `ERROR` 放行，解决 MockMvc SSE 异步派发被 Spring Security 拦截的问题。
  - 前端 SSE 客户端使用 `fetch + ReadableStream`，解析 `meta/delta/done/error` 事件；流结束缺少 `done` 时有兜底刷新。
  - 作答体验优化：聊天页仅首次加载显示全屏 loading；回答/提示/跳过/结束后的数据刷新改为静默刷新，避免回复后出现转圈；发送按钮不再使用 loading spinner，改为“面试官思考中…”文案，配合面试官气泡内的“AI 正在思考…”提示。
- 尚未推送：W3 分支提交在本地；W2 的 PR #2 仍未合并。
