import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getTransferLimits,
  getMyTransfers,
  createTransfer,
} from './starlightTransfer'
import { getMyInvite, bindInvite } from './user'
import { getMyResourceLogs } from './wish'

describe('R19 星光转赠 / 邀请有礼 API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getTransferLimits() calls GET /wish/starlight/transfers/limits', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getTransferLimits()

    expect(request.get).toHaveBeenCalledWith('/wish/starlight/transfers/limits')
  })

  it('getMyTransfers() calls GET my with page/pageSize params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getMyTransfers({ page: 2, pageSize: 10 })

    expect(request.get).toHaveBeenCalledWith('/wish/starlight/transfers/my', {
      params: { page: 2, pageSize: 10 },
    })
  })

  it('createTransfer() posts payload including idempotent requestKey', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await createTransfer({
      toUserId: 88,
      amount: 50,
      message: '祝开心',
      requestKey: 'abc123def456',
    })

    expect(request.post).toHaveBeenCalledWith('/wish/starlight/transfers', {
      toUserId: 88,
      amount: 50,
      message: '祝开心',
      requestKey: 'abc123def456',
    })
  })

  it('createTransfer() posts without message when omitted but always carries requestKey', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await createTransfer({ toUserId: 9, amount: 10, requestKey: 'key-xyz' })

    expect(request.post).toHaveBeenCalledWith('/wish/starlight/transfers', {
      toUserId: 9,
      amount: 10,
      requestKey: 'key-xyz',
    })
  })

  it('getMyInvite() calls GET /user/invites/my', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getMyInvite()

    expect(request.get).toHaveBeenCalledWith('/user/invites/my')
  })

  it('bindInvite() posts invite code payload', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await bindInvite('FRIEND01')

    expect(request.post).toHaveBeenCalledWith('/user/invites/bind', { code: 'FRIEND01' })
  })

  it('getMyResourceLogs() calls GET logs with source/cursor/pageSize params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getMyResourceLogs({ source: 'INVITE_REWARD', cursor: '10086', pageSize: 20 })

    expect(request.get).toHaveBeenCalledWith('/wish/my/resources/logs', {
      params: { source: 'INVITE_REWARD', cursor: '10086', pageSize: 20 },
    })
  })
})
