import request from '@/utils/request'
import type { Post, Comment, Tag, User, UserBasic, FollowUser, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const communityApi = {
  getFeed: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/feed${buildQuery(params as Record<string, unknown>)}` }),
  getFollowingFeed: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/feed/following${buildQuery(params as Record<string, unknown>)}` }),
  getPost: (id: number | string) => request<Post>({ url: `/community/posts/${id}` }),
  createPost: (data: { title: string; content: string; tags?: string[] }) =>
    request<Post>({ url: '/community/posts', method: 'POST', data }),
  deletePost: (id: number | string) => request<void>({ url: `/community/posts/${id}`, method: 'DELETE' }),
  likePost: (id: number | string) => request<void>({ url: `/community/posts/${id}/like`, method: 'POST' }),
  unlikePost: (id: number | string) => request<void>({ url: `/community/posts/${id}/like`, method: 'DELETE' }),
  collectPost: (id: number | string) => request<void>({ url: `/community/posts/${id}/collect`, method: 'POST' }),
  uncollectPost: (id: number | string) => request<void>({ url: `/community/posts/${id}/collect`, method: 'DELETE' }),
  sharePost: (id: number | string) => request<void>({ url: `/community/posts/${id}/share`, method: 'POST' }),
  getComments: (postId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Comment>>({ url: `/community/posts/${postId}/comments${buildQuery(params as Record<string, unknown>)}` }),
  createComment: (postId: number | string, data: { content: string; parentId?: number | string }) =>
    request<Comment>({ url: `/community/posts/${postId}/comments`, method: 'POST', data }),
  likeComment: (id: number | string) => request<void>({ url: `/community/comments/${id}/like`, method: 'POST' }),
  unlikeComment: (id: number | string) => request<void>({ url: `/community/comments/${id}/like`, method: 'DELETE' }),
  searchPosts: (params: { keyword: string; page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/search${buildQuery(params as Record<string, unknown>)}` }),
  getHotTags: () => request<Tag[]>({ url: '/community/tags/hot' }),
  getTrendingTags: () => request<Tag[]>({ url: '/community/tags/trending' }),
  getTagPosts: (tagId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/tags/${tagId}${buildQuery(params as Record<string, unknown>)}` }),
  getUserPosts: (userId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/users/${userId}${buildQuery(params as Record<string, unknown>)}` }),
  followUser: (userId: number | string) => request<void>({ url: `/community/users/${userId}/follow`, method: 'POST' }),
  unfollowUser: (userId: number | string) => request<void>({ url: `/community/users/${userId}/follow`, method: 'DELETE' }),
  getUserProfile: (userId: number | string) => request<User>({ url: `/community/users/${userId}/profile` }),
  getUserFollowers: (userId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<FollowUser>>({ url: `/community/users/${userId}/followers${buildQuery(params as Record<string, unknown>)}` }),
  getUserFollowing: (userId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<FollowUser>>({ url: `/community/users/${userId}/following${buildQuery(params as Record<string, unknown>)}` }),
  getUserCollections: (userId: number | string, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/users/${userId}/collections${buildQuery(params as Record<string, unknown>)}` }),
  getLikedPosts: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/liked${buildQuery(params as Record<string, unknown>)}` }),
  getMyComments: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Comment>>({ url: `/community/comments/mine${buildQuery(params as Record<string, unknown>)}` }),
  getUserDrafts: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<Post>>({ url: `/community/posts/drafts${buildQuery(params as Record<string, unknown>)}` }),
  updatePost: (id: number, data: Record<string, unknown>) =>
    request<Post>({ url: `/community/posts/${id}`, method: 'PUT', data }),
  searchUsers: (params: { keyword: string; page?: number; pageSize?: number }) =>
    request<PaginatedResult<UserBasic>>({ url: `/community/users/search${buildQuery(params as Record<string, unknown>)}` }),
  blockUser: (userId: number | string) => request<void>({ url: `/community/blocks/${userId}`, method: 'POST' }),
  unblockUser: (userId: number | string) => request<void>({ url: `/community/blocks/${userId}`, method: 'DELETE' }),
  report: (data: { targetType: string; targetId: number | string; reason: string; description?: string }) =>
    request<void>({ url: '/community/reports', method: 'POST', data }),
  getSettings: () => request<Record<string, string>>({ url: '/community/settings' }),
  updateSettings: (data: Record<string, unknown>) => request<void>({ url: '/community/settings', method: 'PUT', data }),
  getHotSearch: () => request<string[]>({ url: '/community/search/hot' }),
  getSearchHistory: () => request<string[]>({ url: '/community/search/history' }),
  clearSearchHistory: () => request<void>({ url: '/community/search/history', method: 'DELETE' }),
}
