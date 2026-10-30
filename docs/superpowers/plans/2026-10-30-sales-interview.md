# 销售岗位面试实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 OfferPilot 面试体系中新增“销售岗位”面试：用户粘贴销售 JD + 选择简历后，系统用销售能力模型和 50 题销售题库组卷；AI 可用时由 AI 个性化出题，AI 不可用时自动降级为“题库模式”，并给出销售专属五维报告。

**Architecture:** 复用现有 interview_track / job_template / interview_session / question_bank 体系。新增 `GENERAL` 类别销售 track、用户私有岗位模板、`generation_mode` 字段；新增 `RoleProfile` 把 track 配置解析为能力模型和报告维度；新增 `SalesBankBlueprintGenerator` 负责离线组卷；AI 生成器、评估器、追问器、报告器按 `RoleProfile` 分支。前端新增销售入口，并把报告/历史维度改为动态渲染。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway、MySQL/H2、Vue 3、Element Plus、TypeScript。

**执行说明:** 按用户当前指示，执行过程中不自动 `git commit` / `git push`；每个任务完成后运行对应测试。用户明确要求提交时再提交。

**实现状态（2026-10-30）:** 已完成。V20/V21 已应用；销售题库模式、AI 模式、AI 失败回退、销售五维报告、动态统计维度、前端销售入口均已联调。后端 131 个测试通过，前端 `npm run build` 通过。补充实现：已创建的销售岗位也能从岗位卡片直接开始（`InterviewService.create` 对 `GENERAL` 赛道走销售组卷，不串技术题）；AI 调用链在 provider 解密失败时统一走 fallback，而不是返回 500。

---

## 文件地图

### 新增

| 文件 | 职责 |
|---|---|
| `backend/src/main/resources/db/migration/V20__sales_track_and_generation_mode.sql` | track 类别、用户岗位模板、generation_mode、销售 track 种子 |
| `backend/src/main/resources/db/migration/V21__sales_question_bank.sql` | 50 道销售题 + track_question 绑定 |
| `backend/src/main/java/com/offerpilot/track/domain/RoleProfile.java` | 角色能力模型/报告维度值对象 |
| `backend/src/main/java/com/offerpilot/track/service/RoleProfileParser.java` | 解析 track.config_json |
| `backend/src/main/java/com/offerpilot/ai/service/AiAvailabilityChecker.java` | 判断当前用户是否有可用 AI provider |
| `backend/src/main/java/com/offerpilot/interview/service/SalesBankBlueprintGenerator.java` | 销售题库离线组卷 |
| `backend/src/main/java/com/offerpilot/interview/web/dto/CreateCustomInterviewRequest.java` | 销售自定义面试请求 |
| `backend/src/test/java/com/offerpilot/track/RoleProfileParserTest.java` | 解析测试 |
| `backend/src/test/java/com/offerpilot/ai/AiAvailabilityCheckerTest.java` | AI 可用性测试 |
| `backend/src/test/java/com/offerpilot/interview/SalesBankBlueprintGeneratorTest.java` | 组卷测试 |
| `backend/src/test/java/com/offerpilot/interview/SalesInterviewControllerIT.java` | 销售面试端到端测试 |

### 修改

| 文件 | 改动 |
|---|---|
| `backend/.../track/domain/InterviewTrack.java` | 增加 `category` |
| `backend/.../track/web/dto/InterviewTrackView.java` | 增加 `category` |
| `backend/.../track/service/InterviewTrackService.java` | `listTech`、`requireByCode`、`profile` |
| `backend/.../track/web/AdminTrackController.java` | 管理端只列 TECH |
| `backend/.../job/domain/JobTemplate.java` | 增加 `userId`、`source` |
| `backend/.../job/service/JobTemplateService.java` | `listActive(Long userId)` |
| `backend/.../job/web/JobTemplateController.java` | 读取当前用户 |
| `backend/.../interview/domain/InterviewSession.java` | 增加 `generationMode` |
| `backend/.../interview/domain/BlueprintRequest.java` | 增加 `RoleProfile profile` |
| `backend/.../interview/web/dto/InterviewSessionView.java` | 增加 `generationMode` |
| `backend/.../interview/web/dto/InterviewReportView.java` | 增加 `dimensionLabels`、`generationMode` |
| `backend/.../interview/web/dto/StatsOverview.java` | 增加 `dimensionLabels` |
| `backend/.../interview/service/AnswerEvaluator.java` | 5 参默认重载 |
| `backend/.../interview/service/InterviewerReplyGenerator.java` | 5 参默认重载 |
| `backend/.../interview/service/InterviewReportGenerator.java` | 5 参默认重载 |
| `backend/.../interview/service/MockAnswerEvaluator.java` | 实现 5 参 |
| `backend/.../interview/service/SpringAiAnswerEvaluator.java` | 销售评分提示词 |
| `backend/.../interview/service/MockInterviewerReplyGenerator.java` | 销售话术 |
| `backend/.../interview/service/SpringAiInterviewerReplyGenerator.java` | 销售面试官提示词 |
| `backend/.../interview/service/MockInterviewReportGenerator.java` | 销售五维启发式报告 |
| `backend/.../interview/service/SpringAiInterviewReportGenerator.java` | 销售报告提示词 |
| `backend/.../interview/service/InterviewBlueprintValidator.java` | 销售蓝图校验 |
| `backend/.../interview/service/SpringAiBlueprintGenerator.java` | 销售蓝图提示词、AI 失败不回退技术 Mock |
| `backend/.../interview/service/InterviewService.java` | `createCustom`、共享落库 |
| `backend/.../interview/web/InterviewController.java` | `POST /api/interviews/custom` |
| `backend/.../interview/service/InterviewConversationService.java` | 按 RoleProfile 调用 AI |
| `backend/.../interview/service/InterviewReportService.java` | 动态维度、labels、generationMode |
| `backend/.../interview/service/WeaknessService.java` | 销售弱项推荐销售题 |
| `backend/.../common/exception/ErrorCode.java` | `AI_UNAVAILABLE` |
| `frontend/src/api/interview.ts` | 自定义请求、动态维度、generationMode |
| `frontend/src/components/InterviewSetupDialog.vue` | 销售 JD 模式 |
| `frontend/src/views/HomeView.vue` | 销售入口 |
| `frontend/src/views/InterviewSessionView.vue` | 新题型、题库模式标签 |
| `frontend/src/views/InterviewChatView.vue` | 新题型、题库模式标签 |
| `frontend/src/views/InterviewReportView.vue` | 动态维度 |
| `frontend/src/views/InterviewHistoryView.vue` | 动态维度 |

