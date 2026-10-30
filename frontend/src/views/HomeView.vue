<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { jobApi, type JobTemplate } from '@/api/job'
import type { InterviewSession } from '@/api/interview'
import InterviewSetupDialog from '@/components/InterviewSetupDialog.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const jobs = ref<JobTemplate[]>([])
const loading = ref(true)
const setupVisible = ref(false)
const selectedJob = ref<JobTemplate | null>(null)
const customSetup = ref(false)
const activeRoleFamily = ref('ALL')

const ROLE_FAMILY_LABELS: Record<string, string> = {
  BACKEND: '后端',
  FRONTEND: '前端',
  FULLSTACK: '全栈',
  AI_APP: 'AI 应用',
  ALGORITHM: '算法',
  TEST: '测试',
  SALES: '销售'
}

const roleFamilyOptions = computed(() => {
  const values = Array.from(new Set(jobs.value.map((job) => job.roleFamily).filter(Boolean))) as string[]
  if (!values.includes('FRONTEND')) {
    values.push('FRONTEND')
  }
  return [
    { value: 'ALL', label: '全部' },
    ...values.map((value) => ({
      value,
      label: ROLE_FAMILY_LABELS[value] ?? value
    }))
  ]
})

const frontendComingSoon = {
  title: '前端',
  category: '前端',
  jdText: 'JavaScript/TypeScript、Vue/React、CSS、浏览器与工程化。前端题库正在建设中，Java 题库完善后开放。',
  primaryLanguage: 'JS_TS',
  roleFamily: 'FRONTEND',
  questionCount: 7
}

const hasFrontendJob = computed(() => jobs.value.some((job) => job.roleFamily === 'FRONTEND'))

const showFrontendPlaceholder = computed(() =>
  !hasFrontendJob.value
  && (activeRoleFamily.value === 'ALL' || activeRoleFamily.value === 'FRONTEND')
)

const templateCount = computed(() =>
  jobs.value.length === 0 ? 4 : jobs.value.length + (hasFrontendJob.value ? 0 : 1)
)

const filteredJobs = computed(() =>
  jobs.value.filter(
    (job) => activeRoleFamily.value === 'ALL' || job.roleFamily === activeRoleFamily.value
  )
)

const emptyDescription = computed(() =>
  jobs.value.length === 0 ? '暂无岗位模板' : '该方向暂无岗位，试试其他方向'
)

onMounted(async () => {
  try {
    if (!auth.user) {
      await auth.fetchMe()
    }
    jobs.value = await jobApi.list()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
})

async function handleLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}

function startInterview(job: JobTemplate) {
  selectedJob.value = job
  customSetup.value = false
  setupVisible.value = true
}

function openSalesSetup() {
  selectedJob.value = null
  customSetup.value = true
  setupVisible.value = true
}

function handleInterviewCreated(session: InterviewSession) {
  setupVisible.value = false
  router.push(`/interviews/${session.id}`)
}

function languageLabel(job: JobTemplate): string {
  if (job.primaryLanguage === 'NONE') return ''
  const labels: Record<string, string> = {
    JAVA: 'Java',
    JS_TS: 'JavaScript/TypeScript',
    PYTHON: 'Python',
    GO: 'Go',
    CPP: 'C++'
  }
  return job.primaryLanguage ? labels[job.primaryLanguage] ?? job.primaryLanguage : ''
}

function focusTags(job: JobTemplate): string[] {
  if (job.trackCode === 'sales' || job.category === '销售岗位') {
    return ['客户开拓', '需求挖掘', '谈判成交']
  }
  if (job.trackCode === 'frontend' || job.roleFamily === 'FRONTEND') {
    return ['JS/TS', 'Vue/React', '浏览器']
  }
  if (job.trackCode === 'java-fullstack-junior') {
    return ['Java 后端', 'Vue/React', '接口联调']
  }
  if (job.trackCode === 'java-ai') {
    return ['RAG', 'Agent', '提示词']
  }
  if (job.trackCode === 'java-microservices') {
    return ['Nacos', 'Gateway', '分布式']
  }
  if (job.category.includes('AI')) {
    return ['RAG', 'Agent', '提示词']
  }
  if (job.category.includes('微服务')) {
    return ['Nacos', 'Gateway', '分布式']
  }
  return ['基础八股', '项目深挖', '场景设计']
}

