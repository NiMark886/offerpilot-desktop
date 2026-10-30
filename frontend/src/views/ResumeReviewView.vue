<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { jobApi, type JobTemplate } from '@/api/job'
import {
  resumeApi,
  type ResumeEvaluation,
  type ResumeIssue,
  type ResumeView
} from '@/api/resume'

const router = useRouter()
const resumes = ref<ResumeView[]>([])
const jobs = ref<JobTemplate[]>([])
const loading = ref(true)
const uploading = ref(false)
const deleting = ref(false)
const evaluating = ref(false)
const result = ref<ResumeEvaluation | null>(null)
const resultEl = ref<HTMLElement | null>(null)

const form = reactive({
  resumeId: undefined as number | undefined,
  jobTemplateId: undefined as number | undefined,
  jdText: ''
})

const selectedResume = computed(() =>
  resumes.value.find((resume) => resume.id === form.resumeId)
)

const dimensions = computed(() => {
  const assessment = result.value?.assessment
  if (!assessment) return []
  return [
    { key: 'education', label: '学历', score: assessment.educationScore, weight: assessment.weights.education ?? 25 },
    { key: 'skills', label: '相关技能', score: assessment.skillsScore, weight: assessment.weights.skills ?? 25 },
    {
      key: 'experience',
      label: '实习/工作经历',
      score: assessment.experienceScore,
      weight: assessment.weights.experience ?? 40
    },
    { key: 'layout', label: '排版布局', score: assessment.layoutScore, weight: assessment.weights.layout ?? 10 }
  ]
})

const issues = computed<ResumeIssue[]>(() => result.value?.assessment.issues ?? [])

const severityLabels: Record<string, string> = {
  HIGH: '高优先级',
  MEDIUM: '中优先级',
  LOW: '低优先级'
}

const severityTypes: Record<string, 'danger' | 'warning' | 'info'> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'info'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [resumeList, jobList] = await Promise.all([resumeApi.list(), jobApi.list()])
    resumes.value = resumeList
    jobs.value = jobList
    if (!form.resumeId && resumeList.length) {
      form.resumeId = resumeList[0].id
    }
  } catch {
    // axios 拦截器统一提示
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
    if (result.value?.resumeId === resume.id) {
      result.value = null
    }
    form.resumeId = resumes.value[0]?.id
    ElMessage.success('简历已删除')
  } catch {
    // axios 拦截器统一提示
  } finally {
    deleting.value = false
  }
}

