import request from '@/utils/request'

export interface LoginResult {
  accessToken: string
  refreshToken: string
}

export const authApi = {
  login: (data: { account: string; password: string }) =>
    request<LoginResult>({ url: '/auth/login', method: 'POST', data }),
  /** 注册（返回 UserVO 含专属小答号 username，供注册成功页展示） */
  register: (data: { nickname: string; email: string; password: string }) =>
    request<{ username?: string; nickname?: string }>({ url: '/user/users/register', method: 'POST', data }),
  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),
  refreshToken: (refreshToken: string) =>
    request<LoginResult>({ url: '/auth/refresh', method: 'POST', data: { refreshToken } }),
}
