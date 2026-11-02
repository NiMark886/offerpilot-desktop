<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  aiProviderApi,
  type AiProvider,
  type AiProviderPreset,
  type AiProviderRequest,
  type AiProviderStatus
} from '@/api/provider'
import { useAuthStore } from '@/stores/auth'
import { createRingtoneUrl } from '@/utils/ringtone'
import { openMicrophoneStream, recordingErrorMessage } from '@/utils/voiceRecording'
import {
  DEFAULT_MICROPHONE_ID,
  DEFAULT_SPEAKER_ID,
  loadVoiceSettings,
  saveVoiceSettings,
  type VoiceSettings
} from '@/utils/voiceSettings'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const providers = ref<AiProvider[]>([])
const presets = ref<AiProviderPreset[]>([])
const status = ref<AiProviderStatus | null>(null)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const originalProvider = ref<AiProvider | null>(null)
const submitting = ref(false)
const testingId = ref<number | null>(null)

interface AudioDeviceOption {
  deviceId: string
  label: string
}

type SinkCapableMediaElement = HTMLMediaElement & {
  setSinkId?: (sinkId: string) => Promise<void>
}

const voice = reactive<VoiceSettings>(loadVoiceSettings())
const microphones = ref<AudioDeviceOption[]>([])
const speakers = ref<AudioDeviceOption[]>([])
const detecting = ref(false)
const voiceError = ref('')
const micTesting = ref(false)
const micDb = ref(-60)
const speakerTesting = ref(false)
let deviceChangeHandler: (() => void) | null = null
let micStream: MediaStream | null = null
let micContext: AudioContext | null = null
let micAnalyser: AnalyserNode | null = null
let micMonitor: HTMLAudioElement | null = null
let micFrame: number | null = null
let speakerAudio: HTMLAudioElement | null = null
let speakerUrl: string | null = null

const savedDeviceMissing = computed(() =>
  voice.deviceId !== DEFAULT_MICROPHONE_ID
  && !microphones.value.some((device) => device.deviceId === voice.deviceId)
)

const savedSpeakerMissing = computed(() =>
  voice.speakerId !== DEFAULT_SPEAKER_ID
  && !speakers.value.some((device) => device.deviceId === voice.speakerId)
)

const micLevelPercent = computed(() =>
  Math.max(0, Math.min(100, Math.round(((micDb.value + 60) / 60) * 100)))
)

const micDbLabel = computed(() => (micTesting.value ? `${Math.round(micDb.value)} dB` : '-- dB'))

const form = reactive<AiProviderRequest>({
  name: '',
  providerType: 'DEEPSEEK',
  baseUrl: '',
  model: '',
  apiKey: '',
  temperature: 0.4,
  enabled: true
})

const activeProvider = computed(() => providers.value.find((provider) => provider.active) ?? null)
const isEditing = computed(() => editingId.value !== null)
const endpointChanged = computed(() => Boolean(
  isEditing.value
  && originalProvider.value
  && (
    form.baseUrl !== originalProvider.value.baseUrl
    || form.providerType !== originalProvider.value.providerType
  )
))

const presetGroups = computed(() => {
  const groups = new Map<string, AiProviderPreset[]>()
  for (const preset of presets.value) {
    const label = preset.group || '其他'
    const options = groups.get(label) ?? []
    options.push(preset)
    groups.set(label, options)
  }
  return Array.from(groups, ([label, options]) => ({ label, options }))
})

const modelOptions = computed(() => {
  const preset = presets.value.find((item) => item.type === form.providerType)
  const options = [...(preset?.models ?? [])]
  if (form.model && !options.includes(form.model)) {
    options.unshift(form.model)
  }
  return options
})

onMounted(async () => {
  await loadDevices()
  const needsLabels = microphones.value.length === 0
    || microphones.value.some((device) => device.label.startsWith('麦克风 '))
    || speakers.value.length === 0
    || speakers.value.some((device) => device.label.startsWith('扬声器 '))
  if (needsLabels) {
    await detectDevices({ silent: true })
  }
  if (navigator.mediaDevices?.addEventListener) {
    deviceChangeHandler = () => {
      void loadDevices()
    }
    navigator.mediaDevices.addEventListener('devicechange', deviceChangeHandler)
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return
    }
  }
  await load()
})

