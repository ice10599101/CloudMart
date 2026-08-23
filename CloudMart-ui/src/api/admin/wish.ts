import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== Types（与 mall-wish AdminWishVO / CategoryVO 对齐） ==========

export type AdminWishStatus = 'DRAFT' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED'
export type AdminAuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_HIDDEN'
export type AdminWishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type AdminFruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'

export interface AdminWishCategoryRecord {
  id: number
  code: string
  name: string
  icon: string | null
  sortOrder: number
}

export interface AdminWishRecord {
  id: number
  userId: number
  title: string
  description: string
  mediaUrls: string[]
  categoryId: number
  categoryName: string
  tags: string[]
  visibility: AdminWishVisibility
  status: AdminWishStatus
  fruitType: AdminFruitType
  auditStatus: AdminAuditStatus
  auditStrategy: string
  isVisible: boolean
  lightCount: number
  sameWishCount: number
  blessCount: number
  supportCount: number
  expectedAt: string | null
  fulfilledAt: string | null
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface AdminWishListParams {
  userId?: number
  categoryId?: number
  status?: AdminWishStatus
  auditStatus?: AdminAuditStatus
  visibility?: AdminWishVisibility
  keyword?: string
  page?: number
  pageSize?: number
}

// ========== 枚举展示映射 ==========

export const WISH_STATUS_MAP: Record<AdminWishStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '进行中', color: 'processing' },
  OVERDUE: { label: '已逾期', color: 'warning' },
  FULFILLING: { label: '还愿中', color: 'cyan' },
  FULFILLED: { label: '已还愿', color: 'success' },
  ARCHIVED: { label: '已归档', color: 'default' },
}

