import request from '@/utils/request'

export function getMenuTree() {
  return request.get('/admin/menus/tree')
}

export function createMenu(data: Record<string, any>) {
  return request.post('/admin/menus', data)
}

export function updateMenu(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/menus/${id}`, data)
}

export function deleteMenu(id: number | string) {
  return request.delete(`/admin/menus/${id}`)
}

export function updateMenuStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/menus/${id}/status`, data)
}

export function getDictTypes(params?: Record<string, any>) {
  return request.get('/admin/dict/types', { params })
}

export function getDictType(id: number | string) {
  return request.get(`/admin/dict/types/${id}`)
}

export function createDictType(data: Record<string, any>) {
  return request.post('/admin/dict/types', data)
}

export function updateDictType(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/dict/types/${id}`, data)
}

export function deleteDictType(id: number | string) {
  return request.delete(`/admin/dict/types/${id}`)
}

export function updateDictTypeStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/dict/types/${id}/status`, data)
}

export function refreshDictCache() {
  return request.put('/admin/dict/types/cache/refresh')
}

export function getDictData(dictType: string, params?: Record<string, any>) {
  return request.get(`/admin/dict/data/type/${dictType}`, { params })
}

export function getDictDataItem(id: number | string) {
  return request.get(`/admin/dict/data/${id}`)
}

export function createDictData(data: Record<string, any>) {
  return request.post('/admin/dict/data', data)
}

export function updateDictData(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/dict/data/${id}`, data)
}

export function deleteDictData(id: number | string) {
  return request.delete(`/admin/dict/data/${id}`)
}

export function updateDictDataStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/dict/data/${id}/status`, data)
}

export function getConfigs(params?: Record<string, any>) {
  return request.get('/admin/configs', { params })
}

export function createConfig(data: Record<string, any>) {
  return request.post('/admin/configs', data)
}

export function updateConfig(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/configs/${id}`, data)
}

export function deleteConfig(id: number | string) {
  return request.delete(`/admin/configs/${id}`)
}

export function refreshConfigCache() {
  return request.put('/admin/configs/cache/refresh')
}

export function getNotices(params?: Record<string, any>) {
  return request.get('/admin/notices/page', { params })
}

export function createNotice(data: Record<string, any>) {
  return request.post('/admin/notices', data)
}

export function updateNotice(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/notices/${id}`, data)
}

export function deleteNotice(id: number | string) {
  return request.delete(`/admin/notices/${id}`)
}

export function updateNoticeStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/notices/${id}/status`, data)
}

/** 一键推送公告到全站用户消息中心（后端经 mall-notification 广播，仅允许已启用的公告） */
export function pushNotice(id: number | string) {
  return request.post(`/admin/notices/${id}/push`)
}

export function getOperLogs(params?: Record<string, any>) {
  return request.get('/admin/logs/oper/page', { params })
}

export function deleteOperLog(id: number | string) {
  return request.delete(`/admin/logs/oper/${id}`)
}

export function cleanOperLogs() {
  return request.delete('/admin/logs/oper/clean')
}

export function getLoginLogs(params?: Record<string, any>) {
  return request.get('/admin/logs/login/page', { params })
}

export function deleteLoginLog(id: number | string) {
  return request.delete(`/admin/logs/login/${id}`)
}

export function cleanLoginLogs() {
  return request.delete('/admin/logs/login/clean')
}

export function getDashboardStats() {
  return request.get('/admin/dashboard/stats')
}

export function getRecentOrders(params?: Record<string, any>) {
  return request.get('/admin/dashboard/recent-orders', { params })
}

export function getSalesTrend(params?: Record<string, any>) {
  return request.get('/admin/dashboard/sales-trend', { params })
}

// ========== T24 多运营：管理员账号管理 ==========

export interface AdminUserRow {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  status: number
  deptId: number | null
  remark: string | null
  createdAt: string
  roles?: Array<{ roleId?: number; roleName: string; roleKey: string }> | null
}

export interface AdminRoleRow {
  id: number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope: number
  status: number
  remark: string | null
  createdAt: string
}

export function listAdminUsers(params?: Record<string, any>) {
  return request.get('/admin/users/page', { params })
}

export function getAdminUser(id: number | string) {
  return request.get<import('@/types/api').ApiResponse<AdminUserRow>>(`/admin/users/${id}`)
}

export function createAdminUser(data: Record<string, any>) {
  return request.post('/admin/users', data)
}

export function updateAdminUser(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/users/${id}`, data)
}

export function deleteAdminUser(id: number | string) {
  return request.delete(`/admin/users/${id}`)
}

export function changeAdminUserStatus(id: number | string, status: number) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function resetAdminUserPassword(id: number | string, newPassword: string) {
  return request.put('/admin/users/resetPassword', { userId: id, newPassword })
}

export function assignAdminUserRoles(id: number | string, roleIds: number[]) {
  return request.put(`/admin/users/${id}/roles`, { roleIds })
}

// ========== T24 多运营：角色管理 ==========

export function listAdminRoles() {
  return request.get('/admin/roles')
}

export function getAdminRole(id: number | string) {
  return request.get(`/admin/roles/${id}`)
}

export function createAdminRole(data: Record<string, any>) {
  return request.post('/admin/roles', data)
}

export function updateAdminRole(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/roles/${id}`, data)
}

export function deleteAdminRole(id: number | string) {
  return request.delete(`/admin/roles/${id}`)
}

export function getAdminRoleMenus(id: number | string) {
  return request.get(`/admin/roles/${id}/menus`)
}

export function assignAdminRoleMenus(data: { roleId: number | string; menuIds: number[] }) {
  return request.put('/admin/roles/menus', data)
}
