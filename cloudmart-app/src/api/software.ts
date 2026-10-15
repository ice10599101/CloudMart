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
  content: string
  status: string
  likeCount: number
  likedByMe: boolean
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
  method: 'POST' | 'PUT' | 'DELETE',
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
  resolve: (id: string, sourceId: string) => {
    const url = `${BASE}/${id}/sources/${sourceId}/resolve`
    return write<SoftwareResolveResult>('POST', url, null, (header) =>
      request({ url, method: 'POST', data: {}, header }),
    )
  },
}
