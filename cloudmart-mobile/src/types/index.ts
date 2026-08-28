// API Response envelope
export interface ApiResponse<T> {
  success: boolean
  data: T
  error?: {
    code: string
    message: string
    details?: unknown[]
  }
  meta?: {
    page: number
    pageSize: number
    total: number
    nextCursor?: string | null
    hasMore?: boolean
  }
}

// User
export interface UserBasic {
  id: number
  nickname: string
  avatar: string
  level?: number
  signature?: string
}

export interface FollowUser extends UserBasic {
  isFollowing: boolean
}

export interface User extends UserBasic {
  username?: string
  email: string
  phone?: string
  gender?: string
  birthday?: string
  constellation?: string
  occupation?: string
  school?: string
  location?: string
  hobbies?: string
  followerCount: number
  followingCount: number
  postCount: number
  likeCount: number
  isFollowing?: boolean
  createdAt?: string
}

// Post
export interface Post {
  id: number
  userId: number
  title: string
  content: string
  images: string[]
  tags?: Tag[]
  likeCount: number
  commentCount: number
  collectCount: number
  shareCount: number
  isLiked?: boolean
  isCollected?: boolean
  createdAt: string
  user?: UserBasic
  coverImage?: string
  mediaUrls?: string[]
}

// Comment
export interface Comment {
  id: number
  postId: number
  userId: number
  content: string
  likeCount: number
  replyCount: number
  isLiked?: boolean
  createdAt: string
  user?: UserBasic
  replies?: Comment[]
}

// Product
export interface Product {
  id: number
  name: string
  description: string
  price: number
  originalPrice?: number
  mainImage: string
  images: string[]
  categoryId: number
  categoryName?: string
  brandName?: string
  sales: number
  rating?: number
  reviewCount: number
  status: number
}

export interface ProductCategory {
  id: number
  name: string
  icon?: string
  parentId?: number
  children?: ProductCategory[]
}

// Cart
export interface CartItem {
  id: number
  skuId: number
  productId: number
  productName: string
  productImage: string
  skuName?: string
  price: number
  quantity: number
  checked: boolean
  stock: number
}

// Order
export interface Order {
  id: number
  orderNo: string
  status: number
  statusText: string
  totalAmount: number
  payAmount: number
  items: OrderItem[]
  createdAt: string
  payTime?: string
  shipTime?: string
  receiveTime?: string
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
}

export interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  skuName?: string
  price: number
  quantity: number
}

// Coupon
export interface CouponTemplate {
  id: number
  name: string
  type: number
  value: number
  minAmount: number
  startTime: string
  endTime: string
  status: number
  claimed?: boolean
}

// 优惠券推荐结果
export interface CouponRecommendation {
  userCouponId: number
  couponName: string
  type: number
  value: number
  discount: number
}

export interface RecommendationResult {
  totalDiscount: number
  finalAmount: number
  recommendations: CouponRecommendation[]
}

// Seckill Activity
export interface SeckillActivity {
  id: number
  name: string
  startTime: string
  endTime: string
  status: number
  products: SeckillProduct[]
}

export interface SeckillProduct {
  id: number
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  seckillPrice: number
  totalStock: number
  availableStock: number
  limitPerUser: number
}

// Group Buy
export interface GroupActivity {
  id: number
  name: string
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  groupPrice: number
  groupSize: number
  currentCount: number
  endTime: string
  status: number
}

// Live
export interface LiveRoom {
  id: number
  title: string
  coverImage: string
  anchorName: string
  anchorAvatar: string
  viewerCount: number
  status: number
  startTime?: string
}

// Tag
export interface Tag {
  id: number
  name: string
  postCount?: number
  isSubscribed?: boolean
}

// Notification
export interface Notification {
  id: number
  type: number
  title: string
  content: string
  isRead: boolean
  createdAt: string
  sender?: UserBasic
}

// Conversation
export interface Conversation {
  id: number
  targetUser: UserBasic
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
}

export interface ChatMessage {
  id: number
  conversationId: number
  senderId: number
  content: string
  type: number
  createdAt: string
  isRecalled: boolean
}

// Growth
export interface CheckInStatus {
  isCheckedIn: boolean
  continuousDays: number
  todayExp: number
}

export interface UserLevel {
  level: number
  exp: number
  nextLevelExp: number
  title: string
}

export interface ExpLog {
  id: number
  userId: number
  exp: number
  source: string
  bizId?: number
  description: string
  createdAt: string
}

export interface LevelConfig {
  level: number
  title: string
  minExp: number
  maxExp: number
}

// Ranking
export interface RankingItem {
  userId: number
  expValue: number
  rankNo: number
  nickname?: string
  avatar?: string
}

export interface UserRanking {
  userId: number
  expValue: number
  rankNo: number
}

export interface RankingSeason {
  id: number
  name: string
  seasonKey: string
  startDate: string
  endDate: string
  status: number
}

// AI
export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

// Address
export interface Address {
  id: number
  name: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
}

// Wishlist
export interface WishlistItem {
  id: number
  productId: number
  productName: string
  productImage: string
  price: number
  addedAt: string
}

// Paginated result
export interface PaginatedResult<T> {
  list: T[]
  page: number
  pageSize: number
  total: number
}

// Cursor pagination meta
export interface CursorMeta {
  nextCursor: string | null
  hasMore: boolean
}

// ============ Wish Universe ============
export type WishStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED' | 'REJECTED'
export type WishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type FruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'

export interface WishCategory {
  id: number
  name: string
  icon?: string
}

export interface WishListItem {
  id: number
  title: string
  description: string
  coverUrl?: string
  mediaUrls?: string[]
  tags?: string[]
  fruitType: FruitType
  status: WishStatus
  authorId: number
  authorNickname: string
  authorAvatar?: string
  supportCount: number
  commentCount: number
  createdAt: string
}

