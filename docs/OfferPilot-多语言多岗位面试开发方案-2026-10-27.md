# OfferPilot 多语言 / 多岗位面试开发方案

> 版本：v1.0  
> 日期：2026-10-27  
> 分支：`feat/multi-language-interview`  
> 基线：`main` @ `a8a05d9`  
> 状态：设计稿；Java 与前端岗位拆分已实施，Python/Go 暂缓  
> 当前实施范围（2026-10-28）：主页展示 Java 后端、Java 全栈、Java + AI、前端；前端题库 67 道；新增 100 道黑马/传智 Java 面试题，题库达到 400 道；旧 1-3 年和 Spring Cloud 模板转为 INACTIVE；工作年限在创建面试弹窗选择
> 目标：把单一 Java 面试扩展为可按岗位选择的多语言、多岗位面试平台；首版先做 Java 后端、前端、Java 全栈  
> 说明：下文部分章节保留了最初的多语言设计稿，岗位命名以本节和第 0.5 节为准

---

## 0. 一页结论

### 0.1 产品结论

主页按**岗位**选，不按**语言**选。语言是岗位标签和筛选条件，不是主入口。

首版岗位（名称不再带年限）：

- Java 后端
- 前端
- Java 全栈
- Java + AI

工作年限放在创建面试的测试选项中：校招 / 应届、1-3 年、3-5 年，年限越高题目难度和追问深度越高。

暂不上线 Python、Go、算法、测试、非技术岗位。它们可以出现在“更多方向筹备中”，但不展示为空卡片。

### 0.2 关键决策

1. **Java 后端和前端分开**，不做“Java 前端”这种伪岗位。前端的技术栈是 JavaScript/TypeScript + Vue/React。
2. **Java 全栈单独作为一个组合岗位**，不是“Java 后端 + 前端”简单平均，而是后端为主、前端为辅，并使用独立评分标准。
3. **每个岗位题库逻辑独立**：管理端可以按岗位分别导入、上下架、排序、统计；底层题目只存一份，通过绑定关系复用到多个岗位题库。
4. **不做物理分表**：不建 `java_backend_questions`、`frontend_questions` 这种表，避免题目复制、答案不一致、统计分裂。
5. **岗位是精选组合，不是笛卡尔积**：不会自动生成“Java 前端”“Go 全栈”“C++ 运营”这类组合；只上架有题库、有评分标准、通过验收的赛道。
6. **首版不改 AI 主链路**：继续复用现有蓝图生成、SSE 追问、异步报告、BYOK Provider 能力，只增加“赛道上下文”和“题库过滤”。

### 0.3 首版不做什么

- 不做在线代码判题、代码沙箱、多语言运行时。
- 不做非技术岗位（产品、运营、HR、销售）。
- 不做 Python/Go 正式题库，只预留赛道模型。
- 不做每个岗位一张物理表。
- 不做语音面试、支付、企业端。

### 0.4 预计投入

| 阶段 | 内容 | 预计 |
|---|---|---|
| 阶段 1 | 赛道表、题库绑定、后端接口、迁移 | 3–5 个工作日 |
| 阶段 2 | 主页岗位筛选、后台题库切换 | 2–3 个工作日 |
| 阶段 3 | Java 后端绑定、前端题库、全栈题库 | 5–10 个工作日 |
| 阶段 4 | 三条赛道端到端验收、修复 | 2–3 个工作日 |

首版从开发到可用，预计 **2–3 周**，主要时间在内容质量，不在代码。

### 0.5 当前实施进度（2026-10-28）

已完成 Java/前端岗位拆分、命名收敛、工作年限选项和题库扩充：

- `V14__java_interview_tracks.sql`：新增 `interview_track`、`track_question`，回填 `job_template.track_id` 和 `interview_session.track_id`。
- `V16__simplify_job_names_and_experience.sql`：岗位名称收敛为 Java 后端 / Java 全栈 / Java + AI；旧 1-3 年和 Spring Cloud 模板转为 INACTIVE；新增前端 DRAFT 题库。
- `V17__java_question_bank_expansion_20261028.sql`：新增 100 道 Java 后端/全栈/AI 题。
- `V18__frontend_question_bank_expansion.sql`：新增 60 道前端题，前端题库达到 67 道并开放。
- `V19__java_interview_question_bank_20261028.sql`：新增 100 道黑马/传智 Java 面试题，覆盖 Java IO/网络、基础算法、Web 基础、MySQL、Spring、MyBatis、Spring Cloud、Redis、MQ、Nginx、ES、Docker、Git、Maven。
- 当前题库 400 道：Java 后端 309 道、Java 全栈 382 道、Java + AI 327 道、前端 67 道。
- 主页展示 4 个 ACTIVE 岗位；按岗位方向筛选，卡片显示语言、题目数。
- 创建面试弹窗把“难度”改为“工作年限”：校招 / 应届、1-3 年、3-5 年，难度随年限递增。
- 后台题库管理已支持按岗位题库切换、新增题目和启用/禁用题目；禁用后题目不参与面试检索。
- 创建面试时按赛道检索题目，并写入 `interview_session.track_id`。
- 后端 112 个测试通过，前端构建通过。
- Python、Go 题库按计划后置，等现有题库继续完善后再开发。

---

## 1. 现状与问题

### 1.1 当前基础

- 已打通「选岗位 → 传简历 → AI 生成蓝图 → 多轮追问 → 五维报告 → 薄弱点复练」闭环。
- 现有 6 个岗位模板，其中 4 个 ACTIVE：
  - Java 后端
  - Java 全栈
  - Java + AI
  - 前端
  - Java 后端旧 1-3 年、Spring Cloud 微服务（INACTIVE，历史兼容）
- 题库 400 道 ACTIVE 题，其中 Java 基础/集合/并发/JVM 共 117 道，前端 67 道：
  - Java 基础 43
  - Java 集合 22
  - Java 并发 33
  - JVM 19
- 新增分类：基础算法 17、Web 基础 7、常用组件 19。
- 其他分类：MySQL/MyBatis 30、Spring Boot 和 Spring 30、Redis 和缓存 20、微服务与消息队列 31、项目场景 6、项目经历追问 10、JWT 和登录认证 8、实习经历追问 8、开放题 6、Java + AI 18、Java 全栈 6。
- 前端分类：JavaScript 10、TypeScript 4、CSS 6、浏览器与网络 8、Vue 8、React 8、前端工程化 6、前端性能 4、前端安全 3、前端项目场景 3、原有 Vue 和前端基础 7。
- 现有 AI 能力：蓝图生成、流式追问、回答评估、异步报告、每用户 BYOK。
- 现有测试：后端 112 个测试通过。

### 1.2 当前限制

