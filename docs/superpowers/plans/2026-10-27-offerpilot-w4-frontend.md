# OfferPilot W4 前端：报告页与成长记录实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现五维评分报告页和成长记录页：雷达图、维度得分、总评、优势/待提升/建议、逐题复盘、分数趋势、历史面试列表。

**Architecture:** 报告页调用 `POST /api/interviews/{id}/report`（幂等，已有报告会直接返回），用 ECharts 雷达图展示五维；成长页并行调用 `/api/interviews/history` 与 `/api/stats/overview`，用 ECharts 折线图/柱状图展示趋势和维度平均分。入口放在聊天结束页和首页顶栏。

**Tech Stack:** Vue3 + TypeScript + Element Plus + ECharts 6.1 + Vite。

---

## 0. 范围与完成定义

**完成定义：**

- 面试结束后，聊天页出现“查看报告”按钮，点击进入 `/interviews/:id/report`。
- 报告页展示：综合得分、五维雷达图、维度得分条、总评、优势、待提升、建议、逐题复盘。
- 首页顶栏出现“成长记录”，进入 `/history`。
- 成长页展示：总场次/完成场次/平均分/最高分、分数趋势折线图、五维平均柱状图、历史面试列表。
- `npm --prefix frontend run build` 通过。
- 浏览器端到端：完成面试 → 查看报告 → 成长记录，页面正常。

**本计划不做：** 报告分享、导出 PDF、异步报告任务、语音。

---

## Task 1: 安装 ECharts 与前端报告 API

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/api/interview.ts`

- [ ] **Step 1: 安装 ECharts**

Run:
```bash
cd /e/work/frontend
npm install echarts@^6.1.0
```

Expected: `package.json` 增加 `echarts`，`package-lock.json` 更新。

- [ ] **Step 2: 增加报告类型与 API**

在 `frontend/src/api/interview.ts` 末尾（`streamAnswer` 之前或之后均可）增加：
```ts
export interface ReportDimensions {
  accuracy: number
  depth: number
  project: number
  expression: number
  match: number
}

export interface QuestionReview {
  questionId: number
  seq: number
  type: string
  question: string
  answer: string
  promptType: string | null
  promptText: string | null
  correctness: string
  score: number
  comment: string
  missingPoints: string[]
}

export interface InterviewReport {
  id: number
  sessionId: number
  jobTitle: string
  mode: string
  overallScore: number
  dimensions: ReportDimensions
  summary: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  status: string
  createdAt: string
  questionReviews: QuestionReview[]
}

export interface InterviewHistoryItem {
  sessionId: number
  jobTitle: string
  mode: string
  status: string
  totalScore: number | null
  createdAt: string
  hasReport: boolean
}

export interface ScorePoint {
  date: string
  score: number
  jobTitle: string
}

export interface StatsOverview {
  totalInterviews: number
  completedInterviews: number
  averageScore: number
  bestScore: number
  dimensionAverages: ReportDimensions
  scoreTrend: ScorePoint[]
}
```

在 `interviewApi` 对象中增加：
```ts
  generateReport: (id: number) => http.post<never, InterviewReport>(`/interviews/${id}/report`),

  getReport: (id: number) => http.get<never, InterviewReport>(`/interviews/${id}/report`),

  history: () => http.get<never, InterviewHistoryItem[]>('/interviews/history'),

  stats: () => http.get<never, StatsOverview>('/stats/overview'),
```

- [ ] **Step 3: 构建验证**

Run:
```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
cd /e/work
git add frontend/package.json frontend/package-lock.json frontend/src/api/interview.ts
git commit -m "feat(frontend): add ECharts and report APIs"
```

---

## Task 2: 五维报告页

**Files:**
- Create: `frontend/src/views/InterviewReportView.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 创建报告页**

