import request from '@/utils/request'
import type {
    AiConversationItem,
    BadgeDefinition,
    BadgeWallItem,
    CapsuleItem,
    ConsentStatus,
    ConsentType,
    CreateCapsulePayload,
    EnvConfigItem,
    HomeAggregation,
    MyCapsuleListQuery,
    MyWishInteraction,
    MyWishListItem,
    SubmitFulfillmentPayload,
    TreeEnvSnapshot,
    TreeFruit,
    TreeFruitsQuery,
    TreeHoleReply,
    WorldTreeAggregation,
    WishCategory,
    WishCommentItem,
    WishDetail,
    WishFulfillmentDetail,
    WishFulfillmentSubmitResult,
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

    // ---- 还愿（Sprint 1.10）----
    submitFulfillment: (wishId: number, data: SubmitFulfillmentPayload) =>
        request<WishFulfillmentSubmitResult>({
            url: `/wish/wishes/${wishId}/fulfillment`,
            method: 'POST',
            data: data as unknown as Record<string, unknown>,
        }),
    getFulfillmentDetail: (wishId: number) =>
        request<WishFulfillmentDetail>({ url: `/wish/wishes/${wishId}/fulfillment` }),

    // ---- 世界树（Sprint 2.1）----
    /** 世界树聚合状态（公开；计数 Redis 缓存 TTL 5min，环境/季节实时） */
    getWorldTree: () => request<WorldTreeAggregation>({ url: '/wish/tree' }),
    /** 果实分页（公开；id DESC 游标 + bounds 视口过滤，异常 bounds 整组忽略退化全量） */
    listTreeFruits: (params: TreeFruitsQuery) =>
        request<TreeFruit[]>({ url: `/wish/tree/fruits${buildQuery(params as Record<string, unknown>)}` }),

    // ---- 动态环境（Sprint 2.2）----
    /** 五维环境快照（公开；displayEnv 为四端唯一渲染依据，tzOffsetMinutes 驱动 timePhase/season 计算） */
    getTreeEnv: (tzOffsetMinutes?: number) =>
        request<TreeEnvSnapshot>({
            url: `/wish/tree-env${buildQuery({ tzOffsetMinutes: tzOffsetMinutes ?? -new Date().getTimezoneOffset() })}`,
        }),
    /** 环境视觉配置清单（公开；前端据此仲裁 skyColor/crownColor/coreColor/particle） */
    listEnvConfigs: () => request<EnvConfigItem[]>({ url: '/wish/tree-env/configs' }),

    // ---- 时间胶囊（Sprint 2.4）----
    /** 创建胶囊：status=SEALED；openAt 为未来 UTC 时间（最远 10 年） */
    createCapsule: (data: CreateCapsulePayload) =>
        request<CapsuleItem>({ url: '/wish/capsules', method: 'POST', data: data as unknown as Record<string, unknown> }),
    /** 我的胶囊列表（id 倒序游标分页；非 OPENED 项不含内容） */
    listMyCapsules: (params?: MyCapsuleListQuery) =>
        request<CapsuleItem[]>({ url: `/wish/capsules${buildQuery(params as Record<string, unknown>)}` }),
    /** 胶囊详情（仅本人可见；非 OPENED 状态内容为 null） */
    getCapsuleDetail: (id: number) => request<CapsuleItem>({ url: `/wish/capsules/${id}` }),
    /** 到期开启（重复调用幂等；未到期 409） */
    openCapsule: (id: number) => request<CapsuleItem>({ url: `/wish/capsules/${id}/open`, method: 'POST' }),
    /** 取消胶囊（SEALED/AVAILABLE → CANCELLED；已开启不可取消） */
    cancelCapsule: (id: number) => request<CapsuleItem>({ url: `/wish/capsules/${id}`, method: 'DELETE' }),
    /** 时区上报（登录/启动/时区变化时；服务端幂等） */
    reportMyTimezone: (timezone: string, offsetMinutes: number) =>
        request<{ timezone: string; updated: boolean }>({
            url: '/wish/my/timezone',
            method: 'POST',
            data: { timezone, offsetMinutes },
        }),
}
