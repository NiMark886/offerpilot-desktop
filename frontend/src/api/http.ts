import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { apiBaseUrl } from './base'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

interface TokenPayload {
  accessToken: string
  refreshToken: string
  user: unknown
}

const http = axios.create({
  baseURL: apiBaseUrl(),
  timeout: 15000
})

let refreshPromise: Promise<string> | null = null

function clearAuth() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

function persistTokens(payload: TokenPayload) {
  localStorage.setItem(ACCESS_KEY, payload.accessToken)
  localStorage.setItem(REFRESH_KEY, payload.refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) {
    throw new Error('refresh token missing')
  }

  // Use a bare axios call so the refresh request never re-enters the 401 interceptor.
  const response = await axios.post<ApiResponse<TokenPayload>>(
    `${apiBaseUrl()}/auth/refresh`,
    { refreshToken },
    { timeout: 15000 }
  )
  const body = response.data
  if (body.code !== 0 || !body.data) {
    throw new Error(body.message || 'refresh failed')
  }
  persistTokens(body.data)
  return body.data.accessToken
}

function refreshOnce(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 运行时返回业务 data；泛型由各 API 方法的第二个参数声明
    return body.data as unknown as typeof response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const original = error.config as RetriableConfig | undefined
    const url = original?.url ?? ''
    const isAuthEndpoint =
      url.includes('/auth/login') ||
      url.includes('/auth/register') ||
      url.includes('/auth/refresh')

    if (status === 401 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true
      try {
        const token = await refreshOnce()
        return http({
          ...original,
          headers: {
            ...original.headers,
            Authorization: `Bearer ${token}`
          }
        })
      } catch {
        clearAuth()
        redirectToLogin()
        ElMessage.error('登录已过期，请重新登录')
        return Promise.reject(error)
      }
    }

    if (status === 401) {
      clearAuth()
      redirectToLogin()
    }

    const message = error.response?.data?.message
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    ElMessage.error(
      message ||
        (isTimeout
          ? '请求超时，AI 任务可能仍在后台处理，请稍后重试'
          : '网络异常，请稍后重试')
    )
    return Promise.reject(error)
  }
)

export default http