---

## Task 1: V20 数据库迁移与销售 track

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__sales_track_and_generation_mode.sql`

- [ ] **Step 1: 写迁移 SQL**

```sql
ALTER TABLE interview_track ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'TECH';

ALTER TABLE job_template ADD COLUMN user_id BIGINT NULL;
ALTER TABLE job_template ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
CREATE INDEX idx_job_template_user_status ON job_template(user_id, status);

ALTER TABLE interview_session ADD COLUMN generation_mode VARCHAR(20) NOT NULL DEFAULT 'AI';

INSERT INTO interview_track
(code, name, category, role_family, primary_language, level, description,
 rubric_code, config_json, status, sort_order, created_at, updated_at)
VALUES
('sales', '销售岗位', 'GENERAL', 'SALES', 'NONE', '通用',
 '销售认知、客户开拓、需求挖掘、方案呈现、异议处理、谈判成交、过程复盘',
 'SALES',
 '{"retrievalMode":"BANK_AI","competencies":[{"name":"客户开拓","weight":20,"description":"寻找、筛选并触达潜在客户"},{"name":"需求挖掘","weight":20,"description":"识别客户痛点、预算、决策链和采购节奏"},{"name":"方案呈现","weight":15,"description":"把产品能力翻译成客户价值并完成演示"},{"name":"异议处理","weight":15,"description":"识别异议类型并推进客户认知"},{"name":"谈判成交","weight":15,"description":"交换条件、处理谈判分歧并推进签约"},{"name":"数据复盘","weight":15,"description":"用漏斗、转化率和丢单原因改进动作"}],"reportDimensions":{"customerInsight":"客户洞察","communication":"沟通说服","resilience":"抗压应变","resultOrientation":"结果导向","jobMatch":"岗位匹配"},"questionMix":{"behavioral":50,"scenario":30,"rolePlay":10,"motivation":10}}',
 'ACTIVE', 100, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
```

- [ ] **Step 2: 启动后端验证 Flyway**

Run: `cd /e/work/backend && mvn -q -DskipTests spring-boot:run`（或重启现有后端进程）
Expected: 日志出现 `Migrating schema ... to version 20`，无 SQL 错误。

- [ ] **Step 3: 验证数据**

```bash
mysql -h127.0.0.1 -uroot -p1234 offerpilot -e "select code,category,role_family from interview_track where code='sales';"
```
Expected: 一行 `sales | GENERAL | SALES`。

---

## Task 2: V21 销售题库（50 题）

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__sales_question_bank.sql`

- [ ] **Step 1: 写 50 道题**

题目分布（category / difficulty / count）：

| 分类 | 1 星 | 2 星 | 3 星 | 合计 |
|---|---:|---:|---:|---:|
| 销售认知与动机 | 4 | 2 | 0 | 6 |
| 客户开拓 | 2 | 4 | 2 | 8 |
| 需求挖掘与决策链 | 1 | 4 | 3 | 8 |
| 方案呈现与价值 | 1 | 3 | 2 | 6 |
| 异议处理 | 2 | 4 | 2 | 8 |
| 谈判与成交 | 0 | 3 | 3 | 6 |
| 过程管理与数据复盘 | 0 | 2 | 2 | 4 |
| 行为与抗压 | 2 | 2 | 0 | 4 |
| **合计** | **12** | **24** | **14** | **50** |

题目清单（题干必须按此实现，参考答案自行扩写为 2-4 句、含可观察行为）：

1. 销售认知与动机：为什么选择销售岗位？你认为销售最核心的特质是什么？
2. 销售认知与动机：你理解销售每天的日常工作是什么？
3. 销售认知与动机：做销售以来最有成就感的一件事是什么？
4. 销售认知与动机：连续几个月没有业绩，你会怎么调整？
5. 销售认知与动机：新客户开拓和老客户维护，你更擅长哪一个？
6. 销售认知与动机：如果这次面试没有通过，你觉得可能是什么原因？
7. 客户开拓：给你一个完全陌生的行业，你如何从零开发客户？
8. 客户开拓：你常用哪些渠道找线索？如何判断渠道质量？
9. 客户开拓：一条线索值不值得跟进，你会看哪些信号？
10. 客户开拓：资源很少、起步很慢时，你如何破局？
11. 客户开拓：冷呼叫或陌拜时，你的开场白怎么设计？
12. 客户开拓：如何让老客户愿意给你转介绍？
13. 客户开拓：展会或线上活动拿到一批名单，你怎么跟进？
14. 客户开拓：连续三个月没有新客户，你会怎么复盘？
15. 需求挖掘与决策链：第一次拜访客户，你如何开场并建立信任？
16. 需求挖掘与决策链：客户只说要“基础方案”，你如何挖出真实需求？
17. 需求挖掘与决策链：如何判断谁是关键决策人？
18. 需求挖掘与决策链：多个部门诉求冲突时，你怎么办？
19. 需求挖掘与决策链：预算、时间、决策权都不明确时，你先确认什么？
20. 需求挖掘与决策链：客户需求频繁变更，你如何管理？
21. 需求挖掘与决策链：客户已有稳定供应商，你如何切入？
22. 需求挖掘与决策链：如何区分“想要”和“必须买”的需求？
23. 方案呈现与价值：客户说功能很多但没价值，你怎么回应？
24. 方案呈现与价值：如何向非专业客户讲清产品价值？
25. 方案呈现与价值：你如何做一次 ROI 测算？
26. 方案呈现与价值：竞品功能相似时，你如何讲差异化？
27. 方案呈现与价值：演示被客户打断，说“我们用不上”，怎么办？
28. 方案呈现与价值：定制化和标准化之间，你如何给客户建议？
29. 异议处理：客户说“太贵了”，你怎么处理？
30. 异议处理：竞品比你们便宜 30%，你怎么回应？
31. 异议处理：客户说“我再考虑一下”，你怎么推进？
32. 异议处理：客户说“我们不需要”，你怎么判断真假？
33. 异议处理：客户预算突然削减 20%，你如何调整方案？
34. 异议处理：客户态度很差并质疑产品，你怎么应对？
35. 异议处理：客户觉得销售很烦，如何重新建立沟通？
36. 异议处理：客户内部流程复杂、一直拖延，你怎么破局？
37. 谈判与成交：签约前客户临时要求再降 20%，你怎么谈？
38. 谈判与成交：你会用哪些条件换取价格让步？
39. 谈判与成交：客户一直拖延不逼单，你如何确认成交信号？
40. 谈判与成交：关键联系人突然离职，合同怎么推进？
41. 谈判与成交：合同条款或回款方式有分歧，你怎么处理？
42. 谈判与成交：长周期项目如何分阶段推进和成交？
43. 过程管理与数据复盘：月度目标你会怎么拆解到每周每天？
44. 过程管理与数据复盘：你如何用漏斗和 CRM 管理商机？
45. 过程管理与数据复盘：丢单后你会复盘哪些内容？
46. 过程管理与数据复盘：你如何用数据判断某个销售动作是否有效？
47. 行为与抗压：讲一个你最有成就感的销售案例（STAR）。
48. 行为与抗压：讲一次被客户拒绝的经历，你当时怎么做的？
49. 行为与抗压：你和售前、交付或团队发生冲突时怎么处理？
50. 行为与抗压：目标很难完成时，你第一步会做什么？

**标签规则：** 每题 `tags` 必须包含六项能力之一：客户开拓、需求挖掘、方案呈现、异议处理、谈判成交、数据复盘；可追加 `销售动机`、`抗压`、`大客户` 等辅助标签。销售认知题优先标 `客户开拓` 或 `需求挖掘`；行为抗压题按案例内容映射到对应能力。

**SQL 形态：**

```sql
INSERT INTO question_bank
(category, level, tags, difficulty, question, reference_answer, source, status, created_at, updated_at)
VALUES
('销售认知与动机', '通用', '客户开拓,销售动机', 1, '为什么选择销售岗位？你认为销售最核心的特质是什么？',
 '参考要点：说明选择销售的真实动机；能说出至少两个可观察特质，如目标感、抗压、同理心、学习能力；结合自己的经历佐证，而不是只讲性格标签。',
 '公开面经整理（改写）', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
-- 其余 49 条同结构
;
```

- [ ] **Step 2: 绑定到 sales track**

```sql
INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT t.id, q.id, 0, 100, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'sales'
WHERE q.source = '公开面经整理（改写）'
  AND q.status = 'ACTIVE'
  AND q.deleted = 0;
```

- [ ] **Step 3: 验证数量和分布**

```bash
mysql -h127.0.0.1 -uroot -p1234 offerpilot -e "
select count(*) total from question_bank where source='公开面经整理（改写）';
select category, difficulty, count(*) from question_bank
where source='公开面经整理（改写）' group by category, difficulty order by category, difficulty;
select count(*) bound from track_question tq
join interview_track t on t.id=tq.track_id where t.code='sales';"
```
Expected: `total=50`，分布与上表一致，`bound=50`。

---

## Task 3: 领域字段与岗位归属

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/track/domain/InterviewTrack.java`
- Modify: `backend/src/main/java/com/offerpilot/job/domain/JobTemplate.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/InterviewSession.java`
- Modify: `backend/src/main/java/com/offerpilot/track/web/dto/InterviewTrackView.java`
- Modify: `backend/src/main/java/com/offerpilot/track/service/InterviewTrackService.java`
- Modify: `backend/src/main/java/com/offerpilot/track/web/AdminTrackController.java`
- Modify: `backend/src/main/java/com/offerpilot/job/service/JobTemplateService.java`
- Modify: `backend/src/main/java/com/offerpilot/job/web/JobTemplateController.java`

- [ ] **Step 1: 实体加字段**

```java
// InterviewTrack
private String category;
// JobTemplate
private Long userId;
private String source;
// InterviewSession
private String generationMode;
```

- [ ] **Step 2: TrackView 加 category，管理端只列 TECH**

```java
// InterviewTrackView 增加 String category
public static InterviewTrackView from(InterviewTrack track) {
    return new InterviewTrackView(track.getId(), track.getCode(), track.getName(),
            track.getCategory(), track.getRoleFamily(), track.getPrimaryLanguage(),
            track.getLevel(), track.getDescription(), track.getRubricCode(),
            track.getStatus(), track.getSortOrder());
}
```

```java
public List<InterviewTrackView> listTech() {
    return listAll().stream()
            .filter(view -> "TECH".equalsIgnoreCase(view.category()))
            .toList();
}
```

`AdminTrackController.list()` 改调 `trackService.listTech()`；`InterviewTrackService.listAll()` 保留给后续通用场景。

- [ ] **Step 3: 岗位模板按用户过滤**

```java
public List<JobTemplate> listActive(Long userId) {
    LambdaQueryWrapper<JobTemplate> wrapper = new LambdaQueryWrapper<JobTemplate>()
            .eq(JobTemplate::getStatus, "ACTIVE")
            .orderByAsc(JobTemplate::getSortOrder);
    if (userId == null) {
        wrapper.isNull(JobTemplate::getUserId);
    } else {
        wrapper.and(w -> w.isNull(JobTemplate::getUserId).or().eq(JobTemplate::getUserId, userId));
    }
    return jobTemplateMapper.selectList(wrapper);
}
```

`JobTemplateController` GET 增加 `@AuthenticationPrincipal Long userId`，调用 `listActive(userId)`。未登录时 `userId=null`，只返回系统模板，保持现有 `JobTemplateControllerIT` 的 4 条期望。

- [ ] **Step 4: 编译**

Run: `cd /e/work/backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

---

## Task 4: RoleProfile 与 AI 可用性

**Files:**
- Create: `backend/src/main/java/com/offerpilot/track/domain/RoleProfile.java`
- Create: `backend/src/main/java/com/offerpilot/track/service/RoleProfileParser.java`
- Create: `backend/src/main/java/com/offerpilot/ai/service/AiAvailabilityChecker.java`
- Test: `backend/src/test/java/com/offerpilot/track/RoleProfileParserTest.java`
- Test: `backend/src/test/java/com/offerpilot/ai/AiAvailabilityCheckerTest.java`

- [ ] **Step 1: RoleProfile**

```java
package com.offerpilot.track.domain;

import java.util.List;
import java.util.Map;

public record RoleProfile(
        String category,
        String rubricCode,
        String retrievalMode,
        List<Competency> competencies,
        Map<String, String> reportDimensions,
        Map<String, Integer> questionMix) {

    public record Competency(String name, int weight, String description) {
    }

    public static RoleProfile tech() {
        return new RoleProfile("TECH", "BACKEND", "BANK",
                List.of(),
                Map.of("accuracy", "技术准确性", "depth", "技术深度",
                        "project", "项目表现", "expression", "表达逻辑", "match", "岗位匹配"),
                Map.of());
    }

    public boolean isGeneral() {
        return "GENERAL".equalsIgnoreCase(category) || "SALES".equalsIgnoreCase(rubricCode);
    }

    public List<String> competencyNames() {
        return competencies.stream().map(Competency::name).toList();
    }

    public String dimensionLabel(String key) {
        return reportDimensions.getOrDefault(key, key);
    }
}
```

- [ ] **Step 2: RoleProfileParser**

```java
package com.offerpilot.track.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoleProfileParser {

    private final ObjectMapper objectMapper;

    public RoleProfileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RoleProfile parse(InterviewTrack track) {
        if (track == null) {
            return RoleProfile.tech();
        }
        RoleProfile fallback = new RoleProfile(
                track.getCategory() == null ? "TECH" : track.getCategory(),
                track.getRubricCode(), "BANK", List.of(), RoleProfile.tech().reportDimensions(), Map.of());
        if (track.getConfigJson() == null || track.getConfigJson().isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(track.getConfigJson());
            List<RoleProfile.Competency> competencies = new ArrayList<>();
            for (JsonNode node : root.path("competencies")) {
                competencies.add(new RoleProfile.Competency(
                        node.path("name").asText(),
                        node.path("weight").asInt(0),
                        node.path("description").asText("")));
            }
            Map<String, String> dimensions = new LinkedHashMap<>();
            root.path("reportDimensions").fields()
                    .forEachRemaining(entry -> dimensions.put(entry.getKey(), entry.getValue().asText()));
            Map<String, Integer> mix = new LinkedHashMap<>();
            root.path("questionMix").fields()
                    .forEachRemaining(entry -> mix.put(entry.getKey(), entry.getValue().asInt(0)));
            return new RoleProfile(
                    track.getCategory() == null ? "TECH" : track.getCategory(),
                    track.getRubricCode(),
                    root.path("retrievalMode").asText("BANK"),
                    List.copyOf(competencies),
                    dimensions.isEmpty() ? fallback.reportDimensions() : Map.copyOf(dimensions),
                    Map.copyOf(mix));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
```

- [ ] **Step 3: AiAvailabilityChecker**

```java
package com.offerpilot.ai.service;

import com.offerpilot.ai.provider.domain.AiProvider;
import com.offerpilot.ai.provider.service.AiProviderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiAvailabilityChecker {

    private final boolean aiEnabled;
    private final AiProviderService providerService;

    public AiAvailabilityChecker(@Value("${app.ai.enabled:false}") boolean aiEnabled,
                                 AiProviderService providerService) {
        this.aiEnabled = aiEnabled;
        this.providerService = providerService;
    }

    public boolean isAvailable(Long userId) {
        if (!aiEnabled || userId == null) {
            return false;
        }
        AiProvider provider = providerService.activeForUser(userId);
        return provider != null && Boolean.TRUE.equals(provider.getEnabled());
    }
}
```

- [ ] **Step 4: 测试**

`RoleProfileParserTest` 构造 `InterviewTrack`，设置 configJson 为 V20 的销售配置，断言 `isGeneral()`、6 个能力、5 个维度标签、`retrievalMode=BANK_AI`；null track 返回 tech。

`AiAvailabilityCheckerTest` 用 Mockito mock `AiProviderService`：AI 关闭返回 false；AI 开启且 provider enabled 返回 true；provider 为 null 返回 false。

- [ ] **Step 5: 运行**

Run: `cd /e/work/backend && mvn -q -Dtest=RoleProfileParserTest,AiAvailabilityCheckerTest test`
Expected: PASS。

---

## Task 5: AI 接口按 RoleProfile 扩展

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/interview/service/AnswerEvaluator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewerReplyGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewReportGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/MockAnswerEvaluator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/SpringAiAnswerEvaluator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/MockInterviewerReplyGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewerReplyGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/MockInterviewReportGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/SpringAiInterviewReportGenerator.java`

- [ ] **Step 1: 三个接口加默认重载**

```java
// AnswerEvaluator
default AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                        String promptType, String answer) {
    return evaluate(question, promptText, promptType, answer, RoleProfile.tech());
}