| 问题 | 现状 | 影响 |
|---|---|---|
| 岗位模板都是 Java | `V2__seed_job_templates.sql` 只有 4 个 Java 模板 | 无法展示多语言、多岗位 |
| 题库没有赛道维度 | `question_bank` 只有 category/tags/level | 无法按岗位隔离题目 |
| 检索加载全部题目 | `KeywordQuestionBankRetriever` 查询所有 ACTIVE 题后打分 | 题库扩大后跨岗位串题、性能下降 |
| 蓝图提示词偏技术后端 | `SpringAiBlueprintGenerator` 使用“资深技术面试官”通用提示 | 前端、全栈岗位考察重点不准确 |
| 报告维度技术岗口径 | 固定 accuracy/depth/project/expression/match | 前端和全栈缺少岗位化解释 |
| 主页只按模板平铺 | `HomeView` 直接渲染 4 张卡 | 岗位变多后没有分组和筛选 |
| 后台题库默认 Java | `AdminQuestionsView` 默认分类 `Java 基础` | 新增语言后管理混乱 |

### 1.3 设计原则

- **赛道（Track）** 是可复用的“题库 + 评分标准 + 岗位元数据”。
- **岗位模板（Job Template）** 是用户看到的卡片，包含标题、级别、JD、配置。
- 一个赛道可以被多个岗位模板引用；例如“Java 后端 1–3 年”和“Spring Cloud 微服务”可以共用 Java 后端题库，但 JD 和考察重点不同。
- 一个岗位模板只属于一个赛道。
- 题目本身不绑定岗位；绑定关系决定它出现在哪些题库。

---

## 2. 产品信息架构

### 2.1 主页结构

```text
┌────────────────────────────────────────────────────────────┐
│ OfferPilot                         成长记录  API 设置  退出 │
├────────────────────────────────────────────────────────────┤
│ 选择你的目标岗位                                            │
│ AI 将根据岗位与简历生成一场带追问的模拟面试。                │
│                                                            │
│ [全部] [后端] [前端] [全栈] [AI 应用]                       │
│ 级别：[全部] [校招] [1-3 年]                                │
│                                                            │
│ ┌────────────────────┐  ┌────────────────────┐             │
│ │ Java 后端开发       │  │ 前端开发            │             │
│ │ 校招 · Java         │  │ 校招 · JavaScript   │             │
│ │ Spring Boot · MySQL │  │ Vue / React · CSS   │             │
│ │ 120 题 · 20–30 分钟 │  │ 90 题 · 20–30 分钟  │             │
│ │ [开始面试]          │  │ [开始面试]          │             │
│ └────────────────────┘  └────────────────────┘             │
│                                                            │
│ ┌────────────────────┐  ┌────────────────────┐             │
│ │ Java 全栈开发       │  │ Java + AI 应用      │             │
│ │ 1-3 年 · Java       │  │ 1-3 年 · Java       │             │
│ │ Vue · MySQL · 工程化│  │ RAG · Agent · 评估  │             │
│ │ [开始面试]          │  │ [开始面试]          │             │
│ └────────────────────┘  └────────────────────┘             │
└────────────────────────────────────────────────────────────┘
```

### 2.2 筛选规则

- 一级筛选：岗位方向，`全部 / 后端 / 前端 / 全栈 / AI 应用`。
- 二级筛选：级别，`全部 / 校招 / 1–3 年`。
- 语言不单独做一级 tab，作为卡片标签展示，例如 `Java`、`JavaScript/TypeScript`。
- 后续赛道变多时，增加搜索框和“更多方向”，不要把所有语言铺在首屏。
- 没有岗位的方向不显示空 tab；未准备好的方向不显示空卡片。
- 卡片按 `interview_track.sort_order` 和 `job_template.sort_order` 排序。

### 2.3 卡片字段

| 字段 | 来源 | 说明 |
|---|---|---|
| 标题 | `job_template.title` | 用户看到的岗位名 |
| 级别 | `job_template.level` | 校招 / 1–3 年 |
| 岗位方向 | `interview_track.role_family` | 后端 / 前端 / 全栈 / AI 应用 |
| 语言/技术栈 | `interview_track.primary_language` + 标签 | Java、JavaScript/TypeScript |
| 考察标签 | `job_template.jd_text` 或赛道配置 | Spring Boot、MySQL、Vue |
| 题目数 | 赛道绑定题目数 | 让用户感知题库规模 |
| 时长 | 固定文案 | 20–30 分钟 |
| 状态 | `job_template.status` + `track.status` | 只有双 ACTIVE 才展示 |

### 2.4 岗位与语言的关系

不做的组合：

- Java 前端：前端不使用 Java，属于伪岗位。
- Go 前端、C++ 全栈：同理，不自动组合。
- “所有语言”平铺：首版只上有内容的岗位。

要做的组合：

- Java 后端：Java + Spring Boot + MySQL + Redis + 并发/JVM。
- 前端：JavaScript/TypeScript + Vue/React + CSS + 浏览器 + 工程化。
- Java 全栈：Java 后端为主 + Vue/React 前端 + 接口联调/部署/工程化。
- Java + AI：Java 后端 + LLM/RAG/Agent/Spring AI。

---

## 3. 岗位赛道设计

### 3.1 赛道定义

| track_code | 展示名 | role_family | primary_language | level | 首版状态 |
|---|---|---|---|---|---|
| `java-backend-campus` | Java 后端开发 | BACKEND | JAVA | 校招 | ACTIVE |
| `java-backend-junior` | Java 后端开发 | BACKEND | JAVA | 1–3 年 | ACTIVE |
| `frontend-campus` | 前端开发 | FRONTEND | JS_TS | 校招 | DRAFT → ACTIVE |
| `frontend-junior` | 前端开发 | FRONTEND | JS_TS | 1–3 年 | DRAFT → ACTIVE |
| `java-fullstack-junior` | Java 全栈开发 | FULLSTACK | JAVA | 1–3 年 | ACTIVE |
| `java-ai` | Java + AI 应用开发 | AI_APP | JAVA | 1–3 年 | ACTIVE |

`role_family` 枚举：

- `BACKEND`
- `FRONTEND`
- `FULLSTACK`
- `AI_APP`
- `ALGORITHM`（预留）
- `TEST`（预留）

`primary_language` 枚举：

- `JAVA`
- `JS_TS`
- `PYTHON`（预留）
- `GO`（预留）
- `CPP`（预留）
- `NONE`（预留，用于非技术岗）

前端赛道在 V14 先以 `DRAFT` 创建，V18 补齐到 67 道后置为 `ACTIVE`；后续继续补到 80+ 道。

### 3.2 现有岗位模板映射

| 现有模板 | 映射赛道 | 处理方式 |
|---|---|---|
| Java 后端开发（校招） | `java-backend-campus` | 直接绑定 |
| Java 后端开发（1–3 年） | `java-backend-junior` | 直接绑定 |
| Java + AI 应用开发 | `java-ai` | 直接绑定，后续补 AI 题 |
| Spring Cloud 微服务开发 | `java-backend-junior` | 首版作为 Java 后端的 JD 变体，共用题库；微服务专属题达到 40 道后再拆独立赛道 |

