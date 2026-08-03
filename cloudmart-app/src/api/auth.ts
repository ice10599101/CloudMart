import request from '@/utils/request'

export interface LoginResult {
  accessToken: string
  refreshToken: string
}

export const authApi = {
  login: (data: { account: string; password: string }) =>
    request<LoginResult>({ url: '/auth/login', method: 'POST', data }),
  register: (data: { nickname: string; email: string; password: string }) =>
    request<void>({ url: '/user/users/register', method: 'POST', data }),
  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),
  refreshToken: (refreshToken: string) =>
    request<LoginResult>({ url: '/auth/refresh', method: 'POST', data: { refreshToken } }),
}