</script>

<template>
  <div class="home-page">
    <header class="topbar">
      <div class="brand">OfferPilot</div>
      <div class="user-area">
        <span class="user-name">{{ auth.user?.nickname || '同学' }}</span>
        <el-button
          v-if="auth.user?.role === 'ADMIN'"
          link
          type="primary"
          @click="router.push('/admin/questions')"
        >
          题库管理
        </el-button>
        <el-button link type="primary" @click="router.push('/history')">成长记录</el-button>
        <el-button link type="primary" @click="router.push('/settings')">API 设置</el-button>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">P0 · WEEK 2</p>
          <h1>选择你的目标岗位</h1>
          <p class="subtitle">
            AI 将根据岗位与简历生成一场带追问的模拟面试。先选一个方向，开始训练。
          </p>
        </div>
        <div class="hero-badge">
          <span>{{ templateCount }}</span>
          <small>岗位模板</small>
        </div>
      </section>

      <section v-if="!loading && jobs.length > 0" class="filters">
        <div class="filter-group">
          <span class="filter-label">方向</span>
          <button
            v-for="option in roleFamilyOptions"
            :key="option.value"
            type="button"
            class="filter-chip"
            :class="{ active: activeRoleFamily === option.value }"
            @click="activeRoleFamily = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </section>

      <section v-if="!loading" class="sales-entry">
        <div>
          <p class="eyebrow">SALES INTERVIEW</p>
          <h2>销售岗面试</h2>
          <p>
            粘贴销售 JD，系统结合简历和 50 道销售题生成面试；AI 可用时个性化出题，
            不可用时自动使用题库模式。
          </p>
        </div>
        <el-button type="primary" size="large" @click="openSalesSetup">
          开始销售岗面试
        </el-button>
      </section>

      <el-skeleton v-if="loading" :rows="5" animated />

      <section v-else class="job-grid">
        <article v-for="job in filteredJobs" :key="job.id" class="job-card">
          <div class="job-meta">
            <span v-if="languageLabel(job)">{{ languageLabel(job) }}</span>
            <span>{{ job.category }}</span>
          </div>
          <h2>{{ job.title }}</h2>
          <p class="job-desc">{{ job.jdText }}</p>
          <div class="focus-row">
            <span v-for="tag in focusTags(job)" :key="tag">{{ tag }}</span>
          </div>
          <p class="job-facts">
            {{ job.questionCount }} 道题 · 20–30 分钟 · 校招 / 1-3 年 / 3-5 年
          </p>
          <div class="card-footer">
            <el-button type="primary" @click="startInterview(job)">开始面试</el-button>
            <span class="coming">约 1 分钟</span>
          </div>
        </article>

        <article v-if="showFrontendPlaceholder" class="job-card job-card--coming">
          <div class="job-meta">
            <span>JavaScript/TypeScript</span>
            <span>前端</span>
          </div>
          <h2>{{ frontendComingSoon.title }}</h2>
          <p class="job-desc">{{ frontendComingSoon.jdText }}</p>
          <div class="focus-row">
            <span>JS/TS</span>
            <span>Vue/React</span>
            <span>CSS</span>
          </div>
          <p class="job-facts">
            {{ frontendComingSoon.questionCount }} 道题 · 题库建设中 · 校招 / 1-3 年 / 3-5 年
          </p>
          <div class="card-footer">
            <el-button type="primary" disabled>筹备中</el-button>
            <span class="coming">Java 题库完善后开放</span>
          </div>
        </article>
      </section>

      <el-empty
        v-if="!loading && filteredJobs.length === 0 && !showFrontendPlaceholder"
        :description="emptyDescription"
      />
    </main>

    <InterviewSetupDialog
      v-model="setupVisible"
      :job="selectedJob"
      :custom="customSetup"
      @created="handleInterviewCreated"
    />
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 20px;
  font-weight: 800;
  color: #17375e;
  letter-spacing: 0.04em;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  color: #5b6b7f;
  font-size: 14px;
}

.content {
  width: 100%;
  min-height: calc(100vh - 68px);
  padding: 20px 32px 32px;
  display: flex;
  flex-direction: column;
}

