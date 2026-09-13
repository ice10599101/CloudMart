# -*- coding: utf-8 -*-
import io, re

p = 'src/api/admin/system.ts'
s = io.open(p, encoding='utf-8').read()

# 删除 用户/角色/部门/岗位 管理函数（单管理员后台，RBAC 组织功能已下线；登录鉴权仍走后端既有接口不受影响）
blocks = [
    # User Management
    """export function getUsers(params?: Record<string, any>) {
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

""",
    # Role Management
    """export function getRoles(params?: Record<string, any>) {
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

""",
    # Dept Management
    """export function getDeptTree() {
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

""",
    # Post Management
    """export function getPosts(params?: Record<string, any>) {
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

""",
]
for b in blocks:
    assert b in s, b[:60]
    s = s.replace(b, "")
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print("api cleaned, lines:", len(s.splitlines()))

# ---------- 单测同步 ----------
p = 'src/api/admin/system.test.ts'
s = io.open(p, encoding='utf-8').read()
for header in ['User Management', 'Role Management', 'Dept Management', 'Post Management']:
    # 删除整段 describe（从 describe 行到下一个顶层 describe 前的空行）
    pat = re.compile(r"describe\('admin system API - %s'.*?\n\}\n\n" % re.escape(header), re.S)
    s2 = pat.sub("", s, count=1)
    assert s2 != s, header
    s = s2
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print("test cleaned, lines:", len(s.splitlines()))