onBeforeUnmount(() => {
  if (deviceChangeHandler) {
    navigator.mediaDevices?.removeEventListener('devicechange', deviceChangeHandler)
  }
  stopMicTest()
  stopSpeakerTest()
})

watch(
  voice,
  () => saveVoiceSettings({
    deviceId: voice.deviceId,
    speakerId: voice.speakerId,
    language: voice.language
  }),
  { deep: true }
)

watch(
  () => voice.deviceId,
  () => {
    if (micTesting.value) stopMicTest()
  }
)

watch(
  () => voice.speakerId,
  () => {
    if (micTesting.value) stopMicTest()
    if (speakerTesting.value) stopSpeakerTest()
  }
)

async function loadDevices() {
  if (!navigator.mediaDevices?.enumerateDevices) {
    voiceError.value = '当前环境不支持音频设备检测'
    return
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices()
    const seenInputs = new Set<string>()
    const seenOutputs = new Set<string>()
    const inputs: AudioDeviceOption[] = []
    const outputs: AudioDeviceOption[] = []
    for (const device of devices) {
      // default/communications 是浏览器聚合出来的别名，保留“系统默认”即可
      if (device.deviceId === 'default' || device.deviceId === 'communications') continue
      if (device.kind === 'audioinput' && !seenInputs.has(device.deviceId)) {
        seenInputs.add(device.deviceId)
        inputs.push({
          deviceId: device.deviceId,
          label: device.label.trim() || `麦克风 ${inputs.length + 1}`
        })
      } else if (device.kind === 'audiooutput' && !seenOutputs.has(device.deviceId)) {
        seenOutputs.add(device.deviceId)
        outputs.push({
          deviceId: device.deviceId,
          label: device.label.trim() || `扬声器 ${outputs.length + 1}`
        })
      }
    }
    microphones.value = inputs
    speakers.value = outputs
    voiceError.value = ''
  } catch (error) {
    voiceError.value = recordingErrorMessage(error)
  }
}

async function detectDevices(options: { silent?: boolean } = {}) {
  if (!navigator.mediaDevices?.getUserMedia) {
    voiceError.value = '当前环境不支持麦克风'
    return
  }
  detecting.value = true
  voiceError.value = ''
  let stream: MediaStream | null = null
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach((track) => track.stop())
    await loadDevices()
    if (savedDeviceMissing.value) {
      voice.deviceId = DEFAULT_MICROPHONE_ID
    }
    if (savedSpeakerMissing.value) {
      voice.speakerId = DEFAULT_SPEAKER_ID
    }
    if (!options.silent) {
      ElMessage.success(
        `检测到 ${microphones.value.length} 个麦克风、${speakers.value.length} 个扬声器`
      )
    }
  } catch (error) {
    voiceError.value = recordingErrorMessage(error)
    if (!options.silent) {
      ElMessage.error(voiceError.value)
    }
  } finally {
    stream?.getTracks().forEach((track) => track.stop())
    detecting.value = false
  }
}

function audioTestErrorMessage(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotFoundError' || error.name === 'OverconstrainedError') {
      return '所选音频设备不可用，请重新选择'
    }
    if (error.name === 'NotAllowedError') {
      return '没有权限访问音频设备'
    }
    if (error.name === 'NotSupportedError') {
      return '当前版本不支持选择扬声器'
    }
  }
  return error instanceof Error ? error.message : '音频测试失败'
}

async function setAudioOutput(element: HTMLMediaElement, speakerId: string) {
  const target = element as SinkCapableMediaElement
  if (typeof target.setSinkId !== 'function') {
    if (speakerId !== DEFAULT_SPEAKER_ID) {
      throw new Error('当前版本不支持选择扬声器')
    }
    return
  }
  await target.setSinkId(speakerId === DEFAULT_SPEAKER_ID ? '' : speakerId)
}

