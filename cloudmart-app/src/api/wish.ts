import request from '@/utils/request'
import type {
  AiConversationItem,
  BadgeDefinition,
  BadgeWallItem,
  ConsentStatus,
  ConsentType,
  HomeAggregation,
  MyWishInteraction,
  MyWishListItem,
  TreeHoleReply,
  WishCategory,
  WishCommentItem,
  WishDetail,
  WishInteractionResult,
  WishInteractionType,
  WishStatus,
  WishVisibility,
} from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export interface CreateWishPayload {
  title: string
  description: string
  categoryId: number
  visibility: WishVisibility
  mediaUrls?: string[]
  tags?: string[]
  expectedAt?: string
}

export interface UpdateWishPayload {
  title?: string
  description?: string
  categoryId?: number
  visibility?: WishVisibility
  mediaUrls?: string[]
  tags?: string[]
  expectedAt?: string
}

export interface WishListQuery {
  categoryId?: number
  keyword?: string
  cursor?: string
  pageSize?: number
}

export interface MyWishListQuery {
  status?: WishStatus
  cursor?: string
  pageSize?: number
}

export interface CommentListQuery {
  cursor?: string
  pageSize?: number
}

export const wishApi = {
  getHome: () => request<HomeAggregation>({ url: '/wish/home' }),
  getCategories: () => request<WishCategory[]>({ url: '/wish/categories' }),
  listWishes: (params: WishListQuery) =>
    request<WishDetail[]>({ url: `/wish/wishes${buildQuery(params as Record<string, unknown>)}` }),
  getWishDetail: (id: number) => request<WishDetail>({ url: `/wish/wishes/${id}` }),
  createWish: (data: CreateWishPayload) =>
    request<WishDetail>({ url: '/wish/wishes', method: 'POST', data: data as unknown as Record<string, unknown> }),
  updateWish: (id: number, data: UpdateWishPayload) =>
    request<WishDetail>({ url: `/wish/wishes/${id}`, method: 'PUT', data: data as unknown as Record<string, unknown> }),
  deleteWish: (id: number) => request<void>({ url: `/wish/wishes/${id}`, method: 'DELETE' }),
  listMyWishes: (params: MyWishListQuery) =>
    request<MyWishListItem[]>({ url: `/wish/wishes/my${buildQuery(params as Record<string, unknown>)}` }),

  // ---- 互动（Sprint 1.2）----
  createInteraction: (wishId: number, data: { type: WishInteractionType; content?: string }) =>
    request<WishInteractionResult>({
      url: `/wish/wishes/${wishId}/interactions`,
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  revokeInteraction: (wishId: number, interactionId: number) =>
    request<{ id: number; type: WishInteractionType; revoked: boolean }>({
      url: `/wish/wishes/${wishId}/interactions/${interactionId}`,
      method: 'DELETE',
    }),
  listMyInteractions: (wishId: number) =>
    request<MyWishInteraction[]>({ url: `/wish/wishes/${wishId}/interactions/my` }),

  // ---- 评论（Sprint 1.2）----
  createComment: (wishId: number, data: { content: string; parentId?: number }) =>
    request<{ id: number; content: string; createdAt: string }>({
      url: `/wish/wishes/${wishId}/comments`,
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  listComments: (wishId: number, params?: CommentListQuery) =>
    request<WishCommentItem[]>({
      url: `/wish/wishes/${wishId}/comments${buildQuery(params as Record<string, unknown>)}`,
    }),
  deleteComment: (wishId: number, commentId: number) =>
    request<void>({ url: `/wish/wishes/${wishId}/comments/${commentId}`, method: 'DELETE' }),

  // ---- 树洞 AI（Sprint 1.3）----
  sendTreeHoleMessage: (wishId: number, message: string) =>
    request<TreeHoleReply>({
      url: '/wish/ai/tree-hole',
      method: 'POST',
      data: { wishId, message },
    }),
  listAiConversations: (params?: { scene?: string; cursor?: string; pageSize?: number }) =>
    request<AiConversationItem[]>({
      url: `/wish/ai/conversations${buildQuery(params as Record<string, unknown>)}`,
    }),
  getConsentStatus: (consentType: ConsentType = 'AI_DATA_PROCESSING') =>
    request<ConsentStatus>({ url: `/wish/my/consents?consentType=${consentType}` }),
  grantConsent: (data: { consentType: ConsentType; version: string; action?: 'GRANT' | 'WITHDRAW' }) =>
    request<void>({
      url: '/wish/my/consents',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),

  // ---- 徽章（Sprint 1.9）----
  getMyBadges: () => request<BadgeWallItem[]>({ url: '/wish/my/badges' }),
  getBadgeDefinitions: () => request<BadgeDefinition[]>({ url: '/wish/badges/definitions' }),
}