### 3.3 首版新增岗位模板

| 模板 | 赛道 | 说明 |
|---|---|---|
| 前端开发（校招） | `frontend-campus` | Vue/React 基础、JS、CSS、浏览器、工程化 |
| 前端开发（1–3 年） | `frontend-junior` | 增加性能、工程化、项目深度 |
| Java 全栈开发（1–3 年） | `java-fullstack-junior` | 后端为主，前端为辅，含联调/部署 |

### 3.4 赛道配置

`interview_track.config_json` 保存赛道默认配置，`job_template.config_json` 可以覆盖。

```json
{
  "focusTags": ["Java", "Spring Boot", "MySQL", "Redis", "并发", "JVM"],
  "questionMix": {
    "basic": 30,
    "project": 35,
    "scenario": 20,
    "ai": 15
  },
  "rubricCode": "BACKEND",
  "rubricLabels": {
    "accuracy": "技术准确性",
    "depth": "技术深度",
    "project": "项目表现",
    "expression": "表达逻辑",
    "match": "岗位匹配"
  },
  "rubricWeights": {
    "accuracy": 0.30,
    "depth": 0.25,
    "project": 0.20,
    "expression": 0.15,
    "match": 0.10
  }
}
```

前端赛道示例：

```json
{
  "focusTags": ["JavaScript", "TypeScript", "Vue", "React", "CSS", "浏览器", "工程化"],
  "questionMix": {
    "basic": 35,
    "project": 30,
    "scenario": 25,
    "ai": 10
  },
  "rubricCode": "FRONTEND"
}
```

全栈赛道示例：

```json
{
  "focusTags": ["Java", "Spring Boot", "Vue", "React", "MySQL", "接口联调", "部署"],
  "questionMix": {
    "basic": 30,
    "project": 30,
    "scenario": 25,
    "ai": 15
  },
  "rubricCode": "FULLSTACK",
  "questionSourceMix": {
    "backend": 60,
    "frontend": 30,
    "fullstack": 10
  }
}
```

### 3.5 赛道上线门槛

一个赛道只有同时满足以下条件才允许 `status=ACTIVE` 并展示在主页：

- 至少 60 道 ACTIVE 且已绑定题目；推荐 100+。
- 至少 3 个难度分布层级：简单、中等、困难都有题。
- 有独立 `rubric_code`，且评分维度标签已配置。
- 完成至少 3 场端到端面试，报告无明显跑题。
- 前端/全栈赛道必须包含项目追问和场景题，不能只有八股。

---

## 4. 题库独立模型

### 4.1 方案对比

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A. 物理独立 | 每个岗位一张题库表，题目复制 | 隔离彻底，理解简单 | 表爆炸、迁移复杂、题目重复、答案不一致 | 不采用 |
| B. 逻辑独立 | 题目主表 + 赛道题库 + 绑定表 | 管理端独立、题目复用、统计统一 | 多一张关联表 | **采用** |
| C. 公共池 + 专属池 | 题目带 scope，公共题所有赛道可见 | 实现简单 | 全栈/前端边界容易混乱 | 作为 B 的补充，不单独使用 |

### 4.2 推荐模型

```text
interview_track        岗位赛道（题库 + 评分标准 + 元数据）
        │
        │ 1:N
        ▼
track_question         赛道与题目的绑定（排序、权重、启用）
        │
        │ N:1
        ▼
question_bank          题目主表（题目内容只存一份）
```

管理端效果：

```text
题库管理
[Java 后端] [前端] [Java 全栈] [Java + AI]

Java 后端题库：133 题
前端题库：90 题
Java 全栈题库：100 题（后端 60 + 前端 30 + 全栈专属 10）
Java + AI 题库：80 题
```

每个题库可以独立：

- 导入题目
- 上下架
- 调整排序
- 设置权重
- 查看难度分布
- 统计分类覆盖

同一道题可以绑定到多个题库；修改题目内容一次，所有题库同步生效。

### 4.3 表结构

```sql
CREATE TABLE interview_track (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    role_family VARCHAR(32) NOT NULL,
    primary_language VARCHAR(32) NOT NULL,
    level VARCHAR(32) NOT NULL,
    description VARCHAR(512) NULL,
    rubric_code VARCHAR(64) NOT NULL,
    config_json TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_interview_track_code ON interview_track(code);
CREATE INDEX idx_interview_track_status_sort ON interview_track(status, sort_order);

CREATE TABLE track_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    track_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    weight INT NOT NULL DEFAULT 100,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_track_question ON track_question(track_id, question_id);
CREATE INDEX idx_track_question_track ON track_question(track_id, enabled);
CREATE INDEX idx_track_question_question ON track_question(question_id);
```

`track_question` 不使用软删除：解绑就是物理删除；题目本身仍使用 `question_bank.deleted` 软删除。

### 4.4 现有表变更

```sql
ALTER TABLE job_template ADD COLUMN track_id BIGINT NULL;
CREATE INDEX idx_job_template_track ON job_template(track_id);

ALTER TABLE interview_session ADD COLUMN track_id BIGINT NULL;
CREATE INDEX idx_interview_session_track ON interview_session(track_id);
```

说明：

- `job_template.track_id` 允许为空，兼容旧数据；迁移脚本会回填现有 4 个模板。
- `interview_session.track_id` 在创建面试时写入，保证旧报告不受赛道配置变更影响。
- 旧会话 `track_id` 为空时，报告服务回退到 `job_template.track_id`。
- 不在 `question_bank` 上加 `language`/`role_family`：题目本身不绑定岗位，绑定关系决定它出现在哪个题库；MySQL/Redis 这类题可以同时服务 Java 和 Python 后端。

### 4.5 题目绑定规则

- 题目创建后至少绑定一个赛道，否则不允许 `ACTIVE`。
- 一道题可以绑定多个赛道；例如 Java 基础题同时绑定 `java-backend-campus`、`java-backend-junior`、`java-fullstack-junior`。
- 题目软删除时，同时删除所有 `track_question` 绑定。
- 从某个题库移除题目只删除对应绑定，不删除题目本身。
- `enabled=0` 表示该题在当前题库暂时停用，不影响其他题库。
- `weight` 用于检索排序，默认 100；高频题可以调高。
- `sort_order` 只影响后台展示顺序，不影响 AI 出题顺序。

### 4.6 题目检索

当前 `KeywordQuestionBankRetriever` 会加载全部 ACTIVE 题再打分。改造后：

```java
List<QuestionBankItem> retrieve(String query, List<String> tags, int limit, Long trackId);
```

执行顺序：

