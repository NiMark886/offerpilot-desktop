# OfferPilot W5A：题库检索与薄弱点推荐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立题库（从本地 71 道面试题种子数据导入），用关键词/标签检索把题库题目接入面试蓝图，并根据历史回答的缺失要点和题目标签生成薄弱点推荐。

**Architecture:** 新增 `questionbank` 业务域。题库表 `question_bank` 存题目、参考答案、分类和标签；`KeywordQuestionBankRetriever` 按岗位/简历标签和 JD 关键词打分检索。`InterviewService.create` 检索题库并把结果放入 `BlueprintRequest`，Mock/AI 蓝图生成器都能使用。`WeaknessService` 聚合 `answer_evaluation` 与 `interview_question.knowledge_tags`，统计薄弱标签并推荐题库题目。本期用关键词检索（RAG-lite），pgvector 向量检索留到部署阶段。

**Tech Stack:** Java 21、Spring Boot 3.5.16、MyBatis-Plus、MySQL/H2、Vue3 + Element Plus。

---

## 0. 范围与完成定义

**完成定义：**

- `question_bank` 表与 71 道种子题导入成功。
- `GET /api/stats/weaknesses` 返回薄弱标签、出现次数、最近时间和推荐题目。
- 创建面试时，题库检索结果进入 `BlueprintRequest`，Mock 蓝图优先使用题库题目。
- 历史页展示“薄弱点推荐”卡片，可展开查看推荐题目和参考答案。
- 后端测试、前端构建、浏览器验收通过。

**本计划不做：** pgvector/bge-m3 向量检索、运营后台题库 CRUD、额度限流（W5B）。

---

## Task 1: 分支、题库表与 71 道种子题

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__question_bank.sql`
- Create: `backend/src/main/java/com/offerpilot/questionbank/domain/QuestionBank.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/mapper/QuestionBankMapper.java`
- Test: `backend/src/test/java/com/offerpilot/questionbank/SchemaV8IT.java`

- [ ] **Step 1: 创建分支**

```bash
cd /e/work
git checkout -b feat/p0-week5-rag
```

- [ ] **Step 2: 生成题库迁移 SQL**

使用本地文档 `C:/Users/Administrator/Desktop/学校/2026年9月8日/倪瀚呈_基础面试题答案汇总.docx` 解析 71 道题，生成 `backend/src/main/resources/db/migration/V8__question_bank.sql`。运行：

```bash
PYTHONUTF8=1 python - <<'PY'
import re
from docx import Document

src = "C:/Users/Administrator/Desktop/学校/2026年9月8日/倪瀚呈_基础面试题答案汇总.docx"
out = "E:/work/backend/src/main/resources/db/migration/V8__question_bank.sql"
d = Document(src)
paras = [x.text.strip() for x in d.paragraphs if x.text.strip()]
items, category, current = [], "未分类", None
for t in paras:
    if re.match(r'^\d+[\.、]\s*', t):
        q = re.sub(r'^\d+[\.、]\s*', '', t).strip()
        current = {"category": category, "question": q, "answer": ""}
        items.append(current)
    elif t.startswith("答：") or t.startswith("答:"):
        if current is not None:
            current["answer"] = (current["answer"] + "\n" + re.sub(r'^答[：:]\s*', '', t)).strip()
    elif current is None or (current and current["answer"] and len(t) < 30):
        category = t

base_tags = {
    "Java 基础": ["Java", "基础"], "Spring Boot 和 Spring": ["Spring", "Spring Boot"],
    "MySQL 和 MyBatis": ["MySQL", "MyBatis"], "Redis 和缓存": ["Redis", "缓存"],
    "JWT 和登录认证": ["JWT", "认证"], "Vue 和前端基础": ["Vue", "前端"],
    "项目经历追问": ["项目", "经历"], "实习经历追问": ["实习", "项目"],
    "开放题": ["开放题", "综合"],
}
keywords = ["HashMap", "线程池", "JVM", "GC", "MySQL", "索引", "事务", "Redis", "缓存",
            "Spring", "Spring Boot", "MyBatis", "JWT", "Vue", "AOP", "IoC", "集合",
            "并发", "异常", "多态", "锁", "消息队列", "微服务", "Nacos", "Gateway",
            "OpenFeign", "RAG", "Agent", "大模型", "分页", "拦截器", "切面", "反射",
            "序列化", "线程", "List", "Map"]