AnswerEvaluationResult evaluate(InterviewQuestion question, String promptText,
                                String promptType, String answer, RoleProfile profile);
```

```java
// InterviewerReplyGenerator
default String generate(InterviewQuestion question, ReplyType type, String answer,
                        List<InterviewMessage> history) {
    return generate(question, type, answer, history, RoleProfile.tech());
}

String generate(InterviewQuestion question, ReplyType type, String answer,
                List<InterviewMessage> history, RoleProfile profile);

default Flux<String> stream(InterviewQuestion question, ReplyType type, String answer,
                            List<InterviewMessage> history) {
    return stream(question, type, answer, history, RoleProfile.tech());
}

default Flux<String> stream(InterviewQuestion question, ReplyType type, String answer,
                            List<InterviewMessage> history, RoleProfile profile) {
    return Flux.fromIterable(chunks(generate(question, type, answer, history, profile)));
}

private static List<String> chunks(String text) {
    if (text == null || text.isEmpty()) {
        return List.of();
    }
    List<String> result = new ArrayList<>();
    for (int i = 0; i < text.length(); i += 12) {
        result.add(text.substring(i, Math.min(text.length(), i + 12)));
    }
    return result;
}
```

```java
// InterviewReportGenerator
default InterviewReportResult generate(InterviewSession session,
                                       List<InterviewQuestion> questions,
                                       List<InterviewMessage> messages,
                                       List<AnswerEvaluation> evaluations) {
    return generate(session, questions, messages, evaluations, RoleProfile.tech());
}