1. 先按 `track_id` 过滤，只取该赛道 `enabled=1` 且题目 `ACTIVE`、未删除的题。
2. 再按标签/关键词打分。
3. 分数相同按 `weight`、`difficulty`、`id` 排序。
4. 返回前 `limit` 条。
5. 如果赛道绑定题不足，记录告警并回退到该赛道全部题；如果仍为空，回退到全局 ACTIVE 题，保证面试不因配置缺失而失败。

兼容策略：

- 保留原三参数方法作为默认实现，内部调用 `retrieve(query, tags, limit, null)`。
- `null` 表示不按赛道过滤，行为与现在一致。
- `WeaknessService` 等没有赛道上下文的调用继续使用全局检索；后续可从最近一场面试取赛道。

SQL 参考：

```sql
SELECT q.*
FROM question_bank q
JOIN track_question tq ON tq.question_id = q.id
WHERE tq.track_id = #{trackId}
  AND tq.enabled = 1
  AND q.status = 'ACTIVE'
  AND q.deleted = 0
```

### 4.7 全栈题库组合

Java 全栈题库不是复制一份后端题库和前端题库，而是绑定子集：

| 来源 | 占比 | 示例 |
|---|---:|---|
| Java 后端子集 | 60% | Java 基础、Spring、MySQL、Redis、并发、JVM |
| 前端子集 | 30% | JS/TS、Vue/React、CSS、浏览器、工程化 |
| 全栈专属 | 10% | 接口设计、跨域、登录态、联调、Nginx、Docker、部署 |

全栈专属题示例：

- 前后端如何约定接口和错误码？
- 跨域是怎么产生的？生产环境怎么处理？
- JWT 放在 Cookie 还是 localStorage？各自风险是什么？
- 一个请求从前端到后端经过哪些环节？如何排查慢请求？
- 前端构建产物如何部署？Nginx 反向代理怎么配置？
- 全栈项目如何做联调和灰度发布？

### 4.8 内容计划

| 赛道 | 现有可绑定 | 首版目标 | 缺口 |
|---|---:|---:|---|
| Java 后端（校招/1–3 年） | 133（除 Vue 7 题） | 130+ | 基本够用，补 Spring Cloud/MQ |
| 前端（校招/1–3 年） | 67 | 80–120 | 已补 JS/TS、CSS、浏览器、Vue/React、工程化，继续补项目场景题 |
| Java 全栈 | 约 90 可复用 | 80–100 | 需新增 10–20 道全栈专属 |
| Java + AI | Java 题可复用 | 80–100 | 需新增 40–60 道 AI 应用题 |

前端题库首版建议覆盖：

- JavaScript：类型与转换、闭包、原型链、this、事件循环、Promise/async、ES6、模块化、垃圾回收
- TypeScript：类型系统、泛型、类型收窄、interface/type、装饰器
- CSS：盒模型、BFC、Flex/Grid、定位、层叠上下文、响应式、动画
- 浏览器：HTTP/HTTPS、缓存、Cookie/Storage、CORS、DOM 事件、渲染流程、安全（XSS/CSRF）
- 框架：Vue 3 响应式、Composition API、diff、生命周期、Router/Pinia；React Hooks、Fiber、状态管理
- 工程化：Vite/Webpack、Tree Shaking、代码分割、ESLint、包管理、CI
- 性能：Core Web Vitals、懒加载、CDN、图片优化、首屏优化
- 项目与场景：前端项目难点、组件设计、权限、埋点、监控、线上排障

AI 应用题库首版建议覆盖：

- LLM 基础：Token、上下文窗口、温度、幻觉、能力边界
- Prompt：结构化提示词、Few-shot、输出约束、注入防护
- RAG：切分、Embedding、向量检索、重排、引用、评估
- Agent：工具调用、Function Calling、规划、记忆、多步执行
- Spring AI：ChatClient、Advisor、结构化输出、流式输出
- 工程：成本、延迟、限流、降级、缓存、安全、评测集

### 4.9 题目内容规范

每道题必须包含：

- `category`：如 `Java 并发`、`前端工程化`
- `level`：校招 / 1–3 年
- `tags`：至少 2 个，逗号分隔
- `difficulty`：1 简单 / 2 中等 / 3 困难
- `question`：一道题只问一个核心点
- `reference_answer`：先结论，再 3–5 个要点，最后 1 个常见误区
- `source`：个人整理 / 网络整理 / 课程整理
- `status`：ACTIVE / INACTIVE

参考答案控制在 100–300 字，避免大段复制公开资料。题目按“问题 + 追问点”设计，方便 AI 继续追问。

---

## 5. 后端设计

### 5.1 新增模块

```text
com.offerpilot.track
├── domain/InterviewTrack.java
├── domain/TrackQuestion.java
├── mapper/InterviewTrackMapper.java
├── mapper/TrackQuestionMapper.java
├── service/InterviewTrackService.java
└── web/AdminTrackController.java
```

职责：

- `InterviewTrackService`：赛道查询、按 code 查询、校验赛道 ACTIVE、统计题目数、绑定/解绑题目。
- `AdminTrackController`：管理员查看赛道列表、题目数、绑定关系。
- 公共岗位列表继续走 `JobTemplateController`，不新增公共赛道接口。

### 5.2 实体变更

| 实体 | 变更 |
|---|---|
| `JobTemplate` | 增加 `Long trackId` |
| `InterviewSession` | 增加 `Long trackId` |
| `InterviewTrack` | 新增 |
| `TrackQuestion` | 新增 |
| `QuestionBank` | 不变 |

### 5.3 接口设计

#### 公共接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/job-templates` | 返回 ACTIVE 且赛道 ACTIVE 的岗位模板 |
| GET | `/api/job-templates/{id}` | 岗位详情，含赛道信息 |

`JobTemplateView` 增加字段：

```java
public record JobTemplateView(
        Long id,
        String title,
        String level,
        String category,
        String jdText,
        Long trackId,
        String trackCode,
        String trackName,
        String roleFamily,
        String primaryLanguage,
        String trackLevel,
        Integer questionCount
) {
}
```

`questionCount` 通过一次分组统计查询得到，避免每个岗位单独查一次。

#### 管理端接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/tracks` | 赛道列表，含题目数、状态 |
| GET | `/api/admin/tracks/{id}/questions` | 某赛道题目分页 |
| POST | `/api/admin/tracks/{id}/questions` | 批量绑定题目 |
| DELETE | `/api/admin/tracks/{id}/questions/{questionId}` | 从赛道移除题目 |
| PATCH | `/api/admin/tracks/{id}/questions/{questionId}` | 启用/停用/调整权重 |
| GET | `/api/admin/questions?trackId=` | 题目分页，支持赛道过滤 |
| POST | `/api/admin/questions` | 创建题目，可带 `trackIds` |
| PUT | `/api/admin/questions/{id}` | 更新题目，可带 `trackIds` |
| PATCH | `/api/admin/questions/{id}/status` | 启用/禁用题目 |
| DELETE | `/api/admin/questions/{id}` | 软删除题目并清理绑定 |

