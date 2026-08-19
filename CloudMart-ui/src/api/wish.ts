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
  growthRecords: WishGrowthRecord[]
  checkinDays: number
  progress: WishProgress | null
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

export type InteractionType = 'LIGHT' | 'SAME_WISH' | 'BLESS'

export interface InteractionResult {
  id: number
  type: InteractionType
  lightCount: number
  sameWishCount: number
  blessCount: number
  starlightCost: number
}

export interface InteractionRevokeResult {
  id: number
  type: InteractionType
  revoked: boolean
}

export interface InteractionItem {
  id: number
  userId: number
  nickname: string
  avatar: string
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