export interface MyWishListItem {
  id: number
  title: string
  fruitType: FruitType
  status: WishStatus
  progress: number
  createdAt: string
}

export interface WishProgress {
  percentage: number
  currentValue: number
  targetValue: number
}

export interface WishGrowthRecord {
  id: number
  content: string
  mediaUrls?: string[]
  progressDelta: number
  createdAt: string
}

export interface WishDetail {
  id: number
  title: string
  description: string
  mediaUrls?: string[]
  tags?: string[]
  fruitType: FruitType
  status: WishStatus
  visibility: WishVisibility
  authorId: number
  authorNickname: string
  authorAvatar?: string
  supportCount: number
  lightCount: number
  sameWishCount: number
  blessCount: number
  /** 匿名星光数（Sprint 2.6，仅详情返回） */
  anonStarCount: number
  commentCount: number
  expectedAt?: string
  createdAt: string
  updatedAt: string
  progress?: WishProgress
  checkinDays: number
  growthRecords?: WishGrowthRecord[]
  /** 是否启用 AI 回复（树洞心愿，Sprint 1.3） */
  enableAiReply?: boolean
}

export interface TodayRecommendItem {
  wishId: number
  title: string
  coverUrl?: string
  fruitType: FruitType
  authorNickname: string
  supportCount: number
}

export interface MyWishSummary {
  wishId: number
  title: string
  fruitType: FruitType
  progress: number
}

export interface HotResonanceItem {
  wishId: number
  title: string
  supportCount: number
}

export interface HomeAggregation {
  todayRecommend: TodayRecommendItem[]
  myWishes: MyWishSummary[]
  hotResonance: HotResonanceItem[]
}

// ============ Wish Interaction & Comment（Sprint 1.2；匿名星光 Sprint 2.6） ============
export type WishInteractionType = 'LIGHT' | 'SAME_WISH' | 'BLESS' | 'ANON_STAR'

export interface WishInteractionResult {
  id: number
  type: WishInteractionType
  lightCount: number
  sameWishCount: number
  blessCount: number
  anonStarCount: number
  starlightCost: number
}

export interface MyWishInteraction {
  id: number
  type: WishInteractionType
  content: string | null
  createdAt: string
  createdToday: boolean
}

export interface WishCommentItem {
  id: number
  wishId: number
  userId: number
  nickname: string
  avatar: string | null
  content: string
  parentId: number | null
  replyToNickname: string | null
  createdAt: string
}

// ========== 树洞 AI（Sprint 1.3） ==========

export type AiResourceType = 'ARTICLE' | 'HOTLINE'

export interface AiResource {
  type: AiResourceType
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

// ---- 徽章（Sprint 1.9，与 mall-wish BadgeWallItemVO/BadgeDefinitionVO 对齐）----

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

// ---- 还愿（Sprint 1.10，与 mall-wish WishFulfillmentSubmitVO/WishFulfillmentVO 对齐）----

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

// ---- 世界树（Sprint 2.1，与 mall-wish WorldTreeVO/TreeFruitVO 对齐）----

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

// ---- 动态环境（Sprint 2.2，与 mall-wish TreeEnvVO/EnvConfigVO 对齐）----

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

// ============ Time Capsule（Sprint 2.4） ============

export type CapsuleStatus = 'SEALED' | 'AVAILABLE' | 'OPENED' | 'CANCELLED'

/** 胶囊视图：非 OPENED 状态 content/mediaUrls 恒为 null（开启是唯一拆信路径） */
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

export interface CreateCapsulePayload {
  title: string
  content: string
  mediaUrls?: string[]
  /** ISO 8601 UTC（到期判定唯一依据） */
  openAt: string
  /** 创建时 IANA 时区 */
  openAtTz: string
}

export interface MyCapsuleListQuery {
  status?: CapsuleStatus
  cursor?: string
  pageSize?: number
}

// ========== AI 心愿助手（Sprint 2.5，契约对齐 mall-wish AiAssistantController） ==========

export type AiGoalStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export type ExpectedActionType = 'EXTEND' | 'ADJUST' | 'TO_CAPSULE'

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
  id: number
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

export interface CreateAiGoalsPayload {
  sessionId: string
  wishId?: number
  goals: Array<Pick<AiBreakdownGoal, 'title' | 'description' | 'estimatedDays' | 'priority'>>
}

export interface MyAiGoalsQuery {
  status?: AiGoalStatus
  wishId?: number
  cursor?: string
  pageSize?: number
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

export interface NotificationPreferenceUpdate {
  type: NotificationType
  channel: NotificationChannel
  enabled: boolean
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

// ========== 排行榜 + 传承（Sprint 2.7，契约对齐 mall-wish LeaderboardController） ==========

export type LeaderboardType = 'HOT' | 'WARM' | 'PERSISTENCE' | 'SPARK'

export interface LeaderboardEntry {
  rank: number
  /** 心愿榜为心愿作者；用户榜为本人 */
  userId: number | null
  nickname: string
  avatar: string
  score: number
  extra: {
    wishTitle?: string
    checkinDays?: number
    helpedCount?: number
    lightCount?: number
    blessCount?: number
  }
  /** 排名变化（三端动效一致依据） */
  rankDelta: 'UP' | 'DOWN' | 'FLAT' | 'NEW'
}

/** 传承推送结果 */
export interface InheritResult {
  inheritId: number
  pushedCount: number
  createdAt: string
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

// ========== 城市幸福地图 + 围栏（Sprint 3.2，契约对齐 mall-wish WarmMapController） ==========

export interface FenceCheckResult {
  wishId: number
  insideFence: boolean
  fenceName: string | null
  bloomTriggered: boolean
  matchedCount: number
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