Create `frontend/src/views/InterviewReportView.vue`:
```vue
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { interviewApi, type InterviewReport } from '@/api/interview'

echarts.use([RadarChart, TooltipComponent, CanvasRenderer])

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const route = useRoute()
const router = useRouter()

const report = ref<InterviewReport | null>(null)
const loading = ref(true)
const error = ref('')
const radarEl = ref<HTMLElement | null>(null)
let radarChart: ReturnType<typeof echarts.init> | null = null

const dimensions = computed(() => {
  const d = report.value?.dimensions
  if (!d) return []
  return [
    { key: 'accuracy', label: '技术准确性', value: d.accuracy },
    { key: 'depth', label: '技术深度', value: d.depth },
    { key: 'project', label: '项目表现', value: d.project },
    { key: 'expression', label: '表达逻辑', value: d.expression },
    { key: 'match', label: '岗位匹配', value: d.match }
  ]
})

const scoreLevel = computed(() => {
  const score = report.value?.overallScore ?? 0
  if (score >= 85) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '及格'
  return '待提升'
})

const typeLabels: Record<string, string> = {
  BASIC: '基础',
  PROJECT: '项目',
  SCENARIO: '场景',
  AI: 'AI 工程'
}

onMounted(() => {
  window.addEventListener('resize', resize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  radarChart?.dispose()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    report.value = await interviewApi.generateReport(Number(route.params.id))
    await nextTick()
    renderRadar()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '报告生成失败'
  } finally {
    loading.value = false
  }
}

function renderRadar() {
  if (!radarEl.value || !report.value) return
  radarChart?.dispose()
  radarChart = echarts.init(radarEl.value)
  radarChart.setOption({
    tooltip: {},
    radar: {
      indicator: dimensions.value.map((d) => ({ name: d.label, max: 100 })),
      radius: '65%',
      axisName: { color: '#5b6b7f' },
      splitArea: { areaStyle: { color: ['#f8fbff', '#ffffff'] } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: dimensions.value.map((d) => d.value),
        name: '本场表现',
        areaStyle: { color: 'rgba(46,117,182,0.18)' },
        lineStyle: { color: '#2e75b6', width: 2 },
        itemStyle: { color: '#2e75b6' }
      }]
    }]
  })
}

function resize() {
  radarChart?.resize()
}

function correctnessLabel(value: string) {
  return ({ CORRECT: '正确', PARTIAL: '部分正确', INSUFFICIENT: '需补充', INCORRECT: '不正确' } as Record<string, string>)[value] ?? value
}

function tagType(value: string): TagType {
  if (value === 'CORRECT') return 'success'
  if (value === 'INCORRECT') return 'danger'
  return 'warning'
}

function backHome() {
  router.push('/')
}

function goHistory() {
  router.push('/history')
}
</script>

<template>
  <div class="report-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="backHome">← 返回岗位</button>
      <div class="brand">OfferPilot</div>
      <el-button link type="primary" @click="goHistory">成长记录</el-button>
    </header>

    <main v-loading="loading" class="content">
      <template v-if="report">
        <section class="hero">
          <div>
            <p class="eyebrow">INTERVIEW REPORT</p>
            <h1>{{ report.jobTitle }}</h1>
            <p class="subtitle">
              {{ report.mode === 'PRACTICE' ? '练习模式' : '模拟面试' }} ·
              {{ report.createdAt?.slice(0, 10) }}
            </p>
          </div>
          <div class="score-box">
            <span class="score">{{ report.overallScore }}</span>
            <small>{{ scoreLevel }} · 综合得分</small>
          </div>
        </section>

        <section class="report-grid">
          <div class="card radar-card">
            <h2>五维能力</h2>
            <div ref="radarEl" class="radar"></div>
          </div>
          <div class="card dimension-card">
            <h2>维度得分</h2>
            <div v-for="dimension in dimensions" :key="dimension.key" class="dimension-row">
              <span class="dimension-label">{{ dimension.label }}</span>
              <div class="bar"><i :style="{ width: dimension.value + '%' }"></i></div>
              <strong>{{ dimension.value }}</strong>
            </div>
          </div>
        </section>

        <section class="card">
          <h2>总评</h2>
          <p class="summary">{{ report.summary }}</p>
        </section>

        <section class="three-cols">
          <div class="card">
            <h2>优势</h2>
            <ul>
              <li v-for="item in report.strengths" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="card">
            <h2>待提升</h2>
            <ul>
              <li v-for="item in report.weaknesses" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="card">
            <h2>建议</h2>
            <ul>
              <li v-for="item in report.suggestions" :key="item">{{ item }}</li>
            </ul>
          </div>
        </section>

        <section class="card">
          <h2>逐题复盘</h2>
          <article
            v-for="review in report.questionReviews"
            :key="review.questionId"
            class="review-card"
          >
            <div class="review-head">
              <span class="review-seq">Q{{ review.seq }}</span>
              <el-tag size="small" effect="plain">{{ typeLabels[review.type] || review.type }}</el-tag>
              <el-tag size="small" :type="tagType(review.correctness)" effect="plain">
                {{ correctnessLabel(review.correctness) }}
              </el-tag>
              <span class="review-score">{{ review.score }}/5</span>
            </div>
            <p class="review-question">{{ review.question }}</p>
            <p class="review-answer"><strong>你的回答：</strong>{{ review.answer }}</p>
            <p class="review-comment">{{ review.comment }}</p>
            <p v-if="review.missingPoints?.length" class="review-missing">
              建议补充：{{ review.missingPoints.join('、') }}
            </p>
          </article>
          <el-empty v-if="!report.questionReviews.length" description="暂无逐题复盘" :image-size="80" />
        </section>

        <div class="actions">
          <el-button @click="backHome">返回岗位</el-button>
          <el-button type="primary" @click="goHistory">查看成长记录</el-button>
        </div>
      </template>

      <el-result v-else-if="error" icon="warning" title="报告生成失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
    </main>
  </div>
</template>

<style scoped>
.report-page {
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

.content {
  max-width: 1100px;
  margin: 0 auto;
  padding: 36px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 26px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 10px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 40px);
}

.subtitle {
  margin: 0;
  color: #7f8c9d;
  font-size: 14px;
}

.score-box {
  flex: none;
  width: 150px;
  height: 150px;
  border-radius: 32px;
  background: linear-gradient(145deg, #1f4e79, #2e75b6);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 18px 36px rgba(31, 78, 121, 0.22);
}

.score {
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
}

.score-box small {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.82;
}

.report-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
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

.radar {
  width: 100%;
  height: 360px;
}

.dimension-row {
  display: grid;
  grid-template-columns: 90px 1fr 36px;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  color: #5b6b7f;
  font-size: 14px;
}

.dimension-row strong {
  color: #17375e;
  text-align: right;
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

.summary {
  margin: 0;
  color: #4a5a6e;
  line-height: 1.9;
  font-size: 15px;
}

.three-cols {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.three-cols .card {
  margin-bottom: 20px;
}

.three-cols ul {
  margin: 0;
  padding-left: 18px;
  color: #5b6b7f;
  line-height: 1.9;
  font-size: 14px;
}

.review-card {
  padding: 18px 20px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  margin-bottom: 12px;
  background: #fbfdff;
}

.review-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.review-seq {
  color: #2e75b6;
  font-weight: 700;
  font-size: 13px;
}

.review-score {
  margin-left: auto;
  color: #8a97a8;
  font-size: 13px;
}

.review-question {
  margin: 0 0 8px;
  color: #17375e;
  font-weight: 700;
  line-height: 1.7;
}

.review-answer {
  margin: 0 0 8px;
  color: #5b6b7f;
  line-height: 1.8;
  font-size: 14px;
}

.review-comment {
  margin: 0;
  color: #4a5a6e;
  line-height: 1.8;
  font-size: 14px;
}

.review-missing {
  margin: 8px 0 0;
  color: #b88230;
  font-size: 13px;
  line-height: 1.7;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
}

@media (max-width: 860px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-grid,
  .three-cols {
    grid-template-columns: 1fr;
  }

  .score-box {
    width: 120px;
    height: 120px;
  }

  .score {
    font-size: 44px;
  }
}
</style>
```