InterviewReportResult generate(InterviewSession session,
                               List<InterviewQuestion> questions,
                               List<InterviewMessage> messages,
                               List<AnswerEvaluation> evaluations,
                               RoleProfile profile);
```

- [ ] **Step 2: Mock 与 Spring AI 实现改 5 参**

`MockAnswerEvaluator`、`SpringAiAnswerEvaluator` 把现有方法签名改成 5 参；旧 4 参由接口默认方法兜底。`MockInterviewerReplyGenerator`、`SpringAiInterviewerReplyGenerator` 同理；Spring AI 同时重写 5 参 `stream`。`MockInterviewReportGenerator`、`SpringAiInterviewReportGenerator` 改 5 参。

- [ ] **Step 3: 销售提示词分支**

`SpringAiAnswerEvaluator`：

```java
private String systemPrompt(RoleProfile profile) {
    if (profile.isGeneral()) {
        return """
                你是销售岗位面试评分器。按 STAR 完整性、客户洞察、需求挖掘、异议处理、
                结果量化、表达说服力评分。只输出 JSON：
                {"correctness":0-100,"score":0-100,"comment":"...","missingPoints":["..."],"confidence":0-1}
                不要因为候选人没有技术背景而扣分，重点看销售行为和结果证据。
                """;
    }
    return "你是技术面试评分器。只输出 JSON：..."; // 保留现有提示词
}
```

`SpringAiInterviewerReplyGenerator`：

```java
private String systemPrompt(RoleProfile profile) {
    if (profile.isGeneral()) {
        return """
                你是一名资深销售面试官。根据候选人的回答追问客户、动作、结果和复盘，
                每次只问一个问题，语气专业、克制，不要替候选人回答，不要输出评分。
                如果候选人回答空泛，优先追问具体客户、数字和决策过程。
                """;
    }
    return "你是一名资深技术面试官。..."; // 保留现有提示词
}
```

`SpringAiInterviewReportGenerator` 销售提示词要求 dimensions 的 key 固定为 `customerInsight`、`communication`、`resilience`、`resultOrientation`、`jobMatch`，summary 用销售语境。

- [ ] **Step 4: 编译 + 现有测试**

Run: `cd /e/work/backend && mvn -q -DskipTests compile && mvn -q -Dtest=MockInterviewReportGeneratorTest,InterviewConversationServiceTest test`
Expected: PASS；旧 4 参调用不受影响。

---

## Task 6: 销售离线组卷与蓝图校验

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/service/SalesBankBlueprintGenerator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewBlueprintValidator.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/domain/BlueprintRequest.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/SpringAiBlueprintGenerator.java`
- Test: `backend/src/test/java/com/offerpilot/interview/SalesBankBlueprintGeneratorTest.java`

