# 面试回答语音输入实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在面试回答输入框增加本地 Whisper 语音输入，录音结束后把简体中文文本追加到输入框，不上传音频、不自动发送。

**Architecture:** 渲染进程用 `MediaRecorder` 录音并转成 16kHz 单声道 WAV；Electron 主进程调用随安装包分发的 `whisper-cli.exe` 和 `ggml-small-q5_1.bin` 做离线识别；识别结果经 OpenCC 繁转简后追加到输入框。

**Tech Stack:** Vue 3、Element Plus、Electron 45、MediaRecorder、Web Audio API、whisper.cpp（small q5_1）、OpenCC。

---

### Task 1: 准备 Whisper 运行时和模型

**Files:**
- Create: `electron/scripts/prepare-whisper.ps1`
- Create: `electron/resources/whisper/`（构建产物，不提交）
- Modify: `.gitignore`
- Modify: `electron/package.json`

- [x] 下载 whisper.cpp Windows x64 运行时
- [x] 下载 `ggml-small-q5_1.bin`，校验 SHA-256
- [x] 只复制 `whisper-cli.exe`、`whisper.dll`、`ggml.dll`、`ggml-base.dll`、`ggml-cpu-*.dll`
- [x] 在 `extraResources` 中加入 `resources/whisper` -> `whisper`

### Task 2: 实现 Electron 语音服务

**Files:**
- Create: `electron/speech.js`
- Modify: `electron/main.js`
- Modify: `electron/preload.js`

- [x] `speech:transcribe` IPC 接收 WAV `ArrayBuffer`
- [x] 校验大小、写临时文件、spawn `whisper-cli.exe`
- [x] 参数：`-m model -f wav -l zh -nt -np -t 8 --prompt ...`
- [x] 超时、空结果、进程失败处理
- [x] 麦克风权限：允许应用窗口的 `media`/`audioCapture`
- [x] preload 暴露 `transcribeAudio`

### Task 3: 前端录音与 WAV 编码

**Files:**
- Create: `frontend/src/utils/voiceRecording.ts`
- Modify: `frontend/src/env.d.ts`

- [x] `getUserMedia` + `MediaRecorder` 录音
- [x] `AudioContext.decodeAudioData` 解码
- [x] `OfflineAudioContext` 重采样到 16kHz 单声道
- [x] 编码 16-bit PCM WAV
- [x] 最长 3 分钟、停止/取消、错误处理

### Task 4: 语音按钮和面试页集成

**Files:**
- Create: `frontend/src/components/VoiceInputButton.vue`
- Modify: `frontend/src/views/InterviewChatView.vue`
- Modify: `frontend/package.json`

- [x] 麦克风按钮、录音计时、识别中状态
- [x] 识别结果追加到输入框，不自动发送
- [x] 录音/识别期间禁用发送、跳过、结束面试
- [x] 使用 opencc-js 繁转简
- [x] 失败时保留已有输入

### Task 5: 测试与打包

- [x] 用真实中文 WAV 测试 `speech.js` 核心识别
- [x] `npm run build` 通过
- [x] Electron 启动、麦克风权限和 IPC 冒烟
- [x] 重新生成 NSIS 安装包并安装验证

### 验收标准

- 点击麦克风按钮可以开始/停止录音
- 停止后几秒内识别出简体中文
- 文本追加到输入框，用户可以修改后手动发送
- 音频不离开本机
- 安装包包含 Whisper 运行时和模型，首次使用不需要下载
