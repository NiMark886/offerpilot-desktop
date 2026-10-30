<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interviewApi, type InterviewSession, type QuestionView } from '@/api/interview'

const route = useRoute()
const router = useRouter()

const session = ref<InterviewSession | null>(null)
const loading = ref(true)
const starting = ref(false)

const typeLabels: Record<string, string> = {
  BASIC: '基础',
  PROJECT: '项目',
  SCENARIO: '场景',
  AI: 'AI 工程',
  BEHAVIORAL: '行为题',
  ROLE_PLAY: '角色扮演',
  MOTIVATION: '动机题'
}

const difficultyLabels: Record<number, string> = {
  1: '简单',
  2: '中等',
  3: '困难'
}

const experienceLabels: Record<string, string> = {
  CAMPUS: '校招 / 应届',
  JUNIOR: '1-3 年',
  SENIOR: '3-5 年',
  EASY: '校招 / 应届',
  STANDARD: '1-3 年',
  HARD: '3-5 年'
}

function experienceLabel(value: string): string {
  return experienceLabels[value] ?? '1-3 年'
}

const groupedQuestions = computed(() => {
  const map = new Map<string, QuestionView[]>()
  for (const question of session.value?.questions ?? []) {
    const list = map.get(question.type) ?? []
    list.push(question)
    map.set(question.type, list)
  }
  return Array.from(map.entries()).map(([type, questions]) => ({
    type,
    label: typeLabels[type] ?? type,
    questions
  }))
})

onMounted(async () => {
  try {
    session.value = await interviewApi.get(Number(route.params.id))
  } catch {
    router.replace('/')
  } finally {
    loading.value = false
  }
})

function backHome() {
  router.push('/')
}

async function startInterview() {
  if (!session.value) return
  starting.value = true
  try {
    await interviewApi.start(session.value.id)
    router.push(`/interviews/${session.value.id}/chat`)
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="session-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="backHome">← 返回岗位</button>
      <div class="brand">OfferPilot</div>
      <span class="status">状态：蓝图预览</span>
    </header>

    <main v-loading="loading" class="content">
      <template v-if="session">
        <section class="hero">
          <div>
            <p class="eyebrow">INTERVIEW BLUEPRINT</p>
            <h1>{{ session.jobTitle }}</h1>
            <div class="meta">
              <span>{{ experienceLabel(session.difficulty) }}</span>
              <span>{{ session.durationMinutes }} 分钟</span>
              <span>{{ session.questions.length }} 道题</span>
              <span>{{ session.persona === 'PRESSURE' ? '压力面' : session.persona === 'GENTLE' ? '温和' : '标准' }}</span>
            </div>
            <el-tag
              v-if="session.generationMode === 'BANK'"
              type="info"
              size="small"
              style="margin-top: 10px"
            >
              题库模式
            </el-tag>
            <el-tag
              v-else-if="session.generationMode === 'AI'"
              type="success"
              size="small"
              style="margin-top: 10px"
            >
              AI 模式
            </el-tag>
          </div>
          <el-button type="primary" size="large" :loading="starting" @click="startInterview">
            开始答题
          </el-button>
        </section>

        <section v-for="group in groupedQuestions" :key="group.type" class="question-group">
          <div class="group-header">
            <h2>{{ group.label }}</h2>
            <span>{{ group.questions.length }} 题</span>
          </div>
          <article
            v-for="question in group.questions"
            :key="question.id"
            class="question-card"
          >
            <div class="question-top">
              <span class="seq">Q{{ question.seq }}</span>
              <el-tag size="small" effect="plain">{{ difficultyLabels[question.difficulty] || '中等' }}</el-tag>
              <el-tag
                v-for="tag in question.knowledgeTags"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
            <p class="question-text">{{ question.content }}</p>
          </article>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.session-page {
  min-height: 100vh;
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
  color: #8a97a8;
  font-size: 13px;
  cursor: default;
  pointer-events: none;
  user-select: none;
}

.content {
  max-width: 1100px;
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

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.question-group {
  margin-top: 34px;
}

.group-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.group-header h2 {
  margin: 0;
  color: #17375e;
  font-size: 20px;
}

.group-header span {
  color: #8a97a8;
  font-size: 13px;
}

.question-card {
  padding: 20px 22px;
  margin-bottom: 12px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #fff;
}

.question-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.seq {
  color: #2e75b6;
  font-size: 13px;
  font-weight: 700;
}

.question-text {
  margin: 0;
  color: #33475b;
  font-size: 15px;
  line-height: 1.8;
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
}
</style>
