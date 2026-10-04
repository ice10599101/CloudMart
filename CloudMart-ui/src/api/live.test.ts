import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  listLiveRooms, getLiveRoom, enterLiveRoom,
  executeLiveSeckill, getLiveSeckillActivity,
  issueWebrtcTicket, getWebrtcSignals, postWebrtcSignal, publishIceCandidate,
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

  it('issueWebrtcTicket() posts roomId and returns ticket envelope', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await issueWebrtcTicket(1)

    expect(request.post).toHaveBeenCalledWith('/live/webrtc/tickets', { roomId: 1 })
  })

  it('getWebrtcSignals() calls GET /live/webrtc/signal/:roomId/:role with ticket param', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getWebrtcSignals(1, 'HOST', 'tk-1')

    expect(request.get).toHaveBeenCalledWith('/live/webrtc/signal/1/HOST', { params: { ticket: 'tk-1' } })
  })

  it('postWebrtcSignal() posts ticket envelope aligned with WebrtcSignalRequest (T08)', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await postWebrtcSignal(1, 'tk-1', 'ANSWER', 'sdp-data')

    expect(request.post).toHaveBeenCalledWith('/live/webrtc/signal', {
      roomId: 1, ticket: 'tk-1', type: 'ANSWER', payload: 'sdp-data',
    })
  })

  it('publishIceCandidate() posts ICE_CANDIDATE envelope with ticket', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await publishIceCandidate({ roomId: 1, ticket: 'tk-1', payload: 'ice-candidate' })

    expect(request.post).toHaveBeenCalledWith('/live/webrtc/ice', {
      roomId: 1, ticket: 'tk-1', type: 'ICE_CANDIDATE', payload: 'ice-candidate',
    })
  })
})
