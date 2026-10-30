import http from './http'
import { apiBaseUrl } from './base'
import type { ResumeIssue } from './resume'

export interface QuestionView {
  id: number
  seq: number
  type: string
  difficulty: number
  content: string
  knowledgeTags: string[]
  referencePoints: string[]
  status: string
  followUpCount: number
  maxFollowUps: number
  hintCount: number
}

export interface InterviewSession {
  id: number
  jobTemplateId: number
  jobTitle: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
  status: string
  generationMode?: 'AI' | 'BANK'
  currentQuestionId: number | null
  createdAt: string
  questions: QuestionView[]
}

export interface MessageView {
  id: number
  sessionId: number
  questionId: number | null
  role: string
  messageType: string
  content: string
  createdAt: string
}

export interface CreateInterviewRequest {
  jobTemplateId: number
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
}

export type ReportDimensions = Record<string, number>

export interface CreateCustomInterviewRequest {
  jdText: string
  resumeId: number
  difficulty: string
  durationMinutes: number
  persona: string
  mode: string
}

export interface ResumeAssessment {
  score: number
  educationScore: number
  skillsScore: number
  experienceScore: number
  layoutScore: number
  weights: Record<string, number>
  aiGenerated: boolean
  comment: string
  issues?: ResumeIssue[]
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
  dimensionLabels?: Record<string, string>
  resumeAssessment?: ResumeAssessment | null
  generationMode?: 'AI' | 'BANK'
  summary: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  status: string
  createdAt: string
  questionReviews: QuestionReview[]
}

export interface GrowthFilter {
  jobTemplateId?: number
  trackCode?: string
}

export interface GrowthJob {
  key: string
  jobTemplateId: number | null
  jobTitle: string
  category?: string | null
  trackCode?: string | null
  trackName?: string | null
  totalInterviews: number
  completedInterviews: number
  averageScore: number
  bestScore: number
  lastInterviewAt: string | null
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
  dimensionLabels?: Record<string, string>
  scoreTrend: ScorePoint[]
}

export interface QuestionBankItem {
  id: number
  category: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
}

export interface WeaknessView {
  tag: string
  count: number
  lastSeen: string
  recommendedQuestions: QuestionBankItem[]
}

export interface AnswerEvaluationView {
  id: number
  candidateMessageId: number
  questionId: number
  promptText: string | null
  promptType: string | null
  correctness: 'CORRECT' | 'PARTIAL' | 'INSUFFICIENT' | 'INCORRECT'
  score: number
  comment: string
  missingPoints: string[]
  confidence: number
  createdAt: string
}

export interface HintResult {
  messageId: number
  hint: string
}

export interface QuotaView {
  plan: string
  sessionLimit: number
  sessionUsed: number
  remainingSessions: number
  tokenLimit: number
  tokenUsed: number
  resetAt: string | null
}

export interface AdminQuestion {
  id: number
  category: string
  tags: string[]
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  trackIds: number[]
  trackNames: string[]
  createdAt: string
  updatedAt: string
}

export interface AdminQuestionRequest {
  category: string
  tags: string
  difficulty: number
  question: string
  referenceAnswer: string
  source: string
  status: string
  trackIds?: number[]
}

export interface QuestionBankOptions {
  categories: string[]
  tags: string[]
  sources: string[]
}

export interface QuestionImportItem {
  question: string
  referenceAnswer: string
  category: string
  difficulty: number
  tags: string[]
  source: string
}

export interface QuestionImportDefaults {
  category?: string
  difficulty?: number
  source?: string
}

export interface QuestionDocumentExtract {
  fileName: string
  textLength: number
  truncated: boolean
  aiUsed: boolean
  total: number
  items: QuestionImportItem[]
}

export interface QuestionImportResult {
  imported: number
  ids: number[]
}