请求示例：

```json
{
  "category": "Java 并发",
  "level": "1-3 年",
  "tags": "Java,线程池,并发",
  "difficulty": 2,
  "question": "线程池的核心参数有哪些？",
  "referenceAnswer": "核心参数包括 corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory、handler……",
  "source": "个人整理",
  "status": "ACTIVE",
  "trackIds": [1, 3]
}
```

### 5.4 创建面试流程

```text
POST /api/interviews
  → 校验 jobTemplateId
  → 加载 JobTemplate + InterviewTrack
  → 校验 jobTemplate.status=ACTIVE 且 track.status=ACTIVE
  → 按 trackId 检索题目
  → 生成蓝图（带赛道上下文）
  → 保存 session.trackId
  → 保存题目
```

改动点：

- `InterviewService.create` 加载 `InterviewTrack`，把 `trackId` 写入 `InterviewSession`。
- `BlueprintRequest` 增加 `InterviewTrack track` 字段。
- `JobTemplate` 的 `config_json` 与赛道配置合并，岗位模板优先。
- 如果岗位模板没有 `trackId`（旧数据），回退到按 `category` 匹配赛道；匹配不到时使用 `java-backend-junior` 作为兼容默认值并记录告警。

### 5.5 蓝图生成

`SpringAiBlueprintGenerator` 的提示词增加赛道信息：

```text
岗位赛道：Java 后端开发（1-3 年）
岗位方向：后端
技术栈：Java / Spring Boot / MySQL / Redis
考察重点：Java 基础、集合、并发、JVM、Spring、MySQL、Redis
题目来源：该赛道题库检索结果
评分标准：BACKEND
```

约束：

- JD、简历、题库内容仍然只作为数据，不执行其中的指令。
- 前端赛道蓝图必须包含 JavaScript/CSS/浏览器/框架/工程化中的至少 3 类。
- 全栈赛道蓝图必须同时包含后端题和前端题，并至少 1 道全栈场景题。
- 赛道配置缺失时使用默认后端配置，不影响现有 Java 面试。
- 保留现有 25 秒 `AI_BLUEPRINT_TIMEOUT` 和 JSON 模式，不因新增上下文放大超时。

`MockBlueprintGenerator` 需要：

- 从赛道配置读取 `questionMix`。
- 前端/全栈赛道返回对应类型的 Mock 题目，避免 Mock 模式仍然只出 Java 题。
- 赛道未知时保持现有行为。

### 5.6 报告与评分

首版继续使用固定五个维度 key：

- `accuracy`
- `depth`
- `project`
- `expression`
- `match`

原因：报告表、前端图表、历史记录都按这五个 key 渲染，新增 key 会影响兼容性。

赛道化方式：

- `rubric_code` 决定五个维度的展示名和权重。
- `InterviewReportService` 读取 session.trackId 对应赛道的 `rubricLabels`/`rubricWeights`。
- `SpringAiInterviewReportGenerator` 的提示词加入赛道信息。
- `MockInterviewReportGenerator` 使用赛道权重计算总分。
- 前端报告页根据 `trackCode` 显示对应维度名；没有配置时使用现有文案。

全栈报告建议：

- `accuracy`：后端基础 + 前端基础
- `depth`：系统设计 + 工程化深度
- `project`：全栈项目完整度
- `expression`：表达逻辑
- `match`：与全栈岗位匹配度

前端报告建议：

- `accuracy`：JS/TS、CSS、浏览器、框架准确性
- `depth`：原理、性能、工程化深度
- `project`：前端项目难点与量化结果
- `expression`：表达逻辑
- `match`：与前端岗位匹配度

### 5.7 兼容性

- 旧 `interview_session` 的 `track_id` 为空：报告和详情页通过 `job_template.track_id` 回退。
- 旧 `job_template` 的 `track_id` 为空：迁移脚本按标题回填；运行期再按 category 兜底。
- 旧接口 `/api/admin/questions` 不传 `trackId` 时保持原行为，返回全部题目。
- 旧三参数 `retrieve` 方法保留，内部委托四参数方法。
- 不修改现有 SSE、报告异步、BYOK Provider 接口。

### 5.8 性能与索引

- `track_question(track_id, enabled)` 覆盖按赛道取题。
- `track_question(question_id)` 覆盖题目反查题库。
- `job_template(track_id)` 覆盖岗位按赛道统计。
- `interview_session(track_id)` 覆盖按赛道分析。
- 岗位列表的 `questionCount` 用 `GROUP BY track_id` 一次查出，禁止 N+1。
- 关键词检索在 500 题以内保持内存打分；超过 1000 题再引入向量/全文检索。

---

## 6. 前端设计

### 6.1 主页 `HomeView.vue`

新增状态：

```ts
const activeRoleFamily = ref('ALL')
const activeLevel = ref('ALL')

const roleFamilyOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'BACKEND', label: '后端' },
  { value: 'FRONTEND', label: '前端' },
  { value: 'FULLSTACK', label: '全栈' },
  { value: 'AI_APP', label: 'AI 应用' }
]

const filteredJobs = computed(() =>
  jobs.value.filter((job) => {
    const roleMatch = activeRoleFamily.value === 'ALL'
      || job.roleFamily === activeRoleFamily.value
    const levelMatch = activeLevel.value === 'ALL'
      || job.level === activeLevel.value
    return roleMatch && levelMatch
  })
)
```

UI 变更：

- Hero 下方增加方向 tab 和级别 chip。
- 卡片显示语言/技术栈标签、题目数。
- 无匹配结果时显示“该方向暂无岗位”，不要显示空白网格。
- 未上线方向不显示 tab；等有 ACTIVE 赛道后再加。
- 卡片按钮仍调用 `InterviewSetupDialog`，创建请求不变。

### 6.2 API 类型 `frontend/src/api/job.ts`

```ts
export interface JobTemplate {
  id: number
  title: string
  level: string
  category: string
  jdText: string
  trackId: number
  trackCode: string
  trackName: string
  roleFamily: 'BACKEND' | 'FRONTEND' | 'FULLSTACK' | 'AI_APP' | 'ALGORITHM' | 'TEST'
  primaryLanguage: 'JAVA' | 'JS_TS' | 'PYTHON' | 'GO' | 'CPP' | 'NONE'
  trackLevel: string
  questionCount: number
}
```

### 6.3 后台题库 `AdminQuestionsView.vue`

新增：

- 顶部赛道 tab：`全部 / Java 后端 / 前端 / Java 全栈 / Java + AI`。
- `activeTrackId` 变化时重新加载第 1 页。
- 表格增加“所属题库”列，展示题目绑定的赛道。
- 新建弹窗增加“所属题库”多选，默认选中当前 tab。
- 题目分类默认值根据当前赛道推导，例如 Java 后端 → `Java 基础`，前端 → `JavaScript`。
- 操作只保留“启用/禁用”，不再提供编辑、删除和移出题库；禁用后题目不参与面试检索。

