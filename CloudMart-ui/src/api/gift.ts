import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== 全站虚拟礼物（心愿 / 帖子 / 直播间） ==========

export type GiftTargetType = 'WISH' | 'POST' | 'LIVE_ROOM'

export interface GiftItem {
  id: number
  name: string
  iconUrl: string | null
  animationUrl: string | null
  priceStarlight: number
  status: 'ON_SHELF' | 'OFF_SHELF'
  sort: number
  description: string | null
}

export interface SendGiftResult {
  recordId: number
  giftId: number
  giftName: string
  giftIconUrl: string | null
  count: number
  totalPrice: number
  balanceAfter: number
  receiverId: number
  targetType: GiftTargetType
  targetId: number
}

export interface GiftRecordItem {
  id: number
  giftId: number
  giftName: string
  giftIconUrl: string | null
  count: number
  totalPrice: number
  senderId: number
  senderNickname: string | null
  receiverId: number
  receiverNickname: string | null
  targetType: GiftTargetType
  targetId: number
  message: string | null
  createdAt: string
}

/** 礼物目录（上架礼物，sort 升序） */
export function listGifts() {
  return request.get<ApiResponse<GiftItem[]>>('/wish/gifts')
}

/** 送礼物（余额不足 402 / 下架 409 / 上限 429；重复提交携带 X-Idempotency-Key） */
export function sendGift(data: {
  giftId: number
  count: number
  targetType: GiftTargetType
  targetId: number | string
  message?: string
}) {
  return request.post<ApiResponse<SendGiftResult>>('/wish/gifts/send', data)
}

/** 我送出的礼物（cursor 分页） */
export function listSentGiftRecords(cursor?: number, pageSize?: number) {
  return request.get<ApiResponse<GiftRecordItem[]>>('/wish/gifts/records/sent', {
    params: { cursor, pageSize },
  })
}

/** 我收到的礼物（cursor 分页） */
export function listReceivedGiftRecords(cursor?: number, pageSize?: number) {
  return request.get<ApiResponse<GiftRecordItem[]>>('/wish/gifts/records/received', {
    params: { cursor, pageSize },
  })
}

/** 场景礼物墙：某心愿/帖子/直播间的最新送礼记录 */
export function listTargetGiftRecords(
  targetType: GiftTargetType,
  targetId: number | string,
  cursor?: number,
  pageSize?: number,
) {
  return request.get<ApiResponse<GiftRecordItem[]>>(
    `/wish/gifts/targets/${targetType}/${targetId}`,
    { params: { cursor, pageSize } },
  )
}

export interface MyGiftSummary {
  /** 累计送出件数 */
  sentCount: number
  /** 累计送出消耗星光 */
  sentStarlight: number
  /** 累计收到件数 */
  receivedCount: number
  /** 累计收到星光价值 */
  receivedStarlight: number
}

/** 我的礼物资产总览（送/收两方向累计；星光余额经 getMyResources 查询） */
export function getMyGiftSummary() {
  return request.get<ApiResponse<MyGiftSummary>>('/wish/gifts/my/summary')
}