export const AUDIT_STATUS_MAP: Record<AdminAuditStatus, { label: string; color: string }> = {
  PENDING: { label: '待审核', color: 'warning' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  AUTO_HIDDEN: { label: '自动隐藏', color: 'default' },
}

export const VISIBILITY_MAP: Record<AdminWishVisibility, { label: string; color: string }> = {
  PUBLIC: { label: '公开', color: 'green' },
  PRIVATE: { label: '私密', color: 'default' },
  TREE_HOLE: { label: '树洞', color: 'purple' },
}

export const FRUIT_TYPE_MAP: Record<AdminFruitType, { label: string; emoji: string }> = {
  GLOW: { label: '微光', emoji: '🌱' },
  RESONANCE: { label: '共鸣', emoji: '💫' },
  BLOOM: { label: '绽放', emoji: '🌸' },
  SPARK: { label: '星光', emoji: '⭐' },
}

// ========== 心愿列表与审核 ==========

export function getAdminWishes(params: AdminWishListParams) {
  return request.get<ApiResponse<AdminWishRecord[]>>('/admin/wish/wishes', { params })
}

export function getAdminWishDetail(id: number) {
  return request.get<ApiResponse<AdminWishRecord>>(`/admin/wish/wishes/${id}`)
}

export function auditAdminWish(id: number, data: { auditStatus: 'APPROVED' | 'REJECTED'; rejectReason?: string }) {
  return request.put<ApiResponse<AdminWishRecord>>(`/admin/wish/wishes/${id}/audit`, data)
}

// ========== 心愿分类管理 ==========

export function getAdminWishCategories() {
  return request.get<ApiResponse<AdminWishCategoryRecord[]>>('/admin/wish/categories')
}

export function createAdminWishCategory(data: { code: string; name: string; sort?: number; icon?: string }) {
  return request.post<ApiResponse<AdminWishCategoryRecord>>('/admin/wish/categories', data)
}

export function updateAdminWishCategory(id: number, data: { name?: string; sort?: number; icon?: string }) {
  return request.put<ApiResponse<AdminWishCategoryRecord>>(`/admin/wish/categories/${id}`, data)
}

export function deleteAdminWishCategory(id: number) {
  return request.delete<ApiResponse<null>>(`/admin/wish/categories/${id}`)
}

// ========== 互动记录审计（Sprint 1.2，与 mall-wish AdminInteractionVO 对齐） ==========

export type AdminInteractionType = 'LIGHT' | 'SAME_WISH' | 'BLESS'

export interface AdminInteractionRecord {
  id: number
  wishId: number
  wishTitle: string
  userId: number
  nickname: string
  type: AdminInteractionType
  content: string | null
  starlightCost: number
  deletedAt: string | null
  createdAt: string
}

export interface AdminInteractionListParams {
  wishId?: number
  userId?: number
  type?: AdminInteractionType
  /** ISO 8601，如 2026-08-18T00:00:00 */
  startTime?: string
  /** ISO 8601（含） */
  endTime?: string
  page?: number
  pageSize?: number
}

export const INTERACTION_TYPE_MAP: Record<AdminInteractionType, { label: string; emoji: string; color: string }> = {
  LIGHT: { label: '点亮', emoji: '💡', color: 'gold' },
  SAME_WISH: { label: '同求', emoji: '🤝', color: 'blue' },
  BLESS: { label: '祝福', emoji: '🌟', color: 'purple' },
}

export function getAdminWishInteractions(params: AdminInteractionListParams) {
  return request.get<ApiResponse<AdminInteractionRecord[]>>('/admin/wish/interactions', { params })
}

// ========== 评论审核（Sprint 1.2，与 mall-wish AdminCommentVO 对齐） ==========

export type AdminWishCommentStatus = 'VISIBLE' | 'HIDDEN'

export interface AdminWishCommentRecord {
  id: number
  wishId: number
  wishTitle: string
  userId: number
  nickname: string
  content: string
  parentId: number | null
  status: AdminWishCommentStatus
  sensitiveHit: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminWishCommentListParams {
  wishId?: number
  userId?: number
  sensitiveHit?: boolean
  status?: AdminWishCommentStatus
  page?: number
  pageSize?: number
}

export const WISH_COMMENT_STATUS_MAP: Record<AdminWishCommentStatus, { label: string; color: string }> = {
  VISIBLE: { label: '已上架', color: 'success' },
  HIDDEN: { label: '已下架', color: 'error' },
}

export function getAdminWishComments(params: AdminWishCommentListParams) {
  return request.get<ApiResponse<AdminWishCommentRecord[]>>('/admin/wish/comments', { params })
}

export function updateAdminWishCommentStatus(id: number, data: { status: AdminWishCommentStatus }) {
  return request.put<ApiResponse<AdminWishCommentRecord>>(`/admin/wish/comments/${id}/status`, data)
}

// ========== 徽章管理（Sprint 1.8，与 mall-wish AdminBadgeVO 对齐） ==========

export type AdminBadgeRarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
export type AdminBadgeConditionType = 'WISH_CREATED' | 'WISH_FULFILLED' | 'TOTAL_HELPED' | 'TOTAL_CHECKIN_DAYS'

export interface AdminBadgeRecord {
  id: number
  code: string
  name: string
  icon: string | null
  rarity: AdminBadgeRarity
  isActive: boolean
  /** 原始 condition JSON 字符串（编辑器回显） */
  condition: string
  createdAt: string
  updatedAt: string
}

export interface AdminBadgeCondition {
  type: AdminBadgeConditionType
  threshold: number
  description: string
}

export const BADGE_RARITY_MAP: Record<AdminBadgeRarity, { label: string; color: string }> = {
  COMMON: { label: '普通', color: 'default' },
  RARE: { label: '稀有', color: 'blue' },
  EPIC: { label: '史诗', color: 'purple' },
  LEGENDARY: { label: '传说', color: 'gold' },
}

export const BADGE_CONDITION_TYPE_MAP: Record<AdminBadgeConditionType, { label: string }> = {
  WISH_CREATED: { label: '累计许愿数' },
  WISH_FULFILLED: { label: '累计还愿数' },
  TOTAL_HELPED: { label: '累计帮助人数' },
  TOTAL_CHECKIN_DAYS: { label: '累计打卡天数' },
}

export function getAdminWishBadges() {
  return request.get<ApiResponse<AdminBadgeRecord[]>>('/admin/wish/badges')
}

export function createAdminWishBadge(data: {
  code: string
  name: string
  icon?: string
  rarity: AdminBadgeRarity
  condition: string
}) {
  return request.post<ApiResponse<AdminBadgeRecord>>('/admin/wish/badges', data)
}

export function updateAdminWishBadge(
    id: number,
    data: { name: string; icon?: string; rarity: AdminBadgeRarity; condition: string }
) {
  return request.put<ApiResponse<AdminBadgeRecord>>(`/admin/wish/badges/${id}`, data)
}

export function updateAdminWishBadgeStatus(id: number, active: boolean) {
  return request.put<ApiResponse<AdminBadgeRecord>>(`/admin/wish/badges/${id}/status`, { active })
}

// ========== 生命树环境管理（Sprint 2.2，与 mall-wish EnvConfigVO/SpecialEventVO 对齐） ==========

export type AdminEnvCategory = 'WEATHER' | 'SEASON' | 'TIME' | 'SPECIAL_EVENT'
export type AdminEnvParticle =
    | 'NONE'
    | 'RAIN'
    | 'SNOWFLAKE'
    | 'PETAL'
    | 'SUNBURST'
    | 'LEAF'
    | 'METEOR'
    | 'AURORA'
    | 'STAR'

export interface AdminEnvConfigRecord {
  id: number
  envCode: string
  category: AdminEnvCategory
  name: string
  description: string | null
  priority: number
  visual: { skyColor?: string; crownColor?: string; lightCoreColor?: string; particle?: AdminEnvParticle } | null
  isActive: boolean
}

export interface AdminSpecialEventRecord {
  id: number
  eventCode: string
  title: string
  description: string | null
  status: 'ACTIVE' | 'ENDED'
  triggeredAt: string
  expiresAt: string | null
}

export const ENV_CATEGORY_MAP: Record<AdminEnvCategory, { label: string; color: string }> = {
  WEATHER: { label: '天气', color: 'blue' },
  SEASON: { label: '季节', color: 'green' },
  TIME: { label: '时段', color: 'orange' },
  SPECIAL_EVENT: { label: '特殊事件', color: 'purple' },
}

export const ENV_PARTICLE_MAP: Record<AdminEnvParticle, { label: string }> = {
  NONE: { label: '无' },
  RAIN: { label: '雨滴' },
  SNOWFLAKE: { label: '雪花' },
  PETAL: { label: '花瓣' },
  SUNBURST: { label: '光斑' },
  LEAF: { label: '落叶' },
  METEOR: { label: '流星' },
  AURORA: { label: '极光' },
  STAR: { label: '星辰' },
}

export function getAdminEnvConfigs() {
  return request.get<ApiResponse<AdminEnvConfigRecord[]>>('/admin/wish/tree-env/configs')
}

export function createAdminEnvConfig(data: {
  envCode: string
  category: AdminEnvCategory
  name: string
  description?: string
  priority: number
  visual: string
  active?: boolean
}) {
  return request.post<ApiResponse<AdminEnvConfigRecord>>('/admin/wish/tree-env/configs', data)
}

export function updateAdminEnvConfig(
    id: number,
    data: {
      envCode: string
      category: AdminEnvCategory
      name: string
      description?: string
      priority: number
      visual: string
    }
) {
  return request.put<ApiResponse<AdminEnvConfigRecord>>(`/admin/wish/tree-env/configs/${id}`, data)
}

export function updateAdminEnvConfigStatus(id: number, active: boolean) {
  return request.put<ApiResponse<AdminEnvConfigRecord>>(`/admin/wish/tree-env/configs/${id}/status`, { active })
}

export function triggerAdminSpecialEvent(data: {
  eventCode: string
  title?: string
  description?: string
  durationMinutes?: number
}) {
  return request.post<ApiResponse<AdminSpecialEventRecord>>('/admin/wish/tree-env/special-events', data)
}

export function endAdminSpecialEvent(id: number) {
  return request.put<ApiResponse<AdminSpecialEventRecord>>(`/admin/wish/tree-env/special-events/${id}/end`)
}

export function getAdminSpecialEvents(limit = 50) {
  return request.get<ApiResponse<AdminSpecialEventRecord[]>>('/admin/wish/tree-env/special-events', {
    params: { limit },
  })
}

// ========== 时间胶囊统计（Sprint 2.4，与 mall-wish AdminCapsuleController / mall-admin 代理对齐） ==========

export interface AdminCapsuleStats {
  total: number
  sealed: number
  available: number
  opened: number
  cancelled: number
  todayCreated: number
}

/** 通知推送记录（转发 mall-notification 通知列表，字段与全站通知一致） */
export interface AdminCapsulePushRecord {
  id: number
  userId: number
  username: string | null
  type: string
  title: string | null
  content: string | null
  isRead: number
  createdAt: string
}

export interface AdminCapsulePushParams {
  userId?: number
  type?: string
  page?: number
  pageSize?: number
}

export const CAPSULE_STATUS_STAT_META: Array<{ key: keyof AdminCapsuleStats; label: string; emoji: string }> = [
  { key: 'sealed', label: '封印中', emoji: '🔒' },
  { key: 'available', label: '待开启', emoji: '🎁' },
  { key: 'opened', label: '已开启', emoji: '💌' },
  { key: 'cancelled', label: '已取消', emoji: '🌑' },
]

export function getAdminCapsuleStats() {
  return request.get<ApiResponse<AdminCapsuleStats>>('/admin/wish/capsules/stats')
}

export function getAdminCapsulePushRecords(params: AdminCapsulePushParams) {
  return request.get<ApiResponse<AdminCapsulePushRecord[]>>('/admin/wish/capsules/notifications', { params })
}