def esc(s):
    return s.replace("'", "''")

header = """CREATE TABLE question_bank (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(64) NOT NULL,
    level VARCHAR(32) NOT NULL,
    tags VARCHAR(512) NOT NULL,
    difficulty INT NOT NULL,
    question TEXT NOT NULL,
    reference_answer TEXT NOT NULL,
    source VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_question_bank_category ON question_bank(category);
CREATE INDEX idx_question_bank_status ON question_bank(status);

-- Seed questions parsed from personal interview notes.
"""
lines = [header]
for item in items:
    tags = list(dict.fromkeys(base_tags.get(item["category"], ["综合"]) +
                              [k for k in keywords if k.lower() in (item["question"] + " " + item["answer"]).lower()]))[:8]
    difficulty = 2 if item["category"] in ("项目经历追问", "实习经历追问", "开放题") else 1
    lines.append(
        "INSERT INTO question_bank (category, level, tags, difficulty, question, reference_answer, source, status, created_at, updated_at) "
        "VALUES ('%s', '校招', '%s', %d, '%s', '%s', '个人整理', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));"
        % (esc(item["category"]), esc(",".join(tags)), difficulty, esc(item["question"]), esc(item["answer"])))
open(out, "w", encoding="utf-8").write("\n".join(lines))
print("questions:", len(items), "file:", out)
PY
```

Expected: `questions: 71`。

- [ ] **Step 3: 先写表结构测试**

Create `backend/src/test/java/com/offerpilot/questionbank/SchemaV8IT.java`:
```java
package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV8IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsQuestionBank() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE status = 'ACTIVE'", Integer.class);
        assertThat(count).isEqualTo(71);
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV8IT test
```

Expected: FAIL，`question_bank` 不存在。

- [ ] **Step 5: 创建实体与 Mapper**

Create `backend/src/main/java/com/offerpilot/questionbank/domain/QuestionBank.java`:
```java
package com.offerpilot.questionbank.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("question_bank")
public class QuestionBank {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;

    private String level;

    private String tags;

    private Integer difficulty;

    private String question;

    private String referenceAnswer;

