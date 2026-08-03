import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  listLiveRooms, getLiveRoom, enterLiveRoom,
  executeLiveSeckill, getLiveSeckillActivity,
  getWebrtcSignals, postWebrtcSignal, addIceCandidate,
} from './live'

describe('live API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listLiveRooms() calls GET /live/rooms with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listLiveRooms(1, 10, 'LIVE')

    expect(request.get).toHaveBeenCalledWith('/live/rooms', { params: { page: 1, size: 10, status: 'LIVE' } })
  })

  it('listLiveRooms() calls GET /live/rooms with defaults', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listLiveRooms()

    expect(request.get).toHaveBeenCalledWith('/live/rooms', { params: { page: 1, size: 10, status: undefined } })
  })

  it('getLiveRoom() calls GET /live/rooms/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getLiveRoom(1)

    expect(request.get).toHaveBeenCalledWith('/live/rooms/1')
  })

  it('enterLiveRoom() calls POST /live/rooms/:id/enter', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await enterLiveRoom(1)

    expect(request.post).toHaveBeenCalledWith('/live/rooms/1/enter')
  })

  it('executeLiveSeckill() calls POST /live/seckill/rooms/:id/execute', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await executeLiveSeckill(1)

    expect(request.post).toHaveBeenCalledWith('/live/seckill/rooms/1/execute')
  })

  it('getLiveSeckillActivity() calls GET /live/seckill/rooms/:id/activity', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getLiveSeckillActivity(1)

    expect(request.get).toHaveBeenCalledWith('/live/seckill/rooms/1/activity')
  })

  it('getWebrtcSignals() calls GET /live/webrtc/signal/:roomId/:role', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getWebrtcSignals(1, 'anchor')

    expect(request.get).toHaveBeenCalledWith('/live/webrtc/signal/1/anchor')
  })

  it('postWebrtcSignal() calls POST /live/webrtc/signal', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await postWebrtcSignal(1, 'anchor', { sdp: 'sdp-data', type: 'offer' })

    expect(request.post).toHaveBeenCalledWith('/live/webrtc/signal', {
      roomId: 1, role: 'anchor', sdp: 'sdp-data', type: 'offer',
    })
  })

  it('addIceCandidate() calls POST /live/webrtc/ice', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await addIceCandidate({ roomId: 1, role: 'viewer', candidate: 'ice-candidate' })

    expect(request.post).toHaveBeenCalledWith('/live/webrtc/ice', {
      roomId: 1, role: 'viewer', candidate: 'ice-candidate',
    })
  })
})
