import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getCommunityStats,
  getCommunityTrend,
  getAdminPosts,
  updatePostStatus,
  togglePostTop,
  getAdminComments,
  updateCommentStatus,
  getAdminTags,
  createAdminTag,
  updateAdminTag,
  deleteAdminTag,
  getAdminReports,
  handleReport,
  getAdminBadges,
  createAdminBadge,
  updateAdminBadge,
  deleteAdminBadge,
  grantBadge,
  getAdminGrowthLevelConfigs,
  createAdminGrowthLevelConfig,
  updateAdminGrowthLevelConfig,
  deleteAdminGrowthLevelConfig,
  getPendingReviewPosts,
  approvePost,
  rejectPost,
  getSensitiveWords,
  addSensitiveWord,
  deleteSensitiveWord,
  refreshSensitiveWordCache,
  getChatConversations,
  getChatMessages,
} from './community'

describe('admin community API - Community Stats', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getCommunityStats() calls GET /admin/stats/overview', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCommunityStats()
    expect(request.get).toHaveBeenCalledWith('/admin/stats/overview')
  })

  it('getCommunityTrend() calls GET /admin/stats/trend with days param', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCommunityTrend(7)
    expect(request.get).toHaveBeenCalledWith('/admin/stats/trend', { params: { days: 7 } })
  })
})

describe('admin community API - Post Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminPosts() calls GET /admin/community/posts', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminPosts({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/posts', { params: { page: 1 } })
  })

  it('updatePostStatus() calls PUT /admin/community/posts/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updatePostStatus(1, { status: 1 })
    expect(request.put).toHaveBeenCalledWith('/admin/community/posts/1/status', { status: 1 })
  })

  it('togglePostTop() calls PUT /admin/community/posts/:id/top', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await togglePostTop(1, { isTop: true })
    expect(request.put).toHaveBeenCalledWith('/admin/community/posts/1/top', { isTop: true })
  })
})

describe('admin community API - Comment Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminComments() calls GET /admin/community/comments', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminComments({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/comments', { params: { page: 1 } })
  })

  it('updateCommentStatus() calls PUT /admin/community/comments/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateCommentStatus(1, { status: 0 })
    expect(request.put).toHaveBeenCalledWith('/admin/community/comments/1/status', { status: 0 })
  })
})

describe('admin community API - Tag Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminTags() calls GET /admin/community/tags', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminTags({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/tags', { params: { page: 1 } })
  })

  it('createAdminTag() calls POST /admin/community/tags', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createAdminTag({ name: 'Tech' })
    expect(request.post).toHaveBeenCalledWith('/admin/community/tags', { name: 'Tech' })
  })

  it('updateAdminTag() calls PUT /admin/community/tags/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateAdminTag(1, { name: 'Updated' })
    expect(request.put).toHaveBeenCalledWith('/admin/community/tags/1', { name: 'Updated' })
  })

  it('deleteAdminTag() calls DELETE /admin/community/tags/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteAdminTag(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/community/tags/1')
  })
})

describe('admin community API - Report Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminReports() calls GET /admin/community/reports', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminReports({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/reports', { params: { page: 1 } })
  })

  it('handleReport() calls PUT /admin/community/reports/:id/handle', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await handleReport(1, { status: 2, handleNote: 'resolved' })
    expect(request.put).toHaveBeenCalledWith('/admin/community/reports/1/handle', { status: 2, handleNote: 'resolved' })
  })
})

describe('admin community API - Badge Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminBadges() calls GET /admin/community/badges', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminBadges({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/badges', { params: { page: 1 } })
  })

  it('createAdminBadge() calls POST /admin/community/badges', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createAdminBadge({ name: 'VIP' })
    expect(request.post).toHaveBeenCalledWith('/admin/community/badges', { name: 'VIP' })
  })

  it('updateAdminBadge() calls PUT /admin/community/badges/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateAdminBadge(1, { name: 'Super VIP' })
    expect(request.put).toHaveBeenCalledWith('/admin/community/badges/1', { name: 'Super VIP' })
  })

  it('deleteAdminBadge() calls DELETE /admin/community/badges/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteAdminBadge(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/community/badges/1')
  })

  it('grantBadge() calls POST /admin/community/badges/:id/grant', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await grantBadge(1, { userId: 10 })
    expect(request.post).toHaveBeenCalledWith('/admin/community/badges/1/grant', { userId: 10 })
  })
})

describe('admin community API - Growth Level Config', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getAdminGrowthLevelConfigs() calls GET /admin/community/growth/level-configs', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getAdminGrowthLevelConfigs({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/community/growth/level-configs', { params: { page: 1 } })
  })

  it('createAdminGrowthLevelConfig() calls POST /admin/community/growth/level-configs', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createAdminGrowthLevelConfig({ level: 1, title: 'Novice' })
    expect(request.post).toHaveBeenCalledWith('/admin/community/growth/level-configs', { level: 1, title: 'Novice' })
  })

  it('updateAdminGrowthLevelConfig() calls PUT /admin/community/growth/level-configs/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateAdminGrowthLevelConfig(1, { title: 'Expert' })
    expect(request.put).toHaveBeenCalledWith('/admin/community/growth/level-configs/1', { title: 'Expert' })
  })

  it('deleteAdminGrowthLevelConfig() calls DELETE /admin/community/growth/level-configs/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteAdminGrowthLevelConfig(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/community/growth/level-configs/1')
  })
})

describe('admin community API - Content Review', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getPendingReviewPosts() calls GET /admin/review/pending/posts', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getPendingReviewPosts({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/review/pending/posts', { params: { page: 1 } })
  })

  it('approvePost() calls PUT /admin/review/posts/:id/approve', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await approvePost(1)
    expect(request.put).toHaveBeenCalledWith('/admin/review/posts/1/approve')
  })

  it('rejectPost() calls PUT /admin/review/posts/:id/reject', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await rejectPost(1, { reason: 'inappropriate' })
    expect(request.put).toHaveBeenCalledWith('/admin/review/posts/1/reject', { reason: 'inappropriate' })
  })
})

describe('admin community API - Sensitive Word Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getSensitiveWords() calls GET /admin/review/sensitive-words', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getSensitiveWords({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/review/sensitive-words', { params: { page: 1 } })
  })

  it('addSensitiveWord() calls POST /admin/review/sensitive-words', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await addSensitiveWord({ word: 'bad', category: 'profanity', level: 2 })
    expect(request.post).toHaveBeenCalledWith('/admin/review/sensitive-words', { word: 'bad', category: 'profanity', level: 2 })
  })

  it('deleteSensitiveWord() calls DELETE /admin/review/sensitive-words/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteSensitiveWord(1)
    expect(request.delete).toHaveBeenCalledWith('/admin/review/sensitive-words/1')
  })

  it('refreshSensitiveWordCache() calls POST /admin/review/sensitive-words/refresh', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await refreshSensitiveWordCache()
    expect(request.post).toHaveBeenCalledWith('/admin/review/sensitive-words/refresh')
  })
})

describe('admin community API - Chat Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getChatConversations() calls GET /admin/chat/conversations', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getChatConversations({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/chat/conversations', { params: { page: 1 } })
  })

  it('getChatMessages() calls GET /admin/chat/conversations/:conversationId/messages', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getChatMessages(1, { page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/chat/conversations/1/messages', { params: { page: 1 } })
  })
})