.hero {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero h1 {
  margin: 0 0 10px;
  color: #17375e;
  font-size: clamp(30px, 3.2vw, 42px);
  line-height: 1.15;
}

.subtitle {
  margin: 0;
  max-width: 720px;
  color: #6b7a8c;
  font-size: 15px;
  line-height: 1.8;
}

.hero-badge {
  flex: none;
  width: 96px;
  height: 96px;
  border-radius: 22px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(31, 78, 121, 0.22);
}

.hero-badge span {
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
}

.hero-badge small {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 28px;
  margin: 0 0 18px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  color: #8a97a8;
  font-size: 12px;
}

.filter-chip {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #dbe6f2;
  background: #fff;
  color: #5b6b7f;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.filter-chip:hover {
  border-color: #2e75b6;
  color: #2e75b6;
}

.filter-chip.active {
  border-color: #1f4e79;
  background: #1f4e79;
  color: #fff;
}

.sales-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin: 0 0 24px;
  padding: 24px 28px;
  border-radius: 18px;
  border: 1px solid #d9e7f4;
  background: linear-gradient(135deg, #f2f8ff 0%, #eef8f5 100%);
}

.sales-entry h2 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: 22px;
}

.sales-entry p {
  margin: 0;
  max-width: 720px;
  color: #5b6b7f;
  font-size: 14px;
  line-height: 1.7;
}

.sales-entry .eyebrow {
  margin: 0 0 8px;
}

.sales-entry :deep(.el-button--primary) {
  flex: none;
  height: 44px;
  padding: 0 24px;
  border-radius: 10px;
  background: #1f4e79;
  border-color: #1f4e79;
}

.job-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: none;
  grid-auto-rows: minmax(320px, auto);
  gap: 20px;
}

.job-card {
  position: relative;
  height: 100%;
  min-height: 0;
  padding: 30px 34px;
  border-radius: 24px;
  border: 1px solid #e4ecf5;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.94));
  box-shadow: 0 10px 30px rgba(31, 78, 121, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.job-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 6px;
  background: var(--accent);
  z-index: 0;
}

.job-card::after {
  content: '';
  position: absolute;
  right: -90px;
  bottom: -110px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: var(--accent-soft);
  z-index: 0;
}

.job-card > * {
  position: relative;
  z-index: 1;
}

.job-body {
  flex: 1;
  min-height: 0;
  max-width: 920px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.focus-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.focus-row span {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--accent);
  background: #fff;
  color: var(--accent);
  font-size: 12px;
}

.job-facts {
  margin: 14px 0 0;
  color: #8a97a8;
  font-size: 12px;
  letter-spacing: 0.02em;
}

.sample-block {
  max-width: 780px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #e4ecf5;
}

.sample-label {
  margin: 0 0 10px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.sample-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 7px;
}

.sample-list li {
  position: relative;
  padding-left: 16px;
  color: #5b6b7f;
  font-size: 13px;
  line-height: 1.6;
}

.sample-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.6;
}

.job-card:nth-child(1) {
  --accent: #2e75b6;
  --accent-soft: rgba(46, 117, 182, 0.1);
}

.job-card:nth-child(2) {
  --accent: #2f9e8f;
  --accent-soft: rgba(47, 158, 143, 0.1);
}

.job-card:nth-child(3) {
  --accent: #c55a11;
  --accent-soft: rgba(197, 90, 17, 0.1);
}

.job-card:nth-child(4) {
  --accent: #6c5ce7;
  --accent-soft: rgba(108, 92, 231, 0.1);
}

.job-card:nth-child(5) {
  --accent: #b23a48;
  --accent-soft: rgba(178, 58, 72, 0.1);
}

.job-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: 0 20px 44px rgba(31, 78, 121, 0.13);
}

.job-card.job-card--coming {
  --accent: #7f8c9d;
  --accent-soft: rgba(127, 140, 157, 0.1);
}

.job-card.job-card--coming:hover {
  transform: none;
  border-color: #e4ecf5;
  box-shadow: 0 10px 30px rgba(31, 78, 121, 0.06);
}

.card-index {
  position: absolute;
  top: 24px;
  right: 30px;
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  color: var(--accent);
  opacity: 0.12;
  pointer-events: none;
}