API 变更：

```ts
export interface AdminQuestionRequest {
  category: string
  level: string
  tags: string
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  trackIds?: number[]
}

export const adminTrackApi = {
  list: () => http.get<never, AdminTrack[]>('/admin/tracks'),
  questions: (trackId: number, params: { page: number; size: number; keyword?: string }) =>
    http.get<never, PageResult<AdminQuestion>>(`/admin/tracks/${trackId}/questions`, { params }),
  bind: (trackId: number, questionIds: number[]) =>
    http.post<never, void>(`/admin/tracks/${trackId}/questions`, { questionIds }),
  unbind: (trackId: number, questionId: number) =>
    http.delete<never, void>(`/admin/tracks/${trackId}/questions/${questionId}`)
}
```

### 6.4 面试页与报告页

- 面试页顶部显示赛道名称，例如 `Java 全栈开发 · 1–3 年`。
- 报告页标题显示赛道名称；维度名称从赛道配置映射。
- 历史记录增加岗位方向标签，方便筛选。
- 旧会话没有赛道信息时，继续显示岗位模板标题。

### 6.5 空态与错误态

- 主页无岗位：显示“暂无岗位模板”，保留管理员入口。
- 某方向无岗位：显示“该方向正在筹备中”。
- 赛道题目不足：创建面试前返回明确错误“该岗位题库正在补充，请选择其他岗位”，不要生成半场面试。
- 后台赛道无题目：显示“该题库还没有题目，去绑定或新建”。

---

## 7. 数据迁移

### 7.1 迁移文件

新增 `V14__multi_language_tracks.sql`。

内容顺序：

1. 创建 `interview_track`。
2. 创建 `track_question`。
3. `job_template` 增加 `track_id`。
4. `interview_session` 增加 `track_id`。
5. 插入 6 个赛道。
6. 回填现有 4 个岗位模板的 `track_id`。
7. 插入 3 个新岗位模板（前端校招/1–3 年、Java 全栈）。
8. 把现有题目绑定到 Java 后端赛道。
9. 把 Vue 题绑定到前端和全栈赛道。
10. 把通用题（项目/实习/开放题）绑定到多个技术赛道。

### 7.2 赛道种子

```sql
INSERT INTO interview_track
(code, name, role_family, primary_language, level, description,
 rubric_code, config_json, status, sort_order, created_at, updated_at)
VALUES
('java-backend-campus', 'Java 后端开发', 'BACKEND', 'JAVA', '校招',
 'Java 基础、集合、并发、JVM、Spring、MySQL、Redis',
 'BACKEND',
 '{"focusTags":["Java","Spring Boot","MySQL","Redis"],"questionMix":{"basic":35,"project":30,"scenario":20,"ai":15}}',
 'ACTIVE', 10, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-backend-junior', 'Java 后端开发', 'BACKEND', 'JAVA', '1-3 年',
 'Java 后端进阶、框架、数据库、缓存、项目与场景',
 'BACKEND',
 '{"focusTags":["Java","Spring Boot","MySQL","Redis","并发","JVM"],"questionMix":{"basic":30,"project":35,"scenario":20,"ai":15}}',
 'ACTIVE', 20, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('frontend-campus', '前端开发', 'FRONTEND', 'JS_TS', '校招',
 'JavaScript、TypeScript、CSS、浏览器、Vue/React 基础',
 'FRONTEND',
 '{"focusTags":["JavaScript","TypeScript","Vue","React","CSS","浏览器"],"questionMix":{"basic":40,"project":25,"scenario":25,"ai":10}}',
 'DRAFT', 30, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('frontend-junior', '前端开发', 'FRONTEND', 'JS_TS', '1-3 年',
 '前端工程化、性能优化、框架原理、项目深度',
 'FRONTEND',
 '{"focusTags":["JavaScript","TypeScript","Vue","React","工程化","性能"],"questionMix":{"basic":30,"project":30,"scenario":30,"ai":10}}',
 'DRAFT', 40, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-fullstack-junior', 'Java 全栈开发', 'FULLSTACK', 'JAVA', '1-3 年',
 'Java 后端为主，Vue/React 前端为辅，含接口联调与部署',
 'FULLSTACK',
 '{"focusTags":["Java","Spring Boot","Vue","React","MySQL","接口联调"],"questionMix":{"basic":30,"project":30,"scenario":25,"ai":15},"questionSourceMix":{"backend":60,"frontend":30,"fullstack":10}}',
 'ACTIVE', 50, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('java-ai', 'Java + AI 应用开发', 'AI_APP', 'JAVA', '1-3 年',
 'Java 后端 + LLM/RAG/Agent/Spring AI',
 'AI_APP',
 '{"focusTags":["Java","Spring AI","RAG","Agent","Embedding"],"questionMix":{"basic":25,"project":30,"scenario":25,"ai":20}}',
 'ACTIVE', 60, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
```

### 7.3 回填岗位模板

```sql
UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-backend-campus')
WHERE title = 'Java 后端开发（校招）';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-backend-junior')
WHERE title = 'Java 后端开发（1–3 年）';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-ai')
WHERE title = 'Java + AI 应用开发';

UPDATE job_template
SET track_id = (SELECT id FROM interview_track WHERE code = 'java-backend-junior')
WHERE title = 'Spring Cloud 微服务开发';
```

### 7.4 绑定现有题目

Java 后端校招和 1–3 年题库先绑定除 Vue 外的全部现有题：

```sql
INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    100,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t
  ON t.code IN ('java-backend-campus', 'java-backend-junior')
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category <> 'Vue 和前端基础';
```

前端校招和 1–3 年题库先绑定现有 Vue 题（后续补到 80+ 后再开放）：

```sql
INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    100,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t
  ON t.code IN ('frontend-campus', 'frontend-junior')
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category = 'Vue 和前端基础';
```

Java 全栈题库绑定 Java 后端子集 + Vue 子集，示例：

```sql
INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    (SELECT id FROM interview_track WHERE code = 'java-fullstack-junior'),
    q.id,
    0,
    100,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category IN (
      'Java 基础', 'Java 集合', 'Java 并发', 'JVM',
      'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
      'JWT 和登录认证', 'Vue 和前端基础', '项目经历追问', '实习经历追问', '开放题'
  );
```

Java + AI 题库先绑定 Java 后端、Spring、数据库、缓存和项目题，AI 专属题在阶段 3 补充：