- [ ] **Step 1: BlueprintRequest 增加 profile**

```java
public record BlueprintRequest(
        JobTemplate job,
        InterviewTrack track,
        RoleProfile profile,
        ResumeProfile resume,
        String difficulty,
        Integer durationMinutes,
        String persona,
        List<QuestionBankItem> retrievedQuestions) {
}
```

所有构造点改为传 `roleProfileParser.parse(track)`。

- [ ] **Step 2: 校验器**

```java
public void validate(InterviewBlueprint blueprint) {
    validate(blueprint, RoleProfile.tech(), 0);
}

public void validate(InterviewBlueprint blueprint, RoleProfile profile, int expectedCount) {
    if (profile != null && profile.isGeneral()) {
        validateGeneral(blueprint, profile, expectedCount);
    } else {
        validateTech(blueprint);
    }
}
```

`validateGeneral` 规则：
- sections 非空，type 只能是 `BEHAVIORAL/SCENARIO/ROLE_PLAY/MOTIVATION`；
- 题目总数等于 `expectedCount`（6/8/10）；
- 每题 text/difficulty/referencePoints 非空，`knowledgeTags` 至少包含一个能力名；
- 总题数 >= 6 时六项能力必须全部覆盖；
- 失败抛 `BusinessException(ErrorCode.VALIDATION_ERROR, "销售蓝图...")`。

- [ ] **Step 3: SalesBankBlueprintGenerator**

关键逻辑：

