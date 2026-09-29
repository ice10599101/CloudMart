import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export function adminLogin(data: { account: string; password: string }) {
  return request.post('/auth/admin/login', data)
}

export function adminRefreshToken(data: { refreshToken: string }) {
  return request.post('/auth/admin/refresh', data)
}

/** 管理员退出全部设备（SEC-02 admin 面） */
export function adminLogoutAll() {
  return request.post<ApiResponse<void>>('/auth/admin/logout-all')
}

export function adminLogout() {
  return request.post('/auth/admin/logout')
}

export function getAdminProfile() {
  return request.get('/admin/profile')
}

export function updateAdminProfile(data: Record<string, any>) {
  return request.put('/admin/profile', data)
}

export function updateAdminPassword(data: Record<string, any>) {
  return request.put('/admin/profile/password', data)
}
