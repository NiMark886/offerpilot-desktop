import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { apiBaseUrl } from './base'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

const http = axios.create({
  baseURL: apiBaseUrl(),
  timeout: 15000
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
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    if (status === 401) {
      ElMessage.error(message || '本地服务认证失败，请重启应用')
    } else {
      ElMessage.error(
        message ||
          (isTimeout
            ? '请求超时，AI 任务可能仍在后台处理，请稍后重试'
            : '网络异常，请稍后重试')
      )
    }
    return Promise.reject(error)
  }
)

export default http