```java
@Component
public class SalesBankBlueprintGenerator implements InterviewBlueprintGenerator {

    public InterviewBlueprint generate(BlueprintRequest request) {
        RoleProfile profile = request.profile();
        int total = expectedCount(request.durationMinutes());
        List<QuestionBankItem> pool = request.retrievedQuestions();
        Map<String, List<QuestionBankItem>> byCompetency = new LinkedHashMap<>();
        for (String name : profile.competencyNames()) {
            byCompetency.put(name, new ArrayList<>());
        }
        for (QuestionBankItem item : pool) {
            String competency = item.tags().stream()
                    .filter(byCompetency::containsKey).findFirst().orElse(null);
            if (competency != null) {
                byCompetency.get(competency).add(item);
            }
        }
        List<QuestionBankItem> picked = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        boolean added = true;
        while (picked.size() < total && added) {
            added = false;
            for (String name : profile.competencyNames()) {
                for (QuestionBankItem item : byCompetency.getOrDefault(name, List.of())) {
                    if (used.add(item.id())) {
                        picked.add(item);
                        added = true;
                        break;
                    }
                }
                if (picked.size() >= total) {
                    break;
                }
            }
        }
        if (picked.size() < total) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题库不足，无法组卷");
        }
        Map<String, List<BlueprintQuestion>> sections = new LinkedHashMap<>();
        for (QuestionBankItem item : picked) {
            String type = classify(item);
            sections.computeIfAbsent(type, key -> new ArrayList<>()).add(toQuestion(item));
        }
        if (sections.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "销售题型覆盖不足");
        }
        List<BlueprintSection> result = sections.entrySet().stream()
                .map(entry -> new BlueprintSection(entry.getKey(),
                        Math.max(1, entry.getValue().size() * 100 / picked.size()), entry.getValue()))
                .toList();
        return new InterviewBlueprint(
                "你好，我是今天的销售面试官。我们会围绕客户开拓、需求挖掘、方案呈现、异议处理和谈判成交展开，请尽量用真实案例回答。",
                result,
                "今天先聊到这里。感谢你的时间，后续结果会由招聘团队同步。");
    }
}
```

`classify`：tags 含“销售认知” -> `MOTIVATION`；含“异议处理/谈判成交” -> `SCENARIO`；否则 `BEHAVIORAL`。`toQuestion` 把参考答案按 `。；;` 切分 3-5 条 referencePoints，并按题型给 2 条 followUpHints。

- [ ] **Step 4: SpringAiBlueprintGenerator 销售分支**

- 系统提示词按 `profile.isGeneral()` 切换；销售提示词列出六项能力、题型、题目数量、JSON 结构。
- 用户提示词包含 JD、简历、难度、时长、retrievedQuestions（题目 + 参考要点）。
- 校验调用 `validator.validate(blueprint, profile, expectedCount)`。
- 销售 AI 失败时抛出 `BusinessException(ErrorCode.AI_UNAVAILABLE, "AI 生成失败")`，**不得**调用技术 Mock。

- [ ] **Step 5: 测试**

`SalesBankBlueprintGeneratorTest` 构造 12 条题库项（六能力各 2 条），配置 sales profile，分别用 duration 20/30/40 断言生成 6/8/10 题、能力全覆盖、无重复 id、referencePoints 非空；题库不足 8 条时断言抛异常。

Run: `cd /e/work/backend && mvn -q -Dtest=SalesBankBlueprintGeneratorTest,InterviewBlueprintValidatorTest test`
Expected: PASS。

---

## Task 7: 自定义销售面试接口

**Files:**
- Create: `backend/src/main/java/com/offerpilot/interview/web/dto/CreateCustomInterviewRequest.java`
- Modify: `backend/src/main/java/com/offerpilot/common/exception/ErrorCode.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewService.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/InterviewController.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewSessionView.java`

- [ ] **Step 1: 请求 DTO**

```java
public record CreateCustomInterviewRequest(
        @NotBlank @Size(max = 120) String jobTitle,
        @NotBlank @Size(min = 30, max = 8000) String jdText,
        @NotNull Long resumeId,
        @NotBlank String difficulty,
        @NotNull @Min(15) @Max(60) Integer durationMinutes,
        @NotBlank String persona,
        @NotBlank String mode) {
}
```

- [ ] **Step 2: ErrorCode**

```java
AI_UNAVAILABLE(40020, "AI 服务不可用"),
```

- [ ] **Step 3: InterviewService.createCustom**

```java
public InterviewSessionView createCustom(Long userId, CreateCustomInterviewRequest request) {
    Resume resume = resumeMapper.selectById(request.resumeId());
    if (resume == null || !userId.equals(resume.getUserId())) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
    }
    InterviewTrack track = trackService.requireByCode("sales");
    RoleProfile profile = roleProfileParser.parse(track);
    quotaService.checkAvailable(userId);

    List<String> tags = new ArrayList<>(profile.competencyNames());
    String query = request.jobTitle() + " " + request.jdText();
    var retrieved = questionBankRetriever.retrieve(query, tags, 50, track.getId());

    JobTemplate job = new JobTemplate();
    job.setTitle(request.jobTitle());
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
            job, track, profile, readProfile(resume), request.difficulty(),
            request.durationMinutes(), request.persona(), retrieved);
    int expected = expectedCount(request.durationMinutes());
    InterviewBlueprint blueprint;
    String generationMode;
    if (aiAvailabilityChecker.isAvailable(userId)) {
        try {
            blueprint = blueprintGenerator.generate(blueprintRequest);
            generationMode = "AI";
        } catch (Exception ex) {
            log.warn("Sales AI blueprint failed, fallback to bank: {}", ex.getMessage());
            blueprint = salesBankBlueprintGenerator.generate(blueprintRequest);
            generationMode = "BANK";
        }
    } else {
        blueprint = salesBankBlueprintGenerator.generate(blueprintRequest);
        generationMode = "BANK";
    }
    blueprintValidator.validate(blueprint, profile, expected);

    String finalMode = generationMode;
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
        session.setBlueprintJson(writeJson(blueprint));
        sessionMapper.insert(session);

        List<QuestionView> questions = saveQuestions(session.getId(), blueprint);
        return toView(session, job.getTitle(), questions);
    }));
}
```

`expectedCount`：`duration <= 20 -> 6`，`<= 35 -> 8`，否则 `10`。现有 `create` 也设置 `generationMode="AI"`（保持旧行为）。

- [ ] **Step 4: Controller**

```java
@PostMapping("/custom")
public InterviewSessionView createCustom(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody CreateCustomInterviewRequest request) {
    return interviewService.createCustom(userId, request);
}
```

- [ ] **Step 5: SessionView 加 generationMode**

在 `status` 后加 `String generationMode`，更新 `toView` 构造参数。前端旧字段不受影响。

- [ ] **Step 6: 编译**