- [ ] **Step 2: 添加路由**

在 `frontend/src/router/index.ts` 的 routes 中加入：
```ts
    {
      path: '/interviews/:id/report',
      name: 'interview-report',
      component: () => import('@/views/InterviewReportView.vue'),
      meta: { requiresAuth: true }
    },
```

- [ ] **Step 3: 构建验证**

```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。若 ECharts 类型报错，检查 `echarts/core` 的 `use` 导入和 `axisName` 配置。

- [ ] **Step 4: 提交**

```bash
cd /e/work
git add frontend/src/views/InterviewReportView.vue frontend/src/router/index.ts
git commit -m "feat(frontend): add five-dimension report page"
```

---

## Task 3: 成长记录页与入口

**Files:**
- Create: `frontend/src/views/InterviewHistoryView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/InterviewChatView.vue`
- Modify: `README.md`

- [ ] **Step 1: 创建成长记录页**

Create `frontend/src/views/InterviewHistoryView.vue`:
```vue
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { interviewApi, type InterviewHistoryItem, type StatsOverview } from '@/api/interview'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const stats = ref<StatsOverview | null>(null)
const history = ref<InterviewHistoryItem[]>([])
const loading = ref(true)
const trendEl = ref<HTMLElement | null>(null)
const dimensionEl = ref<HTMLElement | null>(null)
let trendChart: ReturnType<typeof echarts.init> | null = null
let dimensionChart: ReturnType<typeof echarts.init> | null = null

onMounted(async () => {
  window.addEventListener('resize', resize)
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  dimensionChart?.dispose()
})

