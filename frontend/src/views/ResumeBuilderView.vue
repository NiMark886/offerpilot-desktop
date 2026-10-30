<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BrandBlock from '@/components/BrandBlock.vue'
import MonthPicker from '@/components/MonthPicker.vue'
import SectionFontSize from '@/components/SectionFontSize.vue'
import { resumeApi, type ResumeProfile, type ResumeView } from '@/api/resume'
import {
  resumeDraftApi,
  type ResumeDraftContent,
  type ResumeDraftView,
  type ResumeEducation,
  type ResumeExperience,
  type ResumeExperienceType,
  type ResumeProject,
  type ResumeSkillGroup
} from '@/api/resumeDraft'
import {
  defaultResumeStyle,
  emptyResumeContent,
  renderResumeDocument,
  resumeFileBaseName
} from '@/utils/resumeTemplate'

const router = useRouter()

const drafts = ref<ResumeDraftView[]>([])
const draftId = ref<number | undefined>()
const lastDraftId = ref<number | undefined>()
const title = ref('我的简历')
const content = ref<ResumeDraftContent>(emptyResumeContent())
const loading = ref(true)
const saving = ref(false)
const exportingPdf = ref(false)
const exportingDocx = ref(false)
const dirty = ref(false)
const activeTab = ref('basic')
const previewHtml = ref(renderResumeDocument(content.value, { placeholder: true }))
const previewWrap = ref<HTMLElement>()
const previewScale = ref(0.55)
const photoInput = ref<HTMLInputElement>()

const importVisible = ref(false)
const importLoading = ref(false)
const resumes = ref<ResumeView[]>([])
const importResumeId = ref<number | undefined>()

let hydrating = false
let autosaveTimer: number | undefined
let previewTimer: number | undefined
let resizeObserver: ResizeObserver | undefined

const previewWrapStyle = computed(() => ({
  height: `${Math.round(1123 * previewScale.value)}px`
}))

const previewFrameStyle = computed(() => ({
  transform: `scale(${previewScale.value})`
}))

const awardsText = computed({
  get: () => content.value.awards.join('\n'),
  set: (value: string) => {
    content.value.awards = value.split('\n')
  }
})

const saveState = computed(() => {
  if (saving.value) return '保存中…'
  if (dirty.value) return '未保存'
  if (draftId.value) return '已保存'
  return '新简历'
})

onMounted(async () => {
  await loadDrafts()
  await nextTick()
  fitPreview()
  if (previewWrap.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(fitPreview)
    resizeObserver.observe(previewWrap.value)
  }
})

onBeforeUnmount(() => {
  window.clearTimeout(autosaveTimer)
  window.clearTimeout(previewTimer)
  resizeObserver?.disconnect()
})

watch(
  content,
  () => {
    if (hydrating) return
    dirty.value = true
    schedulePreview()
    scheduleAutosave()
  },
  { deep: true }
)

watch(title, () => {
  if (hydrating) return
  dirty.value = true
  scheduleAutosave()
})

async function loadDrafts() {
  loading.value = true
  try {
    drafts.value = await resumeDraftApi.list()
    if (drafts.value.length) {
      await selectDraft(drafts.value[0].id)
    } else {
      resetDraft()
    }
  } catch {
    // axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}

async function selectDraft(id: number) {
  const draft = drafts.value.find((item) => item.id === id) ?? await resumeDraftApi.get(id)
  hydrating = true
  draftId.value = draft.id
  lastDraftId.value = draft.id
  title.value = draft.title
  content.value = normalizeContent(draft.content)
  dirty.value = false
  await nextTick()
  hydrating = false
  dirty.value = false
  updatePreview()
}

async function onDraftChange(id: number) {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前简历有未保存修改，切换后会丢失，确定继续？',
        '切换简历',
        { type: 'warning', confirmButtonText: '继续切换', cancelButtonText: '取消' }
      )
    } catch {
      draftId.value = lastDraftId.value
      return
    }
  }
  if (id) {
    await selectDraft(id)
  }
}