Run: `cd /e/work/backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

---

## Task 8: 会话/报告链路传递 RoleProfile

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewConversationService.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewReportService.java`

- [ ] **Step 1: ConversationService 注入**

```java
private final InterviewTrackService trackService;
private final RoleProfileParser roleProfileParser;

private RoleProfile profile(InterviewSession session) {
    if (session.getTrackId() == null) {
        return RoleProfile.tech();
    }
    return roleProfileParser.parse(trackService.require(session.getTrackId()));
}
```

- [ ] **Step 2: 所有 AI 调用传 profile**

`start`、`answer`、`skip`、`finish` 拿到 session 后解析一次 profile，传给 `replyGenerator.generate(..., profile)` 和 `evaluateIfPractice(..., profile)`。

`streamReply`：按 `context.sessionId()` 查 session，解析 profile，调用 `replyGenerator.stream(..., profile)`。
`evaluateStreamAnswer`：`loadOwned` 后解析 profile，调用 `answerEvaluator.evaluate(..., profile)`。
`evaluateIfPractice` 增加 `RoleProfile profile` 参数。

- [ ] **Step 3: ReportService 注入并传 profile**

```java
private final InterviewTrackMapper trackMapper;
private final RoleProfileParser roleProfileParser;

private RoleProfile profile(InterviewSession session) {
    return session.getTrackId() == null
            ? RoleProfile.tech()
            : roleProfileParser.parse(trackMapper.selectById(session.getTrackId()));
}
```

`generateAsync`：解析 profile，`ensureEvaluations(..., profile)`，`reportGenerator.generate(..., profile)`。
`ensureEvaluations` 增加 profile 参数并传给 `answerEvaluator.evaluate(...)`。

- [ ] **Step 4: 测试**

Run: `cd /e/work/backend && mvn -q -Dtest=InterviewConversationServiceTest,InterviewReportControllerIT test`
Expected: PASS；技术面试行为不变。

---

## Task 9: 报告与统计动态维度

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/InterviewReportView.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/web/dto/StatsOverview.java`
- Modify: `backend/src/main/java/com/offerpilot/interview/service/InterviewReportService.java`

- [ ] **Step 1: DTO 加字段**

```java
// InterviewReportView：dimensions 后加
Map<String, String> dimensionLabels,
String generationMode,
```

```java
// StatsOverview：dimensionAverages 后加
Map<String, String> dimensionLabels,
```

- [ ] **Step 2: buildView 填 labels/generationMode**

```java
RoleProfile profile = profile(session);
Map<String, String> labels = new LinkedHashMap<>();
readMap(report.getDimensionsJson()).keySet()
        .forEach(key -> labels.put(key, profile.dimensionLabel(key)));
```

构造 `InterviewReportView` 时依次传入 `readMap(...)`、`labels`、`session.getGenerationMode()`。

- [ ] **Step 3: stats 动态聚合**

```java
Map<Long, InterviewSession> sessionById = sessions.stream()
        .collect(Collectors.toMap(InterviewSession::getId, s -> s, (a, b) -> a));
Map<Long, InterviewTrack> trackById = new HashMap<>();
for (InterviewSession session : sessions) {
    if (session.getTrackId() != null && !trackById.containsKey(session.getTrackId())) {
        trackById.put(session.getTrackId(), trackMapper.selectById(session.getTrackId()));
    }
}

Set<String> keys = new LinkedHashSet<>();
Map<Long, Map<String, Integer>> dimensionsByReport = new LinkedHashMap<>();
for (InterviewReport report : reports) {
    Map<String, Integer> dimensions = readMap(report.getDimensionsJson());
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
```

`labelFor` 找到第一条含该 key 的报告 -> session -> track -> `roleProfileParser.parse(...).dimensionLabel(key)`；找不到时回退内置技术标签表。

- [ ] **Step 4: 测试**

Run: `cd /e/work/backend && mvn -q -Dtest=InterviewReportControllerIT test`
Expected: 技术维度仍为 accuracy/depth/project/expression/match，原有断言通过。

---

## Task 10: 销售弱项推荐

**Files:**
- Modify: `backend/src/main/java/com/offerpilot/interview/service/WeaknessService.java`

- [ ] **Step 1: 按 session 的 track 判断销售**

在遍历评估时，用 `sessionId -> trackId` 映射；track category 为 `GENERAL` 或 rubric `SALES` 时，累加器标记 `sales=true` 并记录 trackId。

- [ ] **Step 2: 推荐时带 trackId**

```java
List<QuestionBankItem> recommended = acc.sales
        ? questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3, acc.trackId)
        : questionBankRetriever.retrieve(null, List.of(entry.getKey()), 3);
```

- [ ] **Step 3: 编译**

Run: `cd /e/work/backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

---

## Task 11: 前端 API 类型

**Files:**
- Modify: `frontend/src/api/interview.ts`

- [ ] **Step 1: 类型改造**

```ts
export type DimensionScores = Record<string, number>

export interface CreateCustomInterviewRequest {
  jobTitle: string
  jdText: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
}

export interface InterviewSession {
  // ...existing
  generationMode?: 'AI' | 'BANK'
}

export interface InterviewReport {
  // ...
  dimensions: DimensionScores
  dimensionLabels?: Record<string, string>
  generationMode?: 'AI' | 'BANK'
}

export interface StatsOverview {
  // ...
  dimensionAverages: DimensionScores
  dimensionLabels?: Record<string, string>
}
```

`interviewApi` 增加：

```ts
createCustom: (data: CreateCustomInterviewRequest) =>
  http.post<InterviewSession>('/interviews/custom', data),
```

- [ ] **Step 2: 类型检查**

Run: `cd /e/work/frontend && npm run build`
Expected: 若报告/历史页尚未改完会报类型错误，进入 Task 12/13 修复。

---

## Task 12: 销售入口与创建弹窗

**Files:**
- Modify: `frontend/src/components/InterviewSetupDialog.vue`
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: Dialog 支持 custom 模式**

新增 props：

```ts
const props = defineProps<{
  modelValue: boolean
  job: JobTemplate | null
  custom?: boolean
}>()
```