export interface AdminTrack {
  id: number
  code: string
  name: string
  category?: string
  roleFamily: string
  primaryLanguage: string
  level: string
  description: string
  rubricCode: string
  status: string
  sortOrder: number
  questionCount: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface StreamHandlers {
  onMeta?: (meta: {
    candidateMessageId: number
    interviewerMessageId: number
    questionId: number
    replyType: string
  }) => void
  onDelta?: (text: string) => void
  onEvaluation?: (evaluation: AnswerEvaluationView) => void
  onDone?: (done: { sessionStatus: string; currentQuestionId: number | null; completed: boolean }) => void
  onError?: (message: string) => void
}

export const interviewApi = {
  create: (data: CreateInterviewRequest) =>
    http.post<never, InterviewSession>('/interviews', data, { timeout: 60000 }),

  createCustom: (data: CreateCustomInterviewRequest) =>
    http.post<never, InterviewSession>('/interviews/custom', data, { timeout: 60000 }),

  get: (id: number) => http.get<never, InterviewSession>(`/interviews/${id}`),

  list: () => http.get<never, InterviewSession[]>('/interviews'),

  generateReport: (id: number) => http.post<never, InterviewReport>(`/interviews/${id}/report`),

  getReport: (id: number) => http.get<never, InterviewReport>(`/interviews/${id}/report`),

  history: (filter?: GrowthFilter) =>
    http.get<never, InterviewHistoryItem[]>('/interviews/history', { params: filter }),

  jobs: () => http.get<never, GrowthJob[]>('/stats/jobs'),

  stats: (filter?: GrowthFilter) =>
    http.get<never, StatsOverview>('/stats/overview', { params: filter }),

  weaknesses: (filter?: GrowthFilter) =>
    http.get<never, WeaknessView[]>('/stats/weaknesses', { params: filter }),

  start: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/start`),

  messages: (id: number) => http.get<never, MessageView[]>(`/interviews/${id}/messages`),

  evaluations: (id: number) => http.get<never, AnswerEvaluationView[]>(`/interviews/${id}/evaluations`),

  hint: (id: number) => http.post<never, HintResult>(`/interviews/${id}/hint`),

  skip: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/skip`),

  finish: (id: number) => http.post<never, InterviewSession>(`/interviews/${id}/finish`)
}

export const quotaApi = {
  me: () => http.get<never, QuotaView>('/quota/me')
}

export const adminQuestionApi = {
  page: (params: { page: number; size: number; keyword?: string; trackId?: number }) =>
    http.get<never, PageResult<AdminQuestion>>('/admin/questions', { params }),

  get: (id: number) => http.get<never, AdminQuestion>(`/admin/questions/${id}`),

  create: (data: AdminQuestionRequest) =>
    http.post<never, AdminQuestion>('/admin/questions', data),

  options: () => http.get<never, QuestionBankOptions>('/admin/questions/options'),

  extractDocument: (file: File, defaults: QuestionImportDefaults) => {
    const body = new FormData()
    body.append('file', file)
    if (defaults.category) body.append('category', defaults.category)
    if (defaults.difficulty) body.append('difficulty', String(defaults.difficulty))
    if (defaults.source) body.append('source', defaults.source)
    return http.post<never, QuestionDocumentExtract>('/admin/questions/import/extract', body, {
      timeout: 120000
    })
  },

  importQuestions: (data: { questions: QuestionImportItem[]; trackIds?: number[] }) =>
    http.post<never, QuestionImportResult>('/admin/questions/import', data),

  setStatus: (id: number, status: 'ACTIVE' | 'INACTIVE') =>
    http.patch<never, AdminQuestion>(`/admin/questions/${id}/status`, { status })
}

export const adminTrackApi = {
  list: () => http.get<never, AdminTrack[]>('/admin/tracks')
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

export async function streamAnswer(
  sessionId: number,
  questionId: number,
  content: string,
  handlers: StreamHandlers
): Promise<void> {
  const token = localStorage.getItem(ACCESS_KEY)
  const response = await fetch(`${apiBaseUrl()}/interviews/${sessionId}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ questionId, content })
  })

  if (response.status === 401) {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
    window.location.assign('/login')
    throw new Error('登录已过期')
  }

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || '提交回答失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const raw of events) {
      const lines = raw.split('\n')
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n')
      if (!event || !data) continue

      const payload = JSON.parse(data)
      if (event === 'meta') handlers.onMeta?.(payload)
      else if (event === 'delta') handlers.onDelta?.(payload.text)
      else if (event === 'evaluation') handlers.onEvaluation?.(payload)
      else if (event === 'done') handlers.onDone?.(payload)
      else if (event === 'error') handlers.onError?.(payload.message)
    }
  }
}
