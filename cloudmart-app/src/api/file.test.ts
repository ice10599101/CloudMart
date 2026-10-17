import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('react-native', () => ({ Platform: { OS: 'web' } }))
vi.mock('@/utils/request', () => ({ default: vi.fn() }))
vi.mock('@/utils/mediaUrl', () => ({ resolveMediaUrl: (url: string) => url }))
import request from '@/utils/request'
import { createUploadForm, fileApi } from './file'

describe('multipart uploads', () => {
  beforeEach(() => {
    vi.mocked(request).mockResolvedValue({ data: { success: true, data: { fileId: '42', url: '/files/a.png' } } } as any)
  })

  it('converts legacy base64 payloads to actual file parts', async () => {
    await fileApi.upload({ file: 'aGVsbG8=', type: 'image/png' })
    const config = vi.mocked(request).mock.calls.at(-1)![0]
    const file = (config.data as FormData).get('file') as File
    expect(file.name).toBe('upload.png')
    expect(await file.text()).toBe('hello')
    expect(config.header).toBeUndefined()
    expect(config.timeout).toBe(60000)
  })

  it('reads a selected web URI as a Blob and preserves private visibility', async () => {
    const form = await createUploadForm({ uri: 'data:image/png;base64,aGVsbG8=', name: 'icon.png', type: 'image/png' })
    expect((form.get('file') as File).type).toBe('image/png')
    await fileApi.upload(form, 'PRIVATE')
    expect(vi.mocked(request).mock.calls.at(-1)![0].url).toBe('/file/assets?visibility=PRIVATE')
  })
})