async function load() {
  loading.value = true
  try {
    const [statsData, historyData] = await Promise.all([
      interviewApi.stats(),
      interviewApi.history()
    ])
    stats.value = statsData
    history.value = historyData
    await nextTick()
    renderTrend()
    renderDimensions()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendEl.value || !stats.value?.scoreTrend?.length) return
  trendChart?.dispose()
  trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: stats.value.scoreTrend.map((point) => point.date),
      axisLine: { lineStyle: { color: '#d6e4f0' } },
      axisLabel: { color: '#7f8c9d' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: '#eef4fb' } },
      axisLabel: { color: '#7f8c9d' }
    },
    series: [{
      type: 'line',
      data: stats.value.scoreTrend.map((point) => point.score),
      smooth: true,
      symbolSize: 8,
      lineStyle: { color: '#2e75b6', width: 3 },
      itemStyle: { color: '#2e75b6' },
      areaStyle: { color: 'rgba(46,117,182,0.12)' }
    }]
  })
}

function renderDimensions() {
  if (!dimensionEl.value || !stats.value) return
  const d = stats.value.dimensionAverages
  const labels = [
    ['技术准确性', d.accuracy],
    ['技术深度', d.depth],
    ['项目表现', d.project],
    ['表达逻辑', d.expression],
    ['岗位匹配', d.match]
  ] as Array<[string, number]>
  dimensionChart?.dispose()
  dimensionChart = echarts.init(dimensionEl.value)
  dimensionChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 88, right: 24, top: 12, bottom: 20 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: '#eef4fb' } },
      axisLabel: { color: '#7f8c9d' }
    },
    yAxis: {
      type: 'category',
      data: labels.map((item) => item[0]),
      axisLine: { lineStyle: { color: '#d6e4f0' } },
      axisLabel: { color: '#5b6b7f' }
    },
    series: [{
      type: 'bar',
      data: labels.map((item) => item[1]),
      barWidth: 14,
      itemStyle: { color: '#2e75b6', borderRadius: [0, 7, 7, 0] }
    }]
  })
}

function resize() {
  trendChart?.resize()
  dimensionChart?.resize()
}

function openReport(item: InterviewHistoryItem) {
  router.push(`/interviews/${item.sessionId}/report`)
}

function goHome() {
  router.push('/')
}

function modeLabel(mode: string) {
  return mode === 'PRACTICE' ? '练习' : '模拟'
}

function statusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'IN_PROGRESS') return '进行中'
  if (status === 'READY') return '待开始'
  return '已放弃'
}
</script>

<template>
  <div class="history-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <div class="brand">OfferPilot</div>
      <span class="status">成长记录</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <p class="eyebrow">GROWTH TRACKER</p>
        <h1>成长记录</h1>
        <p class="subtitle">用数据看见每一次面试的进步。</p>
      </section>

      <section v-if="stats" class="stat-grid">
        <div class="stat-card"><span>{{ stats.totalInterviews }}</span><small>总场次</small></div>
        <div class="stat-card"><span>{{ stats.completedInterviews }}</span><small>完成场次</small></div>
        <div class="stat-card"><span>{{ stats.averageScore }}</span><small>平均分</small></div>
        <div class="stat-card"><span>{{ stats.bestScore }}</span><small>最高分</small></div>
      </section>

      <section class="chart-grid">
        <div class="card">
          <h2>分数趋势</h2>
          <div
            v-if="stats?.scoreTrend?.length"
            ref="trendEl"
            class="chart"
          ></div>
          <el-empty v-else description="完成面试后这里会出现分数趋势" :image-size="80" />
        </div>
        <div class="card">
          <h2>五维平均</h2>
          <div ref="dimensionEl" class="chart"></div>
        </div>
      </section>

      <section class="card">
        <h2>面试历史</h2>
        <el-table v-if="history.length" :data="history" style="width: 100%">
          <el-table-column prop="jobTitle" label="岗位" min-width="200" />
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="总分" width="90">
            <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="130">
            <template #default="{ row }">{{ row.createdAt?.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'COMPLETED'"
                link
                type="primary"
                @click="openReport(row)"
              >
                查看报告
              </el-button>
              <span v-else class="muted">未完成</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="还没有面试记录" :image-size="100" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.history-page {
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
  padding: 36px 28px 72px;
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
  margin: 0 0 28px;
  color: #7f8c9d;
  font-size: 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 22px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #e4ecf5;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-card span {
  color: #17375e;
  font-size: 32px;
  font-weight: 800;
}

