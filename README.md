# OfferPilot Desktop

本地优先的 AI 模拟面试教练桌面应用。

基于 Electron + Spring Boot + H2，安装后不需要单独安装 Java、MySQL、Node.js，也不需要连接 OfferPilot 服务器。面试记录、简历、成长数据和 API Key 都保存在本机。

> 本仓库维护的是桌面版（Electron）。所有面试、评估和报告内容均为 AI 输出，不同模型可能产生不同结果，仅供参考。

## 下载安装

前往 [Releases](https://github.com/NiMark886/offerpilot-desktop/releases/latest) 下载：

- `OfferPilot-Electron-0.1.0-x64-setup.exe`
- 适用系统：Windows 10 / 11 x64
- 安装包约 215 MB，安装后约 484 MB
- 安装完成后直接运行，应用会自动以本地管理员身份登录

首次使用请打开右上角 **API 设置**，配置自己的大模型 Provider（BYOK）。不配置时，面试蓝图和评估会使用确定性的 Mock/规则模式。

## 功能

### 岗位面试

- 内置 Java 后端、Java 全栈、Java + AI、前端岗位题库
- 销售岗面试：粘贴销售 JD，结合简历和销售能力模型生成面试
- 支持校招 / 1-3 年 / 3-5 年难度，题目按岗位题库检索
- 多轮追问、提示、跳过、练习模式实时点评
- 面试结束后生成五维报告：技术准确性、技术深度、项目表现、表达逻辑、岗位匹配度

### 简历评估

独立简历评估页面，支持选择或上传 PDF / DOCX，可选目标岗位或直接粘贴 JD。

评分维度：

| 维度 | 权重 |
|------|------|
| 学历 | 25% |
| 相关技能 | 25% |
| 实习 / 工作经历 | 40% |
| 排版布局 | 10% |

- 学历、技能、经历由 AI 结合目标岗位评估
- 排版布局由后端做结构检测，不判断视觉审美
- 输出具体缺陷列表：类别、优先级、问题描述、简历证据、修改建议
- 不把“可实习时间、每周到岗天数、是否可转正”等招聘流程信息当作简历缺陷

### 成长记录

- 按岗位独立记录，每个岗位单独查看
- 分数趋势、五维能力变化、薄弱点和推荐练习题
- 销售岗面试统一归入一张成长卡片

### 本地优先

- Spring Boot 后端只监听 `127.0.0.1:18080`
- 后端、JRE、前端静态资源全部随安装包分发
- H2 文件数据库，无需安装数据库服务
- 简历文件保存在本机
- API Key 使用 AES-GCM 加密后保存在本机数据库，接口只返回掩码
- 除用户主动配置的大模型 Provider 外，不会向 OfferPilot 服务器发送数据

## 技术栈

- 桌面壳：Electron 45
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、ECharts
- 后端：Java 21、Spring Boot 3.5、Spring Security、Spring AI
- 数据：MyBatis-Plus、Flyway、H2
- 文档解析：PDFBox、Apache POI
- 打包：electron-builder、NSIS、jlink JRE

## 架构

```text
Electron main process
├── BrowserWindow + preload
├── Spring Boot sidecar（内置 jlink JRE）
│   ├── H2 文件数据库
│   ├── 本地简历存储
│   └── Spring AI / 用户配置的模型 Provider
└── 本地数据目录：%USERPROFILE%\.offerpilot
```

本地数据目录：

```text
%USERPROFILE%\.offerpilot\
├── data\offerpilot.mv.db
├── uploads\
├── logs\
└── secrets\
```

## 从源码构建

### 环境要求

仅构建时需要：

- JDK 21
- Maven 3.9+
- Node.js 20+
- Windows 10 / 11

### 构建安装包

```powershell
git clone https://github.com/NiMark886/offerpilot-desktop.git
cd offerpilot-desktop\electron
powershell -ExecutionPolicy Bypass -File .\build-installer.ps1
```

脚本会依次完成：

1. 构建前端（`OFFERPILOT_DESKTOP=true`，使用相对资源路径）
2. 构建后端 jar
3. 生成或复用 jlink Java runtime
4. 复制前端静态资源、后端 jar 和 runtime
5. 使用 electron-builder 生成 NSIS 安装包

产物：

```text
OfferPilot-Electron-0.1.0-x64-setup.exe
```

### 开发调试

先执行一次安装包构建，生成 `electron/resources/backend.jar`、`electron/resources/runtime` 和前端 `dist`，然后：

```powershell
cd electron
npm install
npm start
```

后端也可以单独启动（开发环境需要本机 Java）：

```powershell
cd backend
mvn spring-boot:run
```

前端开发服务器：

```powershell
cd frontend
npm install
npm run dev
```

## 测试

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

后端测试覆盖简历解析、简历评估、AI Provider 加密、面试流程、报告生成、成长统计、销售面试和桌面 profile。

## 目录结构

```text
.
├── backend\      Spring Boot 后端、数据库迁移、AI Provider、简历评估
├── frontend\     Vue 3 前端页面
├── electron\     Electron 桌面壳、jlink runtime 与 NSIS 打包脚本
├── docs\         实施与设计文档
└── scripts\      辅助脚本
```

## 安全说明

- 桌面版默认只监听回环地址，不接受局域网或公网连接
- 桌面模式自动使用本地管理员身份，数据不会上传到云端
- 更换 `%USERPROFILE%\.offerpilot\secrets\settings.key` 会导致已保存的 API Key 无法解密
- 生产分发建议增加代码签名与自动更新机制

## 已知限制

- 当前安装包未做代码签名，Windows 可能提示“未知发布者”
- 本地端口固定为 `18080`
- 向量检索、支付和多端同步尚未接入