新增表单：

```ts
const customForm = reactive({
  jobTitle: '大客户销售',
  jdText: ''
})
```

模板在 `custom` 为 true 时显示“岗位名称”“销售 JD”两个输入；JD 为空时禁止提交。提交：

```ts
if (props.custom) {
  const created = await interviewApi.createCustom({
    jobTitle: customForm.jobTitle,
    jdText: customForm.jdText,
    resumeId: selectedResumeId.value,
    difficulty: form.difficulty,
    durationMinutes: form.durationMinutes,
    persona: form.persona,
    mode: form.mode
  })
  emit('created', created)
}
```

标题改为“创建销售面试”；按钮文案“开始销售面试”。

- [ ] **Step 2: HomeView 增加销售卡片**

在岗位列表上方增加：

```vue
<section class="sales-entry">
  <div>
    <p class="eyebrow">SALES INTERVIEW</p>
    <h2>销售岗位面试</h2>
    <p>粘贴销售 JD，系统结合简历和 50 道销售题生成面试；AI 不可用时自动使用题库模式。</p>
  </div>
  <el-button type="primary" size="large" @click="openSalesSetup">开始销售面试</el-button>
</section>
```

状态：

```ts
const customSetup = ref(false)

function openSalesSetup() {
  selectedJob.value = null
  customSetup.value = true
  setupVisible.value = true
}

function openSetup(job: JobTemplate) {
  selectedJob.value = job
  customSetup.value = false
  setupVisible.value = true
}
```

`InterviewSetupDialog` 传 `:custom="customSetup"`。

- [ ] **Step 3: 构建**

Run: `cd /e/work/frontend && npm run build`
Expected: 通过（若报告页未改完继续 Task 13）。

---

## Task 13: 报告/历史/会话页动态化

**Files:**
- Modify: `frontend/src/views/InterviewReportView.vue`
- Modify: `frontend/src/views/InterviewHistoryView.vue`
- Modify: `frontend/src/views/InterviewSessionView.vue`
- Modify: `frontend/src/views/InterviewChatView.vue`

- [ ] **Step 1: ReportView 动态维度**

```ts
const dimensions = computed(() => {
  const scores = report.value?.dimensions ?? {}
  const labels = report.value?.dimensionLabels ?? {}
  return Object.entries(scores).map(([key, value]) => ({
    key,
    label: labels[key] ?? key,
    value
  }))
})
```

模板、雷达图、列表全部改用 `dimensions`，删除硬编码的 accuracy/depth/project/expression/match。

标题旁增加：

```vue
<el-tag v-if="report?.generationMode === 'BANK'" type="info">题库模式</el-tag>
```

- [ ] **Step 2: HistoryView 动态维度**

`hasDimensions` 改为 `Object.keys(stats.dimensionAverages ?? {}).length > 0`；`renderDimensions` 改为遍历 `Object.entries`，label 取 `stats.dimensionLabels?.[key] ?? key`；ECharts radar indicator 同步动态。

- [ ] **Step 3: 会话/聊天页新题型和模式标签**

两处 `typeLabels` 增加：

```ts
BEHAVIORAL: '行为题',
SCENARIO: '情景题',
ROLE_PLAY: '角色扮演',
MOTIVATION: '动机题'
```

会话页 meta 区增加：

```vue
<el-tag v-if="session?.generationMode === 'BANK'" type="info">题库模式</el-tag>
<el-tag v-else-if="session?.generationMode === 'AI'" type="success">AI 模式</el-tag>
```

- [ ] **Step 4: 构建**

Run: `cd /e/work/frontend && npm run build`
Expected: BUILD SUCCESS，无 TS 错误。

---

## Task 14: 端到端测试与手工验证

**Files:**
- Create: `backend/src/test/java/com/offerpilot/interview/SalesInterviewControllerIT.java`

- [ ] **Step 1: 销售题库模式 IT**

流程：注册用户 -> 上传 `TestFiles.docx` -> `POST /api/interviews/custom`（`app.ai.enabled=false` 默认测试配置）-> 断言：
- HTTP 200；
- `generationMode == "BANK"`；
- 题目数 8；
- 每题 `knowledgeTags` 至少包含六能力之一；
- 题干不包含 `Java`/`Spring`/`MySQL`/`Redis`；
- 用户 GET `/api/job-templates` 能看到刚创建的自定义岗位；
- 另一个用户 GET 看不到该岗位。

- [ ] **Step 2: 销售报告维度 IT**

同一测试内：`start` -> `finish` -> `POST /api/reports/{sessionId}` -> 轮询 GET，断言 dimensions key 为 `customerInsight/communication/resilience/resultOrientation/jobMatch`，`dimensionLabels.customerInsight == "客户洞察"`，`generationMode == "BANK"`。

- [ ] **Step 3: AI 模式单元测试**

`InterviewServiceTest` 使用 mock：`aiAvailabilityChecker.isAvailable=true`，`blueprintGenerator` 返回合法销售蓝图，断言 `generationMode="AI"`；再让 blueprintGenerator 抛异常，断言回退 `BANK` 且调用 `salesBankBlueprintGenerator`。

- [ ] **Step 4: 全量测试**

Run: `cd /e/work/backend && mvn test`
Expected: 旧 112 个测试 + 新增测试全部 PASS。

- [ ] **Step 5: 前端构建**

Run: `cd /e/work/frontend && npm run build`
Expected: BUILD SUCCESS。

- [ ] **Step 6: 手工验证**

1. 重启后端（Flyway 执行 V20/V21）。
2. 打开 `http://localhost:5173`，点击“开始销售面试”。
3. 登录、上传简历、粘贴一段销售 JD，创建面试。
4. 确认题目为销售题，页面显示“题库模式”或“AI 模式”。
5. 回答 1-2 题，结束面试，生成报告。
6. 确认报告五维为“客户洞察/沟通说服/抗压应变/结果导向/岗位匹配”。
7. 进入历史页，确认维度图例为销售标签。
8. 用技术岗位再走一遍，确认技术五维和旧流程无回归。
