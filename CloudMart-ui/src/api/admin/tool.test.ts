import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getGenTables,
  getGenTableDetail,
  previewGenCode,
  downloadGenCode,
} from './tool'

describe('admin tool API - Code Generator', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getGenTables() calls GET /gen/tables', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getGenTables({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/gen/tables', { params: { page: 1 } })
  })

  it('getGenTableDetail() calls GET /gen/tables/:tableName', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getGenTableDetail('sys_user')
    expect(request.get).toHaveBeenCalledWith('/gen/tables/sys_user')
  })

  it('previewGenCode() calls POST /gen/preview', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await previewGenCode({ tableName: 'sys_user' })
    expect(request.post).toHaveBeenCalledWith('/gen/preview', { tableName: 'sys_user' })
  })

  it('downloadGenCode() calls POST /gen/download with responseType blob', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await downloadGenCode({ tableName: 'sys_user' })
    expect(request.post).toHaveBeenCalledWith('/gen/download', { tableName: 'sys_user' }, { responseType: 'blob' })
  })
})
