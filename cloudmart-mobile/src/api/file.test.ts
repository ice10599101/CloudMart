import { describe, expect, it, vi } from 'vitest'
vi.mock('@tarojs/taro', () => ({ default: { getStorageSync: () => 'token', uploadFile: vi.fn() } }))
vi.mock('@/utils/request', () => ({ API_BASE: 'https://example.test/api' }))
import Taro from '@tarojs/taro'
import { fileApi, resolveFileUrl } from './file'

describe('file uploads', () => {
  it('uses the configured host and propagates server failures', async () => {
    vi.mocked(Taro.uploadFile).mockImplementation((options: any) => {
      expect(options.url).toBe('https://example.test/api/file/assets')
      expect(options.formData.visibility).toBe('PRIVATE')
      options.success({ statusCode: 413, data: JSON.stringify({ success: false, error: { code: 'FILE_TOO_LARGE', message: '文件过大' } }) })
      return {} as any
    })
    await expect(fileApi.uploadAsset('local.png', 'PRIVATE')).rejects.toMatchObject({ code: 'FILE_TOO_LARGE', message: '文件过大' })
  })
  it('resolves public images on the same configured host', () => {
    expect(resolveFileUrl('/files/by-id/42')).toBe('https://example.test/files/by-id/42')
  })
})
