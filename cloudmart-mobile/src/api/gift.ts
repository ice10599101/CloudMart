import request from '@/utils/request'
import type { GiftItem, GiftRecordItem, GiftTargetType, MyGiftSummary, SendGiftResult } from '@/types'

// ========== 全站虚拟礼物（心愿 / 帖子 / 直播间；契约对齐 mall-wish GiftController） ==========

/** query 串构造（cursor/pageSize 统一序列化；空值跳过） */
function buildGiftQuery(params?: Record<string, string | number | undefined>): string {
  const query = Object.entries(params ?? {})
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return query ? `?${query}` : ''
}

/** 礼物目录（上架礼物，sort 升序） */
export const giftApi = {
  listGifts: () => request<GiftItem[]>({ url: '/wish/gifts' }),

  /** 送礼物（余额不足 402 / 下架 409 / 上限 429） */
  sendGift: (data: { giftId: number; count: number; targetType: GiftTargetType; targetId: number | string; message?: string }) =>
    request<SendGiftResult>({ url: '/wish/gifts/send', method: 'POST', data: data as unknown as Record<string, unknown> }),

  /** 场景礼物墙：某心愿/帖子/直播间的最新送礼记录（cursor 分页） */
  listTargetGiftRecords: (
    targetType: GiftTargetType,
    targetId: number | string,
    params?: { cursor?: string; pageSize?: number },
  ) =>
    request<GiftRecordItem[]>({
      url: `/wish/gifts/targets/${targetType}/${targetId}${buildGiftQuery(params)}`,
    }),

  /** 我的礼物资产总览（送/收两方向累计；星光余额另经 wishApi.getMyResources 查询） */
  getMyGiftSummary: () => request<MyGiftSummary>({ url: '/wish/gifts/my/summary' }),

  /** 我送出的礼物（id 倒序 cursor 分页，游标为上一页末条 id） */
  listSentGiftRecords: (params?: { cursor?: number; pageSize?: number }) =>
    request<GiftRecordItem[]>({
      url: `/wish/gifts/records/sent${buildGiftQuery(params)}`,
    }),

  /** 我收到的礼物（id 倒序 cursor 分页） */
  listReceivedGiftRecords: (params?: { cursor?: number; pageSize?: number }) =>
    request<GiftRecordItem[]>({
      url: `/wish/gifts/records/received${buildGiftQuery(params)}`,
    }),
}
