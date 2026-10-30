import http from './http'

export interface UserView {
  id: number
  nickname: string
  email: string
  role: string
}

export const authApi = {
  me: () => http.get<never, UserView>('/auth/me')
}
