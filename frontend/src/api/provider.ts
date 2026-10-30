import http from './http'

export interface AiProvider {
  id: number
  name: string
  providerType: string
  baseUrl: string
  model: string
  apiKeyHint: string
  temperature: number
  enabled: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AiProviderRequest {
  name: string
  providerType: string
  baseUrl?: string
  model?: string
  apiKey?: string
  temperature?: number
  enabled?: boolean
}

export interface AiProviderPreset {
  type: string
  label: string
  baseUrl: string
  model: string
  models: string[]
  group: string
}

export interface AiProviderStatus {
  aiEnabled: boolean
  defaultModel: string
}

export interface AiProviderTestResult {
  success: boolean
  latencyMs: number
  model: string
  message: string
}

export const aiProviderApi = {
  list: () => http.get<never, AiProvider[]>('/ai/providers'),

  presets: () => http.get<never, AiProviderPreset[]>('/ai/providers/presets'),

  status: () => http.get<never, AiProviderStatus>('/ai/providers/status'),

  create: (data: AiProviderRequest) =>
    http.post<never, AiProvider>('/ai/providers', data),

  update: (id: number, data: AiProviderRequest) =>
    http.put<never, AiProvider>(`/ai/providers/${id}`, data),

  remove: (id: number) => http.delete<never, void>(`/ai/providers/${id}`),

  activate: (id: number) => http.post<never, AiProvider>(`/ai/providers/${id}/activate`),

  test: (id: number) => http.post<never, AiProviderTestResult>(`/ai/providers/${id}/test`)
}
