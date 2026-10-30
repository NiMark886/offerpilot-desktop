<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  interviewApi,
  streamAnswer,
  type AnswerEvaluationView,
  type InterviewSession,
  type MessageView,
  type QuestionView
} from '@/api/interview'

const route = useRoute()
const router = useRouter()

const session = ref<InterviewSession | null>(null)
const messages = ref<MessageView[]>([])
const loading = ref(true)
const sending = ref(false)
const input = ref('')
const streamingMessageId = ref<number | null>(null)
const messagesEl = ref<HTMLElement | null>(null)
const evaluations = ref<Record<number, AnswerEvaluationView>>({})

const correctnessLabels: Record<string, string> = {
  CORRECT: '正确',
  PARTIAL: '部分正确',
  INSUFFICIENT: '需补充',
  INCORRECT: '不正确'
}

function correctnessLabel(value: string) {
  return correctnessLabels[value] ?? value
}

const typeLabels: Record<string, string> = {
  BASIC: '基础',
  PROJECT: '项目',
  SCENARIO: '场景',
  AI: 'AI 工程',
  BEHAVIORAL: '行为题',
  ROLE_PLAY: '角色扮演',
  MOTIVATION: '动机题'
}

const currentQuestion = computed<QuestionView | null>(() => {
  const current = session.value
  if (!current || current.currentQuestionId == null) return null
  return current.questions.find((q) => q.id === current.currentQuestionId) ?? null
})

const completed = computed(() => session.value?.status === 'COMPLETED')
const canSend = computed(() =>
  Boolean(currentQuestion.value) && !sending.value && !completed.value && input.value.trim().length > 0
)

onMounted(load)

async function load(options: { silent?: boolean } = {}) {
  if (!options.silent) loading.value = true
  try {
    const id = Number(route.params.id)
    session.value = await interviewApi.get(id)
    if (session.value.status === 'READY') {
      session.value = await interviewApi.start(id)
    }
    const [messageList, evaluationList] = await Promise.all([
      interviewApi.messages(id),
      interviewApi.evaluations(id)
    ])
    messages.value = messageList
    evaluations.value = Object.fromEntries(
      evaluationList.map((evaluation) => [evaluation.candidateMessageId, evaluation])
    )
    await scrollToBottom()
  } catch {
    router.replace('/')
  } finally {
    if (!options.silent) loading.value = false
  }
}

