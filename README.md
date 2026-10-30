# OfferPilot

面向 Java 后端 / Java+AI 求职者的垂直 AI 模拟面试教练。

当前进度：P0 已闭环（Week 6 完成：Docker Compose 一键部署、20 并发压测、演示物料）。

当前主交付为 **Electron 桌面版**：`electron/`。它内置 Spring Boot sidecar、jlink JRE 和 H2 本地数据库，安装后无需 Java/MySQL/Node，所有面试数据与 API Key 保存在本机 `%USERPROFILE%\.offerpilot`。

桌面版还提供独立的**简历评估**页面：选择或上传简历、可选目标岗位/JD，输出学历、相关技能、实习/工作经历、排版布局四维评分，并列出具体缺陷与修改建议。接口为 `POST /api/resumes/{id}/evaluation`。

桌面版运行与打包见 `electron/README.md`。

下一步：P1（向量检索、真实 AI 深度评测、支付与增长）。

## 目录

- `backend/`：Spring Boot 3.5 + MyBatis-Plus + Flyway
- `frontend/`：Vue3 + Vite + TypeScript + Element Plus
- `electron/`：Electron 桌面壳 + NSIS 安装包构建
- `docs/`：实施计划
- `OfferPilot-智能AI面试问答项目总体方案-V1.0.docx`：产品与技术方案

## 本地运行（当前已验证）

前置条件：本机 MySQL 已启动，存在 `offerpilot` 数据库，账号默认 `root/1234`。

`mysql` profile 必须提供 `JWT_SECRET`（base64 编码的 64 字节）。本地开发可先生成：

```bash
export JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
```

后端：

```bash
cd backend
SPRING_PROFILES_ACTIVE=mysql MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 \
MYSQL_DATABASE=offerpilot MYSQL_USER=root MYSQL_PASSWORD=1234 \
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm run dev
```

访问 http://localhost:5173。

## Docker 一键启动（需 Docker Desktop/WSL2）

Windows PowerShell：
```powershell
.\scripts\docker-up.ps1
```

Git Bash / macOS / Linux：
```bash
bash scripts/docker-up.sh
```

访问 http://localhost:8088。首次启动会自动生成 `.env`（含随机 JWT_SECRET）并构建镜像。

前置条件：本机 MySQL 已启动，存在 `offerpilot` 数据库，`.env` 中默认使用 `root/1234`。如本机账号密码不同，请修改 `.env` 的 `MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE`。容器内通过 `host.docker.internal:3306` 访问宿主 MySQL。

> 本次 W6 验收因本机 Docker Desktop/WSL2 未就绪，使用“本地运行”路径完成；Docker 容器启动验证待环境恢复后补做。

常用命令：

```bash
docker compose logs -f backend   # 后端日志
docker compose ps                # 服务状态
bash scripts/docker-down.sh      # 停止
bash scripts/docker-down.sh -v   # 停止并清空上传文件
```

可选基础设施（当前业务未使用）：

```bash
docker compose --profile infra up -d
```

## 快速开始

### 后端（H2，默认，不需要 Docker）

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:offerpilot`
  - User: `sa`，Password 留空

### 后端（MySQL）

先启动 Docker Desktop，然后：

```bash
docker compose up -d mysql
export JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
cd backend
SPRING_PROFILES_ACTIVE=mysql mvn spring-boot:run
```

PowerShell:
```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
$env:SPRING_PROFILES_ACTIVE="mysql"; mvn spring-boot:run
```

> `local` / `test` profile 内置开发用默认密钥；`mysql` profile 不提供默认值，未设置 `JWT_SECRET` 会启动失败。

### 前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173。

### 测试

```bash
cd backend
mvn test
```

## 压测

```bash
python scripts/load_test.py --users 20 --base-url http://localhost:8080
```

若使用 Docker 栈，将 `--base-url` 改为 `http://localhost:8088`。

报告输出到 `docs/reports/load-test-20u.json` 与 `docs/reports/load-test-20u.md`。默认 Mock AI，不产生 DeepSeek 费用。

## 演示物料

- `docs/demo/3-minute-demo.md`：3 分钟演示脚本
- `docs/demo/product-intro.html`：产品介绍页（浏览器直接打开）
- `docs/demo/resume-project.md`：简历项目描述（含实测数据）

