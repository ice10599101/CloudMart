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
