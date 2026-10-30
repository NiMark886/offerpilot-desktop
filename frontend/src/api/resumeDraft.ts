import http from './http'

export interface ResumePersonal {
  name: string
  gender: string
  birthDate: string
  ethnicity: string
  politicalStatus: string
  birthplace: string
  currentCity: string
  phone: string
  email: string
  communityUrl: string
  photo: string
}

export interface ResumeEducation {
  school: string
  college: string
  major: string
  degree: string
  startDate: string
  endDate: string
  courses: string
  gpa: string
  ranking: string
}

export type ResumeExperienceType = 'INTERNSHIP' | 'WORK'

export interface ResumeExperience {
  type: ResumeExperienceType
  company: string
  role: string
  startDate: string
  endDate: string
  techStack: string
  bullets: string[]
}

export interface ResumeProject {
  name: string
  startDate: string
  endDate: string
  techStack: string
  repoUrl: string
  bullets: string[]
}

export interface ResumeSkillGroup {
  label: string
  content: string
}

export interface ResumeStyle {
  bodyFontSize: number
  nameFontSize: number
  sectionTitleFontSize: number
  entryTitleFontSize: number
  metaFontSize: number
  lineHeight: number
  letterSpacing: number
  sectionFontSizes: Record<string, number>
}

export interface ResumeDraftContent {
  personal: ResumePersonal
  education: ResumeEducation[]
  internships: ResumeExperience[]
  projects: ResumeProject[]
  skills: ResumeSkillGroup[]
  awards: string[]
  style: ResumeStyle
}

export interface ResumeDraftView {
  id: number
  title: string
  content: ResumeDraftContent
  createdAt: string
  updatedAt: string
}

export interface ResumeDraftRequest {
  title: string
  content: ResumeDraftContent
}

export interface ResumeDocxExport {
  fileName: string
  contentBase64: string
}

export const resumeDraftApi = {
  list: () => http.get<never, ResumeDraftView[]>('/resume-drafts'),

  get: (id: number) => http.get<never, ResumeDraftView>(`/resume-drafts/${id}`),

  create: (data: ResumeDraftRequest) =>
    http.post<never, ResumeDraftView>('/resume-drafts', data),

  update: (id: number, data: ResumeDraftRequest) =>
    http.put<never, ResumeDraftView>(`/resume-drafts/${id}`, data),

  remove: (id: number) => http.delete<never, void>(`/resume-drafts/${id}`),

  exportDocx: (id: number) =>
    http.post<never, ResumeDocxExport>(`/resume-drafts/${id}/export/docx`, {}, { timeout: 60000 })
}