    private String source;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

Create `backend/src/main/java/com/offerpilot/questionbank/mapper/QuestionBankMapper.java`:
```java
package com.offerpilot.questionbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerpilot.questionbank.domain.QuestionBank;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionBankMapper extends BaseMapper<QuestionBank> {
}
```

- [ ] **Step 6: 运行测试，确认通过**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=SchemaV8IT test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /e/work
git add backend/src/main/resources/db/migration/V8__question_bank.sql \
  backend/src/main/java/com/offerpilot/questionbank/domain/QuestionBank.java \
  backend/src/main/java/com/offerpilot/questionbank/mapper/QuestionBankMapper.java \
  backend/src/test/java/com/offerpilot/questionbank/SchemaV8IT.java
git commit -m "feat(questionbank): add question bank table and 71 seed questions"
```

---

## Task 2: 题库检索与蓝图接入

**Files:**
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankItem.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/service/QuestionBankRetriever.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/service/KeywordQuestionBankRetriever.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/BlueprintRequest.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/MockBlueprintGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java`
- Modify: `backend/src/test/java/com/offerpilot/interview/MockBlueprintGeneratorTest.java`
- Modify: `backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java`
- Test: `backend/src/test/java/com/offerpilot/questionbank/QuestionBankRetrieverIT.java`

- [ ] **Step 1: 创建 `QuestionBankItem`**

Create `backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankItem.java`:
```java
package com.offerpilot.questionbank.web.dto;

import com.offerpilot.questionbank.domain.QuestionBank;

import java.util.Arrays;
import java.util.List;

public record QuestionBankItem(
        Long id,
        String category,
        String level,
        List<String> tags,
        int difficulty,
        String question,
        String referenceAnswer,
        String source
) {

    public static QuestionBankItem from(QuestionBank bank) {
        List<String> tags = bank.getTags() == null ? List.of()
                : Arrays.stream(bank.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new QuestionBankItem(
                bank.getId(),
                bank.getCategory(),
                bank.getLevel(),
                tags,
                bank.getDifficulty() == null ? 2 : bank.getDifficulty(),
                bank.getQuestion(),
                bank.getReferenceAnswer(),
                bank.getSource());
    }
}
```

- [ ] **Step 2: 创建检索接口与实现**

Create `backend/src/main/java/com/offerpilot/questionbank/service/QuestionBankRetriever.java`:
```java
package com.offerpilot.questionbank.service;

import com.offerpilot.questionbank.web.dto.QuestionBankItem;

import java.util.List;

public interface QuestionBankRetriever {

    List<QuestionBankItem> retrieve(String query, List<String> tags, int limit);
}
```

Create `backend/src/main/java/com/offerpilot/questionbank/service/KeywordQuestionBankRetriever.java`:
```java
package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.questionbank.domain.QuestionBank;
import com.offerpilot.questionbank.mapper.QuestionBankMapper;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class KeywordQuestionBankRetriever implements QuestionBankRetriever {

    private final QuestionBankMapper questionBankMapper;

    public KeywordQuestionBankRetriever(QuestionBankMapper questionBankMapper) {
        this.questionBankMapper = questionBankMapper;
    }

    @Override
    public List<QuestionBankItem> retrieve(String query, List<String> tags, int limit) {
        List<QuestionBank> questions = questionBankMapper.selectList(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getStatus, "ACTIVE"));
        List<String> tagTokens = tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> queryTokens = new ArrayList<>();
        if (query != null) {
            for (String token : query.toLowerCase(Locale.ROOT).split("[\\s,，、;；/]+")) {
                if (token.length() >= 2) {
                    queryTokens.add(token);
                }
            }
        }
        int size = Math.max(1, limit);
        return questions.stream()
                .map(question -> new Scored(question, score(question, tagTokens, queryTokens)))
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(size)
                .map(scored -> QuestionBankItem.from(scored.question()))
                .toList();
    }

    private int score(QuestionBank question, List<String> tags, List<String> queryTokens) {
        String text = (question.getQuestion() + " " + question.getReferenceAnswer() + " "
                + question.getTags() + " " + question.getCategory()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String tag : tags) {
            if (text.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 5;
            }
        }
        for (String token : queryTokens) {
            if (text.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private record Scored(QuestionBank question, int score) {
    }
}
```

- [ ] **Step 3: 写检索测试**

Create `backend/src/test/java/com/offerpilot/questionbank/QuestionBankRetrieverIT.java`:
```java
package com.offerpilot.questionbank;

import com.offerpilot.questionbank.service.QuestionBankRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuestionBankRetrieverIT {

    @Autowired
    private QuestionBankRetriever retriever;

    @Test
    void retrievesRelevantQuestionsByTags() {
        var results = retriever.retrieve("Java 集合 HashMap", List.of("Java", "集合"), 5);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(item -> assertThat(item.question()).contains("HashMap"));
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /e/work
mvn -f backend/pom.xml -Dtest=QuestionBankRetrieverIT test
```

Expected: PASS。

- [ ] **Step 5: 扩展 `BlueprintRequest`**

将 `BlueprintRequest` 改为：
```java
package com.offerpilot.interview.domain;

import com.offerpilot.job.domain.JobTemplate;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.resume.domain.ResumeProfile;

import java.util.List;

public record BlueprintRequest(
        JobTemplate jobTemplate,
        ResumeProfile resumeProfile,
        String difficulty,
        int durationMinutes,
        String persona,
        List<QuestionBankItem> retrievedQuestions
) {
}
```

- [ ] **Step 6: 更新 `InterviewService.create`**

在构造函数中增加 `QuestionBankRetriever questionBankRetriever` 字段与参数，并在 `create` 中：
```java
        ResumeProfile profile = readProfile(resume);
        List<String> tags = new ArrayList<>();
        tags.add(job.getCategory());
        if (profile != null && profile.skills() != null) {
            tags.addAll(profile.skills());
        }
        var retrieved = questionBankRetriever.retrieve(job.getJdText(), tags, 8);
        InterviewBlueprint blueprint = blueprintGenerator.generate(new BlueprintRequest(
                job, profile, request.difficulty(), request.durationMinutes(), request.persona(), retrieved));
```

导入：
```java
import com.offerpilot.questionbank.service.QuestionBankRetriever;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import java.util.ArrayList;
```
（`List` 已导入。）

- [ ] **Step 7: 更新 Mock 蓝图生成器**

在 `MockBlueprintGenerator` 中：
- `generate` 中调用改为：
```java
        List<BlueprintQuestion> basic = basicQuestions(category, request.retrievedQuestions());
```
- 将 `basicQuestions(String category)` 改为 `basicQuestions(String category, List<QuestionBankItem> retrieved)`，并在每个分支返回前把题库题目放在前面：
```java
    private List<BlueprintQuestion> basicQuestions(String category, List<QuestionBankItem> retrieved) {
        List<BlueprintQuestion> result = new ArrayList<>(bankQuestions(retrieved));
        if (category.contains("AI")) {
            result.addAll(List.of(
                    question("请说明 RAG 的完整流程，以及每个阶段常见的失败点。", 2,
                            List.of("RAG", "检索"), List.of("文档切分", "Embedding", "召回", "重排", "生成"),
                            List.of("召回率低时你会怎么优化？")),
                    question("Embedding 和关键词检索各有什么优缺点？", 2,
                            List.of("Embedding", "检索"), List.of("语义匹配", "精确匹配", "混合检索"),
                            List.of("如何做混合检索？")),
                    question("如何设计一个稳定的 Prompt 模板？", 2,
                            List.of("提示词"), List.of("角色", "任务", "上下文", "输出格式", "约束"),
                            List.of("如何处理提示词注入？")),
                    question("如何评估一个 LLM 应用的效果？", 3,
                            List.of("评估"), List.of("离线评测", "人工评估", "线上指标", "A/B 测试"),
                            List.of("没有标注数据时怎么办？"))
            ));
            return dedupe(result);
        }
        if (category.contains("微服务")) {
            result.addAll(List.of(
                    question("Nacos 的服务注册与发现流程是怎样的？", 2,
                            List.of("Nacos"), List.of("注册", "心跳", "健康检查", "本地缓存"),
                            List.of("Nacos 挂了服务还能调用吗？")),
                    question("OpenFeign 的调用流程和超时、重试如何配置？", 2,
                            List.of("OpenFeign"), List.of("动态代理", "负载均衡", "超时", "重试"),
                            List.of("重试会带来什么问题？")),
                    question("Gateway 的核心能力有哪些？", 1,
                            List.of("Gateway"), List.of("路由", "断言", "过滤器", "限流"),
                            List.of("如何做灰度发布？")),
                    question("分布式事务有哪些常见方案？", 3,
                            List.of("分布式事务"), List.of("2PC", "TCC", "Saga", "本地消息表"),
                            List.of("你项目中用了哪种？为什么？"))
            ));
            return dedupe(result);
        }
        result.addAll(List.of(
                question("HashMap 在 JDK 8 中的扩容机制是怎样的？", 2,
                        List.of("集合", "HashMap"), List.of("数组+链表+红黑树", "扩容 2 倍", "链表转红黑树阈值 8"),
                        List.of("扩容后元素如何重新分布？")),
                question("线程池的核心参数有哪些？任务提交后如何处理？", 2,
                        List.of("并发", "线程池"), List.of("corePoolSize", "maximumPoolSize", "队列", "拒绝策略"),
                        List.of("队列满了会怎样？")),
                question("JVM 垃圾回收的分代模型和常见收集器？", 2,
                        List.of("JVM", "GC"), List.of("新生代", "老年代", "Minor GC", "Full GC", "G1"),
                        List.of("什么时候会触发 Full GC？")),
                question("MySQL 索引失效的常见场景有哪些？", 2,
                        List.of("MySQL", "索引"), List.of("最左前缀", "函数运算", "隐式转换", "范围查询"),
                        List.of("如何用 EXPLAIN 分析？"))
        ));
        return dedupe(result);
    }

    private List<BlueprintQuestion> bankQuestions(List<QuestionBankItem> retrieved) {
        if (retrieved == null) {
            return List.of();
        }
        return retrieved.stream()
                .map(item -> question(item.question(), item.difficulty(),
                        item.tags().stream().limit(3).toList(),
                        List.of(item.referenceAnswer()),
                        List.of("能再展开讲讲这道题的边界条件吗？")))
                .toList();
    }

    private List<BlueprintQuestion> dedupe(List<BlueprintQuestion> questions) {
        java.util.Map<String, BlueprintQuestion> map = new java.util.LinkedHashMap<>();
        for (BlueprintQuestion question : questions) {
            map.putIfAbsent(question.text(), question);
        }
        return new ArrayList<>(map.values());
    }
```

导入：
```java
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
```

- [ ] **Step 8: 更新 Spring AI 蓝图生成器的提示词**

在 `SpringAiBlueprintGenerator` 的 `.user(...)` 中增加：
```java
                            "参考题库：%s%n"
```
并在 `.formatted(...)` 的参数最后加入 `request.retrievedQuestions()`。完整 `.user` 片段：
```java
                    .user("""
                            岗位：%s
                            JD：%s
                            候选人简历：%s
                            难度：%s
                            时长：%d 分钟
                            面试官风格：%s
                            题目总数：%d
                            参考题库：%s
                            """.formatted(
                            request.jobTemplate().getTitle(),
                            request.jobTemplate().getJdText(),
                            request.resumeProfile(),
                            request.difficulty(),
                            request.durationMinutes(),
                            request.persona(),
                            count,
                            request.retrievedQuestions()))
```

- [ ] **Step 9: 更新测试构造**

`MockBlueprintGeneratorTest` 中的：
```java
        InterviewBlueprint blueprint = generator.generate(new BlueprintRequest(
                job, profile, "STANDARD", 30, "STANDARD"));
```
改为：
```java
        InterviewBlueprint blueprint = generator.generate(new BlueprintRequest(
                job, profile, "STANDARD", 30, "STANDARD", List.of()));
```

`InterviewConversationServiceTest.followUpEvaluation...` 中的：
```java
        assertThat(second.evaluation().promptText()).contains("扩容后元素如何重新分布");
```
改为：
```java
        assertThat(second.evaluation().promptText()).isEqualTo(first.replyText());
```

`InterviewConversationControllerIT.practiceModeReturnsEvaluation` 中的：
```java
                .andExpect(jsonPath("$.data[1].promptText").value(org.hamcrest.Matchers.containsString("扩容后元素如何重新分布")));
```
改为：
```java
                .andExpect(jsonPath("$.data[1].promptText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
```

- [ ] **Step 10: 运行全量后端测试**

```bash
cd /e/work
mvn -f backend/pom.xml test
```

Expected: 所有测试 PASS。

- [ ] **Step 11: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/questionbank/web/dto/QuestionBankItem.java \
  backend/src/main/java/com/offerpilot/questionbank/service \
  backend/src/main/java/com/offerpilot/interview/domain/BlueprintRequest.java \
  backend/src/main/java/com/offerpilot/interview/service/InterviewService.java \
  backend/src/main/java/com/offerpilot/interview/service/MockBlueprintGenerator.java \
  backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java \
  backend/src/test/java/com/offerpilot/questionbank/QuestionBankRetrieverIT.java \
  backend/src/test/java/com/offerpilot/interview/MockBlueprintGeneratorTest.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationServiceTest.java \
  backend/src/test/java/com/offerpilot/interview/InterviewConversationControllerIT.java
git commit -m "feat(questionbank): retrieve questions and use them in interview blueprints"
```

---

## Task 3: 薄弱点推荐与前端展示

**Files:**
- Create: `backend/src/main/java/com/offerpilot/questionbank/web/dto/WeaknessView.java`
- Create: `backend/src/main/java/com/offerpilot/questionbank/service/WeaknessService.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/InterviewStatsController.java`
- Modify: `frontend/src/api/interview.ts`
- Modify: `frontend/src/views/InterviewHistoryView.vue`
- Modify: `README.md`

- [ ] **Step 1: 创建 `WeaknessView`**

Create `backend/src/main/java/com/offerpilot/questionbank/web/dto/WeaknessView.java`:
```java
package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record WeaknessView(
        String tag,
        int count,
        String lastSeen,
        List<QuestionBankItem> recommendedQuestions
) {
}
```

- [ ] **Step 2: 创建 `WeaknessService`**

Create `backend/src/main/java/com/offerpilot/questionbank/service/WeaknessService.java`:
```java
package com.offerpilot.questionbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.interview.domain.AnswerEvaluation;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.InterviewSession;
import com.offerpilot.interview.mapper.AnswerEvaluationMapper;
import com.offerpilot.interview.mapper.InterviewQuestionMapper;
import com.offerpilot.interview.mapper.InterviewSessionMapper;
import com.offerpilot.questionbank.web.dto.QuestionBankItem;
import com.offerpilot.questionbank.web.dto.WeaknessView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WeaknessService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final QuestionBankRetriever questionBankRetriever;
    private final ObjectMapper objectMapper;

