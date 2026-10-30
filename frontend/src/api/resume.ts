import http from './http'

export interface ProjectExperience {
  name: string
  description: string
}

export interface ResumeProfile {
  name: string | null
  email: string | null
  phone: string | null
  yearsOfExperience: number
  skills: string[]
  projects: ProjectExperience[]
  education: string | null
  summary: string
}

export interface ResumeView {
  id: number
  fileName: string
  fileSize: number
  parseStatus: string
  profile: ResumeProfile | null
  createdAt: string
}

export interface ResumeIssue {
  category: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | string
  title: string
  detail: string
  suggestion: string
  evidence: string
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
  issues: ResumeIssue[]
}

export interface ResumeEvaluation {
  resumeId: number
  fileName: string
  jobTitle: string
  assessment: ResumeAssessment
}

export interface ResumeEvaluationRequest {
  jobTemplateId?: number
  jobTitle?: string
  jdText?: string
}

export const resumeApi = {
  list: () => http.get<never, ResumeView[]>('/resumes'),

  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<never, ResumeView>('/resumes', form, { timeout: 60000 })
  },

  get: (id: number) => http.get<never, ResumeView>(`/resumes/${id}`),

  evaluate: (id: number, data: ResumeEvaluationRequest) =>
    http.post<never, ResumeEvaluation>(`/resumes/${id}/evaluation`, data, { timeout: 120000 }),

  remove: (id: number) => http.delete<never, void>(`/resumes/${id}`)
}