## 当前 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册并返回令牌 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新令牌（轮换） |
| POST | `/api/auth/logout` | 注销 refresh token |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/job-templates` | 岗位模板列表 |
| POST | `/api/resumes` | 上传 PDF/DOCX 简历并解析 |
| GET | `/api/resumes` | 当前用户简历列表 |
| GET | `/api/resumes/{id}` | 简历详情 |
| DELETE | `/api/resumes/{id}` | 删除简历 |
| POST | `/api/interviews` | 创建面试并生成蓝图 |
| GET | `/api/interviews/{id}` | 面试详情与题目 |
| GET | `/api/interviews` | 面试历史 |
| POST | `/api/interviews/{id}/start` | 开始面试 |
| GET | `/api/interviews/{id}/messages` | 面试消息历史 |
| POST | `/api/interviews/{id}/answers` | 提交回答（SSE 流式返回） |
| POST | `/api/interviews/{id}/hint` | 请求提示（每题 1 次） |
| POST | `/api/interviews/{id}/skip` | 跳过当前题 |
| POST | `/api/interviews/{id}/finish` | 结束面试 |
| GET | `/api/interviews/{id}/evaluations` | 练习模式逐题点评 |
| POST | `/api/interviews/{id}/report` | 生成/获取五维报告 |
| GET | `/api/interviews/{id}/report` | 查询五维报告 |
| GET | `/api/interviews/history` | 面试历史 |
| GET | `/api/stats/overview` | 成长统计 |
| GET | `/api/stats/weaknesses` | 薄弱点与推荐题目 |
| GET | `/api/quota/me` | 当前用户额度（默认不限） |
| GET | `/api/ai/providers` | 当前用户的 AI Provider 列表 |
| GET | `/api/ai/providers/presets` | 主流大模型预设、分组与可选模型列表 |
| GET | `/api/ai/providers/status` | 真实 AI 开关与默认模型 |
| POST | `/api/ai/providers` | 新增 AI Provider |
| PUT | `/api/ai/providers/{id}` | 编辑 AI Provider |
| DELETE | `/api/ai/providers/{id}` | 删除 AI Provider |
| POST | `/api/ai/providers/{id}/activate` | 设为当前 Provider |
| POST | `/api/ai/providers/{id}/test` | 测试连接 |
| GET | `/api/admin/questions` | 管理员题库分页，支持 `trackId` 筛选 |
| GET | `/api/admin/questions/options` | 分类、标签、来源下拉选项 |
| POST | `/api/admin/questions` | 管理员新增题目，可带 `trackIds` |
| POST | `/api/admin/questions/import/extract` | 上传 PDF/DOCX/TXT/Markdown 提取题目（AI + 规则兜底） |
| POST | `/api/admin/questions/import` | 批量导入勾选后的题目 |
| PUT | `/api/admin/questions/{id}` | 管理员编辑题目，可带 `trackIds` |
| PATCH | `/api/admin/questions/{id}/status` | 管理员启用/禁用题目 |
| DELETE | `/api/admin/questions/{id}` | 管理员删除题目 |
| GET | `/api/admin/tracks` | 岗位题库列表与题目数 |
| POST | `/api/admin/tracks/{id}/questions` | 批量绑定题目到岗位题库 |
| DELETE | `/api/admin/tracks/{id}/questions/{questionId}` | 从岗位题库移除题目 |

## AI 配置（可选）

默认 `app.ai.enabled=false`，使用确定性的 Mock 蓝图生成器，不需要 DeepSeek Key。

启用真实 AI：

```bash
export DEEPSEEK_API_KEY=sk-...
export APP_AI_ENABLED=true
cd backend && mvn spring-boot:run
```

PowerShell:
```powershell
$env:DEEPSEEK_API_KEY="sk-..."
$env:APP_AI_ENABLED="true"
mvn spring-boot:run
```

AI 调用失败会自动回退 Mock 生成器，不会阻塞面试创建。上传的简历默认保存在 `backend/data/uploads`，可通过 `OFFERPILOT_UPLOAD_DIR` 修改。

简历解析：`APP_AI_ENABLED=true` 时使用当前用户激活的 Provider 提取姓名、联系方式、技能、项目、教育等字段；AI 返回字段缺失或格式不规范时会自动归一化，并用规则解析补齐；AI 整体失败时回退到规则解析。上传同步完成，通常需要数秒。

面试蓝图：默认读取超时 25 秒（`AI_BLUEPRINT_TIMEOUT`，ISO-8601 时长），超时或 AI 返回不合法时自动回退 Mock 蓝图；前端创建面试请求超时 60 秒。生成通常需要 5–15 秒，题目越多耗时越长。

## 用户 API 设置（BYOK）

每个用户可以在首页「API 设置」中保存自己的 Provider，面试、评估和报告会使用当前激活的配置：

| 分组 | 预设 | Base URL | 默认模型 |
|---|---|---|---|
| 国内官方 | DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| 国内官方 | 小米 MiMo | `https://api.xiaomimimo.com/v1` | `mimo-v2.5` |
| 国内官方 | 小米 MiMo Token Plan | `https://token-plan-cn.xiaomimimo.com/v1` | `mimo-v2.5` |
| 国内官方 | Kimi（月之暗面） | `https://api.moonshot.cn/v1` | `kimi-k3` |
| 国内官方 | 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-5.3` |
| 国内官方 | 通义千问（阿里云百炼） | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen3.8-max` |
| 国内官方 | MiniMax | `https://api.minimax.cn/v1` | `MiniMax-M3.1-Flash` |
| 国内官方 | 阶跃星辰 StepFun | `https://api.stepfun.com/v1` | `step-3.7-flash` |
| 国内官方 | 火山方舟（豆包） | `https://ark.cn-beijing.volces.com/api/v3` | `doubao-seed-2-1-pro-260915` |
| 国内官方 | 百度千帆 | `https://qianfan.baidubce.com/v2` | `deepseek-v4-flash` |
| 国内官方 | 腾讯混元 | `https://tokenhub.tencentmaas.com/v1` | `hy3` |
| 国内官方 | 腾讯 Token Plan | `https://api.lkeap.cloud.tencent.com/plan/v3` | `tc-code-latest` |
| 国内官方 | LongCat（美团） | `https://api.longcat.chat/openai/v1` | `LongCat-2.0` |
| 国内聚合 | 硅基流动 SiliconFlow | `https://api.siliconflow.cn/v1` | `deepseek-ai/DeepSeek-V4-Flash` |
| 国内聚合 | 魔搭 ModelScope | `https://api-inference.modelscope.cn/v1` | `ZhipuAI/GLM-5.2` |
| 海外 / 聚合 | OpenAI | `https://api.openai.com/v1` | `gpt-5.4` |
| 海外 / 聚合 | Anthropic Claude | `https://api.anthropic.com/v1` | `claude-sonnet-5-5` |
| 海外 / 聚合 | Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.8-flash` |
| 海外 / 聚合 | xAI Grok | `https://api.x.ai/v1` | `grok-4.7` |
| 海外 / 聚合 | OpenRouter | `https://openrouter.ai/api/v1` | `openai/gpt-6.1-sol` |
| 海外 / 聚合 | NVIDIA NIM | `https://integrate.api.nvidia.com/v1` | `moonshotai/kimi-k2.6` |
| 海外 / 聚合 | Groq | `https://api.groq.com/openai/v1` | `openai/gpt-oss-120b` |
| 海外 / 聚合 | Mistral AI | `https://api.mistral.ai/v1` | `mistral-medium-3-5` |
| 自定义 | 自定义（OpenAI 兼容） | 用户填写 | 用户填写 |

