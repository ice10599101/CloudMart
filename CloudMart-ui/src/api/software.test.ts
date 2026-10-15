import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  listSoftware,
  listSoftwareCategories,
  listSoftwareTags,
  listAllSoftwareTags,
  getSoftwareDetail,
  getSoftwareAccessPolicy,
  getSoftwareAccessState,
  listSoftwareReleases,
  resolveSoftwareSource,
  createSoftwareComment,
  deleteSoftwareComment,
  likeSoftwareComment,
  unlikeSoftwareComment,
  favoriteSoftware,
  unfavoriteSoftware,
  getPublisherStatus,
  createSoftware,
  getSoftwareEditor,
  saveSoftwareDraft,
  submitSoftwareForReview,
  listMySoftware,
  createWorkingRevision,
} from './software'

const ok = { data: { success: true, data: {} } } as any

function pageOf(records: unknown[], pages = 1) {
  return {
    data: {
      success: true,
      data: { current: 1, pages, size: 50, total: records.length, records },
    },
  } as any
}

describe('software API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listSoftware() 用后端字段名 license/sort 传参（方案 7.2）', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listSoftware({
      keyword: '编辑器',
      categoryId: '12',
      platform: 'WINDOWS',
      license: 'OPEN_SOURCE',
      tagId: '3',
      sort: 'latest',
      page: 2,
      size: 20,
    })

    expect(request.get).toHaveBeenCalledWith('/software', {
      params: {
        keyword: '编辑器',
        categoryId: '12',
        platform: 'WINDOWS',
        license: 'OPEN_SOURCE',
        tagId: '3',
        sort: 'latest',
        page: 2,
        size: 20,
      },
    })
  })

  it('listSoftwareCategories() 请求 /software/categories', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listSoftwareCategories()

    expect(request.get).toHaveBeenCalledWith('/software/categories')
  })

  it('listSoftwareTags() 分页返回，参数透传', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listSoftwareTags({ keyword: '剪辑', page: 1, size: 50 })

    expect(request.get).toHaveBeenCalledWith('/software/tags', {
      params: { keyword: '剪辑', page: 1, size: 50 },
    })
  })

  it('listAllSoftwareTags() 单页时只请求一次', async () => {
    vi.mocked(request.get).mockResolvedValue(
      pageOf([{ id: '1', name: 'a' }])
    )

    const tags = await listAllSoftwareTags()

    expect(tags).toEqual([{ id: '1', name: 'a' }])
    expect(request.get).toHaveBeenCalledTimes(1)
  })

  it('listAllSoftwareTags() 多页时按 pages 合并，不丢后段标签', async () => {
    vi.mocked(request.get)
      .mockResolvedValueOnce(pageOf([{ id: '1', name: 'a' }], 2))
      .mockResolvedValueOnce(pageOf([{ id: '2', name: 'b' }], 2))

    const tags = await listAllSoftwareTags()

    expect(tags.map((tag) => tag.id)).toEqual(['1', '2'])
    expect(request.get).toHaveBeenNthCalledWith(2, '/software/tags', {
      params: { page: 2, size: 50 },
    })
  })

  it('getSoftwareDetail() 请求 /software/:id', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getSoftwareDetail('100')

    expect(request.get).toHaveBeenCalledWith('/software/100')
  })

  it('getSoftwareAccessPolicy() 请求 /software/:id/access-policy', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getSoftwareAccessPolicy('100')

    expect(request.get).toHaveBeenCalledWith('/software/100/access-policy')
  })

  it('getSoftwareAccessState() 请求 /software/:id/access-state', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getSoftwareAccessState('100')

    expect(request.get).toHaveBeenCalledWith('/software/100/access-state')
  })

  it('listSoftwareReleases() 请求 /software/:id/releases', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listSoftwareReleases('100')

    expect(request.get).toHaveBeenCalledWith('/software/100/releases')
  })

  it('resolveSoftwareSource() 携带幂等键时下发 Idempotency-Key 头', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await resolveSoftwareSource('100', '9', 'key-1')

    expect(request.post).toHaveBeenCalledWith(
      '/software/100/sources/9/resolve',
      null,
      { headers: { 'Idempotency-Key': 'key-1' } }
    )
  })

  it('resolveSoftwareSource() 无幂等键时不带该头', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await resolveSoftwareSource('100', '9')

    expect(request.post).toHaveBeenCalledWith(
      '/software/100/sources/9/resolve',
      null,
      { headers: undefined }
    )
  })

  it('createSoftwareComment() 提交内容并携带幂等键', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await createSoftwareComment('100', { content: '好用', parentId: '5' }, 'key-2')

    expect(request.post).toHaveBeenCalledWith(
      '/software/100/comments',
      { content: '好用', parentId: '5' },
      { headers: { 'Idempotency-Key': 'key-2' } }
    )
  })

  it('deleteSoftwareComment() 请求 DELETE /software/:id/comments/:commentId', async () => {
    vi.mocked(request.delete).mockResolvedValue(ok)

    await deleteSoftwareComment('100', '5')

    expect(request.delete).toHaveBeenCalledWith('/software/100/comments/5')
  })

  it('likeSoftwareComment()/unlikeSoftwareComment() 用 PUT/DELETE .../like', async () => {
    vi.mocked(request.put).mockResolvedValue(ok)
    vi.mocked(request.delete).mockResolvedValue(ok)

    await likeSoftwareComment('100', '5')
    await unlikeSoftwareComment('100', '5')

    expect(request.put).toHaveBeenCalledWith('/software/100/comments/5/like')
    expect(request.delete).toHaveBeenCalledWith('/software/100/comments/5/like')
  })

  it('favoriteSoftware()/unfavoriteSoftware() 用 PUT/DELETE /software/:id/favorite', async () => {
    vi.mocked(request.put).mockResolvedValue(ok)
    vi.mocked(request.delete).mockResolvedValue(ok)

    await favoriteSoftware('100')
    await unfavoriteSoftware('100')

    expect(request.put).toHaveBeenCalledWith('/software/100/favorite')
    expect(request.delete).toHaveBeenCalledWith('/software/100/favorite')
  })

  it('getPublisherStatus() 请求 /software/publisher-status', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getPublisherStatus()

    expect(request.get).toHaveBeenCalledWith('/software/publisher-status')
  })

  it('createSoftware() 创建草稿并携带幂等键', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await createSoftware({ name: '测试软件' }, 'key-3')

    expect(request.post).toHaveBeenCalledWith(
      '/software',
      { name: '测试软件' },
      { headers: { 'Idempotency-Key': 'key-3' } }
    )
  })

  it('getSoftwareEditor() 请求工作副本 /software/:id/editor', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await getSoftwareEditor('100')

    expect(request.get).toHaveBeenCalledWith('/software/100/editor')
  })

  it('saveSoftwareDraft() 带 revisionId 保存工作副本', async () => {
    vi.mocked(request.put).mockResolvedValue(ok)

    await saveSoftwareDraft('100', '7', { name: '改后', expectedVersion: 3 })

    expect(request.put).toHaveBeenCalledWith('/software/100/revisions/7', {
      name: '改后',
      expectedVersion: 3,
    })
  })

  it('submitSoftwareForReview() 提交审核并携带幂等键', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await submitSoftwareForReview('100', '7', { expectedVersion: 3 }, 'key-4')

    expect(request.post).toHaveBeenCalledWith(
      '/software/100/revisions/7/submit',
      { expectedVersion: 3 },
      { headers: { 'Idempotency-Key': 'key-4' } }
    )
  })

  it('listMySoftware() 请求 /software/mine 并透传 tab', async () => {
    vi.mocked(request.get).mockResolvedValue(ok)

    await listMySoftware({ tab: 'rejected', page: 1, size: 20 })

    expect(request.get).toHaveBeenCalledWith('/software/mine', {
      params: { tab: 'rejected', page: 1, size: 20 },
    })
  })

  it('createWorkingRevision() 创建新工作副本并携带幂等键', async () => {
    vi.mocked(request.post).mockResolvedValue(ok)

    await createWorkingRevision('100', 'key-5')

    expect(request.post).toHaveBeenCalledWith(
      '/software/100/working-revision',
      null,
      { headers: { 'Idempotency-Key': 'key-5' } }
    )
  })
})
