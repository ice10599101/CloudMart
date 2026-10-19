import request from '@/utils/request'
import { softwareWriteHeader, settleSoftwareKey } from '@/utils/softwareIntent'

/**
 * 小答软件 App 端 API（方案 §7.2/§7.3）：与 Web/Taro 同一后端契约；
 * 公开读取不含真实链接/提取码，只有 resolve 满足查看条件后返回且仅存内存。
 */
const BASE = '/software'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export interface SoftwarePage<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface SoftwareCard {
  id: string
  name: string
  summary: string
  iconAssetId: string | null
  categoryId: string | null
  categoryName: string | null
  licenseType: string | null
  platforms: string[]
  lastReleaseAt: string | null
  favoriteCount: number
  commentCount: number
  ratingCount: number
  ratingAvg: number | null
}

export interface SoftwareCategory {
  id: string
  name: string
  slug: string
  icon: string | null
  sortOrder: number
}

export interface SoftwareDetail {
  id: string
  name: string
  summary: string
  descriptionHtml: string
  iconAssetId: string | null
  categoryName: string | null
  tags: Array<{ id: string; name: string }>
  developerName: string | null
  attributionType: string | null
  systemRequirements: string | null
  /** SF02：安装指南（随快照审核） */
  installationGuide: string | null
  /** SF02：已知问题（随快照审核） */
  knownIssues: string | null
  /** UX03：短亮点（≤3 条） */
  highlights?: string[]
  /** UX03：同类选择（不可见目标已过滤） */
  relatedItems?: Array<{
    softwareId: string
    name: string
    summary: string | null
    relationType: 'SIMILAR_USE' | 'ALTERNATIVE_PLATFORM'
    reason: string | null
  }>
  /** UX03：发布者资料卡 */
  publisherSummary?: {
    userId: string
    nickname: string | null
    publishedCount: number
    lastPublishedAt: string | null
  } | null
  licenseType: string | null
  publishedAt: string | null
  lastReleaseAt: string | null
  commentCount: number
  favoriteCount: number
  ratingCount: number
  ratingAvg: number | null
  ownerNickname: string | null
  isFavorited: boolean
  ownerId: string
  myScore: number | null
  isSubscribed: boolean | null
}

export interface SoftwareRelease {
  releaseKey: string
  versionName: string
  channel: string
  releaseDate: string | null
  changelog: string | null
  recommended: boolean | null
  sources: Array<{
    sourceId: string
    sourceKey: string
    /** 所属版本的稳定键（SF02：兼容性反馈定位） */
    releaseKey?: string
    sourceType: string
    provider: string | null
    platform: string | null
    arch?: string | null
    label: string | null
    sizeLabel?: string | null
    claimedSha256?: string | null
    note?: string | null
    availability: string
    availabilityTip: string | null
  }>
}

export interface SoftwareAccessState {
  softwareId: string
  accessMode: string
  allowed: boolean
  reasonCode: string | null
  currentLevel: number | null
  requiredLevel: number | null
  grantExpiresAt: string | null
  contacts: Array<{ id: string; name: string; contactType: string; contactValue: string }>
}

export interface SoftwareResolveResult {
  sourceId: string
  sourceKey: string
  sourceType: string
  provider: string | null
  platform: string | null
  label: string | null
  url: string
  extractionCode: string | null
  archivePassword: string | null
  sizeLabel: string | null
  availability: string
  availabilityTip: string | null
}

export interface SoftwareTopicSummary {
  topicId: string
  slug: string
  title: string
  summary: string | null
  coverAssetId: string | null
  sortOrder: number
}

export interface SoftwareTopicDetail {
  topicId: string
  slug: string
  title: string
  summary: string | null
  coverAssetId: string | null
  items: SoftwareCard[]
  /** UX02：工具箱三要素（无数据省略） */
  audience?: string | null
  problem?: string | null
  prerequisites?: string | null
  /** UX02：稳定分组（groupKey 聚合；无分组=平铺） */
  groups?: Array<{
    groupKey: string
    items: Array<{
      softwareId: string
      name: string
      roleNote: string | null
      optional: boolean
      sortOrder: number
    }>
  }>
}

