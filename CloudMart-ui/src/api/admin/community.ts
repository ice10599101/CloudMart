import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface AdminPostRecord {
  id: number
  title: string
  userId: number
  mediaType: string
  likeCount: number
  commentCount: number
  favoriteCount: number
  viewCount: number
  status: number
  isTop: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminCommentRecord {
  id: number
  postId: number
  userId: number
  content: string
  likeCount: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface AdminTagRecord {
  id: number
  name: string
  icon: string
  postCount: number
  isHot: boolean
  status: number
  createdAt: string
  updatedAt: string
}

export interface AdminReportRecord {
  id: number
  reporterId: number
  targetType: string
  targetId: number
  reason: string
  status: number
  handlerId: number | null
  handleNote: string | null
  handleTime: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminBadgeRecord {
  id: number
  name: string
  icon: string
  description: string
  level: number
  status: number
  createdAt: string
  updatedAt: string
}

export function getCommunityStats() {
  return request.get<ApiResponse<Record<string, number>>>('/admin/stats/overview')
}

export function getCommunityTrend(days = 7) {
  return request.get<ApiResponse<Array<{ date: string; postCount: number; commentCount: number; reportCount: number }>>>('/admin/stats/trend', {
    params: { days },
  })
}

export function getAdminPosts(params?: Record<string, any>) {
  return request.get('/admin/community/posts', { params })
}

export function updatePostStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/posts/${id}/status`, data)
}

export function togglePostTop(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/posts/${id}/top`, data)
}

export function deletePost(id: number | string) {
  return request.delete(`/admin/community/posts/${id}`)
}

export function getAdminComments(params?: Record<string, any>) {
  return request.get('/admin/community/comments', { params })
}

export function updateCommentStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/comments/${id}/status`, data)
}

export function deleteComment(id: number | string) {
  return request.delete(`/admin/community/comments/${id}`)
}

export function getAdminTags(params?: Record<string, any>) {
  return request.get('/admin/community/tags', { params })
}

export function createAdminTag(data: Record<string, any>) {
  return request.post('/admin/community/tags', data)
}

export function updateAdminTag(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/tags/${id}`, data)
}

export function deleteAdminTag(id: number | string) {
  return request.delete(`/admin/community/tags/${id}`)
}

export function updateTagStatus(id: number | string, status: number) {
  return request.put(`/admin/community/tags/${id}/status`, { status })
}

export function getAdminReports(params?: Record<string, any>) {
  return request.get('/admin/community/reports', { params })
}

export function handleReport(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/reports/${id}/handle`, data)
}

export function getAdminBadges(params?: Record<string, any>) {
  return request.get('/admin/community/badges', { params })
}

export function createAdminBadge(data: Record<string, any>) {
  return request.post('/admin/community/badges', data)
}

export function updateAdminBadge(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/badges/${id}`, data)
}

export function deleteAdminBadge(id: number | string) {
  return request.delete(`/admin/community/badges/${id}`)
}

export function updateBadgeStatus(id: number | string, status: number) {
  return request.put(`/admin/community/badges/${id}/status`, { status })
}

export function grantBadge(id: number | string, data: Record<string, any>) {
  return request.post(`/admin/community/badges/${id}/grant`, data)
}

export interface AdminGrowthLevelConfig {
  id: number
  level: number
  title: string
  minExp: number
  icon: string
  benefits: string
  status: number
  createdAt: string
  updatedAt: string
}

export function getAdminGrowthLevelConfigs(params?: Record<string, any>) {
  return request.get('/admin/community/growth/level-configs', { params })
}

export function createAdminGrowthLevelConfig(data: Record<string, any>) {
  return request.post('/admin/community/growth/level-configs', data)
}

export function updateAdminGrowthLevelConfig(id: number | string, data: Record<string, any>) {
  return request.put(`/admin/community/growth/level-configs/${id}`, data)
}

export function deleteAdminGrowthLevelConfig(id: number | string) {
  return request.delete(`/admin/community/growth/level-configs/${id}`)
}

export function updateGrowthLevelStatus(id: number | string, status: number) {
  return request.put(`/admin/community/growth/level-configs/${id}/status`, { status })
}

export interface SensitiveWordRecord {
  id: number
  word: string
  category: string
  level: number
  createdAt: string
}

export function getPendingReviewPosts(params?: Record<string, any>) {
  return request.get('/admin/review/pending/posts', { params })
}

export function approvePost(id: number | string) {
  return request.put(`/admin/review/posts/${id}/approve`)
}

export function rejectPost(id: number | string, data: { reason: string }) {
  return request.put(`/admin/review/posts/${id}/reject`, data)
}

export function getSensitiveWords(params?: Record<string, any>) {
  return request.get('/admin/review/sensitive-words', { params })
}

export function addSensitiveWord(data: { word: string; category: string; level: number }) {
  return request.post('/admin/review/sensitive-words', data)
}

export function deleteSensitiveWord(id: number | string) {
  return request.delete(`/admin/review/sensitive-words/${id}`)
}

export function updateSensitiveWord(id: number | string, data: { word?: string; category?: string; level?: number }) {
  return request.put(`/admin/review/sensitive-words/${id}`, data)
}

export function getChatConversations(params?: Record<string, any>) {
  return request.get('/admin/chat/conversations', { params })
}

export function getChatMessages(conversationId: number, params?: Record<string, any>) {
  return request.get(`/admin/chat/conversations/${conversationId}/messages`, { params })
}

export function refreshSensitiveWordCache() {
  return request.post('/admin/review/sensitive-words/refresh')
}

// ==================== 排行榜管理 ====================

export interface AdminRankingItem {
  userId: number
  expValue: number
  rankNo: number
}

export interface AdminRankingSeason {
  id: number
  name: string
  seasonKey: string
  startDate: string
  endDate: string
  status: number
}

export function getAdminCurrentRanking(size = 50) {
  return request.get<ApiResponse<AdminRankingItem[]>>('/admin/rankings/current', { params: { size } })
}

export function persistLastMonthRanking() {
  return request.post<ApiResponse<void>>('/admin/rankings/persist')
}

export function getAdminRankingSeasons(params?: { status?: number; page?: number; pageSize?: number }) {
  return request.get<ApiResponse<AdminRankingSeason[]>>('/admin/rankings/seasons', { params })
}

export function getAdminSeasonRanking(seasonId: number, params?: { page?: number; pageSize?: number }) {
  return request.get<ApiResponse<AdminRankingItem[]>>(`/admin/rankings/seasons/${seasonId}`, { params })
}

export function updateSeasonStatus(seasonId: number, status: number) {
  return request.put<ApiResponse<void>>(`/admin/rankings/seasons/${seasonId}/status`, { status })
}