```sql
INSERT INTO track_question (track_id, question_id, sort_order, weight, enabled, created_at, updated_at)
SELECT
    t.id,
    q.id,
    0,
    100,
    1,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM question_bank q
JOIN interview_track t ON t.code = 'java-ai'
WHERE q.status = 'ACTIVE'
  AND q.deleted = 0
  AND q.category IN (
      'Java 基础', 'Java 集合', 'Java 并发', 'JVM',
      'Spring Boot 和 Spring', 'MySQL 和 MyBatis', 'Redis 和缓存',
      'JWT 和登录认证', '项目经历追问', '实习经历追问', '开放题'
  );
```

Java 校招和 1–3 年赛道可以复用同一批题，但通过 `weight` 和蓝图难度控制差异；如果希望两个赛道完全独立，可在阶段 3 调整绑定子集。

### 7.5 新岗位模板

新增前端校招、前端 1–3 年、Java 全栈三个 `job_template`，`track_id` 指向对应赛道。JD 要写清楚考察重点，便于蓝图生成。

### 7.6 迁移注意事项

- Flyway 迁移不可回滚，执行前备份 MySQL：`mysqldump offerpilot`。
- 先在 H2 测试库跑完整测试，再在 MySQL 开发库执行。
- 子查询在 H2/MySQL 都可用；避免使用 MySQL 特有 JSON 函数。
- `config_json` 使用 `TEXT`，不新增 JSON 类型，保持 H2 兼容。
- 迁移后校验：
  - 6 个赛道全部存在；Java 后端/全栈/AI/前端为 ACTIVE。
  - 6 个岗位模板 `track_id` 全部非空。
  - Java 后端两个赛道各绑定 ≥ 100 题。
  - 前端赛道绑定 ≥ 60 题（当前 67 题）。
  - 全栈赛道绑定 ≥ 60 题。
  - Java + AI 赛道绑定 ≥ 60 题。

---

## 8. 实施计划

### 8.1 阶段 1：赛道与题库模型（3–5 天）

- [ ] 新增 `V14__multi_language_tracks.sql`
- [ ] 新增 `InterviewTrack`、`TrackQuestion` 实体与 Mapper
- [ ] 新增 `InterviewTrackService`
- [ ] `JobTemplate` 增加 `trackId`
- [ ] `InterviewSession` 增加 `trackId`
- [ ] `JobTemplateView` 增加赛道字段和题目数
- [ ] `QuestionBankRetriever` 增加赛道过滤
- [ ] `InterviewService.create` 写入 `trackId`
- [ ] `BlueprintRequest` 增加赛道信息
- [ ] 新增 `SchemaV14IT`、`TrackQuestionRetrieverIT`
- [ ] 后端全量测试通过

### 8.2 阶段 2：主页与后台 UI（2–3 天）

- [ ] `frontend/src/api/job.ts` 增加赛道字段
- [ ] `HomeView.vue` 增加方向/级别筛选
- [ ] 岗位卡片显示语言、技术栈、题目数
- [ ] `AdminQuestionsView.vue` 增加赛道 tab
- [ ] 新建/编辑题目支持多选所属题库
- [ ] 增加“从当前题库移除”
- [ ] 前端构建通过

### 8.3 阶段 3：内容建设（5–10 天）

- [ ] 绑定 Java 后端现有题
- [ ] 新增前端题库 80–120 道
- [ ] 前端题库达到 80+ 后，将 `frontend-campus`、`frontend-junior` 置为 ACTIVE
- [ ] 新增 Java 全栈专属题 10–20 道
- [ ] 新增 Java + AI 题 40–60 道（可放到下一批）
- [ ] 每个赛道校验难度分布
- [ ] 管理端逐题检查分类/标签/答案质量

### 8.4 阶段 4：端到端验收（2–3 天）

- [ ] Java 后端校招完整面试
- [ ] 前端校招完整面试
- [ ] Java 全栈完整面试
- [ ] 检查蓝图是否包含赛道考察重点
- [ ] 检查报告维度是否符合赛道
- [ ] 检查旧 Java 会话仍可打开
- [ ] 检查后台绑定/解绑/统计
- [ ] 修复问题并回归测试

### 8.5 后续阶段

- Python 后端、Go 后端赛道
- 算法、测试开发赛道
- 非技术岗位独立产品线
- 向量/混合检索
- 在线代码判题

---

## 9. 测试与验收

### 9.1 后端测试

| 测试 | 内容 |
|---|---|
| `SchemaV14IT` | 赛道、绑定表、`track_id` 回填、新岗位模板 |
| `TrackQuestionRetrieverIT` | 按赛道只返回绑定题；Java 后端不返回纯前端题；全栈返回两类 |
| `InterviewServiceTest` | 创建面试写入 `trackId`；旧模板无赛道时回退 |
| `JobTemplateControllerIT` | 岗位列表包含赛道字段和题目数 |
| `QuestionBankAdminServiceTest` | 创建/更新题目时绑定多赛道；解绑不删除题目 |
| `InterviewReportServiceTest` | 赛道 rubric 生效；旧会话回退 |

### 9.2 前端验收

- 主页方向/级别筛选结果正确。
- 卡片题目数、语言标签正确。
- 空态文案正确。
- 后台题库 tab 切换后分页从第 1 页开始。
- 新建题目默认绑定当前题库。
- 编辑题目能正确回显所属题库。
- “从当前题库移除”后题目仍在“全部题目”中。
- 面试页和报告页显示赛道名称。

### 9.3 端到端验收标准

1. 三个首版赛道都能从主页创建面试。
2. 每个赛道至少完成 3 场真实 AI 面试，题目不跨赛道串题。
3. Java 后端面试不出现 Vue/React 专属题。
4. 前端面试不出现 Java/JVM 专属题。
5. 全栈面试同时出现后端、前端和全栈场景题。
6. 报告维度名称和权重与赛道一致。
7. 旧 Java 会话、旧报告仍可正常打开。
8. 后台可以按赛道独立管理题库，绑定/解绑后统计即时更新。
9. 后端 85 个现有测试 + 新增测试全部通过。
10. 前端 `npm run build` 通过。

### 9.4 性能验收

- 岗位列表 100 个模板以内，接口 P95 < 300ms。
- 赛道题目 500 道以内，关键词检索 P95 < 100ms。
- 蓝图生成保持现有 25s 超时，前端 60s 创建超时。
- 后台题目分页 20 条，P95 < 300ms。

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 前端题库目前 67 道 | 前端面试质量仍需提升 | 继续补到 80–120 道，优先项目场景和工程化 |
| 全栈变成“后端换皮” | 用户感知不到全栈差异 | 强制 60/30/10 配比 + 10 道全栈专属题 + 独立 rubric |
| 关键词检索跨赛道串题 | 后端问前端、前端问 JVM | 先按 `track_id` 过滤再打分；测试覆盖 |
| 赛道膨胀 | 主页卡片过多、内容维护不过来 | 只展示 ACTIVE 且有 60+ 题的赛道；未准备好不展示 |
| AI 蓝图延迟增加 | 创建面试转圈更久 | 赛道配置精简；保留 25s 超时和 JSON 模式；必要时异步化 |
| 报告维度不匹配 | 前端/全栈评分解释奇怪 | 首版保持五个 key，只换标签和权重；后续再扩维度 |
| 旧数据兼容 | 历史会话打不开 | `track_id` 可空 + job template 回退 + 迁移测试 |
| 题库绑定关系复杂 | 管理员误操作 | 默认当前题库、二次确认、解绑不删题、提供“全部题目”视图 |
| 题目重复/答案不一致 | 维护成本高 | 题目只存一份，绑定复用；导入时按题干去重 |
| 迁移失败 | 开发库/测试库不一致 | 先 H2 后 MySQL；执行前备份；迁移后跑校验 SQL |