- 模型下拉框包含各厂商常用模型，也支持直接输入新的模型 ID；厂商模型更新时无需改代码。
- 使用 Coding Plan / Token Plan 专属端点时，以厂商控制台给出的 Base URL 为准，可在预设基础上修改。
- Anthropic Claude 使用官方 OpenAI SDK 兼容层（`/v1/chat/completions`），Gemini 使用官方 OpenAI 兼容端点；两者均只保证基础对话能力。
- API Key 使用 AES-GCM 加密存储，接口只返回 `sk-****abcd` 掩码，不会明文回显。
- 每个用户只能看到和操作自己的配置。
- 未配置时使用系统默认配置；`APP_AI_ENABLED=false` 时全部使用 Mock，设置页会提示。
- 生产环境建议单独设置 `AI_SETTINGS_SECRET`；未设置时从 `JWT_SECRET` 派生。更换该密钥会导致已保存的 API Key 无法解密。
- 生产环境 Base URL 必须是 https 且不能指向内网；本地/测试 profile 默认允许 http 和私网地址。需要连接本地 LLM 时设置 `AI_ALLOW_PRIVATE_BASE_URL=true`。

## 题库与薄弱点

- `question_bank` 表内置 400 道面试题（含参考答案、分类、标签），其中 Java 基础、集合、并发、JVM 共 117 道，前端题库 67 道；2026-10-28 新增 260 道题，包括 100 道 Java 后端/全栈/AI 题、100 道黑马/传智 Java 面试题整理和 60 道前端题。
- 新增题目参考 2024 传智播客面试知识点（飞书知识库）及公开的黑马 Java 面试题目录，答案按面试回答场景重写，未逐字复制原文。
- 已引入岗位赛道 `interview_track` 和题目绑定 `track_question`：当前主页展示 Java 后端、Java 全栈、Java + AI、前端四个岗位；旧 Java 后端 1-3 年和 Spring Cloud 模板保留为 INACTIVE，历史面试仍可查看。
- 工作年限不再写进岗位名称，而是在创建面试弹窗选择：校招 / 应届、1-3 年、3-5 年；年限只通过题目难度区分，校招以简单题为主，3-5 年以困难题为主。
- 创建面试时按 `job_template.track_id` 只检索该岗位题库，避免跨岗位串题。
- 后台题库管理支持按岗位题库切换、新增题目和启用/禁用题目；禁用后该题不再参与面试检索，岗位卡片的题目数也会同步减少。
- 新增题目时，分类、标签、来源都是可搜索下拉框；已有值可直接选择，也可以输入新值并保存为后续可选选项。
- 支持从 PDF、DOCX、TXT、Markdown 文档导入题目：系统优先用当前用户激活的 AI Provider 提取题目和答案，失败时回退到编号题/问答规则解析；识别结果会先展示在预览表中，可勾选、全选、逐题修改或移除，确认后再批量写入题库。扫描版 PDF 需要先 OCR。
- 成长页根据历史回答的缺失要点和题目标签聚合薄弱点，推荐题库题目；推荐题目可展开查看参考答案。
- 当前为关键词检索（RAG-lite），pgvector/bge-m3 向量检索留到部署阶段。