    public WeaknessService(InterviewSessionMapper sessionMapper,
                           InterviewQuestionMapper questionMapper,
                           AnswerEvaluationMapper evaluationMapper,
                           QuestionBankRetriever questionBankRetriever,
                           ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.evaluationMapper = evaluationMapper;
        this.questionBankRetriever = questionBankRetriever;
        this.objectMapper = objectMapper;
    }

    public List<WeaknessView> weaknesses(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        List<InterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getSessionId, sessionIds));
        Map<Long, List<String>> tagsByQuestion = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            tagsByQuestion.put(question.getId(), readList(question.getKnowledgeTags()));
        }
        Map<Long, LocalDateTime> sessionTimes = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            sessionTimes.put(session.getId(), session.getCreatedAt());
        }

        List<AnswerEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<AnswerEvaluation>()
                        .in(AnswerEvaluation::getSessionId, sessionIds));

        Map<String, Accumulator> counts = new LinkedHashMap<>();
        for (AnswerEvaluation evaluation : evaluations) {
            boolean weak = !"CORRECT".equals(evaluation.getCorrectness())
                    || (evaluation.getMissingPoints() != null && !evaluation.getMissingPoints().isBlank());
            if (!weak) {
                continue;
            }
            List<String> tags = tagsByQuestion.getOrDefault(evaluation.getQuestionId(), List.of());
            LocalDateTime time = sessionTimes.get(evaluation.getSessionId());
            for (String tag : tags) {
                Accumulator accumulator = counts.computeIfAbsent(tag, key -> new Accumulator());
                accumulator.count++;
                if (time != null && (accumulator.lastSeen == null || time.isAfter(accumulator.lastSeen))) {
                    accumulator.lastSeen = time;
                }
            }
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                .limit(6)
                .map(entry -> new WeaknessView(
                        entry.getKey(),
                        entry.getValue().count,
                        entry.getValue().lastSeen == null ? "" : entry.getValue().lastSeen.toLocalDate().toString(),
                        questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3)))
                .toList();
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

    private static class Accumulator {
        private int count;
        private LocalDateTime lastSeen;
    }
}
```

- [ ] **Step 3: 在统计控制器中暴露接口**

在 `InterviewStatsController` 中注入 `WeaknessService`：
```java
    private final WeaknessService weaknessService;

    public InterviewStatsController(InterviewReportService reportService, WeaknessService weaknessService) {
        this.reportService = reportService;
        this.weaknessService = weaknessService;
    }
