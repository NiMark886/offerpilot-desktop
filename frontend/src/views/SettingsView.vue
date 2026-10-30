<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { computed, onMounted, reactive, ref } from 'vue'
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
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return
    }
  }
  await load()
})

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
      <BrandBlock suffix="API 设置" />
      <span class="status">BYOK</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">AI PROVIDERS</p>
          <h1>API 设置</h1>
          <p class="subtitle">
            配置你自己的大模型 API（DeepSeek、Kimi、智谱、通义千问、OpenAI 等），
            面试、评估和报告会使用当前激活的配置。API Key 加密存储，只显示掩码。
          </p>
        </div>
        <el-button type="primary" size="large" @click="openCreate">新增配置</el-button>
      </section>

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

  .provider-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .provider-actions {
    justify-content: flex-start;
  }
}
</style>
