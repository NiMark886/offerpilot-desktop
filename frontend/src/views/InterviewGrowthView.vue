<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { interviewApi, type GrowthJob } from '@/api/interview'

const router = useRouter()
const jobs = ref<GrowthJob[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    jobs.value = await interviewApi.jobs()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
})

function openJob(job: GrowthJob) {
  router.push(`/history/${job.key}`)
}

function goHome() {
  router.push('/')
}

function formatDate(value: string | null) {
  return value ? value.slice(0, 10) : ''
}
</script>

<template>
  <div class="growth-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <BrandBlock />
      <span class="status">成长记录</span>
    </header>

    <main v-loading="loading" class="content">
      <section class="hero">
        <p class="eyebrow">GROWTH TRACKER</p>
        <h1>成长记录</h1>
        <p class="subtitle">
          每个岗位独立记录。选择你面试过的岗位，查看它的分数趋势、能力变化和薄弱点。
        </p>
      </section>

      <section v-if="jobs.length" class="growth-grid">
        <article
          v-for="job in jobs"
          :key="job.key"
          class="growth-card"
          role="button"
          tabindex="0"
          @click="openJob(job)"
          @keydown.enter="openJob(job)"
        >
          <div class="card-head">
            <el-tag size="small" effect="plain">{{ job.category || job.trackName || '岗位' }}</el-tag>
            <span v-if="job.lastInterviewAt" class="last-date">
              最近 {{ formatDate(job.lastInterviewAt) }}
            </span>
          </div>
          <h2>{{ job.jobTitle }}</h2>
          <p v-if="job.trackName && job.trackName !== job.jobTitle" class="track-name">
            {{ job.trackName }}
          </p>
          <div class="metrics">
            <div>
              <strong>{{ job.completedInterviews }}</strong>
              <small>完成场次</small>
            </div>
            <div>
              <strong>{{ job.averageScore || '—' }}</strong>
              <small>平均分</small>
            </div>
            <div>
              <strong>{{ job.bestScore || '—' }}</strong>
              <small>最高分</small>
            </div>
          </div>
          <div class="card-foot">
            <span class="total">共 {{ job.totalInterviews }} 场面试</span>
            <span class="link">查看成长记录 →</span>
          </div>
        </article>
      </section>

      <el-empty
        v-else-if="!loading"
        description="还没有面试过的岗位，先完成一场面试吧"
        :image-size="100"
      >
        <el-button type="primary" @click="goHome">去选择岗位</el-button>
      </el-empty>
    </main>
  </div>
</template>

<style scoped>
.growth-page {
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
  margin: 0 0 28px;
  max-width: 720px;
  color: #7f8c9d;
  font-size: 14px;
  line-height: 1.8;
}

.growth-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.growth-card {
  padding: 24px;
  border-radius: 18px;
  border: 1px solid #e4ecf5;
  background: #fff;
  box-shadow: 0 10px 28px rgba(31, 78, 121, 0.05);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.growth-card:hover,
.growth-card:focus-visible {
  transform: translateY(-3px);
  border-color: #b9d5ee;
  box-shadow: 0 16px 34px rgba(31, 78, 121, 0.12);
  outline: none;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.last-date {
  color: #a0aab6;
  font-size: 12px;
}

.growth-card h2 {
  margin: 0 0 6px;
  color: #17375e;
  font-size: 22px;
  line-height: 1.35;
}

.track-name {
  margin: 0 0 18px;
  color: #8a97a8;
  font-size: 13px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 16px 0;
  border-top: 1px solid #eef4fb;
  border-bottom: 1px solid #eef4fb;
}

.metrics div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metrics strong {
  color: #17375e;
  font-size: 24px;
  line-height: 1;
}

.metrics small {
  color: #8a97a8;
  font-size: 12px;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  font-size: 13px;
}

.total {
  color: #8a97a8;
}

.link {
  color: #2e75b6;
  font-weight: 600;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 28px 16px 56px;
  }

  .growth-grid {
    grid-template-columns: 1fr;
  }
}
</style>
