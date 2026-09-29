import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface LoginData {
  account: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export function login(data: LoginData) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', data)
}

export function refreshTokenApi(refreshToken: string) {
  return request.post<ApiResponse<LoginResult>>('/auth/refresh', { refreshToken })
}

export function logoutApi() {
  return request.post<ApiResponse<void>>('/auth/logout')
}

/** SEC-02：退出全部设备（认证状态版本递增 + 撤销全部刷新令牌家族；调用后本端也被登出） */
export function logoutAllDevices() {
  return request.post<ApiResponse<void>>('/auth/logout-all')
}
