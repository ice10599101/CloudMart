import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== Types ==========

export type WishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type WishStatus = 'DRAFT' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED'
export type FruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'
export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_HIDDEN'

export interface WishListItem {
  id: number
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
  id: number
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
  id: number
  title: string
  status: WishStatus
  fruitType: FruitType
  createdAt: string
}

export interface MyWishListItem {
  id: number
  title: string
  status: WishStatus
  fruitType: FruitType
  progress: number
  lightCount: number
  createdAt: string
}

export interface Category {
  id: number
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
  id: number
  type: InteractionType
  lightCount: number
  sameWishCount: number
  blessCount: number
  anonStarCount: number
  starlightCost: number
}

export interface InteractionRevokeResult {
  id: number
  type: InteractionType
  revoked: boolean
}

export interface InteractionItem {
  id: number
  /** 匿名星光记录不透出用户身份（userId/avatar 为 null） */
  userId: number | null
  nickname: string
  avatar: string | null
  type: InteractionType
  content: string | null
  createdAt: string
}

export interface MyInteractionItem {
  id: number
  type: InteractionType
  content: string | null
  createdAt: string
  createdToday: boolean
}

export interface WishCommentItem {
  id: number
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
  id: number
  content: string
  createdAt: string
}

// ========== API Functions ==========

/** BGM 播放歌曲（公开接口，空列表由播放器回退默认曲） */
export interface BgmSong {
  id: number
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

export function getWishDetail(id: number) {
  return request.get<ApiResponse<WishDetail>>(`/wish/wishes/${id}`)
}

export function updateWish(id: number, data: {
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

export function deleteWish(id: number) {
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

export function createInteraction(wishId: number, data: { type: InteractionType; content?: string }) {
  return request.post<ApiResponse<InteractionResult>>(`/wish/wishes/${wishId}/interactions`, data)
}

export function revokeInteraction(wishId: number, interactionId: number) {
  return request.delete<ApiResponse<InteractionRevokeResult>>(
      `/wish/wishes/${wishId}/interactions/${interactionId}`
  )
}

export function listInteractions(wishId: number, params: {
  type?: InteractionType
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<InteractionItem[]>>(`/wish/wishes/${wishId}/interactions`, { params })
}

export function listMyInteractions(wishId: number) {
  return request.get<ApiResponse<MyInteractionItem[]>>(`/wish/wishes/${wishId}/interactions/my`)
}

export function createWishComment(wishId: number, data: { content: string; parentId?: number }) {
  return request.post<ApiResponse<WishCommentCreateResult>>(`/wish/wishes/${wishId}/comments`, data)
}

export function listWishComments(wishId: number, params: {
  cursor?: string
  pageSize?: number
}) {
  return request.get<ApiResponse<WishCommentItem[]>>(`/wish/wishes/${wishId}/comments`, { params })
}

export function deleteWishComment(wishId: number, commentId: number) {
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
  id: number
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
export function sendTreeHoleMessage(wishId: number, data: { message: string }) {
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
  id: number
  wishId: number
  status: WishStatus
  fruitType: FruitType
  badgeAwarded: { id: number; name: string }[]
  starlightReward: number
  createdAt: string
}

export interface WishFulfillmentDetail {
  id: number
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
export function submitFulfillment(wishId: number, data: SubmitFulfillmentPayload) {
  return request.post<ApiResponse<WishFulfillmentSubmitResult>>(
      `/wish/wishes/${wishId}/fulfillment`,
      data,
  )
}

/** 还愿详情（公开心愿匿名可见；PRIVATE/TREE_HOLE 仅作者；未还愿 404） */
export function getFulfillmentDetail(wishId: number) {
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
  id: number
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
  id: number
  envCode: string
  category: 'WEATHER' | 'SEASON' | 'TIME' | 'SPECIAL_EVENT'
  name: string
  description: string | null
  priority: number
  visual: TreeEnvVisual | null
  isActive: boolean
}

export interface TreeSpecialEvent {
  id: number
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
  id: number
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

export function getCapsuleDetail(id: number) {
  return request.get<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}`)
}

export function openCapsule(id: number) {
  return request.post<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}/open`)
}

export function cancelCapsule(id: number) {
  return request.delete<ApiResponse<CapsuleItem>>(`/wish/capsules/${id}`)
}

/** 时区上报（登录/启动/时区变化时调用；IANA 时区 + UTC 偏移分钟，重复上报幂等） */
export function reportMyTimezone(timezone: string = Intl.DateTimeFormat().resolvedOptions().timeZone, offsetMinutes: number = -new Date().getTimezoneOffset()) {
  return request.post<ApiResponse<{ timezone: string; updated: boolean }>>('/wish/my/timezone', { timezone, offsetMinutes })
}