.job-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding-right: 80px;
}

.job-meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
}

.job-card h2 {
  max-width: 780px;
  margin: 0 0 14px;
  padding-right: 60px;
  color: #17375e;
  font-size: clamp(24px, 2vw, 32px);
  line-height: 1.3;
}

.job-desc {
  max-width: 780px;
  margin: 0;
  color: #6b7a8c;
  font-size: 16px;
  line-height: 1.9;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: none;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 24px;
}

.job-card :deep(.el-button--primary) {
  height: 46px;
  padding: 0 28px;
  border-radius: 12px;
  background: var(--accent);
  border-color: var(--accent);
  font-size: 15px;
}

.job-card :deep(.el-button--primary:hover) {
  background: var(--accent);
  border-color: var(--accent);
  opacity: 0.9;
}

.coming {
  color: #a0aab6;
  font-size: 12px;
}

@media (max-width: 1000px) {
  .content {
    padding: 20px 24px 32px;
  }

  .job-card {
    padding: 24px;
  }

  .card-index {
    font-size: 52px;
    top: 20px;
    right: 22px;
  }
}

@media (max-height: 820px) and (min-width: 761px) {
  .content {
    min-height: auto;
  }

  .job-card {
    min-height: 300px;
  }
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 20px;
  }

  .content {
    min-height: auto;
    padding: 24px 18px 44px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-badge {
    display: none;
  }

  .job-grid {
    flex: none;
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }

  .job-card {
    height: auto;
    min-height: 300px;
    padding: 26px 22px;
  }

  .job-body {
    justify-content: flex-start;
  }

  .job-card h2 {
    padding-right: 50px;
  }

  .job-desc {
    -webkit-line-clamp: 4;
  }
}
/* Compact card layout: many positions can fit on one screen. */
.content {
  max-width: 1180px;
  min-height: auto;
  margin: 0 auto;
  padding: 40px 32px 80px;
  display: block;
}

.hero {
  align-items: flex-end;
  margin-bottom: 24px;
}

.job-grid {
  flex: none;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  grid-template-rows: none;
  grid-auto-rows: auto;
  gap: 20px;
}

.job-card {
  position: relative;
  height: auto;
  min-height: 230px;
  padding: 24px;
  border-radius: 18px;
  overflow: visible;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e4ecf5;
  box-shadow: none;
  display: flex;
  flex-direction: column;
}

.job-card::before,
.job-card::after {
  display: none;
}

.job-card:hover {
  transform: translateY(-3px);
  border-color: #d5e3f2;
  box-shadow: 0 16px 34px rgba(31, 78, 121, 0.1);
}

.job-meta {
  padding-right: 0;
  margin-bottom: 12px;
}

.job-meta span {
  background: #eef4fb;
  color: #2e75b6;
}

.job-card h2 {
  margin: 0 0 10px;
  padding-right: 0;
  font-size: 22px;
  line-height: 1.35;
}

.job-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  -webkit-line-clamp: 3;
}

.focus-row {
  margin-top: 14px;
  gap: 6px;
}

.focus-row span {
  padding: 3px 9px;
  font-size: 11px;
}

.job-facts {
  margin: 12px 0 0;
  font-size: 12px;
}

.card-footer {
  margin-top: auto;
  padding-top: 18px;
}

.job-card :deep(.el-button--primary) {
  height: 38px;
  padding: 0 18px;
  border-radius: 10px;
  background: #1f4e79;
  border-color: #1f4e79;
  font-size: 14px;
}

.job-card :deep(.el-button--primary:hover) {
  background: #1f4e79;
  border-color: #1f4e79;
}

.job-card.job-card--coming {
  background: #fafbfd;
  border-style: dashed;
}

.job-card.job-card--coming h2 {
  color: #5b6b7f;
}

.job-card.job-card--coming :deep(.el-button--primary) {
  background: #e8eef5;
  border-color: #e8eef5;
  color: #7f8c9d;
}

@media (max-width: 760px) {
  .content {
    padding: 24px 18px 44px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
    margin-bottom: 20px;
  }

  .job-grid {
    grid-template-columns: 1fr;
  }

  .job-card {
    min-height: 210px;
  }

  .sales-entry {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px;
  }
}


</style>
