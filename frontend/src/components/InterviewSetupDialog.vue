<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import type { JobTemplate } from '@/api/job'
import { resumeApi, type ResumeView } from '@/api/resume'
import { interviewApi, type InterviewSession } from '@/api/interview'

const props = defineProps<{
  modelValue: boolean
  job: JobTemplate | null
  custom?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [session: InterviewSession]
}>()

const resumes = ref<ResumeView[]>([])
const loading = ref(false)
const uploading = ref(false)
const deleting = ref(false)
const submitting = ref(false)

const form = reactive({
  resumeId: undefined as number | undefined,
  difficulty: 'JUNIOR',
  durationMinutes: 30,
  persona: 'STANDARD',
  mode: 'INTERVIEW'
})

const customForm = reactive({
  jdText: ''
})

const isCustom = computed(() => props.custom === true)

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const selectedResume = computed(() =>
  resumes.value.find((item) => item.id === form.resumeId)
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    await loadResumes()
  }
)

async function loadResumes() {
  loading.value = true
  try {
    resumes.value = await resumeApi.list()
    if (!form.resumeId && resumes.value.length > 0) {
      form.resumeId = resumes.value[0].id
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const resume = await resumeApi.upload(options.file as File)
    resumes.value.unshift(resume)
    form.resumeId = resume.id
    options.onSuccess(resume)
    ElMessage.success(
      resume.parseStatus === 'PARSED' ? '简历上传并解析成功' : '简历已上传，但解析失败'
    )
  } catch (error) {
    options.onError(error as never)
  } finally {
    uploading.value = false
  }
}

async function removeResume() {
  const resume = selectedResume.value
  if (!resume) return
  try {
    await ElMessageBox.confirm(
      `确定删除简历「${resume.fileName}」吗？删除后不可恢复。`,
      '删除简历',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await resumeApi.remove(resume.id)
    resumes.value = resumes.value.filter((item) => item.id !== resume.id)
    form.resumeId = resumes.value[0]?.id
    ElMessage.success('简历已删除')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    deleting.value = false
  }
}

async function submit() {
  if (!isCustom.value && !props.job) return
  if (!form.resumeId) {
    ElMessage.warning('请先上传或选择一份简历')
    return
  }
  if (isCustom.value && customForm.jdText.trim().length < 30) {
    ElMessage.warning('请粘贴至少 30 个字的销售 JD')
    return
  }

  submitting.value = true
  try {
    const session = isCustom.value
      ? await interviewApi.createCustom({
          jdText: customForm.jdText.trim(),
          resumeId: form.resumeId,
          difficulty: form.difficulty,
          durationMinutes: form.durationMinutes,
          persona: form.persona,
          mode: form.mode
        })
      : await interviewApi.create({
          jobTemplateId: props.job!.id,
          resumeId: form.resumeId,
          difficulty: form.difficulty,
          durationMinutes: form.durationMinutes,
          persona: form.persona,
          mode: form.mode
        })
    ElMessage.success('面试已创建')
    emit('created', session)
    visible.value = false
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
  >
    <template #header>
      <div class="dialog-header">
        <span>{{ isCustom ? '创建销售岗面试' : '创建模拟面试' }}</span>
        <small v-if="isCustom">销售岗面试 · 粘贴 JD 后，系统结合简历和能力模型出题</small>
        <small v-else-if="job">{{ job.title }}</small>
      </div>
    </template>

    <div v-loading="loading" class="setup-body">
      <section v-if="isCustom" class="field-block">
        <div class="field-title"><span>销售岗面试</span></div>
        <p class="mode-hint" style="margin-top: 0; margin-bottom: 12px">
          本次面试统一归入“销售岗面试”，可在 JD 中写明具体方向（如大客户销售、SaaS 销售）。
        </p>
        <div class="field-title"><span>销售 JD</span></div>
        <el-input
          v-model="customForm.jdText"
          type="textarea"
          :rows="6"
          maxlength="8000"
          show-word-limit
          placeholder="粘贴目标岗位的 JD，至少 30 个字。系统会结合简历和销售能力模型生成题目。"
        />
      </section>

      <section class="field-block">
        <div class="field-title">
          <span>选择简历</span>
          <div class="field-actions">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              accept=".pdf,.docx"
              :disabled="uploading"
            >
              <el-button size="small" :loading="uploading">上传 PDF / DOCX</el-button>
            </el-upload>
            <el-button
              v-if="resumes.length"
              link
              type="danger"
              size="small"
              :loading="deleting"
              :disabled="!form.resumeId || uploading"
              @click="removeResume"
            >
              删除当前简历
            </el-button>
          </div>
        </div>

        <el-empty
          v-if="!loading && resumes.length === 0"
          description="还没有简历，先上传一份"
          :image-size="80"
        />
        <el-select
          v-else
          v-model="form.resumeId"
          placeholder="选择一份简历"
          style="width: 100%"
        >
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="`${resume.fileName} · ${resume.parseStatus === 'PARSED' ? '已解析' : '解析失败'}`"
            :value="resume.id"
          />
        </el-select>

        <div v-if="selectedResume?.profile" class="resume-summary">
          <div class="summary-row">
            <strong>{{ selectedResume.profile.name || '候选人' }}</strong>
            <span v-if="selectedResume.profile.yearsOfExperience">
              {{ selectedResume.profile.yearsOfExperience }} 年经验
            </span>
          </div>
          <div class="skill-tags">
            <el-tag
              v-for="skill in (selectedResume.profile.skills ?? []).slice(0, 8)"
              :key="skill"
              size="small"
              effect="plain"
            >
              {{ skill }}
            </el-tag>
          </div>
          <p v-if="selectedResume.profile.projects?.length" class="project-line">
            项目：{{ selectedResume.profile.projects?.map((p) => p.name).join('、') }}
          </p>
        </div>
      </section>

      <section class="field-block">
        <div class="field-title"><span>模式</span></div>
        <el-radio-group v-model="form.mode">
          <el-radio-button value="INTERVIEW">模拟面试</el-radio-button>
          <el-radio-button value="PRACTICE">练习模式（实时点评）</el-radio-button>
        </el-radio-group>
        <p v-if="form.mode === 'PRACTICE'" class="mode-hint">
          每题回答后会立即显示正确性点评，适合刷题练习。
        </p>
      </section>

      <section class="field-block">
        <div class="field-title"><span>工作年限</span></div>
        <el-radio-group v-model="form.difficulty">
          <el-radio-button value="CAMPUS">校招 / 应届</el-radio-button>
          <el-radio-button value="JUNIOR">1-3 年</el-radio-button>
          <el-radio-button value="SENIOR">3-5 年</el-radio-button>
        </el-radio-group>
        <p class="mode-hint">年限越高，题目难度和追问深度越高。</p>
      </section>

      <section class="field-block">
        <div class="field-title"><span>时长</span></div>
        <el-radio-group v-model="form.durationMinutes">
          <el-radio-button :value="20">20 分钟</el-radio-button>
          <el-radio-button :value="30">30 分钟</el-radio-button>
          <el-radio-button :value="45">45 分钟</el-radio-button>
        </el-radio-group>
      </section>

      <section class="field-block">
        <div class="field-title"><span>面试官风格</span></div>
        <el-radio-group v-model="form.persona">
          <el-radio-button value="GENTLE">温和</el-radio-button>
          <el-radio-button value="STANDARD">标准</el-radio-button>
          <el-radio-button value="PRESSURE">压力面</el-radio-button>
        </el-radio-group>
      </section>

      <el-alert
        v-if="submitting"
        :title="isCustom
          ? '正在生成销售面试蓝图：AI 可用时会个性化出题，否则自动使用题库模式，请勿关闭'
          : 'AI 正在生成蓝图，约 10–30 秒，请勿关闭'"
        type="info"
        :closable="false"
        show-icon
      />
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ submitting ? '生成中…' : (isCustom ? '开始销售岗面试' : '生成面试蓝图') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #17375e;
  font-size: 20px;
  font-weight: 700;
}

.dialog-header small {
  color: #7f8c9d;
  font-size: 13px;
  font-weight: 400;
}

.setup-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.field-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #17375e;
  font-size: 14px;
  font-weight: 700;
}

.field-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.resume-summary {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f5f8fc;
  border: 1px solid #e4ecf5;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #17375e;
}

.summary-row span {
  color: #7f8c9d;
  font-size: 12px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.project-line {
  margin: 10px 0 0;
  color: #6b7a8c;
  font-size: 13px;
  line-height: 1.6;
}

.mode-hint {
  margin: 8px 0 0;
  color: #8a97a8;
  font-size: 12px;
}
</style>