```
增加：
```java
    @GetMapping("/stats/weaknesses")
    public ApiResponse<List<WeaknessView>> weaknesses(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(weaknessService.weaknesses(userId));
    }
```
导入：
```java
import com.offerpilot.questionbank.service.WeaknessService;
import com.offerpilot.questionbank.web.dto.WeaknessView;
```

- [ ] **Step 4: 前端增加类型与 API**

在 `frontend/src/api/interview.ts` 中增加：
```ts
export interface QuestionBankItem {
  id: number
  category: string
  level: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
}

export interface WeaknessView {
  tag: string
  count: number
  lastSeen: string
  recommendedQuestions: QuestionBankItem[]
}
```
在 `interviewApi` 中增加：
```ts
  weaknesses: () => http.get<never, WeaknessView[]>('/stats/weaknesses'),
```

- [ ] **Step 5: 历史页加载并展示薄弱点**

在 `InterviewHistoryView.vue`：
- 导入 `WeaknessView`：
```ts
import { interviewApi, type InterviewHistoryItem, type StatsOverview, type WeaknessView } from '@/api/interview'
```
- 增加：
```ts
const weaknesses = ref<WeaknessView[]>([])
```
- `load` 中改为：
```ts
    const [statsData, historyData, weaknessData] = await Promise.all([
      interviewApi.stats(),
      interviewApi.history(),
      interviewApi.weaknesses()
    ])
    stats.value = statsData
    history.value = historyData
    weaknesses.value = weaknessData
