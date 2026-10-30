import http from './http'

export interface UserView {
  id: number
  nickname: string
  email: string
  role: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  user: UserView
}

export const authApi = {
  register: (data: { email: string; password: string; nickname: string }) =>
    http.post<never, TokenResponse>('/auth/register', data),

  login: (data: { account: string; password: string }) =>
    http.post<never, TokenResponse>('/auth/login', data),

  refresh: (refreshToken: string) =>
    http.post<never, TokenResponse>('/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    http.post<never, void>('/auth/logout', { refreshToken }),

  me: () => http.get<never, UserView>('/auth/me')
}
