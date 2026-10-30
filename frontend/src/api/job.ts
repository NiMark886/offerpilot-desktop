import http from './http'

export interface JobTemplate {
  id: number
  title: string
  level: string
  category: string
  jdText: string
  trackId: number | null
  trackCode: string | null
  trackName: string | null
  roleFamily: string | null
  primaryLanguage: string | null
  trackLevel: string | null
  questionCount: number
}

export const jobApi = {
  list: () => http.get<never, JobTemplate[]>('/job-templates')
}