export interface SoftwareComment {
  id: string
  parentId: string | null
  userId: string
  nickname: string
  content: string
  status: string
  likeCount: number
  likedByMe: boolean
  replies?: SoftwareComment[]
  createdAt: string
  pinned: boolean
}

export interface SoftwareRatingResult {
  softwareId: string
  myScore: number | null
  ratingCount: number
  ratingAvg: number | null
}

/** 软件域写入包装：自动带 `Idempotency-Key` 并按终态清理 */
async function write<T>(
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  url: string,
  payload: unknown,
  call: (header: Record<string, string>) => Promise<{ data: { success: boolean; data: T } }>,
) {
  const header = softwareWriteHeader(method, url, payload)
  try {
    const res = await call(header)
    settleSoftwareKey(method, url, payload)
    return res
  } catch (error) {
    settleSoftwareKey(method, url, payload, error)
    throw error
  }
}

export const softwareApi = {
  list: (params?: {
    keyword?: string
    categoryId?: string
    license?: string
    sort?: string
    page?: number
    size?: number
  }) =>
    request<SoftwarePage<SoftwareCard>>({
      url: `${BASE}${buildQuery(params as Record<string, unknown>)}`,
    }),
  categories: () => request<SoftwareCategory[]>({ url: `${BASE}/categories` }),
  detail: (id: string) => request<SoftwareDetail>({ url: `${BASE}/${id}` }),
  accessState: (id: string) => request<SoftwareAccessState>({ url: `${BASE}/${id}/access-state` }),
  releases: (id: string, page = 1, size = 20) =>
    request<SoftwarePage<SoftwareRelease>>({
      url: `${BASE}/${id}/releases${buildQuery({ page, size })}`,
    }),
  comments: (id: string, page = 1, size = 20) =>
    request<SoftwarePage<SoftwareComment>>({
      url: `${BASE}/${id}/comments${buildQuery({ page, size })}`,
    }),
  /** SW11：回复楼中楼——parentId 恒指向根楼，replyToCommentId 精确到目标 */
  createComment: (
    id: string,
    content: string,
    parentId?: string,
    replyToCommentId?: string,
  ) => {
    const url = `${BASE}/${id}/comments`
    const payload = {
      content,
      ...(parentId ? { parentId } : {}),
      ...(replyToCommentId ? { replyToCommentId } : {}),
    }
    return write<SoftwareComment>('POST', url, payload, (header) =>
      request({ url, method: 'POST', data: payload, header }),
    )
  },

  /** SW11：删除自己的评论 */
  deleteComment: (id: string, commentId: string) =>
    write<void>('DELETE', `${BASE}/${id}/comments/${commentId}`, {}, (header) =>
      request({ url: `${BASE}/${id}/comments/${commentId}`, method: 'DELETE', header }),
    ),

  /** SW11：软件/评论举报 */
  report: (
    id: string,
    payload: {
      targetType: 'SOFTWARE' | 'SOFTWARE_COMMENT'
      targetId?: string
      reason: string
      description?: string
    },
  ) => {
    const url = `${BASE}/${id}/reports`
    return write<{ reportId: string; status: string }>('POST', url, payload, (header) =>
      request({ url, method: 'POST', data: payload, header }),
    )
  },

  /** SW11：来源失效反馈（LINK_INVALID/CODE_WRONG/FILE_MISMATCH/SUSPICIOUS/OTHER） */
  /** SF02：兼容性反馈可携带 releaseKey/平台/架构/系统版本文本（不收集设备标识） */
  sourceFeedback: (
    id: string,
    sourceId: string,
    reasonCode: string,
    context?: {
      releaseKey?: string | null
      platform?: string | null
      arch?: string | null
      systemVersionText?: string | null
    },
  ) => {
    const url = `${BASE}/${id}/sources/${sourceId}/feedback`
    const payload = { reasonCode, ...(context ?? {}) }
    return write<{ feedbackId: string; status: string }>('POST', url, payload, (header) =>
      request({ url, method: 'POST', data: payload, header }),
    )
  },

  /** SW11：榜单（access/favorite/rating，口径由服务端定义） */
  rankings: (type: 'access' | 'favorite' | 'rating', page = 1, size = 20) =>
    request<SoftwarePage<SoftwareCard>>({
      url: `${BASE}/rankings${buildQuery({ type, page, size })}`,
    }),

  /** SW11-3：专题列表（只返回 PUBLISHED） */
  topics: () => request<SoftwareTopicSummary[]>({ url: `${BASE}/topics` }),
  /** SW11-3：专题详情（清单按实时可见性过滤） */
  topic: (slug: string) =>
    request<SoftwareTopicDetail>({ url: `${BASE}/topics/${slug}` }),

  /** SW11：更新提醒偏好（与订阅关系相互独立） */
  preferences: () => request<{ updateNotifications: boolean }>({ url: `${BASE}/preferences` }),
  updatePreferences: (updateNotifications: boolean) => {
    const url = `${BASE}/preferences`
    const payload = { updateNotifications }
    return write<{ updateNotifications: boolean }>('PUT', url, payload, (header) =>
      request({ url, method: 'PUT', data: payload, header }),
    )
  },
  likeComment: (id: string, commentId: string) =>
    request<void>({ url: `${BASE}/${id}/comments/${commentId}/like`, method: 'PUT' }),
  unlikeComment: (id: string, commentId: string) =>
    request<void>({ url: `${BASE}/${id}/comments/${commentId}/like`, method: 'DELETE' }),
  favorite: (id: string) => request<void>({ url: `${BASE}/${id}/favorite`, method: 'PUT' }),
  unfavorite: (id: string) => request<void>({ url: `${BASE}/${id}/favorite`, method: 'DELETE' }),
  subscribe: (id: string) => request<void>({ url: `${BASE}/${id}/subscription`, method: 'PUT' }),
  unsubscribe: (id: string) =>
    request<void>({ url: `${BASE}/${id}/subscription`, method: 'DELETE' }),
  rate: (id: string, score: number) => {
    const url = `${BASE}/${id}/rating`
    const payload = { score }
    return write<SoftwareRatingResult>('PUT', url, payload, (header) =>
      request({ url, method: 'PUT', data: payload, header }),
    )
  },

  /** SW11：撤销评分（真实均分随刷新返回） */
  revokeRating: (id: string) =>
    write<void>('DELETE', `${BASE}/${id}/rating`, {}, (header) =>
      request({ url: `${BASE}/${id}/rating`, method: 'DELETE', header }),
    ),
  resolve: (id: string, sourceId: string) => {
    const url = `${BASE}/${id}/sources/${sourceId}/resolve`
    return write<SoftwareResolveResult>('POST', url, null, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },
}

// ---------------------------------------------------------------------------
// S13 发布流（作者侧）：与 Web/Taro 同一后端契约
// ---------------------------------------------------------------------------

export interface PublisherStatus {
  publishMode?: 'DENIED' | 'DIRECT' | 'REVIEW'
  canPublish: boolean
  reasonCode: string | null
  publishEnabled: boolean
}

export interface SoftwareMineItem {
  softwareId: string
  name: string
  summary: string
  status: 'PUBLISHED' | 'UNLISTED' | 'BANNED' | 'DRAFT' | 'PENDING' | 'REJECTED'
  revisionId: string
  favoriteCount: number
  updatedAt: string
  unavailable: boolean
}

export interface SoftwareContact {
  id: string
  name: string
  contactType: string
  contactValue: string
}

export interface SoftwareEditorSource {
  sourceId?: string
  sourceKey?: string
  sourceType:    | 'NETDISK'
    | 'MAGNET'
    | 'TORRENT_URL'
    | 'HTTPS_DOWNLOAD'
    | 'OFFICIAL_PAGE'
    | 'WEB_APP'
  provider?: string | null
  platform: 'WINDOWS' | 'MACOS' | 'LINUX' | 'ANDROID' | 'IOS' | 'CROSS_PLATFORM' | 'WEB'
  arch: 'X64' | 'X86' | 'ARM64' | 'UNIVERSAL' | 'OTHER' | 'ANY'
  label: string | null
  url: string | null
  extractionCode: string | null
  archivePassword: string | null
  sizeLabel: string | null
  claimedSha256?: string | null
  note?: string | null
  displayOrder?: number
}

export interface SoftwareEditorRelease {
  releaseId?: string
  releaseKey?: string
  versionName: string
  channel: 'STABLE' | 'BETA'
  releaseDate?: string | null
  changelog: string | null
  recommended: boolean | null
  displayOrder?: number
  sources: SoftwareEditorSource[]
}

export interface SoftwareEditorMedia {
  assetId: string
  altText: string | null
  displayOrder: number
}

export interface SoftwareEditor {
  softwareId: string
  revisionId: string
  reviewStatus: 'DRAFT' | 'PENDING' | 'WITHDRAWN' | 'APPROVED' | 'REJECTED'
  name: string
  summary: string
  categoryId: string | null
  iconAssetId: string | null
  descriptionHtml: string
  licenseType: 'FREE' | 'OPEN_SOURCE' | 'TRIAL' | 'COMMERCIAL' | null
  developerName: string | null
  officialUrl: string | null
  attributionType: 'ORIGINAL' | 'AUTHORIZED' | 'OFFICIAL_PUBLIC' | null
  attributionNote: string | null
  systemRequirements: string | null
  /** SF02：安装指南/已知问题（随快照审核） */
  installationGuide: string | null
  knownIssues: string | null
  languages: string[] | null
  accessMode: 'REGISTERED' | 'COMMUNITY_LEVEL' | 'ADMIN_PAID'
  minCommunityLevel: number | null
  accessContactId: string | null
  accessNote: string | null
  tagIds: string[] | null
  screenshots: SoftwareEditorMedia[]
  releases: SoftwareEditorRelease[]
  reviewReason: string | null
  lifecycle: 'ACTIVE' | 'UNLISTED' | 'BANNED' | null
  publishedRevisionId: string | null
  workingRevisionId: string | null
}

export interface SoftwareCreated {
  softwareId: string
  workingRevisionId: string
}

export interface SoftwareSubmitted {
  softwareId: string
  revisionId: string
  reviewStatus: 'DRAFT' | 'PENDING' | 'WITHDRAWN' | 'APPROVED' | 'REJECTED'
}

/** 移动端纯文本 → 后端 HTML（服务端消毒） */
export function textToDescriptionHtml(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return `<p>${escaped.replace(/\n/g, '<br/>')}</p>`
}

/** 编辑器 HTML → 纯文本回填（简易去标签） */
export function descriptionHtmlToText(html: string | null): string {
  if (!html) return ''
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>\s*<p[^>]*>/gi, '\n\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .trim()
}

export const softwarePublishApi = {
  publisherStatus: () => request<PublisherStatus>({ url: `${BASE}/publisher-status` }),

  mine: (page = 1, size = 20, tab: 'published' | 'draft' | 'pending' | 'rejected'
    | 'favorites' | 'subscriptions' = 'published') =>
    request<SoftwarePage<SoftwareMineItem>>({
      url: `${BASE}/mine${buildQuery({ tab, page, size })}`,
    }),

  contacts: () => request<SoftwareContact[]>({ url: `${BASE}/access-contacts` }),

  create: () => {
    const url = BASE
    return write<SoftwareCreated>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  editor: (id: string) => request<SoftwareEditor>({ url: `${BASE}/${id}/editor` }),

  workingRevision: (id: string) => {
    const url = `${BASE}/${id}/working-revision`
    return write<SoftwareEditor>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  saveDraft: (id: string, revisionId: string, snapshot: Record<string, unknown>) => {
    const url = `${BASE}/${id}/revisions/${revisionId}`
    return write<SoftwareEditor>('PUT', url, snapshot, (header) =>
      request({ url, method: 'PUT', data: snapshot, header }),
    )
  },

  /** SW02：局部保存——只提交用户实际修改的字段；缺省字段保持服务端现值 */
  patchDraft: (id: string, revisionId: string, patch: Record<string, unknown>) => {
    const url = `${BASE}/${id}/revisions/${revisionId}`
    return write<SoftwareEditor>('PATCH', url, patch, (header) =>
      request({ url, method: 'PATCH', data: patch, header }),
    )
  },

  submit: (id: string, revisionId: string) => {
    const url = `${BASE}/${id}/revisions/${revisionId}/submit`
    return write<SoftwareSubmitted>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  withdraw: (id: string, revisionId: string) => {
    const url = `${BASE}/${id}/revisions/${revisionId}/withdraw`
    return write<SoftwareEditor>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  unlist: (id: string) => {
    const url = `${BASE}/${id}/unlist`
    return write<SoftwareEditor>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  relist: (id: string) => {
    const url = `${BASE}/${id}/relist`
    return write<SoftwareEditor>('POST', url, {}, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  deleteSoftware: (id: string) => {
    const url = `${BASE}/${id}`
    return write<void>('DELETE', url, {}, (header) =>
      request({ url, method: 'DELETE', header }),
    )
  },
}


// ==================== UX01/UX05/UX06 移动端 API ====================

export interface SoftwareHomeSectionItem {
  itemId: string
  targetType: 'SOFTWARE' | 'TOPIC' | 'GUIDE' | 'FILTER'
  headline?: string
  editorNote?: string
  software?: SoftwareCard
  topic?: { id: string; slug: string; title: string; summary?: string }
  guide?: { id: string; title: string; summary?: string; guideType?: string }
}

export interface SoftwareHomeSection {
  id: string
  slotKey: string
  kind: 'SPOTLIGHT' | 'SCENARIOS' | 'SOFTWARE_GRID' | 'RANKING_LIST' | 'UPDATE_FEED' | 'GUIDE_LIST'
  title: string
  subtitle?: string
  state?: 'OK' | 'ERROR' | 'EMPTY'
  items: SoftwareHomeSectionItem[]
  hasMore: boolean
}

export function getSoftwareHome(platform: 'APP' = 'APP') {
  return request<SoftwareHomeSection[]>({
    url: `${BASE}/home`,
    method: 'GET',
    data: { platform },
  })
}

export interface SoftwareUpdateItem {
  eventId: string
  softwareId: string
  read: boolean
  publishedAt: string | null
  businessChange?: boolean
  softwareName?: string
  versionName?: string
  changelog?: string
  softwareVisible: boolean
}

export function listMyUpdates(cursor: string | null, size = 20) {
  return request<{
    items: SoftwareUpdateItem[]
    hasMore: boolean
    nextCursor: string | null
  }>({
    url: `${BASE}/updates/mine`,
    method: 'GET',
    data: { cursor: cursor ?? 0, size },
  })
}

export function markUpdatesRead(eventIds: string[]) {
  return request<number>({
    url: `${BASE}/updates/read`,
    method: 'POST',
    data: { eventIds: eventIds.map((v) => Number(v)) },
  })
}

export function getUpdatesUnreadCount() {
  return request<number>({ url: `${BASE}/updates/unread-count`, method: 'GET' })
}

export function createSoftwareRequest(input: {
  title: string
  usageDesc: string
  mustHaveFeatures?: string[]
  platformScope?: string[]
  acceptableLicense?: string[]
  sourceKeyword?: string
}) {
  return request<string>({ url: `${BASE}/requests`, method: 'POST', data: input })
}

export function listMySoftwareRequests() {
  return request<Array<{
    id: string
    title: string
    usageDesc: string
    status: string
    auditStatus: string
    auditReason: string | null
    createdAt: string | null
  }>>({ url: `${BASE}/requests/mine`, method: 'GET' })
}