async function evaluate() {
  if (!form.resumeId) {
    ElMessage.warning('请先上传或选择一份简历')
    return
  }
  evaluating.value = true
  try {
    result.value = await resumeApi.evaluate(form.resumeId, {
      jobTemplateId: form.jobTemplateId,
      jdText: form.jobTemplateId ? undefined : form.jdText.trim() || undefined
    })
    await nextTick()
    resultEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch {
    // axios 拦截器统一提示
  } finally {
    evaluating.value = false
  }
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="resume-review-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <BrandBlock />
      <span class="status">简历评估</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <p class="eyebrow">RESUME REVIEW</p>
        <h1>简历评估</h1>
        <p class="subtitle">
          单独评估一份简历，按目标岗位给出学历、相关技能、实习/工作经历和排版布局评分，
          并指出具体缺陷和修改建议。
        </p>
      </section>

      <section class="card setup-card">
        <div class="setup-grid">
          <div class="field-block">
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
              v-if="!resumes.length"
              description="还没有简历，先上传一份"
              :image-size="72"
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
            <div v-if="selectedResume?.profile" class="resume-hint">
              {{ selectedResume.profile.name || '候选人' }}
              <span v-if="selectedResume.profile.yearsOfExperience">
                · {{ selectedResume.profile.yearsOfExperience }} 年经验
              </span>
              <span v-if="selectedResume.profile.skills?.length">
                · {{ selectedResume.profile.skills.slice(0, 4).join(' / ') }}
              </span>
            </div>
          </div>

          <div class="field-block">
            <div class="field-title"><span>目标岗位</span></div>
            <el-select
              v-model="form.jobTemplateId"
              clearable
              placeholder="可选：选择岗位模板"
              style="width: 100%"
            >
              <el-option
                v-for="job in jobs"
                :key="job.id"
                :label="job.title"
                :value="job.id"
              />
            </el-select>
            <p class="field-hint">不选岗位也可以做通用简历评估；选择岗位后会按对应 JD 评估匹配度。</p>
            <div class="field-title" style="margin-top: 14px"><span>岗位 JD</span></div>
            <el-input
              v-model="form.jdText"
              type="textarea"
              :rows="3"
              maxlength="8000"
              placeholder="可选：粘贴目标岗位 JD；如果上面已选岗位，以岗位模板的 JD 为准"
              :disabled="Boolean(form.jobTemplateId)"
            />
          </div>
        </div>

        <div class="setup-actions">
          <span class="muted">评估会读取简历文本和结构，不会修改原文件。</span>
          <el-button type="primary" size="large" :loading="evaluating" @click="evaluate">
            {{ evaluating ? 'AI 评估中…' : '开始评估' }}
          </el-button>
        </div>
      </section>

      <template v-if="result">
        <section ref="resultEl" class="card result-card">
          <div class="result-head">
            <div class="score-box">
              <span class="score">{{ result.assessment.score }}</span>
              <small>简历评估分</small>
            </div>
            <div class="result-meta">
              <h2>{{ result.fileName }}</h2>
              <p>{{ result.jobTitle }}</p>
              <div class="result-tags">
                <el-tag
                  :type="result.assessment.aiGenerated ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ result.assessment.aiGenerated ? 'AI 结合岗位评估' : '规则评估' }}
                </el-tag>
                <el-tag type="info" effect="plain">排版布局为结构检测</el-tag>
              </div>
              <p class="comment">{{ result.assessment.comment }}</p>
            </div>
          </div>
        </section>

        <section class="card">
          <h2>四维评分</h2>
          <div class="dimension-grid">
            <div v-for="dimension in dimensions" :key="dimension.key" class="dimension-row">
              <div class="dimension-head">
                <span>{{ dimension.label }}<em>{{ dimension.weight }}%</em></span>
                <strong>{{ dimension.score }}</strong>
              </div>
              <div class="bar"><i :style="{ width: dimension.score + '%' }"></i></div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="issues-head">
            <h2>具体缺陷</h2>
            <span class="muted">共 {{ issues.length }} 条，按优先级排序</span>
          </div>

          <div v-if="issues.length" class="issue-list">
            <article v-for="(issue, index) in issues" :key="issue.title + index" class="issue-card">
              <div class="issue-head">
                <el-tag size="small" effect="plain">{{ issue.category }}</el-tag>
                <el-tag
                  size="small"
                  :type="severityTypes[issue.severity] || 'info'"
                  effect="plain"
                >
                  {{ severityLabels[issue.severity] || issue.severity }}
                </el-tag>
                <strong>{{ issue.title }}</strong>
              </div>
              <p class="issue-detail">{{ issue.detail }}</p>
              <p v-if="issue.evidence" class="issue-evidence">证据：{{ issue.evidence }}</p>
              <p v-if="issue.suggestion" class="issue-suggestion">
                修改建议：{{ issue.suggestion }}
              </p>
            </article>
          </div>
          <el-empty v-else description="暂未发现明显缺陷" :image-size="80" />
        </section>
      </template>

      <el-empty
        v-else-if="!loading"
        description="点击“开始评估”后，结果会显示在这里"
        :image-size="90"
      />
    </main>
  </div>
</template>

<style scoped>
.resume-review-page {
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
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px 28px 72px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 40px);
}

.subtitle {
  margin: 0 0 26px;
  max-width: 780px;
  color: #7f8c9d;
  font-size: 14px;
  line-height: 1.8;
}

.card {
  background: #fff;
  border: 1px solid #e4ecf5;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
}

.card h2 {
  margin: 0 0 18px;
  color: #17375e;
  font-size: 19px;
}

.setup-grid {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 28px;
}

.field-block {
  min-width: 0;
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

.field-hint {
  margin: 8px 0 0;
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.6;
}

.resume-hint {
  margin-top: 10px;
  color: #6b7a8c;
  font-size: 13px;
  line-height: 1.6;
}

.setup-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid #eef4fb;
}

.muted {
  color: #8a97a8;
  font-size: 12px;
}

.result-head {
  display: flex;
  align-items: center;
  gap: 24px;
}

.score-box {
  flex: none;
  width: 126px;
  height: 126px;
  border-radius: 28px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 32px rgba(31, 78, 121, 0.2);
}

.score {
  font-size: 48px;
  font-weight: 800;
  line-height: 1;
}

.score-box small {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.85;
}

.result-meta {
  min-width: 0;
}

.result-meta h2 {
  margin: 0 0 6px;
  font-size: 21px;
}

.result-meta > p {
  margin: 0;
  color: #7f8c9d;
  font-size: 13px;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}

.comment {
  color: #4a5a6e !important;
  line-height: 1.8 !important;
}

.dimension-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 36px;
}

.dimension-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #5b6b7f;
  font-size: 14px;
}

.dimension-head em {
  margin-left: 6px;
  color: #a0aab6;
  font-size: 11px;
  font-style: normal;
}

.dimension-head strong {
  color: #17375e;
}

.bar {
  height: 8px;
  border-radius: 999px;
  background: #eef4fb;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #2e75b6, #67a9e8);
}

.issues-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.issues-head h2 {
  margin: 0;
}

.issue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.issue-card {
  padding: 18px 20px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #fbfdff;
}

.issue-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  color: #17375e;
}

.issue-head strong {
  font-size: 15px;
}

.issue-detail {
  margin: 0 0 8px;
  color: #4a5a6e;
  font-size: 14px;
  line-height: 1.8;
}

.issue-evidence {
  margin: 0 0 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f4f7fb;
  color: #7f8c9d;
  font-size: 12px;
  line-height: 1.7;
}

.issue-suggestion {
  margin: 0;
  color: #b88230;
  font-size: 13px;
  line-height: 1.8;
}

@media (max-width: 860px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 28px 16px 56px;
  }

  .setup-grid,
  .dimension-grid {
    grid-template-columns: 1fr;
  }

  .result-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .setup-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
