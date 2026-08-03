import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { uploadFile, deleteFile } from './file'

describe('file API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uploadFile() calls POST /file/upload with FormData', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    const file = new File(['test content'], 'test.png', { type: 'image/png' })
    await uploadFile(file)

    expect(request.post).toHaveBeenCalledWith('/file/upload', expect.any(FormData), {
      headers: { 'Content-Type': 'multipart/form-data' },
    })

    const callArgs = vi.mocked(request.post).mock.calls[0]
    const formData = callArgs[1] as FormData
    expect(formData.get('file')).toBeInstanceOf(File)
  })

  it('deleteFile() calls DELETE /file/delete with url param', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await deleteFile('https://cdn.example.com/test.png')

    expect(request.delete).toHaveBeenCalledWith('/file/delete', {
      params: { url: 'https://cdn.example.com/test.png' },
    })
  })
})
