import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { pageAdminFileAssets, getAdminFileAsset, deleteAdminFileAsset } from './file'

/** ADMIN-04：管理员域资产接口——只读管理员域，fileId 字符串透传（BASE-01）。 */
describe('admin file assets API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('pageAdminFileAssets() GET /admin/file/assets with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await pageAdminFileAssets({ page: 2, size: 10, purpose: 'GIFT_IMAGE' })

    expect(request.get).toHaveBeenCalledWith('/admin/file/assets', {
      params: { page: 2, size: 10, purpose: 'GIFT_IMAGE' },
    })
  })

  it('getAdminFileAsset() 大 ID 字符串透传（不落 Number）', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getAdminFileAsset('9007199254740993')

    expect(request.get).toHaveBeenCalledWith('/admin/file/assets/9007199254740993')
  })

  it('deleteAdminFileAsset() DELETE by fileId', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as never)

    await deleteAdminFileAsset('42')

    expect(request.delete).toHaveBeenCalledWith('/admin/file/assets/42')
  })
})
