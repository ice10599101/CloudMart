import request from '@/utils/request'
import type { LiveRoom, PaginatedResult } from '@/types'

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

export const liveApi = {
  getRooms: (params?: { page?: number; pageSize?: number; status?: number }) =>
    request<PaginatedResult<LiveRoom>>({ url: `/live/rooms${buildQuery(params as Record<string, unknown>)}` }),
  getRoom: (id: number) => request<LiveRoom>({ url: `/live/rooms/${id}` }),
  enterRoom: (id: number) => request<void>({ url: `/live/rooms/${id}/enter`, method: 'POST' }),
  leaveRoom: (id: number) => request<void>({ url: `/live/rooms/${id}/leave`, method: 'POST' }),
}
