import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  checkIn,
  getCheckInStatus,
  getUserLevel,
  getExpLogs,
  getLevelConfigs,
  getCheckInCalendar,
  getContinuousDays,
} from './growth'

describe('growth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('checkIn() calls POST /community/growth/check-in', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await checkIn()

    expect(request.post).toHaveBeenCalledWith('/community/growth/check-in')
  })

  it('getCheckInStatus() calls GET /community/growth/check-in/status', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getCheckInStatus()

    expect(request.get).toHaveBeenCalledWith('/community/growth/check-in/status')
  })

  it('getUserLevel() calls GET /community/growth/level', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getUserLevel()

    expect(request.get).toHaveBeenCalledWith('/community/growth/level')
  })

  it('getExpLogs() calls GET with default pagination', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getExpLogs()

    expect(request.get).toHaveBeenCalledWith('/community/growth/exp-logs', { params: { page: 1, size: 20 } })
  })

  it('getExpLogs() calls GET with custom pagination', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getExpLogs(3, 50)

    expect(request.get).toHaveBeenCalledWith('/community/growth/exp-logs', { params: { page: 3, size: 50 } })
  })

  it('getLevelConfigs() calls GET /community/growth/level-configs', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getLevelConfigs()

    expect(request.get).toHaveBeenCalledWith('/community/growth/level-configs')
  })

  it('getCheckInCalendar() calls GET with year and month', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getCheckInCalendar(2026, 5)

    expect(request.get).toHaveBeenCalledWith('/community/growth/check-in/calendar', {
      params: { year: 2026, month: 5 },
    })
  })

  it('getContinuousDays() calls GET /community/growth/check-in/continuous', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getContinuousDays()

    expect(request.get).toHaveBeenCalledWith('/community/growth/check-in/continuous')
  })
})