async function toggleMicTest() {
  if (micTesting.value) {
    stopMicTest()
    return
  }
  // 避免扬声器测试的铃声和麦克风实时回放同时播放
  stopSpeakerTest()
  try {
    const deviceId = voice.deviceId === DEFAULT_MICROPHONE_ID ? undefined : voice.deviceId
    micStream = await openMicrophoneStream(deviceId)
    const activeLabel = micStream.getAudioTracks()[0]?.label ?? ''
    if (/virtual|todesk|stereo\s*mix|立体声混音|cable|voicemeeter/i.test(activeLabel)) {
      ElMessage.warning('当前麦克风可能是虚拟声卡，会采集系统声音；建议改选物理麦克风')
    }
    micMonitor = new Audio()
    micMonitor.srcObject = micStream
    await setAudioOutput(micMonitor, voice.speakerId)
    await micMonitor.play()

    micContext = new AudioContext()
    const source = micContext.createMediaStreamSource(micStream)
    micAnalyser = micContext.createAnalyser()
    micAnalyser.fftSize = 2048
    micAnalyser.smoothingTimeConstant = 0.75
    source.connect(micAnalyser)
    micTesting.value = true
    updateMicLevel()
  } catch (error) {
    stopMicTest()
    ElMessage.error(audioTestErrorMessage(error))
  }
}

function updateMicLevel() {
  if (!micTesting.value || !micAnalyser) return
  const data = new Float32Array(micAnalyser.fftSize)
  micAnalyser.getFloatTimeDomainData(data)
  let sum = 0
  for (let index = 0; index < data.length; index += 1) {
    sum += data[index] * data[index]
  }
  const rms = Math.sqrt(sum / data.length)
  const db = rms > 0 ? 20 * Math.log10(rms) : -60
  micDb.value = Math.max(-60, Math.min(0, db))
  micFrame = window.requestAnimationFrame(updateMicLevel)
}

function stopMicTest() {
  micTesting.value = false
  if (micFrame !== null) {
    window.cancelAnimationFrame(micFrame)
    micFrame = null
  }
  micAnalyser?.disconnect()
  micAnalyser = null
  micContext?.close().catch(() => {})
  micContext = null
  if (micMonitor) {
    micMonitor.pause()
    micMonitor.srcObject = null
    micMonitor = null
  }
  micStream?.getTracks().forEach((track) => track.stop())
  micStream = null
  micDb.value = -60
}

async function toggleSpeakerTest() {
  if (speakerTesting.value) {
    stopSpeakerTest()
    return
  }
  // 避免麦克风实时回放和铃声同时播放
  stopMicTest()
  try {
    speakerUrl = createRingtoneUrl()
    speakerAudio = new Audio(speakerUrl)
    speakerAudio.onended = () => stopSpeakerTest()
    speakerAudio.onerror = () => {
      stopSpeakerTest()
      ElMessage.error('铃声播放失败')
    }
    await setAudioOutput(speakerAudio, voice.speakerId)
    speakerTesting.value = true
    await speakerAudio.play()
  } catch (error) {
    stopSpeakerTest()
    ElMessage.error(audioTestErrorMessage(error))
  }
}

function stopSpeakerTest() {
  speakerTesting.value = false
  if (speakerAudio) {
    speakerAudio.onended = null
    speakerAudio.onerror = null
    speakerAudio.pause()
    speakerAudio.src = ''
    speakerAudio = null
  }
  if (speakerUrl) {
    URL.revokeObjectURL(speakerUrl)
    speakerUrl = null
  }
}

