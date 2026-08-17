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

// ========== Wish Universe (心愿宇宙) ==========

export type WishVisibility = 'PUBLIC' | 'PRIVATE' | 'TREE_HOLE'
export type WishStatus = 'DRAFT' | 'ACTIVE' | 'OVERDUE' | 'FULFILLING' | 'FULFILLED' | 'ARCHIVED'
export type FruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'

export interface WishCategory {
  id: number
  code: string
  name: string
  icon: string
  sortOrder: number
}

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

export interface MyWishListItem {
  id: number
  title: string
  status: WishStatus
  fruitType: FruitType
  progress: number
  lightCount: number
  createdAt: string
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
  growthRecords: WishGrowthRecord[]
  checkinDays: number
  progress: WishProgress | null
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

export interface HomeAggregation {
  worldTree: null
  todayRecommend: TodayRecommendItem[]
  myWishes: MyWishSummary[]
  hotResonance: HotResonanceItem[]
  entries: {
    wishEntry: boolean
    mapEntry: boolean
    aiAssistantEntry: boolean
  }
}
