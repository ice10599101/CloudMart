import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getJobs,
  getJob,
  createJob,
  updateJob,
  deleteJob,
  changeJobStatus,
  runJob,
  getJobLogs,
  deleteJobLog,
  cleanJobLogs,
  getServerInfo,
  getCacheInfo,
  getOnlineUsers,
  forceLogout,
} from './monitor'

describe('admin monitor API - Job Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getJobs() calls GET /job/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getJobs({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/job/list', { params: { page: 1 } })
  })

  it('getJob() calls GET /job/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getJob(1)
    expect(request.get).toHaveBeenCalledWith('/job/1')
  })

  it('createJob() calls POST /job', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)
    await createJob({ name: 'CleanupJob' })
    expect(request.post).toHaveBeenCalledWith('/job', { name: 'CleanupJob' })
  })

  it('updateJob() calls PUT /job/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await updateJob(1, { name: 'UpdatedJob' })
    expect(request.put).toHaveBeenCalledWith('/job/1', { name: 'UpdatedJob' })
  })

  it('deleteJob() calls DELETE /job/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteJob(1)
    expect(request.delete).toHaveBeenCalledWith('/job/1')
  })

  it('changeJobStatus() calls PUT /job/:id/status', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await changeJobStatus(1, { status: 1 })
    expect(request.put).toHaveBeenCalledWith('/job/1/status', { status: 1 })
  })

  it('runJob() calls PUT /job/:id/run', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)
    await runJob(1)
    expect(request.put).toHaveBeenCalledWith('/job/1/run')
  })
})

describe('admin monitor API - Job Log Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getJobLogs() calls GET /job/log/page', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getJobLogs({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/job/log/page', { params: { page: 1 } })
  })

  it('deleteJobLog() calls DELETE /job/log/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await deleteJobLog(1)
    expect(request.delete).toHaveBeenCalledWith('/job/log/1')
  })

  it('cleanJobLogs() calls DELETE /job/log/clean', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await cleanJobLogs()
    expect(request.delete).toHaveBeenCalledWith('/job/log/clean')
  })
})

describe('admin monitor API - Server & Cache Monitor', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getServerInfo() calls GET /admin/monitor/server', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getServerInfo()
    expect(request.get).toHaveBeenCalledWith('/admin/monitor/server')
  })

  it('getCacheInfo() calls GET /admin/monitor/cache', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getCacheInfo()
    expect(request.get).toHaveBeenCalledWith('/admin/monitor/cache')
  })
})

describe('admin monitor API - Online User Management', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getOnlineUsers() calls GET /admin/online/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)
    await getOnlineUsers({ page: 1 })
    expect(request.get).toHaveBeenCalledWith('/admin/online/list', { params: { page: 1 } })
  })

  it('forceLogout() calls DELETE /admin/online/:tokenId', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)
    await forceLogout('abc-token-123')
    expect(request.delete).toHaveBeenCalledWith('/admin/online/abc-token-123')
  })
})
