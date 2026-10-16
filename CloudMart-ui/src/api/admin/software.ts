import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { PageResult } from '@/api/software'
import { softwareWriteConfig } from '@/utils/softwareIntent'

/**
 * 小答软件管理端 API（方案 7.5）：外部 /api/admin/software/** 经 mall-admin 代理，
 * 权限点与后台菜单种子一一对应（software:list/review/moderate/feature/category/
 * comment/report/link/publisher:list|manage/access:list|manage/contact:manage/
 * permission:audit）。
 *
 * 写操作统一走 softwareWriteConfig 生成 `Idempotency-Key`（终态后换新键）；
 * 精选/封禁/解除额外要求 expectedVersion（软件主体 lockVersion，列表已返回）。
 */

const BASE = '/admin/software'

// ==================== 类型 ====================

export type SoftwareLifecycle = 'ACTIVE' | 'UNLISTED' | 'BANNED'

export interface AdminSoftwareItem {
  softwareId: string
  ownerId: string | null
  name: string | null
  summary: string | null
  lifecycle: SoftwareLifecycle
  featuredRank: number
  lockVersion: number
  publicationSeq: number | null
  publishedRevisionId: string | null
  workingRevisionId: string | null
  pendingRevisionId: string | null
  categoryId: string | null
  categoryName: string | null
  licenseType: string | null
  commentCount: number
  favoriteCount: number
  linkAccessCount: number
  ratingCount: number
  ratingSum: number
  publishedAt: string | null
  lastReleaseAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminSoftwareDetail extends Record<string, unknown> {
  softwareId: string
  ownerId: string
  lifecycle: string
  lockVersion: number
  featuredRank: number
  pendingRevisionId: string | null
  name: string | null
  summary: string | null
  reviewStatus: string | null
  publishedRevisionId: string | null
  workingRevisionId: string | null
  accessMode: string | null
  minCommunityLevel: number | null
  accessNote: string | null
  reviewReason: string | null
  auditTrail: Array<Record<string, string>>
}

/** 审核快照：目标（待审/已审）与当前线上版；均为编辑器完整结构（含链接/提取码） */
export interface AdminReviewDetail extends Record<string, unknown> {
  softwareId: string
  revisionId: string
  reviewStatus: string
  submitSeq: number
  lockVersion: number
  /** 审核决策的 expectedVersion 语义（软件主体乐观锁） */
  softwareLockVersion: number
  lifecycle: string
  publishedRevisionId: string | null
  target: AdminRevisionSnapshot | null
  published: AdminRevisionSnapshot | null
}

export interface AdminRevisionSnapshot extends Record<string, unknown> {
  revisionId: string
  name: string | null
  summary: string | null
  categoryId: string | null
  descriptionHtml: string | null
  licenseType: string | null
  attributionType: string | null
  attributionNote: string | null
  developerName: string | null
  officialUrl: string | null
  systemRequirements: string | null
  accessMode: string | null
  minCommunityLevel: number | null
  accessNote: string | null
  tagIds: string[]
  screenshots: Array<{ assetId: string; altText: string | null; displayOrder: number }>
  releases: Array<{
    releaseKey: string
    versionName: string
    channel: string
    recommended: boolean | null
    sources: Array<{
      sourceKey: string
      sourceType: string
      platform: string | null
      label: string | null
      url: string
      extractionCode: string | null
      archivePassword: string | null
      note: string | null
    }>
  }>
}

export interface AdminSoftwareComment {
  commentId: string
  softwareId: string
  userId: string
  content: string | null
  status: 'PENDING' | 'VISIBLE' | 'HIDDEN' | 'DELETED'
  likeCount: number
  createdAt: string
}

export interface AdminSoftwareCategory {
  categoryId: string
  name: string
  slug: string
  icon: string | null
  sortOrder: number
  enabled: boolean
  updatedAt: string
}

export interface AdminSoftwareTag {
  tagId: string
  name: string
  enabled: boolean
  updatedAt: string
}

export interface AdminPublisherPermission {
  userId: string
  userNickname?: string | null
  canPublish: boolean
  lockVersion: number
  reason: string | null
  updatedBy: string | null
  updatedByName?: string | null
  updatedAt: string | null
}

export interface AdminAccessGrant {
  softwareId: string
  userId: string
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
  grantedBy: string | null
  grantedAt: string
  expiresAt: string | null
  confirmationNote: string | null
  externalReference: string | null
  lockVersion: number
}

export interface AdminAccessContact {
  contactId: string
  name: string
  contactType: string
  contactValue: string
  enabled: boolean
  sortOrder: number
}

export interface AdminSoftwareReport {
  reportId: string
  reporterId: string
  softwareId: string
  targetType: 'SOFTWARE' | 'SOFTWARE_COMMENT'
  targetId: string | null
  reason: string
  description: string | null
  status: 'OPEN' | 'VERIFYING' | 'REJECTED' | 'RESOLVED'
  handleNote: string | null
  createdAt: string
}

export interface AdminLinkFeedback {
  feedbackId: string
  softwareId: string
  sourceKey: string
  reporterId: string
  reasonCode: string
  description: string | null
  status: string
  resolvedRevisionId: string | null
  createdAt: string
}

// ==================== 目录（software:list/moderate/feature） ====================

export function listAdminSoftware(params?: {
  keyword?: string
  ownerId?: string
  categoryId?: string
  license?: string
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AdminSoftwareItem>>>(`${BASE}/items`, { params })
}

export function getAdminSoftwareDetail(softwareId: string) {
  return request.get<ApiResponse<AdminSoftwareDetail>>(`${BASE}/${softwareId}/admin-detail`)
}

/** 独立封禁：必须原因与 expectedVersion */
export function banAdminSoftware(
  softwareId: string,
  payload: { reason: string; expectedVersion: number },
  idempotencyKey?: string,
) {
  const url = `${BASE}/items/${softwareId}/ban`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

/** 解除封禁：BANNED → UNLISTED（由作者恢复展示） */
export function unbanAdminSoftware(
  softwareId: string,
  payload: { reason: string; expectedVersion: number },
  idempotencyKey?: string,
) {
  const url = `${BASE}/items/${softwareId}/unban`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

/** 精选排序：featuredRank=0 取消精选 */
export function featureAdminSoftware(
  softwareId: string,
  payload: { featuredRank: number; expectedVersion: number },
  idempotencyKey?: string,
) {
  const url = `${BASE}/items/${softwareId}/featured`
  return request.put<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('PUT', url, payload, idempotencyKey),
  )
}

// ==================== 审核（software:review） ====================

export function listPendingReviews(params?: { page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<Record<string, unknown>>>>(
    `${BASE}/revisions/pending`,
    { params },
  )
}

export function getReviewDetail(revisionId: string) {
  return request.get<ApiResponse<AdminReviewDetail>>(`${BASE}/revisions/${revisionId}`)
}

export function decideReview(
  revisionId: string,
  payload: {
    softwareId: string
    submitSeq: number
    expectedVersion: number
    approve: boolean
    reason: string
  },
  idempotencyKey?: string,
) {
  const url = `${BASE}/revisions/${revisionId}/decision`
  return request.post<ApiResponse<Record<string, unknown>>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

/** 来源状态处置：VERIFY / DISABLE / RESTORE（software:link） */
export function setSourceState(
  softwareId: string,
  payload: { sourceKey: string; availability: string; reason: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/${softwareId}/sources/state`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

// ==================== 评论（software:comment） ====================

/** 软件模块运营统计总览（Dashboard 软件区块数据源） */
export function getAdminSoftwareStatistics() {
  return request.get<ApiResponse<Record<string, number | string>>>(
    `${BASE}/statistics`,
  )
}

/** 全局评论列表（softwareId 可选筛选；后端 /comments 固定路径） */
export function listAdminSoftwareComments(
  softwareId: string | undefined,
  params?: { status?: string; page?: number; size?: number },
) {
  return request.get<ApiResponse<PageResult<AdminSoftwareComment>>>(
    `${BASE}/comments`,
    { params: { ...params, softwareId: softwareId || undefined } },
  )
}

export function moderateAdminSoftwareComment(
  softwareId: string,
  commentId: string,
  payload: { action: 'APPROVE' | 'HIDE' | 'RESTORE' | 'REJECT'; reason: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/${softwareId}/comments/${commentId}/moderate`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

// ==================== 分类与标签（software:category） ====================

export function listAdminSoftwareCategories() {
  return request.get<ApiResponse<AdminSoftwareCategory[]>>(`${BASE}/categories`)
}

export function createSoftwareCategory(
  payload: { name: string; slug: string; icon?: string; sortOrder?: number; enabled?: boolean },
  idempotencyKey?: string,
) {
  const url = `${BASE}/categories`
  return request.post<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function updateSoftwareCategory(
  categoryId: string,
  payload: { name: string; slug: string; icon?: string; sortOrder?: number; enabled?: boolean },
  idempotencyKey?: string,
) {
  const url = `${BASE}/categories/${categoryId}`
  return request.put<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('PUT', url, payload, idempotencyKey),
  )
}

/** 管理员删除软件（软删 lifecycle=DELETED，幂等；公开面即时隐藏） */
export function deleteAdminSoftware(
  softwareId: string,
  expectedVersion: number,
  idempotencyKey?: string,
) {
  const url = `${BASE}/items/${softwareId}?expectedVersion=${expectedVersion}`
  return request.delete<ApiResponse<boolean>>(
    url,
    softwareWriteConfig('DELETE', url, null, idempotencyKey),
  )
}

/** DELETE 语义为禁用（被引用分类不做物理删除） */
export function disableSoftwareCategory(categoryId: string, idempotencyKey?: string) {
  const url = `${BASE}/categories/${categoryId}`
  return request.delete<ApiResponse<boolean>>(
    url,
    softwareWriteConfig('DELETE', url, null, idempotencyKey),
  )
}

export function listAdminSoftwareTags(params?: {
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AdminSoftwareTag>>>(`${BASE}/tags`, { params })
}

export function createSoftwareTag(
  payload: { name: string; enabled?: boolean },
  idempotencyKey?: string,
) {
  const url = `${BASE}/tags`
  return request.post<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function updateSoftwareTag(
  tagId: string,
  payload: { name: string; enabled?: boolean },
  idempotencyKey?: string,
) {
  const url = `${BASE}/tags/${tagId}`
  return request.put<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('PUT', url, payload, idempotencyKey),
  )
}

/** DELETE 语义为禁用 */
export function disableSoftwareTag(tagId: string, idempotencyKey?: string) {
  const url = `${BASE}/tags/${tagId}`
  return request.delete<ApiResponse<boolean>>(
    url,
    softwareWriteConfig('DELETE', url, null, idempotencyKey),
  )
}

// ==================== 发布资格（publisher:list/manage） ====================

export function listPublisherPermissions(params?: { page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<AdminPublisherPermission>>>(
    `${BASE}/publisher-permissions`,
    { params },
  )
}

/** 会员详情页批量查询（缺失记录=false） */
export function batchPublisherPermissions(userIds: Array<string | number>) {
  return request.post<ApiResponse<AdminPublisherPermission[]>>(
    `${BASE}/publisher-permissions/batch`,
    userIds,
  )
}

export function togglePublisherPermission(
  userId: string,
  payload: { enable: boolean; reason: string; expectedVersion?: number },
  idempotencyKey?: string,
) {
  const url = `${BASE}/publisher-permissions/${userId}/toggle`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

// ==================== 付款授权与审计（access:list/manage、permission:audit） ====================

export function listAccessGrantsBySoftware(softwareId: string) {
  return request.get<ApiResponse<AdminAccessGrant[]>>(`${BASE}/${softwareId}/access-grants`)
}

export function listAccessGrants(params?: {
  softwareId?: string
  userId?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AdminAccessGrant>>>(`${BASE}/access-grants`, { params })
}

export function grantSoftwareAccess(
  softwareId: string,
  payload: {
    userId: string
    expiresAt?: string | null
    confirmationNote?: string
    externalReference?: string
    reason: string
  },
  idempotencyKey?: string,
) {
  const url = `${BASE}/${softwareId}/access-grants`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function revokeSoftwareAccess(
  softwareId: string,
  userId: string,
  payload: { reason: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/${softwareId}/access-grants/${userId}/revoke`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function listPermissionAudits(params?: {
  userId?: string
  softwareId?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Record<string, string>>>>(
    `${BASE}/permission-audits`,
    { params },
  )
}

// ==================== 联系入口（contact:manage） ====================

export function listAccessContacts() {
  return request.get<ApiResponse<AdminAccessContact[]>>(`${BASE}/access-contacts`)
}

export function saveAccessContact(
  payload: {
    id?: string
    name: string
    contactType: string
    contactValue: string
    enabled?: boolean
    sortOrder?: number
    reason?: string
  },
  idempotencyKey?: string,
) {
  const url = `${BASE}/access-contacts`
  return request.post<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

// ==================== 举报与失效反馈（report / link） ====================

export function listSoftwareReports(params?: {
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AdminSoftwareReport>>>(`${BASE}/reports`, { params })
}

export function handleSoftwareReport(
  reportId: string,
  payload: {
    status: 'RESOLVED' | 'REJECTED'
    action?: 'NONE' | 'BAN_SOFTWARE' | 'HIDE_COMMENT'
    note: string
  },
  idempotencyKey?: string,
) {
  const url = `${BASE}/reports/${reportId}/handle`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function listLinkFeedbacks(params?: {
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AdminLinkFeedback>>>(`${BASE}/link-feedbacks`, {
    params,
  })
}

export function markLinkFeedback(
  feedbackId: string,
  payload: { status: string; resolvedRevisionId?: number | null; note?: string },
  idempotencyKey?: string,
) {
  const url = `${BASE}/link-feedbacks/${feedbackId}/mark`
  return request.post<ApiResponse<boolean>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

// ==================== 专题（P1：software:topic） ====================

export interface AdminSoftwareTopic {
  topicId: string
  slug: string
  title: string
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  sortOrder: number
  itemCount: number
  updatedAt: string
}

export interface AdminTopicPayload {
  slug: string
  title: string
  summary?: string
  coverAssetId?: number
  status?: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  sortOrder?: number
  items: Array<{ softwareId: number; sortOrder?: number }>
}

export function listAdminTopics(params?: { page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<AdminSoftwareTopic>>>(`${BASE}/topics`, { params })
}

/** 专题编辑读取：专题 + 原始清单（不过滤可见性） */
export function getAdminTopic(topicId: string) {
  return request.get<ApiResponse<AdminTopicPayload & { topicId: string }>>(
    `${BASE}/topics/${topicId}`,
  )
}

export function createAdminTopic(payload: AdminTopicPayload, idempotencyKey?: string) {
  const url = `${BASE}/topics`
  return request.post<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('POST', url, payload, idempotencyKey),
  )
}

export function updateAdminTopic(
  topicId: string,
  payload: AdminTopicPayload,
  idempotencyKey?: string,
) {
  const url = `${BASE}/topics/${topicId}`
  return request.put<ApiResponse<string>>(
    url,
    payload,
    softwareWriteConfig('PUT', url, payload, idempotencyKey),
  )
}

/** DELETE 语义为禁用 */
export function disableAdminTopic(topicId: string, idempotencyKey?: string) {
  const url = `${BASE}/topics/${topicId}`
  return request.delete<ApiResponse<boolean>>(
    url,
    softwareWriteConfig('DELETE', url, null, idempotencyKey),
  )
}

// ==================== 统计（P1：software:stats） ====================

export interface AdminSoftwareStatistics {
  windowDays: number
  windowNote: string
  publishedCount: number
  pendingReviewCount: number
  avgReviewMinutes: number | null
  accessCount: number
  accessUsers: number
  favoriteTotal: number
  newFavorites: number
  newRatings: number
  newRatingAvg: number | null
  pendingFeedbacks: number
  pendingReports: number
}

export function getSoftwareStatistics() {
  return request.get<ApiResponse<AdminSoftwareStatistics>>(`${BASE}/statistics`)
}
