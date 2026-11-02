<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Converter } from 'opencc-js/t2cn'
import { startVoiceRecording, type VoiceRecorderHandle } from '@/utils/voiceRecording'
import {
  DEFAULT_MICROPHONE_ID,
  loadVoiceSettings,
  type VoiceLanguage
} from '@/utils/voiceSettings'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  transcribed: [text: string]
  busy: [value: boolean]
}>()

const MAX_SECONDS = 600
const toSimplified = Converter({ from: 'tw', to: 'cn' })

// 只修正语音识别中高置信度的技术术语同音字，避免影响正常表达。
const termFixes: Array<[RegExp, string]> = [
  [/分[布佈](?:诗|施|思|詩|施|思)所/g, '分布式锁'],
  [/消息对列/g, '消息队列'],
  [/一(?:部|步)任务/g, '异步任务'],
  [/面试(?:观|关)/g, '面试官'],
  [/(?:Luer|Luaer|鲁尔)/gi, 'Lua'],
  [/异部/g, '异步']
]

const recording = ref(false)
const transcribing = ref(false)
const seconds = ref(0)
const statusText = ref('')
let recorder: VoiceRecorderHandle | null = null
let timer: number | null = null
let recordingLanguage: VoiceLanguage = 'zh'

const busy = computed(() => recording.value || transcribing.value)
const label = computed(() => {
  if (transcribing.value) return '识别中'
  if (recording.value) return `停止 ${formatSeconds(seconds.value)}`
  return '语音'
})
const buttonTitle = computed(() => {
  if (transcribing.value) return '正在本地识别语音'
  if (recording.value) return '点击停止并识别'
  return '语音输入（本地识别，不上传录音）'
})

function formatSeconds(value: number) {
  const minutes = Math.floor(value / 60)
  const remainder = value % 60
  return `${minutes}:${String(remainder).padStart(2, '0')}`
}

function clearTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function setBusy(value: boolean) {
  emit('busy', value)
}

function normalizeTranscript(raw: string) {
  let text = raw.trim()
  try {
    text = toSimplified(text)
  } catch {
    // 转换失败时保留原始识别结果
  }
  for (const [pattern, replacement] of termFixes) {
    text = text.replace(pattern, replacement)
  }
  return text.trim()
}

async function start() {
  if (props.disabled || busy.value) return
  const desktop = window.offerpilotDesktop
  if (!desktop?.transcribeAudio) {
    ElMessage.error('语音输入仅支持桌面版应用')
    return
  }

  try {
    const speechStatus = await desktop.speechStatus()
    if (!speechStatus.ready) {
      ElMessage.error(speechStatus.reason || '语音识别组件未就绪')
      return
    }
    const settings = loadVoiceSettings()
    recordingLanguage = settings.language
    recorder = await startVoiceRecording(
      settings.deviceId === DEFAULT_MICROPHONE_ID ? undefined : settings.deviceId
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法启动录音')
    return
  }

  recording.value = true
  seconds.value = 0
  statusText.value = '正在录音，点击按钮停止'
  setBusy(true)
  timer = window.setInterval(() => {
    seconds.value += 1
    if (seconds.value >= MAX_SECONDS) {
      void stop()
    }
  }, 1000)
}

async function stop() {
  if (!recording.value || !recorder) return
  clearTimer()
  recording.value = false
  transcribing.value = true
  statusText.value = '正在识别…'
  const current = recorder
  recorder = null

  try {
    const wav = await current.stop()
    const result = await window.offerpilotDesktop?.transcribeAudio(wav, {
      language: recordingLanguage
    })
    const text = normalizeTranscript(result?.text ?? '')
    if (!text) {
      throw new Error('没有识别到语音，请重试')
    }
    emit('transcribed', text)
    statusText.value = ''
  } catch (error) {
    if (error instanceof Error && error.message === '录音已取消') {
      statusText.value = ''
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '语音识别失败')
    statusText.value = ''
  } finally {
    transcribing.value = false
    setBusy(false)
  }
}

function toggle() {
  if (recording.value) {
    void stop()
  } else {
    void start()
  }
}

onBeforeUnmount(() => {
  clearTimer()
  recorder?.cancel()
  recorder = null
})
</script>

<template>
  <div class="voice-input">
    <el-button
      class="voice-button"
      :class="{ recording }"
      :type="recording ? 'danger' : 'default'"
      :plain="recording"
      :disabled="disabled || transcribing"
      :loading="transcribing"
      :title="buttonTitle"
      @click="toggle"
    >
      <span v-if="recording" class="recording-dot" aria-hidden="true"></span>
      <svg
        v-else
        class="mic-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <rect x="9" y="3" width="6" height="11" rx="3"></rect>
        <path d="M5 11a7 7 0 0 0 14 0"></path>
        <path d="M12 18v3"></path>
        <path d="M8 21h8"></path>
      </svg>
      <span>{{ label }}</span>
    </el-button>
    <span v-if="statusText" class="voice-status">{{ statusText }}</span>
  </div>
</template>

<style scoped>
.voice-input {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.voice-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.mic-icon {
  width: 15px;
  height: 15px;
}

.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  animation: voice-pulse 1.1s ease-in-out infinite;
}

.voice-status {
  color: #8a97a8;
  font-size: 12px;
  white-space: nowrap;
}

@keyframes voice-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.45;
    transform: scale(0.78);
  }
}
</style>
