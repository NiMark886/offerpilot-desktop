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

export const resumeApi = {
  list: () => http.get<never, ResumeView[]>('/resumes'),

  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<never, ResumeView>('/resumes', form, { timeout: 60000 })
  },

  get: (id: number) => http.get<never, ResumeView>(`/resumes/${id}`),

  remove: (id: number) => http.delete<never, void>(`/resumes/${id}`)
}
