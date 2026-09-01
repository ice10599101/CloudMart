export type { ApiResponse } from './api'

export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDING' | 'REFUNDED'

export interface OrderItem {
  id: number
  productId: number
  skuId: number
  productName: string
  skuImage: string
  skuAttributes: string
  quantity: number
  price: number
}

export interface Order {
  id: number
  orderNo: string
  totalAmount: number
  payAmount: number
  discountAmount: number
  couponId: number | null
  status: OrderStatus
  receiverName: string | null
  receiverPhone: string | null
  receiverAddress: string | null
  shippedAt: string | null
  completedAt: string | null
  refundReason: string | null
  refundRejectReason: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

export interface CreateOrderRequest {
  requestId: string
  items: CreateOrderItemInput[]
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  couponId?: number
  activityId?: number
}

export interface CreateOrderItemInput {
  productId?: number
  skuId: number
  quantity: number
  productName?: string
  skuImage?: string
  skuAttributes?: string
  price: number
}

export interface OrderQueryParams {
  page?: number
  size?: number
  status?: OrderStatus
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

export const ORDER_STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'orange',
  PAID: 'blue',
  SHIPPED: 'cyan',
  COMPLETED: 'green',
  CANCELLED: 'default',
  REFUNDING: 'volcano',
  REFUNDED: 'red',
}

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDING' | 'REFUNDED'

export interface Payment {
  id: number
  orderId: number
  paymentNo: string
  paymentMethod: string
  amount: number
  status: PaymentStatus
  paidAt: string | null
  createdAt: string
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

export interface Product {
  id: number
  name: string
  description: string
  categoryId: number
  categoryName?: string
  brand?: string
  mainImage: string
  status: number
  skus: Sku[]
  createdAt: string
}

export interface Sku {
  id: number
  productId: number
  attributes: string
  price: number
  originalPrice: number
  stock: number
  image: string
  status: number
}

export interface Category {
  id: number
  name: string
  parentId: number | null
  icon: string | null
  sortOrder: number
  status: number
  children?: Category[]
}

export interface ProductSearchRequest {
  keyword?: string
  categoryId?: number
  brand?: string
  minPrice?: number
  maxPrice?: number
  sort?: string
  page?: number
  size?: number
}

/**
 * 搜索结果商品项：对应后端 ProductVO。
 * name 字段可能含 ES 高亮 <em> 标签，需通过 renderHighlight 渲染。
 */
export interface ProductSearchItem {
  id: number
  name: string
  mainImage: string
  price: number
  originalPrice: number
  stock: number
  sales: number | null
  categoryName: string
  brandName: string
  status: number
  createdAt: string
}

export interface BrandBucket {
  brand: string
  count: number
}

export interface CategoryBucket {
  categoryId: number
  count: number
}

export interface ProductSearchResult {
  products: ProductSearchItem[]
  brands: BrandBucket[]
  categories: CategoryBucket[]
  total: number
  page: number
  size: number
}

export interface CartItem {
  id: number
  skuId: number
  productId: number
  productName: string
  skuImage: string
  skuAttributes: string
  price: number
  quantity: number
  checked: number
}

export interface Cart {
  items: CartItem[]
  totalQuantity: number
  totalPrice: number
}

export type CouponType = 'AMOUNT_OFF' | 'PERCENT_OFF'
export type CouponValidityType = 'FIXED_DATE' | 'FIXED_DAYS'
export type CouponTemplateStatus = 'ENABLED' | 'DISABLED'
export type UserCouponStatus = 'UNUSED' | 'USED' | 'EXPIRED'

export interface CouponTemplate {
  id: number
  name: string
  type: CouponType
  discountAmount?: number
  discountRate?: number
  thresholdAmount: number
  totalQuantity: number
  remainingQuantity: number
  perUserLimit: number
  validityType: CouponValidityType
  validDays: number | null
  startTime: string | null
  endTime: string | null
  status: CouponTemplateStatus
  createdAt: string
}

export interface UserCoupon {
  id: number
  templateId: number
  templateName: string
  templateType: CouponType
  discountAmount?: number
  discountRate?: number
  thresholdAmount: number
  status: UserCouponStatus
  usedAt: string | null
  expiredAt: string
}

export type SeckillActivityStatus = 'UPCOMING' | 'ONGOING' | 'ENDED'
export type SeckillProductStatus = 'ON_SHELF' | 'OFF_SHELF'
export type SeckillResultStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

export interface SeckillActivity {
  id: number
  name: string
  description: string
  startTime: string
  endTime: string
  status: SeckillActivityStatus
  createdAt: string
}

export interface SeckillProduct {
  id: number
  activityId: number
  skuId: number
  productName: string
  seckillPrice: number
  originalPrice: number
  totalStock: number
  availableStock: number
  perUserLimit: number
  status: SeckillProductStatus
  createdAt: string
}

export interface SeckillResult {
  orderId: number | null
  status: SeckillResultStatus
  message: string
}

export interface ShippingAddress {
  id: number
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: boolean
}

export interface CreateAddressRequest {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault?: boolean
}

export interface UpdateAddressRequest {
  receiverName?: string
  receiverPhone?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  isDefault?: boolean
}

export interface AdminInfo {
  id: number
  username: string
  avatar: string
  permissions: string[]
  roles: Array<{ roleKey: string; roleName: string }>
}

export type PostType = 'ARTICLE' | 'VIDEO' | 'PRODUCT' | 'REPOST'
export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN' | 'DELETED'

export interface PostAuthor {
  id: number
  nickname: string
  avatar: string
  level: number
  isFollowed: boolean
}

export interface PostTag {
  id: number
  name: string
  heat: number
}

export interface Post {
  id: number
  type: PostType
  title: string
  content: string
  summary: string
  coverImage: string
  images: string[]
  videoUrl: string | null
  videoCover: string | null
  author: PostAuthor
  tags: PostTag[]
  productId: number | null
  productName: string | null
  productPrice: number | null
  likeCount: number
  commentCount: number
  collectCount: number
  shareCount: number
  viewCount: number
  isLiked: boolean
  isCollected: boolean
  status: PostStatus
  createdAt: string
}

export interface PostComment {
  id: number
  postId: number
  author: PostAuthor
  content: string
  parentId: number | null
  replyTo: PostAuthor | null
  likeCount: number
  isLiked: boolean
  children: PostComment[]
  createdAt: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatar: string
  signature: string
  gender: string
  constellation: string | null
  email: string | null
  occupation: string | null
  school: string | null
  hobbies: string | null
  location: string | null
  status: number
  createdAt: string
}

export interface UserBadge {
  id: number
  name: string
  icon: string
  description: string
}

export interface HotTopic {
  id: number | string
  name: string
  postCount: number
  heat: number
  icon?: string
  isHot: boolean
}

export interface RecommendUser {
  userId: number
  nickname: string
  avatar: string
  postCount: number
  followCount: number
  followerCount: number
  collectCount: number
  isFollowed: boolean
}
