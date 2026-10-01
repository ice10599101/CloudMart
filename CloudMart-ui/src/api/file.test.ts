import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { uploadFile, uploadFileAsset, deleteFileAsset } from './file'

// S01/LC05：旧 /file/upload 与 /file/delete 通道已删除——唯一入口 /file/assets，
// 删除按 fileId（旧 URL 无任何执行入口）。
describe('file API (assets only)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uploadFile() calls POST /file/assets with PUBLIC visibility', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    const file = new File(['test content'], 'test.png', { type: 'image/png' })
    await uploadFile(file)

    expect(request.post).toHaveBeenCalledWith(
        '/file/assets',
        expect.any(FormData),
        expect.objectContaining({ timeout: 60000 })
    )

    const formData = vi.mocked(request.post).mock.calls[0][1] as FormData
    expect(formData.get('file')).toBeInstanceOf(File)
    expect(formData.get('visibility')).toBe('PUBLIC')
  })

  it('uploadFile() forwards progress events as percent', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    const onProgress = vi.fn()
    const file = new File(['test content'], 'test.png', { type: 'image/png' })
    await uploadFile(file, { onProgress })

    const config = vi.mocked(request.post).mock.calls[0][2] as {
      onUploadProgress?: (event: { loaded: number; total?: number }) => void
    }
    config.onUploadProgress?.({ loaded: 25, total: 100 })
    expect(onProgress).toHaveBeenCalledWith(25)
  })

  it('uploadFileAsset() forwards explicit visibility (PRIVATE 无公开 URL 语义由后端保证)', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    const file = new File(['secret'], 'evidence.pdf', { type: 'application/pdf' })
    await uploadFileAsset(file, 'PRIVATE')

    const formData = vi.mocked(request.post).mock.calls[0][1] as FormData
    expect(formData.get('visibility')).toBe('PRIVATE')
  })

  it('deleteFileAsset() calls DELETE /file/assets/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await deleteFileAsset('42')

    expect(request.delete).toHaveBeenCalledWith('/file/assets/42')
  })
})
