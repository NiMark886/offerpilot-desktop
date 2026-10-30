# 简历项目描述：OfferPilot AI 模拟面试教练

## 项目简介

面向 Java 后端 / Java+AI 求职者的垂直 AI 模拟面试平台，覆盖“选岗位 → 传简历 → 生成面试蓝图 → 多轮追问 → 五维评分报告 → 薄弱点复练”的完整闭环。

## 技术栈

Java 21、Spring Boot 3.5、Spring AI、MyBatis-Plus、Flyway、MySQL、Vue 3、TypeScript、Element Plus、Docker Compose、Nginx。

## 个人职责与亮点（STAR）

- **Situation：** 求职者缺少低成本、可重复的真实技术面试练习，通用 AI 对话无法稳定覆盖岗位匹配、追问与结构化评估。
- **Task：** 独立完成从需求、后端、前端到部署的 MVP，支撑一场 6–10 题的完整模拟面试与五维报告。
- **Action：**
  - 设计岗位 JD + 简历画像 + 题库标签的蓝图生成流程，支持基础、项目、场景题与动态追问。
  - 实现 SSE 流式面试对话、提示/跳过/断点恢复与练习模式逐题评估。
  - 设计五维评分（准确性、深度、项目表现、表达逻辑、岗位匹配）与薄弱点推荐。
  - 实现管理员题库 CRUD 与可配置的每日额度控制（默认关闭）。
  - 使用 Docker Compose + Nginx 完成一键部署，Nginx 同时托管 SPA 与反代 API。
- **Result：**
  - 后端 48 个自动化测试全部通过，前端构建通过。
  - 20 并发完整链路压测成功率 100%，总耗时 0.7s，吞吐 29.16 用户/秒。
  - 创建面试 P95 0.08s，SSE 答题 P95 0.24s（Mock AI 环境）。
  - 支持本地一条命令启动完整栈，演示流程 3 分钟内可复现。

## 可直接粘贴到简历的版本

- 独立开发 AI 模拟面试平台，打通岗位/简历解析、面试蓝图、SSE 多轮追问、五维报告与薄弱点推荐闭环。
- 基于 Spring Boot 3.5 + Spring AI + MyBatis-Plus + MySQL 构建后端，Vue3 + TypeScript 构建前端。
- 实现管理员题库后台与可配置额度；Docker Compose + Nginx 一键部署，20 并发完整链路压测成功率 100%。