async function send() {
  if (!canSend.value || !session.value || !currentQuestion.value) return
  const answer = input.value.trim()
  const id = session.value.id
  const questionId = currentQuestion.value.id
  input.value = ''

  const tempCandidateId = -Date.now()
  const tempInterviewerId = tempCandidateId - 1
  messages.value.push({
    id: tempCandidateId,
    sessionId: id,
    questionId,
    role: 'CANDIDATE',
    messageType: 'ANSWER',
    content: answer,
    createdAt: new Date().toISOString()
  })
  messages.value.push({
    id: tempInterviewerId,
    sessionId: id,
    questionId,
    role: 'INTERVIEWER',
    messageType: 'QUESTION',
    content: '',
    createdAt: new Date().toISOString()
  })
  streamingMessageId.value = tempInterviewerId
  sending.value = true
  await scrollToBottom()

  try {
    await streamAnswer(id, questionId, answer, {
      onMeta: (meta) => {
        const candidate = messages.value.find((m) => m.id === tempCandidateId)
        if (candidate) candidate.id = meta.candidateMessageId
        const message = messages.value.find((m) => m.id === streamingMessageId.value)
        if (message) {
          message.id = meta.interviewerMessageId
          message.questionId = meta.questionId
          message.messageType = meta.replyType
          streamingMessageId.value = meta.interviewerMessageId
        }
      },
      onDelta: (text) => {
        const message = messages.value.find((m) => m.id === streamingMessageId.value)
        if (message) message.content += text
        scrollToBottom()
      },
      onEvaluation: (evaluation) => {
        evaluations.value[evaluation.candidateMessageId] = evaluation
      },
      onDone: async (done) => {
        if (session.value) {
          session.value.status = done.sessionStatus
          session.value.currentQuestionId = done.currentQuestionId
        }
        sending.value = false
        streamingMessageId.value = null
        await load({ silent: true })
      },
      onError: async (message) => {
        ElMessage.error(message)
        sending.value = false
        streamingMessageId.value = null
        await load({ silent: true })
      }
    })
    if (sending.value) {
      sending.value = false
      streamingMessageId.value = null
      await load({ silent: true })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交回答失败')
    sending.value = false
    streamingMessageId.value = null
    await load({ silent: true })
  }
}

async function handleHint() {
  if (!session.value) return
  try {
    const result = await interviewApi.hint(session.value.id)
    ElMessage.success(`提示：${result.hint}`)
    await load({ silent: true })
  } catch {
    // 拦截器已提示
  }
}

async function handleSkip() {
  if (!session.value) return
  try {
    await interviewApi.skip(session.value.id)
    await load({ silent: true })
  } catch {
    // 拦截器已提示
  }
}

async function handleFinish() {
  if (!session.value) return
  try {
    await ElMessageBox.confirm('确定要结束这场面试吗？', '结束面试', {
      type: 'warning',
      confirmButtonText: '结束',
      cancelButtonText: '继续面试'
    })
    await interviewApi.finish(session.value.id)
    await load({ silent: true })
  } catch {
    // 用户取消或接口失败
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

watch(messages, scrollToBottom, { deep: true })

function backToPreview() {
  router.push(`/interviews/${route.params.id}`)
}
</script>

<template>
  <div class="chat-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="backToPreview">← 返回蓝图</button>
      <BrandBlock />
      <div class="topbar-right">
        <span v-if="session?.generationMode === 'BANK'" class="mode-tag">题库模式</span>
        <span v-else-if="session?.generationMode === 'AI'" class="mode-tag ai">AI 模式</span>
        <span class="status" :class="{ done: completed }">
          {{ completed ? '面试已结束' : '面试进行中' }}
        </span>
      </div>
    </header>

    <main v-loading="loading" class="chat-main">
      <section ref="messagesEl" class="messages">
        <template v-for="message in messages" :key="message.id">
          <div v-if="message.role === 'SYSTEM'" class="system-message">
            {{ message.content }}
          </div>
          <div v-else class="message-row" :class="message.role === 'CANDIDATE' ? 'right' : 'left'">
            <div class="avatar">{{ message.role === 'CANDIDATE' ? '你' : 'AI' }}</div>
            <div class="message-content">
              <div class="bubble">
                <span v-if="message.id === streamingMessageId && !message.content" class="typing">AI 正在思考…</span>
                <span v-else class="bubble-text">{{ message.content }}</span>
              </div>
              <div
                v-if="message.role === 'CANDIDATE' && evaluations[message.id]"
                class="evaluation-card"
                :class="evaluations[message.id].correctness.toLowerCase()"
              >
                <div class="evaluation-head">
                  <span class="evaluation-label">
                    {{ correctnessLabel(evaluations[message.id].correctness) }}
                    <em class="evaluation-scope">
                      {{ evaluations[message.id].promptType === 'FOLLOW_UP' ? '针对追问' : '针对本题' }}
                    </em>
                  </span>
                  <span class="evaluation-score">{{ evaluations[message.id].score }}/5</span>
                </div>
                <p
                  v-if="evaluations[message.id].promptType === 'FOLLOW_UP'"
                  class="evaluation-prompt"
                >
                  {{ evaluations[message.id].promptText }}
                </p>
                <p class="evaluation-comment">{{ evaluations[message.id].comment }}</p>
                <p
                  v-if="evaluations[message.id].missingPoints?.length"
                  class="evaluation-missing"
                >
                  建议补充：{{ evaluations[message.id].missingPoints.join('、') }}
                </p>
              </div>
            </div>
          </div>
        </template>
      </section>

      <section v-if="!completed" class="composer">
        <div v-if="currentQuestion" class="question-bar">
          <span class="q-label">当前 Q{{ currentQuestion.seq }}</span>
          <el-tag size="small" effect="plain">{{ typeLabels[currentQuestion.type] || currentQuestion.type }}</el-tag>
          <span class="q-meta">追问 {{ currentQuestion.followUpCount }}/{{ currentQuestion.maxFollowUps }} · 提示 {{ currentQuestion.hintCount }}/1</span>
        </div>
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入你的回答，Enter 发送，Shift + Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send"
        />
        <div class="actions">
          <div class="actions-left">
            <el-button
              size="small"
              :disabled="sending || !currentQuestion || currentQuestion.hintCount >= 1"
              @click="handleHint"
            >
              提示
            </el-button>
            <el-button size="small" :disabled="sending" @click="handleSkip">跳过</el-button>
            <el-button size="small" type="danger" plain :disabled="sending" @click="handleFinish">
              结束面试
            </el-button>
          </div>
          <el-button type="primary" :disabled="!canSend" @click="send">
            {{ sending ? '面试官思考中…' : '发送' }}
          </el-button>
        </div>
      </section>

      <section v-else class="finished">
        <el-result icon="success" title="面试已结束" sub-title="查看能力评分报告，复盘本场表现。">
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
      </section>
    </main>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
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
  flex: none;
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

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #7f8c9d;
  font-size: 12px;
}

.mode-tag.ai {
  background: #f0f9eb;
  color: #529b2e;
}

.status {
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef4fb;
  color: #2e75b6;
  font-size: 12px;
}

.status.done {
  background: #f0f9eb;
  color: #529b2e;
}

.chat-main {
  flex: 1;
  min-height: 0;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 24px 24px;
  display: flex;
  flex-direction: column;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 4px 24px;
  scroll-behavior: smooth;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  align-items: flex-start;
}

.message-row.right {
  flex-direction: row-reverse;
}

.avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #1f4e79;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.message-row.right .avatar {
  background: #2e75b6;
}

.message-content {
  max-width: 78%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-row.right .message-content {
  align-items: flex-end;
}

.bubble {
  max-width: 100%;
  padding: 12px 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e4ecf5;
  color: #33475b;
  font-size: 15px;
  line-height: 1.8;
  box-shadow: 0 4px 14px rgba(31, 78, 121, 0.05);
}

.message-row.right .bubble {
  background: #2e75b6;
  border-color: #2e75b6;
  color: #fff;
}

.bubble-text {
  white-space: pre-wrap;
}

.typing {
  color: #8a97a8;
}

.message-row.right .typing {
  color: rgba(255, 255, 255, 0.8);
}

.system-message {
  max-width: 76%;
  margin: 0 auto 18px;
  padding: 9px 14px;
  border-radius: 999px;
  background: #fff7e6;
  border: 1px solid #f3d19e;
  color: #b88230;
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
}

.evaluation-card {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e4ecf5;
  background: #fbfdff;
  font-size: 13px;
  box-sizing: border-box;
}

.evaluation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.evaluation-label {
  font-weight: 700;
}

.evaluation-scope {
  margin-left: 6px;
  color: #8a97a8;
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
}

.evaluation-score {
  color: #8a97a8;
}

.evaluation-prompt {
  margin: 0 0 6px;
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.evaluation-card.correct {
  border-color: #c2e7b0;
  background: #f0f9eb;
}

.evaluation-card.correct .evaluation-label {
  color: #529b2e;
}

.evaluation-card.partial {
  border-color: #f3d19e;
  background: #fdf6ec;
}

.evaluation-card.partial .evaluation-label {
  color: #b88230;
}

.evaluation-card.insufficient {
  border-color: #f3d19e;
  background: #fff7e6;
}

.evaluation-card.insufficient .evaluation-label {
  color: #d48806;
}

.evaluation-card.incorrect {
  border-color: #f0b4b4;
  background: #fef0f0;
}

.evaluation-card.incorrect .evaluation-label {
  color: #c45656;
}

.evaluation-comment {
  margin: 0;
  color: #5b6b7f;
  line-height: 1.7;
}

.evaluation-missing {
  margin: 6px 0 0;
  color: #8a97a8;
  line-height: 1.6;
}

.composer {
  flex: none;
  padding-top: 12px;
  border-top: 1px solid #e4ecf5;
}

.question-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.q-label {
  color: #17375e;
  font-size: 13px;
  font-weight: 700;
}

.q-meta {
  color: #8a97a8;
  font-size: 12px;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.actions-left {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.finished {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .chat-main {
    padding: 12px 14px 18px;
  }

  .message-content {
    max-width: 86%;
  }

  .bubble {
    max-width: 100%;
  }

  .actions {
    align-items: flex-end;
    flex-direction: column;
  }
}
</style>