async function load() {
  loading.value = true
  try {
    const [providerList, presetList, statusData] = await Promise.all([
      aiProviderApi.list(),
      aiProviderApi.presets(),
      aiProviderApi.status()
    ])
    providers.value = providerList
    presets.value = presetList
    status.value = statusData
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  originalProvider.value = null
  const preset = presets.value.find((item) => item.type === 'DEEPSEEK')
  Object.assign(form, {
    name: preset?.label ?? '',
    providerType: preset?.type ?? 'DEEPSEEK',
    baseUrl: preset?.baseUrl ?? '',
    model: preset?.model ?? '',
    apiKey: '',
    temperature: 0.4,
    enabled: true
  })
  dialogVisible.value = true
}

function openEdit(provider: AiProvider) {
  editingId.value = provider.id
  originalProvider.value = provider
  Object.assign(form, {
    name: provider.name,
    providerType: provider.providerType,
    baseUrl: provider.baseUrl,
    model: provider.model,
    apiKey: '',
    temperature: provider.temperature,
    enabled: provider.enabled
  })
  dialogVisible.value = true
}

function handleTypeChange(type: string) {
  const preset = presets.value.find((item) => item.type === type)
  if (!preset) return
  if (type !== 'CUSTOM') {
    form.name = preset.label
    form.baseUrl = preset.baseUrl
    form.model = preset.model || preset.models?.[0] || ''
  } else {
    form.name = form.name || '自定义'
    form.baseUrl = form.baseUrl || ''
    form.model = form.model || ''
  }
}

function typeLabel(type: string) {
  return presets.value.find((item) => item.type === type)?.label ?? type
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写配置名称')
    return
  }
  if (!isEditing.value && !form.apiKey?.trim()) {
    ElMessage.warning('请填写 API Key')
    return
  }
  if (endpointChanged.value && !form.apiKey?.trim()) {
    ElMessage.warning('修改服务商或 Base URL 需要重新输入 API Key')
    return
  }
  if (form.providerType === 'CUSTOM' && (!form.baseUrl?.trim() || !form.model?.trim())) {
    ElMessage.warning('自定义配置需要填写 Base URL 和模型')
    return
  }

  submitting.value = true
  try {
    const payload: AiProviderRequest = {
      ...form,
      apiKey: form.apiKey?.trim() || undefined
    }
    if (editingId.value !== null) {
      await aiProviderApi.update(editingId.value, payload)
      ElMessage.success('配置已更新')
    } else {
      await aiProviderApi.create(payload)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function activate(provider: AiProvider) {
  try {
    await aiProviderApi.activate(provider.id)
    ElMessage.success(`已切换到 ${provider.name}`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function testConnection(provider: AiProvider) {
  testingId.value = provider.id
  try {
    const result = await aiProviderApi.test(provider.id)
    if (result.success) {
      ElMessage.success(`连接成功：${result.model} · ${result.latencyMs}ms`)
    } else {
      ElMessage.error(`连接失败：${result.message}`)
    }
  } catch {
    // 拦截器已提示
  } finally {
    testingId.value = null
  }
}

async function remove(provider: AiProvider) {
  try {
    await ElMessageBox.confirm(`确定删除「${provider.name}」吗？`, '删除配置', { type: 'warning' })
    await aiProviderApi.remove(provider.id)
    ElMessage.success('配置已删除')
    await load()
  } catch {
    // 用户取消或接口失败
  }
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="settings-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <BrandBlock suffix="设置" />
      <span class="status">LOCAL</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">PREFERENCES</p>
          <h1>设置</h1>
          <p class="subtitle">
            配置面试回答的语音输入和 AI Provider。所有设置只保存在本机，录音不会上传。
          </p>
        </div>
      </section>

      <section class="settings-section voice-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">VOICE INPUT</p>
            <h2>语音设置</h2>
            <p class="section-desc">检测本机麦克风和扬声器，选择面试回答使用的输入设备、输出设备和识别语言。</p>
          </div>
          <el-button :loading="detecting" @click="detectDevices">检测设备</el-button>
        </div>

        <el-form label-position="top" class="voice-form">
          <div class="voice-grid">
            <el-form-item label="输入设备">
              <el-select
                v-model="voice.deviceId"
                placeholder="系统默认麦克风"
                style="width: 100%"
              >
                <el-option label="系统默认麦克风" :value="DEFAULT_MICROPHONE_ID" />
                <el-option
                  v-for="device in microphones"
                  :key="device.deviceId"
                  :label="device.label"
                  :value="device.deviceId"
                />
                <el-option
                  v-if="savedDeviceMissing"
                  label="已保存的麦克风（当前未检测到）"
                  :value="voice.deviceId"
                />
              </el-select>
              <p class="field-hint">
                {{
                  microphones.length
                    ? `已检测到 ${microphones.length} 个输入设备`
                    : '未读取到独立设备时使用系统默认麦克风；点击“检测设备”可重试'
                }}
              </p>
            </el-form-item>

            <el-form-item label="识别语言">
              <el-select v-model="voice.language" style="width: 100%">
                <el-option label="中文（推荐）" value="zh" />
                <el-option label="英文" value="en" />
                <el-option label="自动检测" value="auto" />
              </el-select>
              <p class="field-hint">录音只在本机识别，选择“自动检测”时由 Whisper 判断语言。</p>
            </el-form-item>
          </div>

          <div class="voice-grid voice-test-grid">
            <el-form-item label="麦克风测试">
              <div class="device-test-row">
                <el-button
                  :type="micTesting ? 'danger' : 'default'"
                  :plain="micTesting"
                  @click="toggleMicTest"
                >
                  {{ micTesting ? '停止测试' : '测试麦克风' }}
                </el-button>
                <div class="db-meter" :class="{ active: micTesting }">
                  <div class="db-bar"><i :style="{ width: `${micLevelPercent}%` }"></i></div>
                  <span class="db-value">{{ micDbLabel }}</span>
                  <span v-if="micTesting" class="live-badge">实时回放</span>
                </div>
              </div>
              <p class="field-hint test-warning">
                不会播放预置音频，只回放当前麦克风采集的环境声；若听到的是系统声音，请改选物理麦克风
                （ToDesk、立体声混音等虚拟设备会采集系统音频）。建议戴耳机，避免啸叫。
              </p>
            </el-form-item>

            <el-form-item label="扬声器">
              <div class="device-test-row">
                <el-select
                  v-model="voice.speakerId"
                  placeholder="系统默认扬声器"
                  class="speaker-select"
                >
                  <el-option label="系统默认扬声器" :value="DEFAULT_SPEAKER_ID" />
                  <el-option
                    v-for="device in speakers"
                    :key="device.deviceId"
                    :label="device.label"
                    :value="device.deviceId"
                  />
                  <el-option
                    v-if="savedSpeakerMissing"
                    label="已保存的扬声器（当前未检测到）"
                    :value="voice.speakerId"
                  />
                </el-select>
                <el-button
                  :type="speakerTesting ? 'danger' : 'default'"
                  :plain="speakerTesting"
                  @click="toggleSpeakerTest"
                >
                  {{ speakerTesting ? '停止铃声' : '测试扬声器' }}
                </el-button>
              </div>
              <p class="field-hint">
                {{
                  speakers.length
                    ? `已检测到 ${speakers.length} 个输出设备，点击测试会播放一段铃声`
                    : '点击“检测设备”读取扬声器名称；未选择时使用系统默认扬声器'
                }}
              </p>
            </el-form-item>
          </div>

          <el-alert
            v-if="voiceError"
            :title="voiceError"
            type="error"
            :closable="false"
            show-icon
          />
        </el-form>
      </section>

      <section class="settings-section api-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">AI PROVIDERS</p>
            <h2>API 设置</h2>
            <p class="section-desc">
              配置你自己的大模型 API（DeepSeek、Kimi、智谱、通义千问、OpenAI 等），
              面试、评估和报告会使用当前激活的配置。API Key 加密存储，只显示掩码。
            </p>
          </div>
          <el-button type="primary" size="large" @click="openCreate">新增配置</el-button>
        </div>

        <el-alert
          v-if="status && !status.aiEnabled"
          class="mock-alert"
          type="warning"
          :closable="false"
          show-icon
          title="服务端当前为 Mock 模式（APP_AI_ENABLED=false），保存的 API 配置暂不生效。"
        />

        <section class="active-banner">
        <template v-if="activeProvider">
          <div>
            <span class="active-label">当前使用</span>
            <strong>{{ activeProvider.name }}</strong>
            <span class="active-meta">{{ activeProvider.model }} · {{ activeProvider.baseUrl }}</span>
          </div>
          <el-tag type="success" effect="dark">已激活</el-tag>
        </template>
        <template v-else>
          <span class="active-meta">尚未配置 API，将使用系统默认配置（如果服务端已开启真实 AI）。</span>
        </template>
      </section>

      <el-empty
        v-if="!loading && providers.length === 0"
        description="还没有 API 配置，点击右上角新增"
      />

      <section v-else class="provider-list">
        <article v-for="provider in providers" :key="provider.id" class="provider-card">
          <div class="provider-main">
            <div class="provider-head">
              <strong>{{ provider.name }}</strong>
              <el-tag v-if="provider.active" type="success" size="small">当前</el-tag>
              <el-tag v-if="!provider.enabled" type="info" size="small">已停用</el-tag>
              <el-tag size="small" effect="plain">{{ typeLabel(provider.providerType) }}</el-tag>
            </div>
            <p class="provider-meta">
              {{ provider.baseUrl }} · {{ provider.model }} ·
              {{ provider.apiKeyHint || '未设置 Key' }}
            </p>
          </div>
          <div class="provider-actions">
            <el-button
              v-if="!provider.active"
              size="small"
              type="primary"
              plain
              @click="activate(provider)"
            >
              设为当前
            </el-button>
            <el-button
              size="small"
              :loading="testingId === provider.id"
              @click="testConnection(provider)"
            >
              测试连接
            </el-button>
            <el-button size="small" @click="openEdit(provider)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(provider)">删除</el-button>
          </div>
        </article>
        </section>
      </section>
    </main>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑 API 配置' : '新增 API 配置'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="预设">
          <el-select
            v-model="form.providerType"
            filterable
            style="width: 100%"
            @change="handleTypeChange"
          >
            <el-option-group
              v-for="group in presetGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="preset in group.options"
                :key="preset.type"
                :label="preset.label"
                :value="preset.type"
              />
            </el-option-group>
          </el-select>
        </el-form-item>

        <el-form-item label="配置名称">
          <el-input v-model="form.name" maxlength="64" placeholder="例如：我的 MiMo" />
        </el-form-item>

        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="选择预设后自动填充，可修改" />
          <p v-if="endpointChanged" class="endpoint-hint">
            修改服务商或 Base URL 后，需要重新输入 API Key。
          </p>
        </el-form-item>

        <el-form-item label="模型">
          <el-select
            v-model="form.model"
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择或输入模型 ID"
            style="width: 100%"
          >
            <el-option v-for="model in modelOptions" :key="model" :label="model" :value="model" />
          </el-select>
          <p class="field-hint">可从预设模型中选择，也可直接输入厂商提供的模型 ID。</p>
        </el-form-item>

        <el-form-item :label="isEditing ? 'API Key（留空保持原 Key）' : 'API Key'">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="isEditing ? '留空表示不修改' : '填写厂商 API Key'"
          />
        </el-form-item>

        <el-form-item label="Temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ isEditing ? '保存修改' : '创建配置' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-page {
  min-height: 100vh;
}

.endpoint-hint {
  margin: 6px 0 0;
  color: #b88230;
  font-size: 12px;
  line-height: 1.6;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
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
  color: #7f8c9d;
  font-size: 13px;
}

.content {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 28px;
  border-bottom: 1px solid #e4ecf5;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 14px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 40px);
}

.subtitle {
  margin: 0;
  max-width: 620px;
  color: #6b7a8c;
  font-size: 14px;
  line-height: 1.7;
}

.field-hint {
  margin: 6px 0 0;
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.5;
}

.settings-section {
  margin-top: 28px;
  padding: 24px 26px;
  border: 1px solid #e4ecf5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 30px rgba(31, 78, 121, 0.04);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: 24px;
}

.section-desc {
  max-width: 720px;
  margin: 0;
  color: #6b7a8c;
  font-size: 14px;
  line-height: 1.7;
}

.voice-form {
  margin-top: 6px;
}

.voice-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 20px;
}

.voice-test-grid {
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px dashed #e4ecf5;
}

.device-test-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.speaker-select {
  flex: 1;
  min-width: 0;
}

.db-meter {
  flex: 1;
  min-width: 120px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.db-bar {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f8;
}

.db-bar i {
  display: block;
  height: 100%;
  width: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, #67c23a, #e6a23c 70%, #f56c6c);
  transition: width 80ms linear;
}

.db-value {
  width: 54px;
  flex: none;
  color: #8a97a8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.db-meter.active .db-value {
  color: #2e75b6;
}

.live-badge {
  flex: none;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 11px;
  white-space: nowrap;
}

.test-warning {
  color: #b88230;
}

.voice-form :deep(.el-alert) {
  margin-top: 4px;
}

.api-section .active-banner {
  margin-top: 20px;
}

.mock-alert {
  margin-top: 20px;
}

.active-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding: 16px 20px;
  border: 1px solid #d8e8f8;
  border-radius: 14px;
  background: #f5f9ff;
  color: #17375e;
}

.active-label {
  margin-right: 10px;
  color: #7f8c9d;
  font-size: 12px;
}

.active-meta {
  margin-left: 10px;
  color: #6b7a8c;
  font-size: 13px;
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 24px;
}

.provider-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #fff;
}

.provider-main {
  min-width: 0;
}

.provider-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #17375e;
}

.provider-head strong {
  font-size: 16px;
}

.provider-meta {
  margin: 8px 0 0;
  color: #6b7a8c;
  font-size: 13px;
  word-break: break-all;
}

.provider-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.provider-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    padding: 28px 18px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .settings-section {
    padding: 20px 18px;
  }

  .section-head {
    flex-direction: column;
  }

  .voice-grid {
    grid-template-columns: 1fr;
  }

  .device-test-row {
    flex-wrap: wrap;
  }

  .db-meter {
    min-width: 100%;
  }

  .provider-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .provider-actions {
    justify-content: flex-start;
  }
}
</style>