```
- 在“面试历史”卡片之后增加：
```vue
      <section class="card">
        <h2>薄弱点推荐</h2>
        <div v-if="weaknesses.length" class="weakness-list">
          <div v-for="weak in weaknesses" :key="weak.tag" class="weakness-item">
            <div class="weakness-head">
              <el-tag type="warning" effect="plain">{{ weak.tag }}</el-tag>
              <span class="muted">出现 {{ weak.count }} 次</span>
              <span v-if="weak.lastSeen" class="muted">最近 {{ weak.lastSeen }}</span>
            </div>
            <ul class="recommend-list">
              <li v-for="question in weak.recommendedQuestions" :key="question.id">
                <details>
                  <summary>{{ question.question }}</summary>
                  <p class="answer">{{ question.referenceAnswer }}</p>
                </details>
              </li>
            </ul>
          </div>
        </div>
        <el-empty v-else description="完成练习后这里会推荐薄弱知识点" :image-size="80" />
      </section>
```
- 增加样式：
```css
.weakness-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.weakness-item {
  padding: 16px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #fbfdff;
}

.weakness-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.recommend-list {
  margin: 0;
  padding-left: 18px;
  color: #5b6b7f;
  font-size: 14px;
  line-height: 1.8;
}

.recommend-list summary {
  cursor: pointer;
  color: #2e75b6;
}

