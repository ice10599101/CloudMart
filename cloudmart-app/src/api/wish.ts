import request from '@/utils/request'
import type {
    AiConversationItem,
    BadgeDefinition,
    BadgeWallItem,
    CapsuleItem,
    AiBreakdownResult,
    MatchRecommendQuery,
    MatchGroupItem,
    MatchGroupCreated,
    MatchGroupDetail,
    LeaderboardType,
    LeaderboardEntry,
    InheritResult,
    AiGoal,
    AiGoalStatus,
    CreateAiGoalsPayload,
    MyAiGoalsQuery,
    ExpectedActionType,
    AnnualReportData,
    NotificationPreferenceMatrix,
    NotificationPreferenceUpdate,
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

/** BGM 曲目（管理端上传，sort 升序播放） */
export interface BgmSong {
    id: number
    title: string
    url: string
    sort: number
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

    // ---- 背景音乐（Sprint 2.3）----
    /** 公开播放列表（is_active=true，sort 升序；空列表由前端回退默认曲） */
    getBgmPlaylist: () => request<BgmSong[]>({ url: '/wish/bgm/playlist' }),

    // ---- AI 心愿助手（Sprint 2.5）----
    /** 意图分析 + 目标拆解（前置 AI 同意；10 次/日；403/429/503 由页面分发） */
    breakdownGoal: (data: { text: string; wishId?: number }) =>
        request<AiBreakdownResult>({ url: '/wish/ai/assistant', method: 'POST', data: data as unknown as Record<string, unknown> }),
    /** 勾选步骤批量持久化（status=PENDING） */
    createAiGoals: (data: CreateAiGoalsPayload) =>
        request<AiGoal[]>({ url: '/wish/ai/goals', method: 'POST', data: data as unknown as Record<string, unknown> }),
    /** 目标状态流转（PENDING→IN_PROGRESS→COMPLETED；非终态可 CANCELLED；终态再变更 409） */
    updateAiGoalStatus: (goalId: number, status: AiGoalStatus) =>
        request<AiGoal>({ url: `/wish/ai/goals/${goalId}`, method: 'PUT', data: { status } }),
    /** 我的 AI 目标列表（id 倒序游标分页） */
    listMyAiGoals: (params?: MyAiGoalsQuery) =>
        request<AiGoal[]>({ url: `/wish/ai/goals${buildQuery(params as Record<string, unknown>)}` }),
    /** 预期管理选项埋点（转化率分析；非本人/不存在心愿 404） */
    recordExpectedAction: (wishId: number, action: ExpectedActionType) =>
        request<null>({ url: '/wish/ai/expected-actions', method: 'POST', data: { wishId, action } }),
    /** 年度报告（growthSummary 异步 AI 生成：首次为模板文案，稍后重查返回 AI 版） */
    getAnnualReport: (year: number) =>
        request<AnnualReportData>({ url: `/wish/ai/annual-report${buildQuery({ year })}` }),

    // ---- 同愿匹配 + 监督小队（Sprint 2.6）----
    /** 匹配推荐（公开浏览；keyword/city 皆空时服务端基于心愿标签推荐） */
    recommendMatchGroups: (params?: MatchRecommendQuery) =>
        request<MatchGroupItem[]>({ url: `/wish/match/groups/recommend${buildQuery(params as Record<string, unknown>)}` }),
    /** 建组（创建者为 LEADER；429 建组日限频 / 409 同关键词已有小队 / 403 被踢冷却） */
    createMatchGroup: (data: { keyword: string; maxMembers?: number; wishId?: number }) =>
        request<MatchGroupCreated>({ url: '/wish/match/groups', method: 'POST', data: data as unknown as Record<string, unknown> }),
    /** 我的小队（ACTIVE 成员身份；含成员活跃度） */
    listMyMatchGroups: () => request<MatchGroupDetail[]>({ url: '/wish/match/groups/my' }),
    /** 小队详情（成员仅暴露昵称/头像/活跃度） */
    getMatchGroupDetail: (groupId: number) =>
        request<MatchGroupDetail>({ url: `/wish/match/groups/${groupId}` }),
    /** 加入小队（409 满员/已是成员/同主题占坑；403 被踢 24h 冷却） */
    joinMatchGroup: (groupId: number, message?: string) =>
        request<null>({ url: `/wish/match/groups/${groupId}/members`, method: 'POST', data: { message } }),
    /** 退出（target=自己）或踢人（LEADER；被踢者 24h 同主题冷却） */
    leaveMatchGroup: (groupId: number, targetUserId: number) =>
        request<null>({ url: `/wish/match/groups/${groupId}/members/${targetUserId}`, method: 'DELETE' }),
    /** 解散小队（仅组长；成员收到通知） */
    dissolveMatchGroup: (groupId: number) =>
        request<null>({ url: `/wish/match/groups/${groupId}/dissolution`, method: 'POST' }),
    /** 互相提醒（点名 targetUserId 或提醒全部 idle 组员；429 日限频） */
    remindSquadMembers: (groupId: number, targetUserId?: number) =>
        request<null>({ url: `/wish/match/groups/${groupId}/reminds${buildQuery({ targetUserId })}`, method: 'POST' }),

    // ---- 排行榜 + 传承（Sprint 2.7）----
    /** 排行榜（公开；Redis ZSet 每 10 分钟刷新；同分按创建时间早在前） */
    getLeaderboard: (type: LeaderboardType, limit = 100) =>
        request<LeaderboardEntry[]>({ url: `/wish/leaderboard${buildQuery({ type, limit })}` }),
    /** 传承推送（作者对 FULFILLED 心愿定向推送曾同求用户；一次还愿一次传承） */
    inheritFulfillment: (wishId: number, message?: string) =>
        request<InheritResult>({ url: `/wish/wishes/${wishId}/fulfillment/inherit`, method: 'POST', data: { message } }),

    // ---- 通知偏好矩阵（Sprint 2.5）----
    getNotificationPreferences: () =>
        request<NotificationPreferenceMatrix>({ url: '/wish/my/notification-preferences' }),
    /** 批量更新偏好（逐项 upsert）；一键关闭所有提醒 = 全类型×全渠道 enabled=false */
    updateNotificationPreferences: (updates: NotificationPreferenceUpdate[]) =>
        request<NotificationPreferenceMatrix>({
            url: '/wish/my/notification-preferences',
            method: 'PUT',
            data: { updates } as unknown as Record<string, unknown>,
        }),
}
