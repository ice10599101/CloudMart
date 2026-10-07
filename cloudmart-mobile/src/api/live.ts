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

  // ==================== P1-12：WS 票据 + 直播间秒杀 ====================
  /** 签发 WS 握手票据（30s 一次性、绑定房间；wsPath 为网关 WS 路由 /ws/live/danmaku） */
  issueWsTicket: (data: { roomId: number; nickname?: string }) =>
    request<LiveWsTicket>({ url: '/live/ws-tickets', method: 'POST', data: data as unknown as Record<string, unknown> }),
  /** 直播间关联的秒杀活动（无活动时后端抛 NO_SECKILL_ACTIVITY） */
  getRoomSeckill: (roomId: number) =>
    request<Record<string, unknown>>({ url: `/live/seckill/rooms/${roomId}/activity` }),
  /** 参与直播间秒杀（仅 LIVE 状态；库存/限购由 mall-seckill 权威） */
  executeRoomSeckill: (roomId: number) =>
    request<Record<string, unknown>>({ url: `/live/seckill/rooms/${roomId}/execute`, method: 'POST' }),
}

/** P1-12：WS 握手票据（契约对齐 LiveWsTicketController.issue 响应） */
export interface LiveWsTicket {
  ticket: string
  expiresAt: string
  /** 网关 WS 路由路径（/ws/live/danmaku），与 resolveWsBase 拼接使用 */
  wsPath: string
}