## 管理员账号

- 邮箱：`admin@offerpilot.local`
- 密码：`admin123`
- 仅本地开发使用，部署前请修改密码或移除种子账号。

管理员登录后首页会出现“题库管理”入口，可按岗位题库筛选题目，并进行分页查询、新增和启用/禁用；普通用户访问管理员接口返回 403。

## 额度（默认关闭）

- 默认所有用户不限面试场次，`/api/quota/me` 返回 `plan=UNLIMITED`、`remainingSessions=-1`。
- 设置 `APP_QUOTA_ENFORCE=true` 可恢复普通用户每日 3 场限制（模拟面试与练习模式共用额度），第 4 场返回 HTTP 429（`code=42902`）；管理员始终不限。
- 额度按自然日重置；创建面试弹窗默认不再显示额度提示。

## 报告与成长

面试结束后可生成五维评分报告：

- 技术准确性、技术深度、项目表现、表达逻辑、岗位匹配度
- 逐题复盘：回答、正确性、得分、点评、缺失要点
- 成长记录：总场次、完成场次、平均分、最高分、分数趋势、五维平均

前端报告页：`/interviews/{id}/report`；成长页：`/history`。

## 练习模式

创建面试时选择“练习模式（实时点评）”，每回答一题会立即返回：

- 正确性：正确 / 部分正确 / 需补充 / 不正确
- 1–5 分
- 一句点评
- 缺失要点

默认使用基于参考要点的 Mock 评估；`APP_AI_ENABLED=true` 时使用 Spring AI 评估，失败自动回退 Mock。普通“模拟面试”模式不显示即时点评。

## 开发约定

- Java 21，包名 `com.offerpilot`。
- 统一响应：`{ code, message, data, traceId }`，`code = 0` 表示成功。
- 时间字段使用 `LocalDateTime`，数据库用 `DATETIME(3)`。
- 所有表使用逻辑删除 `deleted`（0/1）。
- 每个任务完成后提交一次 git commit。