---

## 11. 后续演进

### 11.1 第二批语言

优先 Python，其次 Go：

- Python 后端：Python 基础、FastAPI/Django、数据库、异步、工程化。
- Go 后端：Go 基础、Gin、并发、网络、工程化。
- 每个赛道 100–150 题，独立 rubric，独立蓝图提示词。

### 11.2 通用技术岗

- 算法：数据结构、算法题思路、复杂度分析。
- 系统设计：高并发、分布式、缓存、消息队列、数据库设计。
- 测试开发：测试理论、自动化、性能测试、CI/CD。

这些赛道跨语言复用率高，适合在 3–5 个语言之后做。

### 11.3 非技术岗位

产品、运营、HR、销售需要独立的能力模型和评分标准，建议单独产品线，不与技术面试共用五维报告。

### 11.4 检索升级

- 题量超过 1000 后引入 pgvector + bge-m3 向量检索。
- 混合检索：赛道过滤 + 关键词 + 向量 + 难度/标签加权。
- 增加题目曝光/使用次数，避免同一题反复出现。

### 11.5 在线代码判题

如果需要手写代码：

- 独立判题服务
- 多语言运行时
- 沙箱隔离、资源限制、安全审计
- 测试用例管理

这是独立工程，不建议和本轮多语言题库混在一起做。

---

## 附录 A：接口示例

### A.1 岗位列表

```http
GET /api/job-templates
```

```json
[
  {
    "id": 1,
    "title": "Java 后端开发（校招）",
    "level": "校招",
    "category": "Java 后端",
    "jdText": "负责公司核心业务系统的后端开发……",
    "trackId": 1,
    "trackCode": "java-backend-campus",
    "trackName": "Java 后端开发",
    "roleFamily": "BACKEND",
    "primaryLanguage": "JAVA",
    "trackLevel": "校招",
    "questionCount": 133
  }
]
```

### A.2 后台赛道列表

```http
GET /api/admin/tracks
```

```json
[
  {
    "id": 1,
    "code": "java-backend-campus",
    "name": "Java 后端开发",
    "roleFamily": "BACKEND",
    "primaryLanguage": "JAVA",
    "level": "校招",
    "status": "ACTIVE",
    "questionCount": 133,
    "activeQuestionCount": 128
  }
]
```

### A.3 绑定题目

```http
POST /api/admin/tracks/1/questions
Content-Type: application/json
```

```json
{
  "questionIds": [101, 102, 103]
}
```

### A.4 解绑题目

```http
DELETE /api/admin/tracks/1/questions/101
```

---

## 附录 B：赛道配置字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `focusTags` | string[] | 蓝图和检索的重点标签 |
| `questionMix` | object | basic/project/scenario/ai 占比 |
| `questionSourceMix` | object | 全栈专用：backend/frontend/fullstack 占比 |
| `rubricCode` | string | 评分标准编码 |
| `rubricLabels` | object | 五个维度的展示名 |
| `rubricWeights` | object | 五个维度的权重，总和为 1 |
| `defaultCategory` | string | 后台新建题目时的默认分类 |
| `blueprintHints` | string[] | 追加到蓝图提示词的考察提示 |

---

## 附录 C：命名与约定

| 名称 | 约定 |
|---|---|
| 赛道 | `interview_track` / `trackCode` |
| 岗位模板 | `job_template` / `jobTemplateId` |
| 题目 | `question_bank` / `questionId` |
| 题库绑定 | `track_question` |
| 岗位方向 | `roleFamily`：BACKEND/FRONTEND/FULLSTACK/AI_APP/ALGORITHM/TEST |
| 主语言 | `primaryLanguage`：JAVA/JS_TS/PYTHON/GO/CPP/NONE |
| 评分标准 | `rubricCode`：BACKEND/FRONTEND/FULLSTACK/AI_APP |

### 待确认但不阻塞开发的选择

1. **前端首版是否同时覆盖 Vue 和 React**：建议都覆盖，Vue 题更深，React 先覆盖基础和 Hooks；JD 决定蓝图侧重。
2. **Python 还是 Go 作为下一个语言**：建议 Python，岗位基数更大；Go 作为第三批。
3. **Spring Cloud 是否独立赛道**：首版作为 Java 后端 1–3 年的 JD 变体共用题库；微服务专属题达到 40 道后再拆。
4. **非技术岗位是否做**：本轮不做，后续单独评估。
5. **后台赛道 CRUD 是否首版就做**：首版赛道由迁移脚本维护；后台只做题目绑定和统计，赛道 CRUD 放到下一阶段。

---

## 附录 D：实施检查清单

### 数据库

- [ ] `V14__multi_language_tracks.sql` 可在 H2 和 MySQL 执行
- [ ] 6 个赛道种子数据正确
- [ ] 7 个岗位模板 `track_id` 非空
- [ ] Java 后端/前端/全栈绑定关系正确
- [ ] 备份 MySQL

### 后端

- [ ] 赛道实体/Mapper/Service
- [ ] `JobTemplateView` 赛道字段
- [ ] `InterviewSession.trackId`
- [ ] 检索按赛道过滤
- [ ] 创建面试写入赛道
- [ ] 蓝图提示词带赛道
- [ ] 报告 rubric 按赛道
- [ ] 后台绑定/解绑接口
- [ ] 旧数据兼容
- [ ] 测试全绿

### 前端

- [ ] 主页筛选
- [ ] 卡片赛道标签/题目数
- [ ] 后台题库 tab
- [ ] 多选所属题库
- [ ] 从题库移除
- [ ] 面试/报告页赛道名称
- [ ] 空态/错误态
- [ ] 构建通过

### 内容

- [ ] Java 后端 130+ 题
- [ ] 前端 80–120 题
- [ ] 全栈 80–100 题
- [ ] 每个赛道难度分布合理
- [ ] 全栈专属题 ≥ 10
- [ ] 前端项目/场景题 ≥ 20
- [ ] 参考答案质量抽查

### 验收

- [ ] 三条赛道各 3 场真实面试
- [ ] 无跨赛道串题
- [ ] 报告维度正确
- [ ] 旧会话可打开
- [ ] 后台统计正确
- [ ] 性能达标
