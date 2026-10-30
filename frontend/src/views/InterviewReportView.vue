<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
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
let pollTimer: number | null = null

const dimensions = computed(() => {
  const scores = report.value?.dimensions ?? {}
  const labels = report.value?.dimensionLabels ?? {}
  return Object.entries(scores).map(([key, value]) => ({
    key,
    label: labels[key] ?? key,
    value
  }))
})

const resumeParts = computed(() => {
  const assessment = report.value?.resumeAssessment
  if (!assessment) return []
  const weights = assessment.weights ?? {}
  return [
    { key: 'education', label: '学历', score: assessment.educationScore, weight: weights.education ?? 25 },
    { key: 'skills', label: '相关技能', score: assessment.skillsScore, weight: weights.skills ?? 25 },
    {
      key: 'experience',
      label: '实习/工作经历',
      score: assessment.experienceScore,
      weight: weights.experience ?? 40
    },
    { key: 'layout', label: '排版布局', score: assessment.layoutScore, weight: weights.layout ?? 10 }
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
  AI: 'AI 工程',
  BEHAVIORAL: '行为题',
  ROLE_PLAY: '角色扮演',
  MOTIVATION: '动机题'
}

onMounted(() => {
  window.addEventListener('resize', resize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  clearPoll()
  radarChart?.dispose()
})

async function load() {
  loading.value = true
  error.value = ''
  clearPoll()
  try {
    const current = await interviewApi.generateReport(Number(route.params.id))
    applyReport(current)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '报告生成失败'
    loading.value = false
  }
}

function applyReport(current: InterviewReport) {
  report.value = current
  if (current.status === 'GENERATING') {
    loading.value = true
    pollTimer = window.setTimeout(pollReport, 1500)
    return
  }
  if (current.status === 'FAILED') {
    report.value = null
    error.value = current.summary || '报告生成失败，请重试'
    loading.value = false
    return
  }
  loading.value = false
  nextTick().then(renderRadar)
}

async function pollReport() {
  try {
    const current = await interviewApi.getReport(Number(route.params.id))
    applyReport(current)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '报告生成失败'
    loading.value = false
  }
}

function clearPoll() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
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
  return ({
    CORRECT: '正确',
    PARTIAL: '部分正确',
    INSUFFICIENT: '需补充',
    INCORRECT: '不正确'
  } as Record<string, string>)[value] ?? value
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
      <BrandBlock />
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
              <el-tag
                v-if="report.generationMode === 'BANK'"
                type="info"
                size="small"
                style="margin-left: 8px"
              >
                题库模式
              </el-tag>
              <el-tag
                v-else-if="report.generationMode === 'AI'"
                type="success"
                size="small"
                style="margin-left: 8px"
              >
                AI 模式
              </el-tag>
            </p>
          </div>
          <div class="score-box">
            <span class="score">{{ report.overallScore }}</span>
            <small>{{ scoreLevel }} · 综合得分</small>
          </div>
        </section>

        <section class="report-grid">
          <div class="card radar-card">
            <h2>能力维度</h2>
            <div ref="radarEl" class="radar"></div>
          </div>
          <div class="card dimension-card">
            <h2>维度得分</h2>
            <div v-for="dimension in dimensions" :key="dimension.key" class="dimension-row">
              <span class="dimension-label">{{ dimension.label }}</span>
              <div class="bar"><i :style="{ width: dimension.value + '%' }"></i></div>
              <strong>{{ dimension.value }}</strong>
            </div>

            <div v-if="resumeParts.length" class="resume-assessment">
              <div class="resume-title">
                <span>简历评估构成</span>
                <small>
                  <el-tag
                    size="small"
                    :type="report.resumeAssessment?.aiGenerated ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ report.resumeAssessment?.aiGenerated ? 'AI 结合岗位评估' : '规则评估' }}
                  </el-tag>
                  加权得分 {{ report.resumeAssessment?.score }}
                </small>
              </div>
              <p v-if="report.resumeAssessment?.comment" class="resume-comment">
                {{ report.resumeAssessment.comment }}
              </p>
              <p v-if="report.resumeAssessment && !report.resumeAssessment.aiGenerated" class="resume-hint">
                当前使用规则评估；在 API 设置中配置可用 Key 后，新报告会由 AI 结合目标岗位重新评分。
              </p>
              <div v-for="part in resumeParts" :key="part.key" class="resume-row">
                <span class="resume-label">
                  {{ part.label }}<em>{{ part.weight }}%</em>
                </span>
                <div class="bar"><i :style="{ width: part.score + '%' }"></i></div>
                <strong>{{ part.score }}</strong>
              </div>
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

.resume-assessment {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #e4ecf5;
}

.resume-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
  color: #17375e;
  font-size: 14px;
  font-weight: 700;
}

.resume-title small {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8a97a8;
  font-size: 12px;
  font-weight: 400;
}

.resume-comment {
  margin: 0 0 14px;
  color: #6b7a8c;
  font-size: 13px;
  line-height: 1.7;
}

.resume-hint {
  margin: -6px 0 14px;
  color: #b88230;
  font-size: 12px;
  line-height: 1.6;
}

.resume-row {
  display: grid;
  grid-template-columns: 110px 1fr 36px;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: #5b6b7f;
  font-size: 13px;
}

.resume-label em {
  margin-left: 6px;
  color: #a0aab6;
  font-size: 11px;
  font-style: normal;
}

.resume-row strong {
  color: #17375e;
  text-align: right;
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
