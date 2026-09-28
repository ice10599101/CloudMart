import request from '@/utils/request'

export interface LoginResult {
  accessToken: string
  refreshToken: string
}

/** 注册验证码发送结果（与注销发码响应结构一致） */
export interface RegisterCodeResult {
  sent: boolean
  expiresInSeconds: number
  /** 开发/测试回显模式的验证码，生产为空串 */
  devCode: string
  message: string
}

export const authApi = {
  login: (data: { account: string; password: string }) =>
    request<LoginResult>({ url: '/auth/login', method: 'POST', data }),
  /** 注册（需先 sendRegisterCode 获取邮箱验证码；返回 UserVO 含专属小答号 username） */
  register: (data: { nickname: string; email: string; password: string; code: string }) =>
    request<{ username?: string; nickname?: string }>({ url: '/user/users/register', method: 'POST', data }),
  sendRegisterCode: (email: string) =>
    request<RegisterCodeResult>({ url: '/user/users/register/code', method: 'POST', data: { email } }),
  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),
  refreshToken: (refreshToken: string) =>
    request<LoginResult>({ url: '/auth/refresh', method: 'POST', data: { refreshToken } }),
}