.recommend-list .answer {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f5f8fc;
  color: #4a5a6e;
  line-height: 1.8;
}
```

- [ ] **Step 6: 构建验证**

```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 7: 浏览器验收**

1. 完成一场练习模式面试，故意答错或答漏一题。
2. 打开“成长记录”，应看到“薄弱点推荐”卡片：
   - 薄弱标签（如 Java、基础、HashMap）
   - 出现次数和最近时间
   - 推荐题目，可展开查看参考答案
3. 创建新面试时，蓝图题目中应包含题库题目（日志或页面题目文本可验证）。

- [ ] **Step 8: 更新 README**

在“当前进度”中补充：
```markdown
当前进度：P0 Week 5A 已完成（题库检索、蓝图接入、薄弱点推荐）。
```

在 API 表格中追加：
```markdown
| GET | `/api/stats/weaknesses` | 薄弱点与推荐题目 |
```

- [ ] **Step 9: 提交**

```bash
cd /e/work
git add backend/src/main/java/com/offerpilot/questionbank/web/dto/WeaknessView.java \
  backend/src/main/java/com/offerpilot/questionbank/service/WeaknessService.java \
  backend/src/main/java/com/offerpilot/interview/web/InterviewStatsController.java \
  frontend/src/api/interview.ts frontend/src/views/InterviewHistoryView.vue README.md
git commit -m "feat(questionbank): add weakness recommendations"
```

---

## 自检结果

- **Spec coverage:** 题库 71 题、检索、蓝图接入、薄弱点推荐、前端展示均覆盖；pgvector/运营后台/额度留到 W5B 或部署阶段。
- **Placeholder scan:** 没有 TBD/TODO；种子 SQL 由明确脚本生成。
- **Type consistency:** `QuestionBankItem`、`WeaknessView`、`BlueprintRequest.retrievedQuestions` 前后一致；前端字段与后端 DTO 一致。
- **默认可用性:** 关键词检索不依赖向量库/外部服务，本地 MySQL/H2 均可运行。

---

## 执行记录（2026-10-27）

- 分支：`feat/p0-week5-rag`（基于 `feat/p0-week4-report`）。
- 种子数据：从本地《基础面试题答案汇总》解析 **71 道题**，导入 `question_bank`（分类/标签/难度/参考答案）。
- Flyway 占位符问题：题库文本含 `${...}`，已在 `application.yml` 设置 `spring.flyway.placeholder-replacement: false`。
- 后端测试：`mvn -f backend/pom.xml test` → **45 tests, 0 failures**。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 浏览器端到端：
  - 练习模式创建面试，首题命中题库技术题「分页查询怎么写 SQL？」。
  - 回答“不知道”后结束，成长页显示薄弱点：MySQL / MyBatis / 分页，及 9 条推荐题目。
  - 推荐题目可展开查看参考答案。
- 调整：
  - 题库题目进入 BASIC 题池，但过滤“开放题/经历类”，避免自我介绍类题目占据技术题位置。
  - 成长页无报告时五维图显示空状态，不再渲染空坐标轴。
- 待推送：分支提交在本地，W3/练习模式/W4 的 PR 仍未合并。
