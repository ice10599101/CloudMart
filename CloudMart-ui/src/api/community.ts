import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface SearchUserResult {
  id: number
  nickname: string
  avatar: string
}

export interface FollowUserRawItem {
  id: number
  userId?: number
  nickname: string
  avatar: string
  signature: string
  isFollowed: boolean
  isMutual: boolean
}

export interface PostTag {
  id: number
  name: string
}

export interface Post {
  id: number
  type: string
  title: string
  content: string
  summary: string
  coverImage: string
  images: string[]
  mediaUrls: string[]
  mediaType: string
  videoUrl: string | null
  videoCover: string | null
  authorAvatar: string
  authorNickname: string
  userId: number
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
  status: string
  createdAt: string
}

export interface PostComment {
  id: number
  postId: number
  authorAvatar: string
  authorNickname: string
  userId: number
  content: string
  parentId: number | null
  replyToNickname?: string
  replyToUserId?: number
  likeCount: number
  isLiked: boolean
  replies: PostComment[]
  createdAt: string
}

export interface HotTopic {
  id: number | string
  name: string
  postCount: number
  icon?: string
  isHot?: boolean
  isSubscribed?: boolean
}

export type CollectionPostItem = Post & { collectedAt?: string }

export interface UserProfile {
  id: number
  nickname: string
  avatar: string
  signature: string
  level: number
  exp: number
  postCount: number
  followerCount: number
  followingCount: number
  followCount: number
  collectionCount: number
  collectCount: number
  isFollowed: boolean
  isMutual: boolean
  isBlocked: boolean
  badges: Array<{ id: number; name: string; icon: string; description: string }>
}

export interface UserSettings {
  [key: string]: string
}

export function searchUsers(keyword: string) {
  return request.get<ApiResponse<SearchUserResult[]>>('/community/users/search', { params: { keyword } })
}

export function getFollowers(userId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<FollowUserRawItem[]>>(`/community/users/${userId}/followers`, { params: { page, size } })
}

export function getFollowingList(userId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<FollowUserRawItem[]>>(`/community/users/${userId}/following`, { params: { page, size } })
}

export function getUserProfile(userId: number | string) {
  return request.get<ApiResponse<UserProfile>>(`/community/users/${userId}/profile`)
}

export function getUserPosts(userId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>(`/community/posts/users/${userId}`, { params: { page, size } })
}

export function getUserCollections(userId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<CollectionPostItem[]>>(`/community/users/${userId}/collections`, { params: { page, size } })
}

export function followUser(userId: number | string) {
  return request.post<ApiResponse<void>>(`/community/users/${userId}/follow`)
}

export function unfollowUser(userId: number | string) {
  return request.delete<ApiResponse<void>>(`/community/users/${userId}/follow`)
}

export function getRecommendUsers(limit = 6) {
  return request.get<ApiResponse<SearchUserResult[]>>('/community/users/recommend', { params: { limit } })
}

export function getFeedPosts(tab = 'recommend', page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>('/community/posts/feed', { params: { tab, page, size } })
}

export function getFollowingFeed(page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>('/community/posts/feed/following', { params: { page, size } })
}

export function getPostDetail(postId: number | string) {
  return request.get<ApiResponse<Post>>(`/community/posts/${postId}`)
}

