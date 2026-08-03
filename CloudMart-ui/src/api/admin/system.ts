import request from '@/utils/request'

export function getUsers(params?: Record<string, any>) {
  return request.get('/admin/users/page', { params })
}

export function getUser(id: number | string) {
  return request.get(`/admin/users/${id}`)
}

export function createUser(data: Record<string, any>) {
  return request.post('/admin/users', data)
}

export function updateUser(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/users/${id}`, data)
}

export function deleteUser(id: number | string) {
  return request.delete(`/admin/users/${id}`)
}

export function updateUserStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/users/${id}/status`, data)
}

export function resetPassword(data: Record<string, any>) {
  return request.put('/admin/users/resetPassword', data)
}

export function assignRoles(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/users/${id}/roles`, data)
}

export function getRoles(params?: Record<string, any>) {
  return request.get('/admin/roles', { params })
}

export function getRole(id: number | string) {
  return request.get(`/admin/roles/${id}`)
}

export function createRole(data: Record<string, any>) {
  return request.post('/admin/roles', data)
}

export function updateRole(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/roles/${id}`, data)
}

export function deleteRole(id: number | string) {
  return request.delete(`/admin/roles/${id}`)
}

export function updateRoleStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/roles/${id}/status`, data)
}

export function assignRoleMenus(data: Record<string, any>) {
  return request.put('/admin/roles/menus', data)
}

export function getRoleMenus(id: number | string) {
  return request.get(`/admin/roles/${id}/menus`)
}

export function updateRoleDataScope(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/roles/${id}/data-scope`, data)
}

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

export function getDeptTree() {
  return request.get('/admin/depts/tree')
}

export function createDept(data: Record<string, any>) {
  return request.post('/admin/depts', data)
}

export function updateDept(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/depts/${id}`, data)
}

export function deleteDept(id: number | string) {
  return request.delete(`/admin/depts/${id}`)
}

export function updateDeptStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/depts/${id}/status`, data)
}

export function getPosts(params?: Record<string, any>) {
  return request.get('/admin/posts', { params })
}

export function createPost(data: Record<string, any>) {
  return request.post('/admin/posts', data)
}

export function updatePost(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/posts/${id}`, data)
}

export function deletePost(id: number | string) {
  return request.delete(`/admin/posts/${id}`)
}

export function updatePostStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/posts/${id}/status`, data)
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
