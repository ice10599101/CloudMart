import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface LiveRoom {
  id: number
  title: string
  description: string
  anchorUserId: number
  anchorName: string
  coverImage: string
  streamUrl: string
  productId: number | null
  seckillActivityId: number | null
  maxViewers: number
  currentViewers: number
  totalViewers: number
  status: string
  startTime: string | null
  endTime: string | null
  createdAt: string
}

export interface LiveRoomPage {
  records: LiveRoom[]
  total: number
  current: number
  size: number
}

export function listLiveRooms(page = 1, size = 10, status?: string) {
  return request.get<ApiResponse<LiveRoomPage>>('/live/rooms', { params: { page, size, status } })
}

export function getLiveRoom(id: number) {
  return request.get<ApiResponse<LiveRoom>>(`/live/rooms/${id}`)
}

export function enterLiveRoom(id: number) {
  return request.post<ApiResponse<LiveRoom>>(`/live/rooms/${id}/enter`)
}

export function executeLiveSeckill(roomId: number) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/live/seckill/rooms/${roomId}/execute`)
}

export function getLiveSeckillActivity(roomId: number) {
  return request.get<ApiResponse<Record<string, unknown>>>(`/live/seckill/rooms/${roomId}/activity`)
}

export function getWebrtcSignals(roomId: number, role: string) {
  return request.get<ApiResponse<unknown[]>>(`/live/webrtc/signal/${roomId}/${role}`)
}

export function postWebrtcSignal(roomId: number, role: string, data: { sdp: string; type: string }) {
  return request.post<ApiResponse<void>>('/live/webrtc/signal', { roomId, role, ...data })
}

export function addIceCandidate(data: { roomId: number; role: string; candidate: string }) {
  return request.post<ApiResponse<void>>('/live/webrtc/ice', data)
}
