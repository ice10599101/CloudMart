import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  banAdminSoftware,
  createSoftwareCategory,
  createSoftwareTag,
  decideReview,
  disableSoftwareCategory,
  disableSoftwareTag,
  featureAdminSoftware,
  getAdminSoftwareDetail,
  getReviewDetail,
  grantSoftwareAccess,
  handleSoftwareReport,
  listAccessContacts,
  listAccessGrants,
  listAdminSoftware,
  listAdminSoftwareCategories,
  listAdminSoftwareComments,
  listAdminSoftwareTags,
  listLinkFeedbacks,
  listPendingReviews,
  listPermissionAudits,
  listPublisherPermissions,
  listSoftwareReports,
  markLinkFeedback,
  moderateAdminSoftwareComment,
  saveAccessContact,
  setSourceState,
  togglePublisherPermission,
  unbanAdminSoftware,
  updateSoftwareCategory,
  updateSoftwareTag,
} from './software'

const ok = { data: { success: true, data: {} } } as any

/**
 * 管理端软件 API 契约（7.5）：路径、参数、幂等头（Idempotency-Key）与
 * 与菜单种子一致的权限点（权限点在后端 @RequiresPermission 校验）。
 */
describe('admin software API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listAdminSoftware() → GET /admin/software/items（目录筛选参数）', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listAdminSoftware({ keyword: '编辑器', status: 'ACTIVE', page: 1, size: 20 })

    expect(request.get).toHaveBeenCalledWith('/admin/software/items', {
      params: { keyword: '编辑器', status: 'ACTIVE', page: 1, size: 20 },
    })
  })

  it('封禁/解除：POST /items/:id/ban|unban 且携带 Idempotency-Key 与 expectedVersion', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await banAdminSoftware('100', { reason: '侵权', expectedVersion: 3 }, 'key-1')
    await unbanAdminSoftware('100', { reason: '核实无违规', expectedVersion: 6 }, 'key-2')

    expect(request.post).toHaveBeenNthCalledWith(
      1,
      '/admin/software/items/100/ban',
      { reason: '侵权', expectedVersion: 3 },
      { headers: { 'Idempotency-Key': 'key-1' } }
    )
    expect(request.post).toHaveBeenNthCalledWith(
      2,
      '/admin/software/items/100/unban',
      { reason: '核实无违规', expectedVersion: 6 },
      { headers: { 'Idempotency-Key': 'key-2' } }
    )
  })

  it('featureAdminSoftware() → PUT /items/:id/featured', async () => {
    vi.mocked(request.put).mockResolvedValue(ok)

    await featureAdminSoftware('100', { featuredRank: 3, expectedVersion: 1 }, 'key-3')

    expect(request.put).toHaveBeenCalledWith(
      '/admin/software/items/100/featured',
      { featuredRank: 3, expectedVersion: 1 },
      { headers: { 'Idempotency-Key': 'key-3' } }
    )
  })

  it('审核：待审列表/快照对比/决策路径与批次参数', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)

    await listPendingReviews({ page: 1, size: 20 })
    await getReviewDetail('501')
    await decideReview(
      '501',
      { softwareId: '100', submitSeq: 2, expectedVersion: 4, approve: true, reason: '通过' },
      'key-4'
    )

    expect(request.get).toHaveBeenNthCalledWith(1, '/admin/software/revisions/pending', {
      params: { page: 1, size: 20 },
    })
    expect(request.get).toHaveBeenNthCalledWith(2, '/admin/software/revisions/501')
    expect(request.post).toHaveBeenCalledWith(
      '/admin/software/revisions/501/decision',
      { softwareId: '100', submitSeq: 2, expectedVersion: 4, approve: true, reason: '通过' },
      { headers: { 'Idempotency-Key': 'key-4' } }
    )
  })

  it('评论管理：列表与处置 → /comments 路径', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)

    await listAdminSoftwareComments('100', { status: 'PENDING', page: 1, size: 20 })
    await moderateAdminSoftwareComment('100', '9', { action: 'APPROVE', reason: '合格' }, 'key-5')

    expect(request.get).toHaveBeenCalledWith('/admin/software/100/comments', {
      params: { status: 'PENDING', page: 1, size: 20 },
    })
    expect(request.post).toHaveBeenCalledWith(
      '/admin/software/100/comments/9/moderate',
      { action: 'APPROVE', reason: '合格' },
      { headers: { 'Idempotency-Key': 'key-5' } }
    )
  })

  it('分类/标签：CRUD 路径 + DELETE 语义为禁用（仍带幂等键）', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)
    vi.mocked(request.put).mockResolvedValue(ok)
    vi.mocked(request.delete).mockResolvedValue(ok)

    await listAdminSoftwareCategories()
    await createSoftwareCategory({ name: '开发工具', slug: 'dev-tools' }, 'cat-1')
    await updateSoftwareCategory('5', { name: '开发工具', slug: 'dev-tools', enabled: false }, 'cat-2')
    await disableSoftwareCategory('5', 'cat-3')
    await listAdminSoftwareTags({ keyword: '效率', page: 1, size: 50 })
    await createSoftwareTag({ name: '效率', enabled: true }, 'tag-1')
    await updateSoftwareTag('7', { name: '效率', enabled: false }, 'tag-2')
    await disableSoftwareTag('7', 'tag-3')

    expect(request.get).toHaveBeenNthCalledWith(1, '/admin/software/categories')
    expect(request.post).toHaveBeenNthCalledWith(
      1,
      '/admin/software/categories',
      { name: '开发工具', slug: 'dev-tools' },
      { headers: { 'Idempotency-Key': 'cat-1' } }
    )
    expect(request.put).toHaveBeenNthCalledWith(
      1,
      '/admin/software/categories/5',
      { name: '开发工具', slug: 'dev-tools', enabled: false },
      { headers: { 'Idempotency-Key': 'cat-2' } }
    )
    expect(request.delete).toHaveBeenNthCalledWith(1, '/admin/software/categories/5', {
      headers: { 'Idempotency-Key': 'cat-3' },
    })
    expect(request.get).toHaveBeenNthCalledWith(2, '/admin/software/tags', {
      params: { keyword: '效率', page: 1, size: 50 },
    })
    expect(request.post).toHaveBeenNthCalledWith(
      2,
      '/admin/software/tags',
      { name: '效率', enabled: true },
      { headers: { 'Idempotency-Key': 'tag-1' } }
    )
    expect(request.put).toHaveBeenNthCalledWith(
      2,
      '/admin/software/tags/7',
      { name: '效率', enabled: false },
      { headers: { 'Idempotency-Key': 'tag-2' } }
    )
    expect(request.delete).toHaveBeenNthCalledWith(2, '/admin/software/tags/7', {
      headers: { 'Idempotency-Key': 'tag-3' },
    })
  })

  it('发布资格：分页/批量/开关（路径与幂等键）', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)

    await listPublisherPermissions({ page: 1, size: 20 })
    await togglePublisherPermission('42', { enable: true, reason: '优质分享者' }, 'perm-1')

    expect(request.get).toHaveBeenCalledWith('/admin/software/publisher-permissions', {
      params: { page: 1, size: 20 },
    })
    expect(request.post).toHaveBeenCalledWith(
      '/admin/software/publisher-permissions/42/toggle',
      { enable: true, reason: '优质分享者' },
      { headers: { 'Idempotency-Key': 'perm-1' } }
    )
  })

  it('付款授权与审计：开通/分页/审计路径', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)
    vi.mocked(request.get).mockResolvedValue(ok)

    await grantSoftwareAccess(
      '100',
      { userId: '42', expiresAt: '2026-12-01T00:00:00', reason: '线下核对' },
      'grant-1'
    )
    await listAccessGrants({ softwareId: '100', page: 1, size: 20 })
    await listPermissionAudits({ userId: '42', page: 1, size: 20 })

    expect(request.post).toHaveBeenCalledWith(
      '/admin/software/100/access-grants',
      { userId: '42', expiresAt: '2026-12-01T00:00:00', reason: '线下核对' },
      { headers: { 'Idempotency-Key': 'grant-1' } }
    )
    expect(request.get).toHaveBeenNthCalledWith(1, '/admin/software/access-grants', {
      params: { softwareId: '100', page: 1, size: 20 },
    })
    expect(request.get).toHaveBeenNthCalledWith(2, '/admin/software/permission-audits', {
      params: { userId: '42', page: 1, size: 20 },
    })
  })

  it('联系入口：列表与保存路径', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)

    await listAccessContacts()
    await saveAccessContact(
      {
        name: '平台客服',
        contactType: 'SITE_USER',
        contactValue: 'kefu',
        enabled: true,
        sortOrder: 1,
      },
      'c-1'
    )

    expect(request.get).toHaveBeenCalledWith('/admin/software/access-contacts')
    expect(request.post).toHaveBeenCalledWith(
      '/admin/software/access-contacts',
      {
        name: '平台客服',
        contactType: 'SITE_USER',
        contactValue: 'kefu',
        enabled: true,
        sortOrder: 1,
      },
      { headers: { 'Idempotency-Key': 'c-1' } }
    )
  })

  it('举报与失效反馈：列表/处置/标记路径', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)
    vi.mocked(request.post).mockResolvedValue(ok)

    await listSoftwareReports({ status: 'OPEN', page: 1, size: 20 })
    await handleSoftwareReport('1', { status: 'RESOLVED', action: 'BAN_SOFTWARE', note: '侵权' }, 'r-1')
    await listLinkFeedbacks({ status: 'OPEN', page: 1, size: 20 })
    await markLinkFeedback('2', { status: 'RESOLVED', resolvedRevisionId: 900, note: '已换链' }, 'f-1')
    await setSourceState(
      '100',
      { sourceKey: 'src-1', availability: 'INVALID', reason: '多次反馈失效' },
      's-1'
    )

    expect(request.get).toHaveBeenNthCalledWith(1, '/admin/software/reports', {
      params: { status: 'OPEN', page: 1, size: 20 },
    })
    expect(request.post).toHaveBeenNthCalledWith(
      1,
      '/admin/software/reports/1/handle',
      { status: 'RESOLVED', action: 'BAN_SOFTWARE', note: '侵权' },
      { headers: { 'Idempotency-Key': 'r-1' } }
    )
    expect(request.get).toHaveBeenNthCalledWith(2, '/admin/software/link-feedbacks', {
      params: { status: 'OPEN', page: 1, size: 20 },
    })
    expect(request.post).toHaveBeenNthCalledWith(
      2,
      '/admin/software/link-feedbacks/2/mark',
      { status: 'RESOLVED', resolvedRevisionId: 900, note: '已换链' },
      { headers: { 'Idempotency-Key': 'f-1' } }
    )
    expect(request.post).toHaveBeenNthCalledWith(
      3,
      '/admin/software/100/sources/state',
      { sourceKey: 'src-1', availability: 'INVALID', reason: '多次反馈失效' },
      { headers: { 'Idempotency-Key': 's-1' } }
    )
  })

  it('getAdminSoftwareDetail() → GET /:softwareId/admin-detail', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getAdminSoftwareDetail('100')

    expect(request.get).toHaveBeenCalledWith('/admin/software/100/admin-detail')
  })
})