.stat-card small {
  color: #8a97a8;
  font-size: 13px;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
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

.chart {
  width: 100%;
  height: 300px;
}

.muted {
  color: #a0aab6;
  font-size: 13px;
}

@media (max-width: 860px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: 添加路由**

在 `frontend/src/router/index.ts` 中加入：
```ts
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/InterviewHistoryView.vue'),
      meta: { requiresAuth: true }
    },
```

- [ ] **Step 3: 首页顶栏加入口**

在 `frontend/src/views/HomeView.vue` 的 `.user-area` 中，退出登录按钮前加入：
```vue
        <el-button link type="primary" @click="router.push('/history')">成长记录</el-button>
```

- [ ] **Step 4: 聊天结束页加入口**

在 `frontend/src/views/InterviewChatView.vue` 的完成页中：
将：
```vue
        <el-result icon="success" title="面试已结束" sub-title="评分报告与薄弱点分析将在 W4 开放">
          <template #extra>
            <el-button type="primary" @click="router.push('/')">返回岗位</el-button>
          </template>
        </el-result>
```
改为：
```vue
        <el-result icon="success" title="面试已结束" sub-title="查看五维评分报告，复盘本场表现。">
          <template #extra>
            <el-button
              type="primary"
              @click="router.push(`/interviews/${route.params.id}/report`)"
            >
              查看报告
            </el-button>
            <el-button @click="router.push('/')">返回岗位</el-button>
          </template>
        </el-result>
```

- [ ] **Step 5: 构建验证**

```bash
cd /e/work/frontend
npm run build
```

Expected: 构建成功。

- [ ] **Step 6: 浏览器端验收**

启动后端（MySQL）与前端后：

1. 注册/登录，创建一场面试（模拟面试或练习模式均可），上传简历，开始答题。
2. 回答一道较长的问题，点击“结束面试”并确认。
3. 点击“查看报告”，应看到：
   - 综合得分
   - 五维雷达图
   - 维度得分条
   - 总评、优势、待提升、建议
   - 逐题复盘
4. 点击“成长记录”，应看到：
   - 总场次、完成场次、平均分、最高分
   - 分数趋势折线图
   - 五维平均柱状图
   - 历史面试列表，可点击“查看报告”

- [ ] **Step 7: 更新 README**

将“当前进度”改为：
```markdown
当前进度：P0 Week 4 已完成（五维评分报告、逐题复盘、成长记录与统计图表）。

下一步：P0 Week 5（题库 RAG、薄弱点推荐、运营后台、额度限流）。
```

在 API 表格中追加：
```markdown
| POST | `/api/interviews/{id}/report` | 生成/获取五维报告 |
| GET | `/api/interviews/{id}/report` | 查询五维报告 |
| GET | `/api/interviews/history` | 面试历史 |
| GET | `/api/stats/overview` | 成长统计 |
```

- [ ] **Step 8: 提交**

```bash
cd /e/work
git add frontend/src/views/InterviewHistoryView.vue frontend/src/router/index.ts \
  frontend/src/views/HomeView.vue frontend/src/views/InterviewChatView.vue README.md
git commit -m "feat(frontend): add growth history page and report entries"
```

---

## 自检结果

- **Spec coverage:** 报告页、雷达图、维度条、逐题复盘、成长页、趋势图、历史列表、入口均已覆盖。
- **Placeholder scan:** 没有 TBD/TODO；代码完整。
- **Type consistency:** `InterviewReport`、`QuestionReview`、`InterviewHistoryItem`、`StatsOverview` 与后端 DTO 字段一致；ECharts 使用 `echarts/core` 按需引入。
- **默认可用性:** 报告页直接调用幂等的 `POST /report`，练习模式和模拟面试模式都可用。

---

## 执行记录（2026-10-27）

- 分支：`feat/p0-week4-report`（基于 `feat/practice-evaluation`）。
- ECharts 6.1.0，按需引入 RadarChart / LineChart / BarChart / CanvasRenderer。
- 前端构建：`npm --prefix frontend run build` → 成功。
- 浏览器端到端（Playwright + Chrome）：
  - 完成一场 30 分钟模拟面试 → 点击“查看报告”。
  - 报告页显示：综合得分 74、五维雷达、维度得分条、总评、优势/待提升/建议、1 条逐题复盘。
  - 点击“成长记录” → 成长页显示 4 个统计卡、分数趋势图、五维平均柱状图、1 条历史记录。
- 说明：ECharts 默认动画约 1 秒，截图需等待动画结束；功能本身正常。
- 待推送：前端提交在本地，`feat/p0-week4-report` 远程分支已包含后端提交。
