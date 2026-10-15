import request from '@/utils/request'
import {
  softwareWriteHeader,
  clearSoftwareKey,
  settleSoftwareKey,
} from '@/utils/softwareIntent'

/**
 * 小答软件 移动端 API（方案 §7.2/§7.3）：与 Web 端同一后端契约；
 * 公开读取不含真实链接/提取码，只有 resolve 满足查看条件后返回。
 * 写操作统一携带 `Idempotency-Key`（软件域契约，见 softwareIntent.ts）。
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

/** Spring Page 响应（records 语义，与 Web 端一致） */
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
  categoryId: string | null
  categoryName: string | null
  tags: Array<{ id: string; name: string }>
  screenshots: Array<{ assetId: string; displayOrder: number; altText: string | null }>
  developerName: string | null
  officialUrl: string | null
  licenseType: string | null
  attributionType: string | null
  attributionNote: string | null
  systemRequirements: string | null
  languages: string[]
  publishedAt: string | null
  lastReleaseAt: string | null
  commentCount: number
  favoriteCount: number
  ratingCount: number
  ratingAvg: number | null
  ownerNickname: string | null
  ownerAvatar: string | null
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
    sourceType: string
    provider: string | null
    platform: string | null
    label: string | null
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

export interface SoftwareComment {
  id: string
  parentId: string | null
  userId: string
  nickname: string
  avatar: string | null
  content: string
  status: 'VISIBLE' | 'PENDING' | 'HIDDEN' | 'DELETED' | 'SELF_PENDING'
  likeCount: number
  likedByMe: boolean
  createdAt: string
  replies: SoftwareComment[]
  replyHasMore: boolean
  pinned: boolean
}

export interface SoftwareRatingResult {
  softwareId: string
  myScore: number | null
  ratingCount: number
  ratingAvg: number | null
}

export interface SoftwarePreference {
  updateNotifications: boolean
  lockVersion: number
}

/** 软件域写入包装：自动带 `Idempotency-Key` 并按终态清理 */
async function write<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  url: string,
  payload: unknown,
  call: (header: Record<string, string>) => Promise<{ data: unknown }>,
) {
  const header = softwareWriteHeader(method, url, payload)
  try {
    const res = await call(header)
    clearSoftwareKey(method, url, payload)
    return res as { data: { success: boolean; data: T } }
  } catch (error) {
    settleSoftwareKey(method, url, payload, error)
    throw error
  }
}

export const softwareApi = {
  // ---- 公开浏览 ----
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
  rankings: (type: 'access' | 'favorite' | 'rating', page = 1, size = 20) =>
    request<SoftwarePage<SoftwareCard>>({
      url: `${BASE}/rankings${buildQuery({ type, page, size })}`,
    }),

  // ---- 评论 ----
  comments: (id: string, page = 1, size = 20) =>
    request<SoftwarePage<SoftwareComment>>({
      url: `${BASE}/${id}/comments${buildQuery({ page, size })}`,
    }),
  createComment: (id: string, content: string) => {
    const url = `${BASE}/${id}/comments`
    const payload = { content }
    return write<SoftwareComment>('POST', url, payload, (header) =>
      request({ url, method: 'POST', data: payload, header }),
    )
  },
  likeComment: (id: string, commentId: string) =>
    request<void>({ url: `${BASE}/${id}/comments/${commentId}/like`, method: 'PUT' }),
  unlikeComment: (id: string, commentId: string) =>
    request<void>({ url: `${BASE}/${id}/comments/${commentId}/like`, method: 'DELETE' }),

  // ---- 收藏 / 订阅 / 评分（P1） ----
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
  revokeRating: (id: string) =>
    request<SoftwareRatingResult>({ url: `${BASE}/${id}/rating`, method: 'DELETE' }),
  preferences: () => request<SoftwarePreference>({ url: `${BASE}/preferences` }),
  updatePreferences: (updateNotifications: boolean) => {
    const url = `${BASE}/preferences`
    const payload = { updateNotifications }
    return write<SoftwarePreference>('PUT', url, payload, (header) =>
      request({ url, method: 'PUT', data: payload, header }),
    )
  },

  // ---- 获取链接：满足查看条件才返回真实链接/提取码（不缓存、不落存储） ----
  resolve: (id: string, sourceId: string) => {
    const url = `${BASE}/${id}/sources/${sourceId}/resolve`
    return write<SoftwareResolveResult>('POST', url, null, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },

  // ---- 举报（软件/评论；复用同一幂等层） ----
  report: (id: string, payload: { targetType: 'SOFTWARE' | 'SOFTWARE_COMMENT'; targetId?: string; reason: string; description?: string }) => {
    const url = `${BASE}/${id}/reports`
    return write<{ reportId: string; status: string }>('POST', url, payload, (header) =>
      request({ url, method: 'POST', data: payload, header }),
    )
  },
}
