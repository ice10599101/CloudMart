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