async function newDraft(skipConfirm = false) {
  if (!skipConfirm && dirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前简历有未保存修改，新建后会丢失，确定继续？',
        '新建简历',
        { type: 'warning', confirmButtonText: '新建', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  resetDraft()
}

function resetDraft() {
  hydrating = true
  draftId.value = undefined
  lastDraftId.value = undefined
  title.value = '我的简历'
  content.value = emptyResumeContent()
  dirty.value = false
  activeTab.value = 'basic'
  nextTick(() => {
    hydrating = false
    dirty.value = false
    updatePreview()
  })
}

async function saveDraft(silent = false): Promise<boolean> {
  if (saving.value) return false
  if (!title.value.trim()) {
    title.value = content.value.personal.name.trim()
      ? `${content.value.personal.name.trim()}的简历`
      : '我的简历'
  }
  saving.value = true
  try {
    const payload = {
      title: title.value.trim(),
      content: content.value
    }
    const saved = draftId.value
      ? await resumeDraftApi.update(draftId.value, payload)
      : await resumeDraftApi.create(payload)
    draftId.value = saved.id
    lastDraftId.value = saved.id
    const existing = drafts.value.find((item) => item.id === saved.id)
    if (existing) {
      existing.title = saved.title
      existing.updatedAt = saved.updatedAt
    } else {
      drafts.value.unshift(saved)
    }
    await nextTick()
    dirty.value = false
    if (!silent) {
      ElMessage.success('简历已保存')
    }
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

async function deleteDraft() {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除简历「${title.value}」吗？删除后不可恢复。`,
      '删除简历',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await resumeDraftApi.remove(draftId.value)
    drafts.value = drafts.value.filter((item) => item.id !== draftId.value)
    if (drafts.value.length) {
      await selectDraft(drafts.value[0].id)
    } else {
      resetDraft()
    }
    ElMessage.success('简历草稿已删除')
  } catch {
    // axios 拦截器统一提示
  }
}

function schedulePreview() {
  window.clearTimeout(previewTimer)
  previewTimer = window.setTimeout(updatePreview, 160)
}

function updatePreview() {
  previewHtml.value = renderResumeDocument(content.value, { placeholder: true })
}

function scheduleAutosave() {
  if (!draftId.value) return
  window.clearTimeout(autosaveTimer)
  autosaveTimer = window.setTimeout(() => {
    void saveDraft(true)
  }, 1500)
}

function fitPreview() {
  const width = previewWrap.value?.clientWidth ?? 0
  if (!width) return
  previewScale.value = Math.max(0.32, Math.min(1, (width - 24) / 794))
}

function addEducation() {
  content.value.education.push(newEducation())
}

function addExperience(type: ResumeExperienceType = 'INTERNSHIP') {
  content.value.internships.push(newExperience(type))
}

function addProject() {
  content.value.projects.push(newProject())
}

function addSkill() {
  content.value.skills.push(newSkill())
}

function removeAt<T>(list: T[], index: number) {
  list.splice(index, 1)
}

function newEducation(): ResumeEducation {
  return {
    school: '',
    college: '',
    major: '',
    degree: '',
    startDate: '',
    endDate: '',
    courses: '',
    gpa: '',
    ranking: ''
  }
}

function newExperience(type: ResumeExperienceType = 'INTERNSHIP'): ResumeExperience {
  return {
    type,
    company: '',
    role: '',
    startDate: '',
    endDate: '',
    techStack: '',
    bullets: ['']
  }
}

function newProject(): ResumeProject {
  return {
    name: '',
    startDate: '',
    endDate: '',
    techStack: '',
    repoUrl: '',
    bullets: ['']
  }
}

function newSkill(): ResumeSkillGroup {
  return { label: '', content: '' }
}

function bulletsText(item: ResumeExperience | ResumeProject): string {
  return (item.bullets ?? []).join('\n')
}

function updateBullets(item: ResumeExperience | ResumeProject, value: string) {
  item.bullets = value.split('\n')
}

function normalizeContent(value: ResumeDraftContent): ResumeDraftContent {
  const base = emptyResumeContent()
  return {
    personal: { ...base.personal, ...(value.personal ?? {}) },
    education: (value.education ?? []).map((item) => ({ ...newEducation(), ...item })),
    internships: (value.internships ?? []).map((item) => ({
      ...newExperience(),
      ...item,
      type: item.type === 'WORK' ? 'WORK' : 'INTERNSHIP',
      bullets: item.bullets ?? ['']
    })),
    projects: (value.projects ?? []).map((item) => ({
      ...newProject(),
      ...item,
      bullets: item.bullets ?? ['']
    })),
    skills: (value.skills ?? []).map((item) => ({ ...newSkill(), ...item })),
    awards: value.awards ?? [],
    style: {
      ...defaultResumeStyle(),
      ...(value.style ?? {}),
      sectionFontSizes: { ...(value.style?.sectionFontSizes ?? {}) }
    }
  }
}

function sectionSize(key: string): number {
  return content.value.style.sectionFontSizes?.[key] ?? 0
}

function setSectionSize(key: string, value: number) {
  const sizes = { ...content.value.style.sectionFontSizes }
  if (value && value > 0) {
    sizes[key] = value
  } else {
    delete sizes[key]
  }
  content.value.style.sectionFontSizes = sizes
}

function resetStyle() {
  content.value.style = defaultResumeStyle()
}

function pickPhoto() {
  photoInput.value?.click()
}

async function handlePhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('照片不能超过 5MB')
    return
  }
  try {
    content.value.personal.photo = await resizePhoto(file)
    ElMessage.success('照片已载入')
  } catch {
    ElMessage.error('照片处理失败，请换一张图片')
  }
}

function resizePhoto(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      const width = 300
      const height = 400
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      if (!context) {
        URL.revokeObjectURL(objectUrl)
        reject(new Error('canvas unavailable'))
        return
      }
      const scale = Math.max(width / image.width, height / image.height)
      const drawWidth = image.width * scale
      const drawHeight = image.height * scale
      context.drawImage(
        image,
        (width - drawWidth) / 2,
        (height - drawHeight) / 2,
        drawWidth,
        drawHeight
      )
      URL.revokeObjectURL(objectUrl)
      resolve(canvas.toDataURL('image/jpeg', 0.86))
    }
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('image load failed'))
    }
    image.src = objectUrl
  })
}

function removePhoto() {
  content.value.personal.photo = ''
}

async function openImport() {
  importVisible.value = true
  importLoading.value = true
  try {
    resumes.value = await resumeApi.list()
    if (!importResumeId.value && resumes.value.length) {
      importResumeId.value = resumes.value[0].id
    }
  } catch {
    // axios 拦截器统一提示
  } finally {
    importLoading.value = false
  }
}

async function applyImport() {
  if (!importResumeId.value) {
    ElMessage.warning('请选择一份已上传简历')
    return
  }
  try {
    await ElMessageBox.confirm(
      '导入会覆盖当前简历中的对应内容，确定继续吗？',
      '导入简历',
      { type: 'warning', confirmButtonText: '导入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  importLoading.value = true
  try {
    const resume = await resumeApi.get(importResumeId.value)
    const profile = resume.profile
    if (!profile) {
      ElMessage.warning('这份简历没有解析结果，请先在简历评估页重新上传')
      return
    }
    applyProfile(profile)
    importVisible.value = false
    dirty.value = true
    updatePreview()
    ElMessage.success('已导入，缺失字段请手动补充')
  } catch {
    // axios 拦截器统一提示
  } finally {
    importLoading.value = false
  }
}

function applyProfile(profile: ResumeProfile) {
  content.value.personal.name = profile.name ?? ''
  content.value.personal.phone = profile.phone ?? ''
  content.value.personal.email = profile.email ?? ''
  if (profile.education) {
    content.value.education = [parseEducation(profile.education)]
  }
  if (profile.skills?.length) {
    content.value.skills = [{
      label: '技能',
      content: profile.skills.join('、')
    }]
  }
  if (profile.projects?.length) {
    content.value.projects = profile.projects.map((project) => ({
      name: project.name,
      startDate: '',
      endDate: '',
      techStack: '',
      repoUrl: '',
      bullets: splitBullets(project.description)
    }))
  }
}

function parseEducation(value: string): ResumeEducation {
  const cleaned = value.replace(/（[^）]*）/g, '').trim()
  const parts = cleaned.split(/[·|]/).map((part) => part.trim()).filter(Boolean)
  const degreeMatch = parts.join(' ').match(/(博士|硕士|研究生|本科|学士|大专|专科|高中|中专)/)
  const degree = degreeMatch?.[1] ?? ''
  const school = parts[0] ?? ''
  const major = parts.slice(1).join(' ')
    .replace(degree, '')
    .replace(/[，,]/g, ' ')
    .trim()
  return {
    school,
    college: '',
    major,
    degree,
    startDate: '',
    endDate: '',
    courses: '',
    gpa: '',
    ranking: ''
  }
}

function splitBullets(value: string | null | undefined): string[] {
  if (!value) return ['']
  const bullets = value
    .split(/\r?\n|[。；;]/)
    .map((item) => item.trim().replace(/^[-*•]\s*/, ''))
    .filter(Boolean)
  return bullets.length ? bullets : ['']
}

async function exportPdf() {
  exportingPdf.value = true
  try {
    const html = renderResumeDocument(content.value)
    const fileName = `${resumeFileBaseName(content.value)}.pdf`
    if (window.offerpilotDesktop?.exportResumePdf) {
      const result = await window.offerpilotDesktop.exportResumePdf(html, fileName)
      if (!result.canceled) {
        ElMessage.success('PDF 已保存')
      }
      return
    }
    const printWindow = window.open('', '_blank')
    if (!printWindow) {
      ElMessage.error('浏览器阻止了打印窗口，请允许弹窗后重试')
      return
    }
    printWindow.document.open()
    printWindow.document.write(html)
    printWindow.document.close()
    window.setTimeout(() => {
      printWindow.focus()
      printWindow.print()
    }, 300)
  } catch {
    ElMessage.error('PDF 导出失败')
  } finally {
    exportingPdf.value = false
  }
}

async function exportDocx() {
  exportingDocx.value = true
  try {
    if (!draftId.value || dirty.value) {
      const saved = await saveDraft(true)
      if (!saved || !draftId.value) return
    }
    const data = await resumeDraftApi.exportDocx(draftId.value)
    const bytes = base64ToBytes(data.contentBase64)
    if (window.offerpilotDesktop?.saveFile) {
      const result = await window.offerpilotDesktop.saveFile(
        bytes,
        data.fileName,
        [{ name: 'Word 文档', extensions: ['docx'] }]
      )
      if (!result.canceled) {
        ElMessage.success('DOCX 已保存')
      }
      return
    }
    const buffer = new ArrayBuffer(bytes.byteLength)
    new Uint8Array(buffer).set(bytes)
    const blob = new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = data.fileName
    anchor.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('DOCX 导出失败')
  } finally {
    exportingDocx.value = false
  }
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="builder-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <BrandBlock />
      <span class="status">简历生成</span>
    </header>

    <main v-loading="loading" class="builder-main">
      <section class="card toolbar">
        <div class="draft-switch">
          <el-select
            v-model="draftId"
            placeholder="选择简历草稿"
            style="width: 190px"
            @change="onDraftChange"
          >
            <el-option
              v-for="draft in drafts"
              :key="draft.id"
              :label="draft.title"
              :value="draft.id"
            />
          </el-select>
          <el-button @click="newDraft()">新建</el-button>
          <el-button :disabled="!draftId" @click="deleteDraft">删除</el-button>
          <el-button @click="openImport">从已上传简历导入</el-button>
        </div>
        <div class="toolbar-actions">
          <span class="save-state" :class="{ dirty }">{{ saveState }}</span>
          <el-button :loading="saving" @click="saveDraft(false)">保存草稿</el-button>
          <el-button type="primary" :loading="exportingPdf" @click="exportPdf">导出 PDF</el-button>
          <el-button :loading="exportingDocx" @click="exportDocx">导出 DOCX</el-button>
        </div>
      </section>

      <section class="builder-grid">
        <div class="card form-panel">
          <div class="form-head">
            <div>
              <p class="eyebrow">RESUME BUILDER</p>
              <h1>写简历</h1>
              <p class="subtitle">按模板填写，右侧实时预览；导出时可以选择保存位置。</p>
            </div>
            <div class="title-field">
              <span>简历名称</span>
              <el-input v-model="title" maxlength="60" placeholder="我的简历" />
            </div>
          </div>

          <el-tabs v-model="activeTab" class="form-tabs">
            <el-tab-pane label="基本信息" name="basic">
              <div class="photo-row">
                <input
                  ref="photoInput"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  class="hidden-input"
                  @change="handlePhoto"
                />
                <div v-if="content.personal.photo" class="photo-preview">
                  <img :src="content.personal.photo" alt="证件照" />
                </div>
                <div v-else class="photo-preview photo-empty">证件照</div>
                <div class="photo-actions">
                  <el-button size="small" @click="pickPhoto">
                    {{ content.personal.photo ? '更换照片' : '上传照片' }}
                  </el-button>
                  <el-button
                    v-if="content.personal.photo"
                    size="small"
                    link
                    type="danger"
                    @click="removePhoto"
                  >
                    移除
                  </el-button>
                  <p class="field-hint">可选，建议 3:4 证件照；会自动压缩。</p>
                </div>
              </div>

              <div class="form-grid">
                <el-form-item label="姓名">
                  <el-input v-model="content.personal.name" maxlength="30" placeholder="张三" />
                </el-form-item>
                <el-form-item label="性别">
                  <el-input v-model="content.personal.gender" maxlength="10" placeholder="男 / 女" />
                </el-form-item>
                <el-form-item label="出生日期">
                  <el-input v-model="content.personal.birthDate" maxlength="20" placeholder="2000.01.01" />
                </el-form-item>
                <el-form-item label="民族">
                  <el-input v-model="content.personal.ethnicity" maxlength="20" placeholder="汉族" />
                </el-form-item>
                <el-form-item label="政治面貌">
                  <el-select
                    v-model="content.personal.politicalStatus"
                    clearable
                    placeholder="请选择"
                    style="width: 100%"
                  >
                    <el-option label="群众" value="群众" />
                    <el-option label="共青团员" value="共青团员" />
                    <el-option label="党员" value="党员" />
                  </el-select>
                </el-form-item>
                <el-form-item label="出生地">
                  <el-input v-model="content.personal.birthplace" maxlength="60" placeholder="示例省示例市" />
                </el-form-item>
                <el-form-item label="现居">
                  <el-input v-model="content.personal.currentCity" maxlength="60" placeholder="示例市" />
                </el-form-item>
                <el-form-item label="电话">
                  <el-input v-model="content.personal.phone" maxlength="30" placeholder="13800138000" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="content.personal.email" maxlength="80" placeholder="name@example.com" />
                </el-form-item>
                <el-form-item label="社区账号" class="span-2">
                  <el-input
                    v-model="content.personal.communityUrl"
                    maxlength="200"
                    placeholder="github.com/yourname、gitee.com/yourname 或个人博客地址"
                  />
                </el-form-item>
              </div>
            </el-tab-pane>

            <el-tab-pane label="教育背景" name="education">
              <div class="section-head">
                <span>教育经历</span>
                <div class="section-actions">
                  <SectionFontSize
                    :model-value="sectionSize('education')"
                    @update:model-value="setSectionSize('education', $event)"
                  />
                  <el-button size="small" @click="addEducation">添加教育</el-button>
                </div>
              </div>
              <el-empty v-if="!content.education.length" description="还没有教育经历" :image-size="70" />
              <article
                v-for="(item, index) in content.education"
                :key="index"
                class="entry-card"
              >
                <div class="entry-card-head">
                  <strong>教育 {{ index + 1 }}</strong>
                  <el-button link type="danger" @click="removeAt(content.education, index)">删除</el-button>
                </div>
                <div class="form-grid">
                  <el-form-item label="学校">
                    <el-input v-model="item.school" placeholder="示例大学" />
                  </el-form-item>
                  <el-form-item label="学院">
                    <el-input v-model="item.college" placeholder="计算机学院" />
                  </el-form-item>
                  <el-form-item label="专业">
                    <el-input v-model="item.major" placeholder="软件工程" />
                  </el-form-item>
                  <el-form-item label="学历">
                    <el-input v-model="item.degree" placeholder="本科" />
                  </el-form-item>
                  <el-form-item label="开始时间">
                    <MonthPicker v-model="item.startDate" />
                  </el-form-item>
                  <el-form-item label="结束时间">
                    <MonthPicker v-model="item.endDate" />
                  </el-form-item>
                  <el-form-item label="GPA">
                    <el-input v-model="item.gpa" maxlength="40" placeholder="3.8 / 4.0" />
                  </el-form-item>
                  <el-form-item label="专业排名">
                    <el-input v-model="item.ranking" maxlength="40" placeholder="5 / 120" />
                  </el-form-item>
                </div>
                <el-form-item label="核心课程">
                  <el-input
                    v-model="item.courses"
                    type="textarea"
                    :rows="2"
                    maxlength="500"
                    placeholder="数据结构、数据库原理、计算机网络、操作系统…"
                  />
                </el-form-item>
              </article>
            </el-tab-pane>

            <el-tab-pane label="实习/工作经历" name="experience">
              <div class="section-head">
                <span>实习/工作经历</span>
                <div class="section-actions">
                  <SectionFontSize
                    :model-value="sectionSize('experience')"
                    @update:model-value="setSectionSize('experience', $event)"
                  />
                  <el-button size="small" @click="addExperience('INTERNSHIP')">添加实习</el-button>
                  <el-button size="small" @click="addExperience('WORK')">添加工作</el-button>
                </div>
              </div>
              <el-empty v-if="!content.internships.length" description="还没有实习或工作经历" :image-size="70" />
              <article
                v-for="(item, index) in content.internships"
                :key="index"
                class="entry-card"
              >
                <div class="entry-card-head">
                  <strong>{{ item.type === 'WORK' ? '工作经历' : '实习经历' }} {{ index + 1 }}</strong>
                  <el-button link type="danger" @click="removeAt(content.internships, index)">删除</el-button>
                </div>
                <el-form-item label="经历类型" class="span-2">
                  <el-radio-group v-model="item.type">
                    <el-radio-button value="INTERNSHIP">实习经历</el-radio-button>
                    <el-radio-button value="WORK">工作经历</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <div class="form-grid">
                  <el-form-item label="公司">
                    <el-input v-model="item.company" placeholder="示例科技有限公司" />
                  </el-form-item>
                  <el-form-item label="岗位">
                    <el-input v-model="item.role" placeholder="后端开发实习生" />
                  </el-form-item>
                  <el-form-item label="开始时间">
                    <MonthPicker v-model="item.startDate" />
                  </el-form-item>
                  <el-form-item label="结束时间">
                    <MonthPicker v-model="item.endDate" />
                  </el-form-item>
                </div>
                <el-form-item label="技术栈">
                  <el-input v-model="item.techStack" placeholder="Vue3 · Spring Boot · RESTful API" />
                </el-form-item>
                <el-form-item label="工作内容（一行一条）">
                  <el-input
                    :model-value="bulletsText(item)"
                    type="textarea"
                    :rows="4"
                    placeholder="参与业务系统接口开发与数据对接，完成字段映射和联调…"
                    @update:model-value="updateBullets(item, $event)"
                  />
                </el-form-item>
              </article>
            </el-tab-pane>

            <el-tab-pane label="项目经历" name="projects">
              <div class="section-head">
                <span>项目经历</span>
                <div class="section-actions">
                  <SectionFontSize
                    :model-value="sectionSize('projects')"
                    @update:model-value="setSectionSize('projects', $event)"
                  />
                  <el-button size="small" @click="addProject">添加项目</el-button>
                </div>
              </div>
              <el-empty v-if="!content.projects.length" description="还没有项目经历" :image-size="70" />
              <article
                v-for="(item, index) in content.projects"
                :key="index"
                class="entry-card"
              >
                <div class="entry-card-head">
                  <strong>项目 {{ index + 1 }}</strong>
                  <el-button link type="danger" @click="removeAt(content.projects, index)">删除</el-button>
                </div>
                <div class="form-grid">
                  <el-form-item label="项目名称" class="span-2">
                    <el-input v-model="item.name" placeholder="在线教育后台管理系统" />
                  </el-form-item>
                  <el-form-item label="开始时间">
                    <MonthPicker v-model="item.startDate" />
                  </el-form-item>
                  <el-form-item label="结束时间">
                    <MonthPicker v-model="item.endDate" />
                  </el-form-item>
                </div>
                <el-form-item label="技术栈">
                  <el-input v-model="item.techStack" placeholder="Spring Boot · MyBatis · MySQL · JWT" />
                </el-form-item>
                <el-form-item label="仓库地址">
                  <el-input
                    v-model="item.repoUrl"
                    maxlength="200"
                    placeholder="github.com/yourname/project"
                  />
                </el-form-item>
                <el-form-item label="项目内容（一行一条）">
                  <el-input
                    :model-value="bulletsText(item)"
                    type="textarea"
                    :rows="4"
                    placeholder="完成用户、订单等后台管理接口，支持多条件查询与分页…"
                    @update:model-value="updateBullets(item, $event)"
                  />
                </el-form-item>
              </article>
            </el-tab-pane>

            <el-tab-pane label="技能与荣誉" name="skills">
              <div class="section-head">
                <span>专业技能</span>
                <div class="section-actions">
                  <SectionFontSize
                    :model-value="sectionSize('skills')"
                    @update:model-value="setSectionSize('skills', $event)"
                  />
                  <el-button size="small" @click="addSkill">添加技能组</el-button>
                </div>
              </div>
              <el-empty v-if="!content.skills.length" description="还没有技能分组" :image-size="70" />
              <article
                v-for="(item, index) in content.skills"
                :key="index"
                class="entry-card"
              >
                <div class="entry-card-head">
                  <strong>技能组 {{ index + 1 }}</strong>
                  <el-button link type="danger" @click="removeAt(content.skills, index)">删除</el-button>
                </div>
                <div class="form-grid">
                  <el-form-item label="分类">
                    <el-input v-model="item.label" placeholder="Java / Spring" />
                  </el-form-item>
                  <el-form-item label="内容" class="span-2">
                    <el-input
                      v-model="item.content"
                      type="textarea"
                      :rows="2"
                      placeholder="熟悉 Java 基础、集合、异常处理；使用 Spring Boot 构建 RESTful API…"
                    />
                  </el-form-item>
                </div>
              </article>

              <div class="section-head awards-head">
                <span>荣誉奖项 / 证书</span>
                <SectionFontSize
                  :model-value="sectionSize('awards')"
                  @update:model-value="setSectionSize('awards', $event)"
                />
              </div>
              <el-input
                v-model="awardsText"
                type="textarea"
                :rows="4"
                placeholder="一行一条，例如：2024 年校级一等奖学金"
              />
            </el-tab-pane>

            <el-tab-pane label="样式设置" name="style">
              <div class="section-head">
                <span>全局排版</span>
                <el-button size="small" @click="resetStyle">恢复默认</el-button>
              </div>
              <div class="form-grid">
                <el-form-item label="正文字号 (pt)">
                  <el-input-number
                    v-model="content.style.bodyFontSize"
                    :min="8"
                    :max="16"
                    :step="0.5"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="姓名字号 (pt)">
                  <el-input-number
                    v-model="content.style.nameFontSize"
                    :min="16"
                    :max="32"
                    :step="0.5"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="栏目标题字号 (pt)">
                  <el-input-number
                    v-model="content.style.sectionTitleFontSize"
                    :min="10"
                    :max="20"
                    :step="0.5"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="条目标题字号 (pt)">
                  <el-input-number
                    v-model="content.style.entryTitleFontSize"
                    :min="9"
                    :max="16"
                    :step="0.5"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="联系方式字号 (pt)">
                  <el-input-number
                    v-model="content.style.metaFontSize"
                    :min="8"
                    :max="14"
                    :step="0.5"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="行间距">
                  <el-input-number
                    v-model="content.style.lineHeight"
                    :min="1"
                    :max="2.5"
                    :step="0.05"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="字间距 (pt)">
                  <el-input-number
                    v-model="content.style.letterSpacing"
                    :min="0"
                    :max="2"
                    :step="0.1"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </div>
              <p class="field-hint">
                正文字号作用于整份简历；如需单独调整某个板块，在该板块标题右侧选择字号。
                行间距和字间距对 PDF、DOCX 和预览同时生效。
              </p>
            </el-tab-pane>
          </el-tabs>
        </div>

        <aside class="card preview-panel">
          <div class="preview-head">
            <strong>实时预览</strong>
            <span>A4 · 单栏模板</span>
          </div>
          <div ref="previewWrap" class="preview-wrap" :style="previewWrapStyle">
            <iframe
              class="preview-frame"
              :srcdoc="previewHtml"
              :style="previewFrameStyle"
              sandbox="allow-scripts"
              title="简历预览"
            />
          </div>
          <p class="preview-hint">导出 PDF 会按 A4 排版；如果内容超过一页，会自动分页。</p>
        </aside>
      </section>
    </main>

    <el-dialog v-model="importVisible" title="从已上传简历导入" width="520px">
      <p class="dialog-hint">
        导入姓名、联系方式、教育和项目的基础信息；公司、时间和技能细节需要手动补充。
      </p>
      <el-select
        v-model="importResumeId"
        placeholder="选择一份已上传简历"
        style="width: 100%"
        :loading="importLoading"
      >
        <el-option
          v-for="resume in resumes"
          :key="resume.id"
          :label="`${resume.fileName} · ${resume.parseStatus === 'PARSED' ? '已解析' : '解析失败'}`"
          :value="resume.id"
        />
      </el-select>
      <el-empty
        v-if="!importLoading && !resumes.length"
        description="还没有上传过简历，请先在简历评估页上传"
        :image-size="70"
      />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importResumeId" @click="applyImport">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.builder-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 20;
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

.builder-main {
  max-width: 1380px;
  margin: 0 auto;
  padding: 20px 24px 48px;
}

.card {
  background: #fff;
  border: 1px solid #e4ecf5;
  border-radius: 18px;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.draft-switch,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.save-state {
  color: #8a97a8;
  font-size: 12px;
  min-width: 44px;
  text-align: right;
}

.save-state.dirty {
  color: #b88230;
}

.builder-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 440px;
  gap: 20px;
  align-items: start;
}

.form-panel {
  padding: 22px 24px 26px;
  min-width: 0;
}

.form-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 10px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.form-head h1 {
  margin: 0 0 6px;
  color: #17375e;
  font-size: 28px;
}

.subtitle {
  margin: 0;
  color: #7f8c9d;
  font-size: 13px;
  line-height: 1.7;
}

.title-field {
  flex: none;
  width: 240px;
}

.title-field > span {
  display: block;
  margin-bottom: 8px;
  color: #5b6b7f;
  font-size: 12px;
}

.form-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.form-grid .span-2 {
  grid-column: span 2;
}

.photo-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding: 14px;
  border-radius: 12px;
  background: #f7f9fc;
  border: 1px solid #e9eff6;
}

.hidden-input {
  display: none;
}

.photo-preview {
  width: 72px;
  height: 96px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce6f0;
}

.photo-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a0acb9;
  font-size: 11px;
  border-style: dashed;
}

.photo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.field-hint {
  width: 100%;
  margin: 0;
  color: #8a97a8;
  font-size: 11px;
  line-height: 1.5;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 12px;
  color: #17375e;
  font-size: 14px;
  font-weight: 700;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.awards-head {
  margin-top: 26px;
}

.entry-card {
  margin-bottom: 14px;
  padding: 16px 16px 4px;
  border: 1px solid #e9eff6;
  border-radius: 12px;
  background: #fbfdff;
}

.entry-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #5b6b7f;
  font-size: 13px;
}

.preview-panel {
  padding: 16px;
  position: sticky;
  top: 88px;
}

.preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #17375e;
  font-size: 14px;
}

.preview-head span {
  color: #9aa7b5;
  font-size: 11px;
  font-weight: 400;
}

.preview-wrap {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  background: #e9eff6;
}

.preview-frame {
  display: block;
  border: 0;
  width: 794px;
  height: 1123px;
  transform-origin: top left;
  background: #fff;
}

.preview-hint {
  margin: 10px 0 0;
  color: #9aa7b5;
  font-size: 11px;
  line-height: 1.6;
}

.dialog-hint {
  margin: 0 0 14px;
  color: #7f8c9d;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .builder-grid {
    grid-template-columns: 1fr;
  }

  .preview-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 18px;
  }

  .builder-main {
    padding: 16px 12px 40px;
  }

  .builder-grid {
    grid-template-columns: 1fr;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-grid .span-2 {
    grid-column: span 1;
  }

  .form-head {
    flex-direction: column;
  }

  .title-field {
    width: 100%;
  }
}
</style>
