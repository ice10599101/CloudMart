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
  /** 后端 LiveRoomVO 仅返回 viewerCount（当前观看数） */
  viewerCount: number
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

// ==================== WebRTC 信令（与 mall-live WebrtcController/WebrtcSignalRequest 对齐） ====================

export type WebrtcRole = 'HOST' | 'VIEWER'

/** 信令条目（Redis 存储为 "type|payload"，服务端还原后返回） */
export interface WebrtcSignal {
  type: 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | string
  payload: string
  role: string
}

/** 拉取指定角色已发布的信令（观看端拉 HOST 的 OFFER；发布端拉 VIEWER 的 ANSWER） */
export function getWebrtcSignals(roomId: number | string, role: WebrtcRole) {
  return request.get<ApiResponse<WebrtcSignal[]>>(`/live/webrtc/signal/${roomId}/${role}`)
}

/** 发布信令（主播发 OFFER / 观众回 ANSWER，payload 为 SDP 文本） */
export function postWebrtcSignal(roomId: number | string, role: WebrtcRole, type: 'OFFER' | 'ANSWER', payload: string) {
  return request.post<ApiResponse<void>>('/live/webrtc/signal', { roomId, role, type, payload })
}

/** 发布 ICE 候选者（type 固定 ICE_CANDIDATE，payload 为候选者 JSON 字符串） */
export function publishIceCandidate(data: { roomId: number | string; role: WebrtcRole; payload: string }) {
  return request.post<ApiResponse<void>>('/live/webrtc/ice', {
    roomId: data.roomId,
    role: data.role,
    type: 'ICE_CANDIDATE',
    payload: data.payload,
  })
}

/** 拉取指定角色的 ICE 候选者列表（发布端与观看端各自拉对端候选） */
export function getWebrtcIceCandidates(roomId: number | string, role: WebrtcRole) {
  return request.get<ApiResponse<string[]>>(`/live/webrtc/ice/${roomId}/${role}`)
}

/** 清除直播间信令缓存（直播结束/切换时由发布端调用） */
export function clearWebrtcSignals(roomId: number | string) {
  return request.delete<ApiResponse<void>>(`/live/webrtc/signal/${roomId}`)
}