export function createPost(data: FormData | Record<string, unknown>) {
  if (data instanceof FormData) {
    return request.post<ApiResponse<Post>>('/community/posts', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }
  return request.post<ApiResponse<Post>>('/community/posts', data)
}

export function updatePost(postId: number, data: FormData | Record<string, unknown>) {
  if (data instanceof FormData) {
    return request.put<ApiResponse<Post>>(`/community/posts/${postId}`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }
  return request.put<ApiResponse<Post>>(`/community/posts/${postId}`, data)
}

export function deletePost(postId: number | string) {
  return request.delete<ApiResponse<void>>(`/community/posts/${postId}`)
}

export function likePost(postId: number) {
  return request.post<ApiResponse<void>>(`/community/posts/${postId}/like`)
}

export function unlikePost(postId: number) {
  return request.delete<ApiResponse<void>>(`/community/posts/${postId}/like`)
}

export function collectPost(postId: number) {
  return request.post<ApiResponse<void>>(`/community/posts/${postId}/collect`)
}

export function uncollectPost(postId: number) {
  return request.delete<ApiResponse<void>>(`/community/posts/${postId}/collect`)
}

export function sharePost(postId: number, channel = 'LINK') {
  return request.post<ApiResponse<void>>(`/community/posts/${postId}/share`, null, { params: { channel } })
}

export function getPostComments(postId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<PostComment[]>>(`/community/posts/${postId}/comments`, { params: { page, size } })
}

export function createComment(postId: number | string, data: { content: string; parentId?: number | string; replyToUserId?: number | string }) {
  return request.post<ApiResponse<PostComment>>(`/community/posts/${postId}/comments`, data)
}

export function likeComment(commentId: number) {
  return request.post<ApiResponse<void>>(`/community/comments/${commentId}/like`)
}

export function unlikeComment(commentId: number) {
  return request.delete<ApiResponse<void>>(`/community/comments/${commentId}/like`)
}

export function searchPosts(keyword: string, page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>('/community/posts/search', { params: { keyword, page, size } })
}

export function getSearchHistory(limit = 10) {
  return request.get<ApiResponse<string[]>>('/community/search/history', { params: { limit } })
}

export function clearSearchHistory() {
  return request.delete<ApiResponse<void>>('/community/search/history')
}

export function getHotSearches(limit = 10) {
  return request.get<ApiResponse<string[]>>('/community/search/hot', { params: { limit } })
}

export function getHotTopics() {
  return request.get<ApiResponse<HotTopic[]>>('/community/tags/hot')
}

export function getTrendingTopics(limit = 10) {
  return request.get<ApiResponse<HotTopic[]>>('/community/tags/trending', { params: { limit } })
}

export function getPostsByTopic(tagId: number | string, page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>(`/community/posts/tags/${tagId}`, { params: { page, size } })
}

export function subscribeTag(tagId: number | string) {
  return request.post<ApiResponse<void>>(`/community/tags/subscriptions/${tagId}`)
}

export function unsubscribeTag(tagId: number | string) {
  return request.delete<ApiResponse<void>>(`/community/tags/subscriptions/${tagId}`)
}

export function checkTagSubscription(tagId: number | string) {
  return request.get<ApiResponse<boolean>>(`/community/tags/subscriptions/${tagId}/status`)
}

export function blockUser(userId: number | string) {
  return request.post<ApiResponse<void>>(`/community/blocks/${userId}`)
}

export function unblockUser(userId: number | string) {
  return request.delete<ApiResponse<void>>(`/community/blocks/${userId}`)
}

export function checkBlockStatus(targetUserId: number | string) {
  return request.get<ApiResponse<boolean>>('/community/blocks/check', { params: { targetUserId } })
}

export function createReport(data: { targetType: string; targetId: number; reason: string; description?: string }) {
  return request.post<ApiResponse<void>>('/community/reports', data)
}

export function getUserSettings() {
  return request.get<ApiResponse<UserSettings>>('/community/settings')
}

export function updateUserSettings(data: UserSettings) {
  return request.put<ApiResponse<void>>('/community/settings', data)
}

export function getUserDrafts(page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>('/community/posts/drafts', { params: { page, size } })
}

export function saveDraft(data: { id?: number; title: string; content: string; coverImage?: string; mediaUrls?: string[]; mediaType?: string; tagIds?: number[] }) {
  if (data.id) {
    return request.put<ApiResponse<Post>>(`/community/posts/${data.id}`, { ...data, status: 0 })
  }
  return request.post<ApiResponse<Post>>('/community/posts', { ...data, status: 0 })
}

export interface MyComment {
  id: number
  postId: number
  postTitle: string
  content: string
  parentId: number | null
  replyToNickname: string | null
  likeCount: number
  createdAt: string
}

export function getLikedPosts(page = 1, size = 20) {
  return request.get<ApiResponse<Post[]>>('/community/posts/liked', { params: { page, size } })
}

export function getMyComments(page = 1, size = 20) {
  return request.get<ApiResponse<MyComment[]>>('/community/comments/mine', { params: { page, size } })
}
