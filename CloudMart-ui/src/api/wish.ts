import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== Types ==========

export type WishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type WishStatus = 'DRAFT' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED'
export type FruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'
export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_HIDDEN'

export interface WishListItem {
  id: number | string
  title: string
  description: string
  mediaUrls: string[]
  categoryId: number
  categoryName: string
  tags: string[]
  visibility: WishVisibility
  status: WishStatus
  fruitType: FruitType
  authorId: number
  authorNickname: string
  authorAvatar: string
  lightCount: number
  sameWishCount: number
  blessCount: number
  supportCount: number
  commentCount: number
  expectedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface WishGrowthRecord {
  id: number | string
  type: string
  content: string
  mediaUrls: string[]
  progressDelta: number
  createdAt: string
}

export interface WishProgress {
  currentValue: number
  targetValue: number
  percentage: number
  version: number
}

export interface WishDetail extends WishListItem {
  /** 匿名星光数（Sprint 2.6，仅详情返回） */
  anonStarCount: number
  growthRecords: WishGrowthRecord[]
  checkinDays: number
  progress: WishProgress | null
  enableAiReply?: boolean
}

export interface WishCreateResult {
  id: number | string
  title: string
  status: WishStatus
  fruitType: FruitType
  createdAt: string
}

export interface MyWishListItem {
  id: number | string
  title: string
  status: WishStatus
  fruitType: FruitType
  progress: number
  lightCount: number
  createdAt: string
}

export interface Category {
  id: number | string
  code: string
  name: string
  icon: string
  sortOrder: number
}

export interface TodayRecommendItem {
  wishId: number
  title: string
  coverUrl: string | null
  authorNickname: string
  supportCount: number
  fruitType: FruitType
}

export interface MyWishSummary {
  wishId: number
  title: string
  status: WishStatus
  progress: number
  fruitType: FruitType
}

export interface HotResonanceItem {
  wishId: number
  title: string
  supportCount: number
}

export interface HomeEntries {
  wishEntry: boolean
  mapEntry: boolean
  aiAssistantEntry: boolean
}

export interface HomeAggregation {
  worldTree: unknown
  todayRecommend: TodayRecommendItem[]
  myWishes: MyWishSummary[]
  hotResonance: HotResonanceItem[]
  entries: HomeEntries
}

export interface CursorMeta {
  pageSize: number
  nextCursor: string | null
  hasMore: boolean
}

// ========== Interaction & Comment Types（Sprint 1.2） ==========

export type InteractionType = 'LIGHT' | 'SAME_WISH' | 'BLESS' | 'ANON_STAR'

export interface InteractionResult {
  id: number | string
  type: InteractionType
  lightCount: number
  sameWishCount: number
  blessCount: number
  anonStarCount: number
  starlightCost: number
}

export interface InteractionRevokeResult {
  id: number | string
  type: InteractionType
  revoked: boolean
}

export interface InteractionItem {
  id: number | string
  /** 匿名星光记录不透出用户身份（userId/avatar 为 null） */
  userId: number | null
  nickname: string
  avatar: string | null
  type: InteractionType
  content: string | null
  createdAt: string
}

export interface MyInteractionItem {
  id: number | string
  type: InteractionType
  content: string | null
  createdAt: string
  createdToday: boolean
}

export interface WishCommentItem {
  id: number | string
  wishId: number
  userId: number
  nickname: string
  avatar: string
  content: string
  parentId: number | null
  replyToNickname: string | null
  createdAt: string
}

export interface WishCommentCreateResult {
  id: number | string
  content: string
  createdAt: string
}

// ========== API Functions ==========

/** BGM 播放歌曲（公开接口，空列表由播放器回退默认曲） */
export interface BgmSong {
  id: number | string
  title: string
  url: string
  sort: number
}

/** 当前 BGM 播放列表（管理端上传+勾选，sort 升序顺序循环） */
export function getBgmPlaylist() {
  return request.get<ApiResponse<BgmSong[]>>('/wish/bgm/playlist')
}

export function getHomeAggregation() {
  return request.get<ApiResponse<HomeAggregation>>('/wish/home')
}

export function getCategories() {
  return request.get<ApiResponse<Category[]>>('/wish/categories')
}

export function createWish(data: {
  title: string
  description: string
  mediaUrls?: string[]
  categoryId: number
  tags?: string[]
  visibility?: WishVisibility
  expectedAt?: string
  enableAiReply?: boolean
  triggerEnvEmo?: boolean
}) {
  return request.post<ApiResponse<WishCreateResult>>('/wish/wishes', data)
}

export function getWishDetail(id: number | string) {
  return request.get<ApiResponse<WishDetail>>(`/wish/wishes/${id}`)
}

export function updateWish(id: number | string, data: {
  title?: string
  description?: string
  mediaUrls?: string[]
  categoryId?: number
  tags?: string[]
  visibility?: WishVisibility
  expectedAt?: string
  fruitType?: FruitType
}) {
  return request.put<ApiResponse<{ id: number; updatedAt: string }>>(`/wish/wishes/${id}`, data)
}

export function deleteWish(id: number | string) {
  return request.delete<ApiResponse<{ id: number; deletedAt: string }>>(`/wish/wishes/${id}`)
}

export function listWishes(params: {
  categoryId?: number
  status?: WishStatus
  keyword?: string
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<WishListItem[]>>('/wish/wishes', { params })
}

export function listMyWishes(params: {
  status?: WishStatus
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<MyWishListItem[]>>('/wish/wishes/my', { params })
}

// ========== Interaction & Comment API（Sprint 1.2） ==========

export function createInteraction(wishId: number | string, data: { type: InteractionType; content?: string }) {
  return request.post<ApiResponse<InteractionResult>>(`/wish/wishes/${wishId}/interactions`, data)
}

export function revokeInteraction(wishId: number | string, interactionId: number | string) {
  return request.delete<ApiResponse<InteractionRevokeResult>>(
      `/wish/wishes/${wishId}/interactions/${interactionId}`
  )
}

export function listInteractions(wishId: number | string, params: {
  type?: InteractionType
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<InteractionItem[]>>(`/wish/wishes/${wishId}/interactions`, { params })
}

export function listMyInteractions(wishId: number | string) {
  return request.get<ApiResponse<MyInteractionItem[]>>(`/wish/wishes/${wishId}/interactions/my`)
}

export function createWishComment(wishId: number | string, data: { content: string; parentId?: number | string }) {
  return request.post<ApiResponse<WishCommentCreateResult>>(`/wish/wishes/${wishId}/comments`, data)
}

export function listWishComments(wishId: number | string, params: {
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<WishCommentItem[]>>(`/wish/wishes/${wishId}/comments`, { params })
}

export function deleteWishComment(wishId: number | string, commentId: number | string) {
  return request.delete<ApiResponse<null>>(`/wish/wishes/${wishId}/comments/${commentId}`)
}

// ========== Tree Hole & Consent API（Sprint 1.3） ==========

export interface AiResource {
  type: 'ARTICLE' | 'HOTLINE'
  title: string
  url: string
}

export interface TreeHoleReply {
  reply: string
  sentimentScore: number | null
  resources: AiResource[]
}

export interface AiConversationItem {
  id: number | string
  role: 'USER' | 'ASSISTANT'
  content: string
  sentimentScore: number | null
  resources: AiResource[]
  createdAt: string
}

export type ConsentType = 'PRIVACY_POLICY' | 'AI_DATA_PROCESSING' | 'BRAND_DATA_SHARE'

export interface ConsentStatus {
  consentType: ConsentType
  granted: boolean
  version: string | null
  latestAction: 'GRANT' | 'WITHDRAW' | null
  updatedAt: string | null
}

/** 发送树洞消息并获取 AI 治愈回复（前置：AI 数据处理同意；10 次/日） */
export function sendTreeHoleMessage(wishId: number | string, data: { message: string }) {
  return request.post<ApiResponse<TreeHoleReply>>(`/wish/ai/tree-hole`, {
    wishId,
    message: data.message,
  })
}

/** AI 对话历史（cursor 分页，默认 scene=TREE_HOLE） */
export function listAiConversations(params?: { scene?: string; cursor?: string; pageSize?: number }) {
  return request.get<ApiResponse<AiConversationItem[]>>('/wish/ai/conversations', { params })
}

/** 查询指定类型的同意状态（默认 AI_DATA_PROCESSING） */
export function getConsentStatus(consentType: ConsentType = 'AI_DATA_PROCESSING') {
  return request.get<ApiResponse<ConsentStatus>>('/wish/my/consents', { params: { consentType } })
}

/** 提交同意/撤回记录（幂等） */
export function grantConsent(data: {
  consentType: ConsentType
  version: string
  action?: 'GRANT' | 'WITHDRAW'
}) {
  return request.post<ApiResponse<null>>('/wish/my/consents', data)
}

// ========== 徽章（Sprint 1.9，与 mall-wish BadgeWallItemVO/BadgeDefinitionVO 对齐） ==========

export type BadgeRarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'

export interface BadgeCondition {
  type: string
  threshold: number
  description: string
}

export interface BadgeDefinition {
  badgeId: number
  code: string
  name: string
  icon: string
  description: string
  rarity: BadgeRarity
  condition: BadgeCondition | null
}

export interface BadgeProgress {
  current: number
  threshold: number
  percentage: number
}

export interface BadgeWallItem extends BadgeDefinition {
  earned: boolean
  earnedAt: string | null
  progress: BadgeProgress | null
}

/** 我的徽章墙（登录；已获得在前 + 未获得锁定态含进度） */
export function getMyBadges() {
  return request.get<ApiResponse<BadgeWallItem[]>>('/wish/my/badges')
}

/** 徽章图鉴（公开；未登录亦可浏览） */
export function getBadgeDefinitions() {
  return request.get<ApiResponse<BadgeDefinition[]>>('/wish/badges/definitions')
}

// ========== 还愿 API（Sprint 1.10 后端契约，文档 2.4） ==========

export interface WishFulfillmentSubmitResult {
  id: number | string
  wishId: number
  status: WishStatus
  fruitType: FruitType
  badgeAwarded: { id: number; name: string }[]
  starlightReward: number
  createdAt: string
}

export interface WishFulfillmentDetail {
  id: number | string
  wishId: number
  story: string
  mediaUrls: string[]
  feeling: string | null
  authorId: number
  authorNickname: string
  authorAvatar: string | null
  createdAt: string
}

export interface SubmitFulfillmentPayload {
  story: string
  mediaUrls?: string[]
  feeling?: string
}

/** 提交还愿（仅作者 + ACTIVE/OVERDUE；提交即 FULFILLED + BLOOM + 星光奖励） */
export function submitFulfillment(wishId: number | string, data: SubmitFulfillmentPayload) {
  return request.post<ApiResponse<WishFulfillmentSubmitResult>>(
      `/wish/wishes/${wishId}/fulfillment`,
      data,
  )
}

/** 还愿详情（公开心愿匿名可见；PRIVATE/TREE_HOLE 仅作者；未还愿 404） */
export function getFulfillmentDetail(wishId: number | string) {
  return request.get<ApiResponse<WishFulfillmentDetail>>(`/wish/wishes/${wishId}/fulfillment`)
}

// ========== 世界树（Sprint 2.1，与 mall-wish WorldTreeVO/TreeFruitVO 对齐） ==========

export type TreeEnvironment = 'SUNNY' | 'RAIN' | 'RAINBOW'

export type TreeSeason = 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER'

export interface WorldTreeAggregation {
  totalFruits: number
  totalBloom: number
  totalLight: number
  environment: TreeEnvironment
  season: TreeSeason
  environmentUpdatedAt: string | null
}

export interface TreeFruitPosition {
  /** 经度角 [0,2π) 弧度 */
  theta: number
  /** 纬度角 (0,π] 弧度（0=北极 π=南极） */
  phi: number
}

export interface TreeFruit {
  id: number | string
  title: string
  fruitType: FruitType
  authorNickname: string
  lightCount: number
  position: TreeFruitPosition
}

export interface TreeFruitsQuery {
  cursor?: string
  /** 视口过滤（弧度制）：lat→phi [0,π]、lng→theta [0,2π)，四参数需同时提供 */
  minLat?: number
  maxLat?: number
  minLng?: number
  maxLng?: number
  pageSize?: number
}

/** 世界树聚合状态（公开；计数 Redis 缓存 TTL 5min，环境/季节实时） */
export function getWorldTree() {
  return request.get<ApiResponse<WorldTreeAggregation>>('/wish/tree')
}

/** 果实分页（公开；id DESC 游标 + bounds 视口过滤，异常 bounds 整组忽略退化全量） */
export function listTreeFruits(params: TreeFruitsQuery) {
  return request.get<ApiResponse<TreeFruit[]>>('/wish/tree/fruits', { params })
}

// ========== 动态环境（Sprint 2.2，与 mall-wish TreeEnvVO/EnvConfigVO 对齐） ==========

export type TreeWeather = 'SUNNY' | 'CLOUDY' | 'RAIN' | 'SNOW' | 'RAINBOW'

export type TreeTimePhase = 'DAY' | 'DUSK' | 'NIGHT' | 'LATE_NIGHT'

export type TreeEnvParticle =
    | 'NONE'
    | 'RAIN'
    | 'SNOWFLAKE'
    | 'PETAL'
    | 'SUNBURST'
    | 'LEAF'
    | 'METEOR'
    | 'AURORA'
    | 'STAR'

/** 环境视觉参数（后端 wish_env_config.visual 透传 JSON） */
export interface TreeEnvVisual {
  skyColor?: string
  crownColor?: string
  lightCoreColor?: string
  particle?: TreeEnvParticle
}

export interface EnvConfigItem {
  id: number | string
  envCode: string
  category: 'WEATHER' | 'SEASON' | 'TIME' | 'SPECIAL_EVENT'
  name: string
  description: string | null
  priority: number
  visual: TreeEnvVisual | null
  isActive: boolean
}

export interface TreeSpecialEvent {
  id: number | string
  eventCode: string
  title: string
  description: string | null
  status: 'ACTIVE' | 'ENDED'
  triggeredAt: string
  expiresAt: string | null
}

/** 五维环境快照：displayEnv 为四端唯一渲染依据 */
export interface TreeEnvSnapshot {
  environment: TreeEnvironment
  source: string | null
  triggeredAt: string | null
  expiresAt: string | null
  lastScanAt: string | null
  moodScore: number | null
  sampleCount: number | null
  season: TreeSeason
  weather: TreeWeather
  timePhase: TreeTimePhase
  specialEvent: TreeSpecialEvent | null
  displayEnv: string
}

/** 环境快照（公开；timePhase 按客户端时区偏移计算，默认取本机时区） */
export function getTreeEnv(tzOffsetMinutes: number = -new Date().getTimezoneOffset()) {
  return request.get<ApiResponse<TreeEnvSnapshot>>('/wish/tree-env', { params: { tzOffsetMinutes } })
}

/** 环境配置图鉴（公开；priority 降序，visual 为四端透传渲染参数） */
export function listEnvConfigs() {
  return request.get<ApiResponse<EnvConfigItem[]>>('/wish/tree-env/configs')
}

// ========== Time Capsule（Sprint 2.4） ==========

export type CapsuleStatus = 'SEALED' | 'AVAILABLE' | 'OPENED' | 'CANCELLED'

/** 胶囊视图：非 OPENED 状态 content/mediaUrls 恒为 null（防绕过，开启是唯一拆信路径） */
export interface CapsuleItem {
  id: number | string
  title: string
  content: string | null
  mediaUrls: string[] | null
  status: CapsuleStatus
  /** 预定开启时间（UTC，ISO 8601；到期判定唯一依据，跨时区旅行不影响） */
  openAt: string
  /** 创建时用户 IANA 时区（回溯展示用，不参与判定） */
  openAtTimezone: string
  openedAt: string | null
  createdAt: string
}

export function createCapsule(data: {
  title: string
  content: string
  mediaUrls?: string[]
  openAt: string
  openAtTz: string
}) {
  return request.post<ApiResponse<CapsuleItem>>('/wish/capsules', data)
}

export function listMyCapsules(params: { status?: CapsuleStatus; cursor?: string; pageSize?: number } = {}) {
  return request.get<ApiResponse<CapsuleItem[]>>('/wish/capsules', { params })
}

export function getCapsuleDetail(id: number | string) {
  return request.get<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}`)
}

export function openCapsule(id: number | string) {
  return request.post<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}/open`)
}

export function cancelCapsule(id: number | string) {
  return request.delete<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}`)
}

/** 时区上报（登录/启动/时区变化时调用；IANA 时区 + UTC 偏移分钟，重复上报幂等） */
export function reportMyTimezone(timezone: string = Intl.DateTimeFormat().resolvedOptions().timeZone, offsetMinutes: number = -new Date().getTimezoneOffset()) {
  return request.post<ApiResponse<{ timezone: string; updated: boolean }>>('/wish/my/timezone', { timezone, offsetMinutes })
}

// ========== AI Assistant（Sprint 2.5，契约对齐 mall-wish AiAssistantController） ==========

export type AiGoalStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export type ExpectedActionType = 'EXTEND' | 'ADJUST' | 'TO_CAPSULE'

export type AiPromptSceneFilter = 'GOAL_BREAKDOWN' | 'TREE_HOLE' | 'ANNUAL_REPORT' | 'EXPECTED_GUIDE'

/** AI 拆解步骤项（POST /wish/ai/assistant 响应 goals 元素） */
export interface AiBreakdownGoal {
  title: string
  description: string
  estimatedDays: number
  /** 优先级 1-5，1 最高 */
  priority: number
}

export interface AiBreakdownResult {
  intent: string
  goals: AiBreakdownGoal[]
  suggestion: string
  /** AI 会话 ID（勾选持久化时回传） */
  sessionId: string
}

/** AI 拆解目标（wish_ai_goal 持久化后的列表项） */
export interface AiGoal {
  id: number | string
  wishId: number | null
  title: string
  description: string
  estimatedDays: number
  priority: number
  status: AiGoalStatus
  aiSessionId: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

/** 意图分析 + 目标拆解（前置 AI 同意；10 次/日；403/429/503 错误码由组件分发） */
export function breakdownGoal(data: { text: string; wishId?: number }) {
  return request.post<ApiResponse<AiBreakdownResult>>('/wish/ai/assistant', data)
}

/** 勾选步骤批量持久化（status=PENDING） */
export function createAiGoals(data: {
  sessionId: string
  wishId?: number
  goals: Array<Pick<AiBreakdownGoal, 'title' | 'description' | 'estimatedDays' | 'priority'>>
}) {
  return request.post<ApiResponse<AiGoal[]>>('/wish/ai/goals', data)
}

/** 目标状态流转（PENDING→IN_PROGRESS→COMPLETED；非终态可 CANCELLED；终态再变更 409） */
export function updateAiGoalStatus(goalId: number | string, status: AiGoalStatus) {
  return request.put<ApiResponse<AiGoal>>(`/wish/ai/goals/${goalId}`, { status })
}

/** 我的 AI 目标列表（id 倒序游标分页） */
export function listMyAiGoals(params: {
  status?: AiGoalStatus
  wishId?: number
  cursor?: string
  pageSize?: number
} = {}) {
  return request.get<ApiResponse<AiGoal[]>>('/wish/ai/goals', { params })
}

/** 预期管理选项埋点（转化率分析；非本人/不存在心愿 404） */
export function recordExpectedAction(wishId: number | string, action: ExpectedActionType) {
  return request.post<ApiResponse<null>>('/wish/ai/expected-actions', { wishId, action })
}

export interface AnnualReportMilestone {
  date: string
  title: string
  description: string
}

export interface AnnualReportTopCategory {
  name: string
  count: number
}

export interface AnnualReportData {
  year: number
  fulfilledCount: number
  totalCheckinDays: number
  growthSummary: string
  milestones: AnnualReportMilestone[]
  topCategories: AnnualReportTopCategory[]
}

/**
 * 年度报告（growthSummary 异步 AI 生成：首次为模板文案，稍后重查返回 AI 版）；
 * 结果缓存 168h，仅含本人数据
 */
export function getAnnualReport(year: number) {
  return request.get<ApiResponse<AnnualReportData>>('/wish/ai/annual-report', { params: { year } })
}

// ========== 通知偏好矩阵（Sprint 2.5，契约对齐 mall-wish MyProfileController） ==========

export type NotificationChannel = 'PUSH' | 'SMS' | 'EMAIL' | 'IN_APP'

export type NotificationType =
  | 'WISH_COMMENT'
  | 'WISH_LIGHT'
  | 'WISH_FULFILL'
  | 'CAPSULE_OPEN'
  | 'AI_REMINDER'
  | 'CHECKIN_REMINDER'
  | 'MATCH_RECOMMEND'
  | 'BRAND_REWARD'
  | 'ENCOUNTER_LETTER'
  | 'DEVICE_OFFLINE'
  | 'LEVEL_UP'
  | 'BADGE_EARNED'
  | 'SYSTEM'

/** 13 类通知 × 4 渠道开关；无记录项默认开启 */
export interface NotificationPreferenceMatrix {
  preferences: Array<{
    type: NotificationType
    channels: Record<NotificationChannel, boolean>
  }>
}

export function getNotificationPreferences() {
  return request.get<ApiResponse<NotificationPreferenceMatrix>>('/wish/my/notification-preferences')
}

/** 批量更新偏好（逐项 upsert）；一键关闭所有提醒 = 全类型×全渠道 enabled=false */
export function updateNotificationPreferences(
  updates: Array<{ type: NotificationType; channel: NotificationChannel; enabled: boolean }>,
) {
  return request.put<ApiResponse<NotificationPreferenceMatrix>>('/wish/my/notification-preferences', {
    updates,
  })
}

// ========== 同愿匹配 + 监督小队（Sprint 2.6，契约对齐 mall-wish MatchGroupController） ==========

export type MatchGroupStatus = 'OPEN' | 'FULL' | 'CLOSED'

export interface MatchRecommendQuery {
  keyword?: string
  /** 同城代理码（geohash 前缀 4，可选；不传时服务端取请求者活跃公开心愿） */
  city?: string
  cursor?: string
  pageSize?: number
}

export interface MatchGroupItem {
  groupId: number
  keyword: string
  memberCount: number
  maxMembers: number
  leaderNickname: string
  leaderAvatar: string
  /** 相似度 0-1（关键词/城市/活跃度加权，权重管理端可配） */
  matchScore: number
  /** 三端一致的相似度说明（如"你们都想看极光"） */
  matchReason: string
  status: MatchGroupStatus
  cityCode: string | null
  createdAt: string
}

export interface MatchGroupCreated {
  groupId: number
  keyword: string
  maxMembers: number
  status: MatchGroupStatus
  role: 'LEADER' | 'MEMBER'
  joinedAt: string
}

export interface MatchMemberItem {
  userId: number
  nickname: string
  avatar: string
  role: 'LEADER' | 'MEMBER'
  status: 'ACTIVE' | 'LEFT' | 'KICKED'
  joinedAt: string
  /** 距最近活跃天数（null=从未活跃；提醒未打卡组员依据） */
  idleDays: number | null
}

export interface MatchGroupDetail {
  groupId: number
  keyword: string
  memberCount: number
  maxMembers: number
  status: MatchGroupStatus
  cityCode: string | null
  createdAt: string
  viewerRole: 'LEADER' | 'MEMBER' | null
  members: MatchMemberItem[]
}

/** 匹配推荐（公开浏览；keyword/city 皆空时服务端基于心愿标签推荐） */
export function recommendMatchGroups(params: MatchRecommendQuery = {}) {
  return request.get<ApiResponse<MatchGroupItem[]>>('/wish/match/groups/recommend', { params })
}

/** 建组（创建者为 LEADER；429 建组日限频 / 409 同关键词已有小队 / 403 被踢冷却） */
export function createMatchGroup(data: { keyword: string; maxMembers?: number; wishId?: number }) {
  return request.post<ApiResponse<MatchGroupCreated>>('/wish/match/groups', data)
}

/** 我的小队（ACTIVE 成员身份；含成员活跃度） */
export function listMyMatchGroups() {
  return request.get<ApiResponse<MatchGroupDetail[]>>('/wish/match/groups/my')
}

/** 小队详情（成员仅暴露昵称/头像/活跃度） */
export function getMatchGroupDetail(groupId: number) {
  return request.get<ApiResponse<MatchGroupDetail>>(`/wish/match/groups/${groupId}`)
}

/** 加入小队（409 满员/已是成员/同主题占坑；403 被踢 24h 冷却） */
export function joinMatchGroup(groupId: number, message?: string) {
  return request.post<ApiResponse<null>>(`/wish/match/groups/${groupId}/members`, { message })
}

/** 退出（target=自己）或踢人（LEADER；被踢者 24h 同主题冷却） */
export function leaveMatchGroup(groupId: number, targetUserId: number) {
  return request.delete<ApiResponse<null>>(`/wish/match/groups/${groupId}/members/${targetUserId}`)
}

/** 解散小队（仅组长；成员收到通知） */
export function dissolveMatchGroup(groupId: number) {
  return request.post<ApiResponse<null>>(`/wish/match/groups/${groupId}/dissolution`)
}

/** 互相提醒（点名 targetUserId 或提醒全部 idle 组员；429 日限频） */
export function remindSquadMembers(groupId: number, targetUserId?: number) {
  return request.post<ApiResponse<null>>(
    `/wish/match/groups/${groupId}/reminds`,
    {},
    { params: targetUserId ? { targetUserId } : undefined },
  )
}

// ========== 排行榜 + 传承（Sprint 2.7，契约对齐 mall-wish LeaderboardController/WishController） ==========

export type LeaderboardType = 'HOT' | 'WARM' | 'PERSISTENCE' | 'SPARK'

export const LEADERBOARD_LABELS: Record<LeaderboardType, string> = {
  HOT: '热门榜',
  WARM: '温暖榜',
  PERSISTENCE: '坚持榜',
  SPARK: '星火榜',
}

export interface LeaderboardEntry {
  rank: number
  /** 心愿榜为心愿作者；用户榜为本人 */
  userId: number | null
  nickname: string
  avatar: string
  score: number
  extra: { wishTitle?: string; checkinDays?: number; helpedCount?: number; lightCount?: number; blessCount?: number }
  /** 排名变化（三端动效一致依据） */
  rankDelta: 'UP' | 'DOWN' | 'FLAT' | 'NEW'
}

/** 排行榜（公开；Redis ZSet 每 10 分钟刷新；同分按创建时间早在前） */
export function getLeaderboard(type: LeaderboardType, limit = 100) {
  return request.get<ApiResponse<LeaderboardEntry[]>>('/wish/leaderboard', { params: { type, limit } })
}

export interface InheritResult {
  inheritId: number
  pushedCount: number
  createdAt: string
}

/** 传承推送（作者对 FULFILLED 心愿定向推送曾同求用户；一次还愿一次传承） */
export function inheritFulfillment(wishId: number | string, message?: string) {
  return request.post<ApiResponse<InheritResult>>(`/wish/wishes/${wishId}/fulfillment/inherit`, { message })
}

/** 撤回还愿故事（作者软删；心愿保持 FULFILLED；community 帖子同步隐藏） */
export function withdrawFulfillment(wishId: number | string) {
  return request.delete<ApiResponse<null>>(`/wish/wishes/${wishId}/fulfillment`)
}

// ========== LBS 地图（Sprint 3.1，契约对齐 mall-wish MapController） ==========

export interface NearbyWish {
  wishId: number
  title: string
  fruitType: string | null
  /** geohash7 网格中心 + 确定性偏移（0-50m，不含精确坐标——隐私） */
  approximateLat: number
  approximateLng: number
  distance: number
  lightCount: number
  geohash: string
  createdAt: string
}

export interface MapCluster {
  geohash6: string
  centerLat: number
  centerLng: number
  count: number
}

/** 附近心愿（公开；radius 异常兜底 5km；空坐标 → 服务端默认城市兜底） */
export function getMapWishes(params: { lat?: number; lng?: number; radius?: number; geohash?: string }) {
  return request.get<ApiResponse<NearbyWish[]>>('/wish/map/wishes', {
    params: {
      lat: params.lat ?? undefined,
      lng: params.lng ?? undefined,
      radius: params.radius ?? undefined,
      geohash: params.geohash ?? undefined,
    },
  })
}

/** 网格聚合（geohash6 数量角标，坐标=网格中心） */
export function getMapClusters(params: { lat?: number; lng?: number; radius?: number; geohash?: string }) {
  return request.get<ApiResponse<MapCluster[]>>('/wish/map/cluster', {
    params: {
      lat: params.lat ?? undefined,
      lng: params.lng ?? undefined,
      radius: params.radius ?? undefined,
      geohash: params.geohash ?? undefined,
    },
  })
}

// ========== 城市幸福地图 + 围栏（Sprint 3.2，契约对齐 mall-wish WarmMapController） ==========

export interface FenceCheckResult {
  wishId: number
  insideFence: boolean
  fenceName: string | null
  bloomTriggered: boolean
  matchedCount: number
}

/** 围栏打卡（打卡坐标不存储；响应不含围栏坐标——隐私） */
export function checkFence(wishId: number | string, lat: number, lng: number) {
  return request.post<ApiResponse<FenceCheckResult>>('/wish/map/fence/check', { wishId, lat, lng })
}

export interface WarmEventItem {
  eventId: number
  title: string
  content: string
  approximateLat: number
  approximateLng: number
  distance: number
  geohash6: string
  cityCode: string | null
  nickname: string | null
  createdAt: string
}

/** 发布温暖事件（DFA 命中 → 自动隐藏；未命中 → 先发后审） */
export function publishWarmEvent(data: { title: string; content: string; lat: number; lng: number }) {
  return request.post<ApiResponse<{ eventId: number }>>('/wish/map/warm-events', data)
}

/** 温暖事件附近列表（空坐标 → 服务端默认城市兜底） */
export function listWarmEvents(params: { lat?: number; lng?: number; radius?: number; cityCode?: string }) {
  return request.get<ApiResponse<WarmEventItem[]>>('/wish/map/warm-events', {
    params: {
      lat: params.lat ?? undefined,
      lng: params.lng ?? undefined,
      radius: params.radius ?? undefined,
      cityCode: params.cityCode ?? undefined,
    },
  })
}

// ========== 擦肩而过（Sprint 3.3，契约对齐 mall-wish EncounterController） ==========

export interface EncounterLetterItem {
  letterId: number
  wishTags: string[]
  encounterTime: string
  encounterGeohash6: string
  status: 'PENDING' | 'DELIVERED' | 'READ'
  /** PENDING 时为 null（契约） */
  content: string | null
  deliveredAt: string | null
}

/** 附近模式开关（开启后客户端每 5 分钟上报；关闭立即生效） */
export function setNearbyMode(enabled: boolean) {
  return request.post<ApiResponse<null>>('/wish/map/nearby-mode', { enabled })
}

/** 附近模式状态查询（刷新后回显） */
export function getNearbyMode() {
  return request.get<ApiResponse<boolean>>('/wish/map/nearby-mode')
}

/** 轨迹上报（坐标转 geohash6 入 Redis；伪造检测/限频在服务端） */
export function reportTrace(lat: number, lng: number) {
  return request.post<ApiResponse<null>>('/wish/map/trace', { lat, lng })
}

export function listEncounterLetters() {
  return request.get<ApiResponse<EncounterLetterItem[]>>('/wish/map/encounter-letters')
}

/** 拆信（DELIVERED → READ） */
export function readEncounterLetter(letterId: number | string) {
  return request.put<ApiResponse<EncounterLetterItem>>(`/wish/encounter-letters/${letterId}/read`)
}

/** 匿名互动（BLESS 免费 / LIGHT 扣星光 2 点亮对方心愿；每信笺每日 1 次） */
export function interactEncounterLetter(letterId: number, type: 'BLESS' | 'LIGHT') {
  return request.post<ApiResponse<EncounterLetterItem>>(`/wish/encounter-letters/${letterId}/interactions`, { type })
}

// ========== 直播心愿挂件（Sprint 3.4，契约对齐 mall-wish LiveWidgetController） ==========

export interface LiveWidgetData {
  streamerId: number
  /** false = 全局降级/主播配置隐藏，前端隐藏挂件 */
  visible: boolean
  /** false = 主播无进行中心愿，前端展示"去许愿"引导 */
  hasWish: boolean
  wishId: number | null
  title: string | null
  progressCurrent: number | null
  progressTarget: number | null
  progressPercentage: number | null
  checkinDays: number | null
  starlightBalance: number | null
  position: 'TOP_LEFT' | 'TOP_RIGHT' | 'BOTTOM_LEFT' | 'BOTTOM_RIGHT'
  styleConfig: string | null
}

/** 挂件数据（公开；服务端 Redis 缓存 TTL 10s，前端 10s 轮询） */
export function getLiveWidget(streamerId: number) {
  return request.get<ApiResponse<LiveWidgetData>>(`/wish/live/widget/${streamerId}`)
}

// ========== 社区活动（Sprint 3.5，契约对齐 mall-wish ActivityController） ==========

export type ActivityType = 'WORLD_EVENT' | 'FESTIVAL' | 'CITY' | 'WISH_PARTNER'
export type ActivityStatus = 'DRAFT' | 'ACTIVE' | 'ENDED' | 'ARCHIVED'

export const ACTIVITY_TYPE_LABELS: Record<ActivityType, string> = {
  WORLD_EVENT: '世界事件',
  FESTIVAL: '节日活动',
  CITY: '城市活动',
  WISH_PARTNER: '心愿合伙人',
}

export interface ActivityItem {
  id: number | string
  type: ActivityType
  title: string
  description: string | null
  coverImage: string | null
  cityCode: string | null
  status: ActivityStatus
  progressCounter: number
  createdAt: string
}

export interface ActivityBoardMember {
  userId: number
  role: string
  title: string | null
  progressPercentage: number
  checkinDays: number
  latestGrowth: string | null
  latestGrowthAt: string | null
}

/** 活动列表（仅 ACTIVE 且展示期内；归档不出现在列表） */
export function listActivities(params: { type?: string; cityCode?: string } = {}) {
  return request.get<ApiResponse<ActivityItem[]>>('/wish/activities', {
    params: { type: params.type ?? undefined, cityCode: params.cityCode ?? undefined },
  })
}

/** 活动详情（归档后仍可访问） */
export function getActivity(id: number | string) {
  return request.get<ApiResponse<ActivityItem>>(`/wish/activities/${id}`)
}

export function getActivityProgress(id: number | string) {
  return request.get<ApiResponse<number>>(`/wish/activities/${id}/progress`)
}

export function joinActivity(id: number | string) {
  return request.post<ApiResponse<null>>(`/wish/activities/${id}/join`)
}

/** 合伙人申请（提交协作心愿 + 技能标签） */
export function applyPartner(id: number | string, wishId: number | string, skills?: string[]) {
  return request.post<ApiResponse<null>>(`/wish/activities/${id}/apply`, { wishId, skills })
}

export function reviewPartnerApplication(id: number | string, applicantUserId: number, approved: boolean) {
  return request.put<ApiResponse<null>>(
    `/wish/activities/${id}/participants/${applicantUserId}/review`,
    { approved },
  )
}

export interface BoardMember {
  userId: number
  role: string
  title: string | null
  progressPercentage: number
  checkinDays: number
  latestGrowth: string | null
  latestGrowthAt: string | null
}

export function getPartnerBoard(id: number | string) {
  return request.get<ApiResponse<{ activityId: number; leaderUserId: number; members: BoardMember[] }>>(
    `/wish/activities/${id}/board`,
  )
}

// ========== 虚拟收藏 + 品牌（Sprint 3.6，契约对齐 mall-wish CollectionController） ==========

export interface UserAssetData {
  id: number | string
  userId: number
  assetId: number
  source: string
  status: string
}

export interface WorkshopAsset {
  assetId: number
  assetType: 'SKIN' | 'BGM' | 'SPECIAL_FRUIT'
  name: string
  description: string | null
  icon: string | null
  priceStarlight: number
  priceRmb: number
  payMethod: string
  stock: number
  owned: boolean
}

export interface CollectionGroup {
  [type: string]: Array<{ id: number; assetId: number; name: string; icon: string; isActive: boolean | null; refWishId: number | null }>
}

export function getWorkshopAssets() {
  return request.get<ApiResponse<WorkshopAsset[]>>('/wish/workshop/assets')
}

export function exchangeAsset(assetId: number, paymentMethod = 'STARLIGHT') {
  return request.post<ApiResponse<UserAssetData>>('/wish/workshop/exchange', { assetId, paymentMethod })
}

export function getCollections() {
  return request.get<ApiResponse<CollectionGroup>>('/wish/collections/assets')
}

export function collectSparkWish(wishId: number | string) {
  return request.post<ApiResponse<null>>(`/wish/collections/spark/${wishId}`)
}

export function setActiveSkin(assetId: number) {
  return request.put<ApiResponse<null>>(`/wish/my/active-skin/${assetId}`)
}

export function setActiveBgm(assetId: number) {
  return request.put<ApiResponse<null>>(`/wish/my/active-bgm/${assetId}`)
}

// ========== 品牌许愿池（Sprint 3.6 / 2.16，四AB B4 用户侧） ==========

export interface BrandItem {
  brandId: number
  brandName: string
  logo: string | null
  description: string | null
  status: string
}

export interface BrandPoolItem {
  poolId: number
  brandId: number
  poolName: string
  targetCount: number
  currentCount: number
  rewardDescription: string | null
  status: string
  endTime: string | null
}

export function listBrands() {
  return request.get<ApiResponse<BrandItem[]>>('/wish/brands')
}

export function listBrandPools(brandId: number | string) {
  return request.get<ApiResponse<BrandPoolItem[]>>(`/wish/brands/${brandId}/pools`)
}

export function joinBrandPool(brandId: number | string, poolId: number | string) {
  return request.post<ApiResponse<null>>(`/wish/brands/${brandId}/pools/${poolId}/join`)
}

export function getBrandPoolDetail(brandId: number | string, poolId: number | string) {
  return request.get<ApiResponse<BrandPoolItem>>(`/wish/brands/${brandId}/pools/${poolId}`)
}

export function getBrandPoolRewards(brandId: number | string, poolId: number | string) {
  return request.get<ApiResponse<Array<Record<string, unknown>>>>(`/wish/brands/${brandId}/pools/${poolId}/rewards`)
}

// ========== 打卡 + 成长记录 + 进度（Sprint 1.3 补齐） ==========

export interface CheckinResult {
  checkinId: number
  currentStreak: number
  maxStreak: number
  starlightCredited: number
}

export function checkinWish(wishId: number | string, content?: string) {
  return request.post<ApiResponse<CheckinResult>>(`/wish/wishes/${wishId}/checkin`, { content })
}

export function addGrowthRecord(wishId: number | string, data: {
  type: string; content: string; mediaUrls?: string[]; progressDelta?: number
}) {
  return request.post<ApiResponse<{ recordId: number; newCurrentValue: number }>>(
    `/wish/wishes/${wishId}/growth`, data)
}

export function getWishProgressDetail(wishId: number | string) {
  return request.get<ApiResponse<{ currentValue: number; targetValue: number; percentage: number; version: number }>>(
    `/wish/wishes/${wishId}/progress`)
}

// ========== 心愿收藏 2.12（Sprint 1.5 补齐） ==========

export interface WishCollectionItem {
  collectionId: number
  wishId: number
  title: string
  authorNickname: string
  fruitType: string
  collectedAt: string
}

export function listWishCollections(cursor?: string, pageSize?: number) {
  return request.get<ApiResponse<WishCollectionItem[]>>('/wish/collections', {
    params: { cursor, pageSize: pageSize ?? 20 },
  })
}

export function getWishCollectionStatus(wishId: number | string) {
  return request.get<ApiResponse<boolean>>(`/wish/collections/${wishId}/status`)
}

export function collectWish(wishId: number | string) {
  return request.post<ApiResponse<{ collectionId: number; wishId: number; collectedAt: string }>>(
    '/wish/collections', { wishId })
}

export function uncollectWish(wishId: number | string) {
  return request.delete<ApiResponse<{ wishId: number; deleted: boolean }>>(`/wish/collections/${wishId}`)
}

// ========== 数据导出（合规 34.2，四AB B5 WEB 端） ==========

export interface DataExportTask {
  id: number
  userId: number
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
  downloadUrl: string | null
  expiresAt: string | null
  createdAt: string
}

export function createDataExport() {
  return request.post<ApiResponse<DataExportTask>>('/wish/my/export')
}

export function getMyExportTask(taskId: number | string) {
  return request.get<ApiResponse<DataExportTask>>(`/wish/my/export/${taskId}`)
}

export function listMyExportTasks() {
  return request.get<ApiResponse<DataExportTask[]>>('/wish/my/exports')
}

export function downloadMyExport(taskId: number | string) {
  return request.get(`/wish/my/export/${taskId}/download`)
}

// ========== 账号注销宽限期（合规 34.2 / API 2.13，四AB A1 WEB 端） ==========

export interface AccountDeletionStatus {
  id: number
  userId: number
  status: 'PENDING' | 'CANCELED' | 'EXECUTED'
  reason: string | null
  requestedAt: string
  executeAfter: string
  canceledAt: string | null
  executedAt: string | null
}

export function sendDeletionCode() {
  return request.post<ApiResponse<{ sent: boolean; expiresInSeconds: number; devCode?: string }>>(
    '/wish/my/account-deletion/code')
}

export function applyAccountDeletion(confirmCode: string, reason?: string) {
  return request.post<ApiResponse<{ userId: number; executeAfter: string; canCancel: boolean; cancelDeadline: string }>>(
    '/wish/my/account-deletion', { confirmCode, reason })
}

export function cancelAccountDeletion() {
  return request.post<ApiResponse<{ userId: number; cancelled: boolean; cancelledAt: string }>>(
    '/wish/my/account/cancel')
}

export function getAccountDeletionStatus() {
  return request.get<ApiResponse<AccountDeletionStatus | null>>('/wish/my/account-deletion')
}
