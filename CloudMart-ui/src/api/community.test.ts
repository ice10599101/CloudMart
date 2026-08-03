import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  searchUsers, getFollowers, getFollowingList, getUserProfile,
  getPostDetail, createPost, deletePost, likePost, unlikePost,
  collectPost, uncollectPost, getPostComments, createComment,
  searchPosts, getHotTopics, followUser, unfollowUser,
} from './community'

describe('community API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('searchUsers() calls GET /community/users/search with keyword', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await searchUsers('test')

    expect(request.get).toHaveBeenCalledWith('/community/users/search', { params: { keyword: 'test' } })
  })

  it('getFollowers() calls GET /community/users/:id/followers', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getFollowers(1, 2, 10)

    expect(request.get).toHaveBeenCalledWith('/community/users/1/followers', { params: { page: 2, size: 10 } })
  })

  it('getFollowingList() calls GET /community/users/:id/following', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getFollowingList(1)

    expect(request.get).toHaveBeenCalledWith('/community/users/1/following', { params: { page: 1, size: 20 } })
  })

  it('getUserProfile() calls GET /community/users/:id/profile', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getUserProfile(1)

    expect(request.get).toHaveBeenCalledWith('/community/users/1/profile')
  })

  it('followUser() calls POST /community/users/:id/follow', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await followUser(1)

    expect(request.post).toHaveBeenCalledWith('/community/users/1/follow')
  })

  it('unfollowUser() calls DELETE /community/users/:id/follow', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await unfollowUser(1)

    expect(request.delete).toHaveBeenCalledWith('/community/users/1/follow')
  })

  it('getPostDetail() calls GET /community/posts/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPostDetail(1)

    expect(request.get).toHaveBeenCalledWith('/community/posts/1')
  })

  it('createPost() calls POST /community/posts with JSON data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createPost({ title: 'Test', content: 'Hello' })

    expect(request.post).toHaveBeenCalledWith('/community/posts', { title: 'Test', content: 'Hello' })
  })

  it('deletePost() calls DELETE /community/posts/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await deletePost(1)

    expect(request.delete).toHaveBeenCalledWith('/community/posts/1')
  })

  it('likePost() calls POST /community/posts/:id/like', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await likePost(1)

    expect(request.post).toHaveBeenCalledWith('/community/posts/1/like')
  })

  it('unlikePost() calls DELETE /community/posts/:id/like', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await unlikePost(1)

    expect(request.delete).toHaveBeenCalledWith('/community/posts/1/like')
  })

  it('collectPost() calls POST /community/posts/:id/collect', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await collectPost(1)

    expect(request.post).toHaveBeenCalledWith('/community/posts/1/collect')
  })

  it('uncollectPost() calls DELETE /community/posts/:id/collect', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await uncollectPost(1)

    expect(request.delete).toHaveBeenCalledWith('/community/posts/1/collect')
  })

  it('getPostComments() calls GET /community/posts/:id/comments', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getPostComments(1, 1, 20)

    expect(request.get).toHaveBeenCalledWith('/community/posts/1/comments', { params: { page: 1, size: 20 } })
  })

  it('createComment() calls POST /community/posts/:id/comments', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createComment(1, { content: 'Nice post' })

    expect(request.post).toHaveBeenCalledWith('/community/posts/1/comments', { content: 'Nice post' })
  })

  it('searchPosts() calls GET /community/posts/search', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await searchPosts('keyword', 1, 10)

    expect(request.get).toHaveBeenCalledWith('/community/posts/search', { params: { keyword: 'keyword', page: 1, size: 10 } })
  })

  it('getHotTopics() calls GET /community/tags/hot', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getHotTopics()

    expect(request.get).toHaveBeenCalledWith('/community/tags/hot')
  })
})
