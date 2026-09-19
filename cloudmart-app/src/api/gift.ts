import request from '@/utils/request'
import type { GiftItem, GiftRecordItem, GiftTargetType, SendGiftResult } from '@/types'

// ========== 全站虚拟礼物（心愿 / 帖子 / 直播间；契约对齐 mall-wish GiftController） ==========

export const giftApi = {
  /** 礼物目录（上架礼物，sort 升序） */
  listGifts: () => request<GiftItem[]>({ url: '/wish/gifts' }),

  /** 送礼物（余额不足 402 / 下架 409 / 上限 429） */
  sendGift: (data: { giftId: number; count: number; targetType: GiftTargetType; targetId: number | string; message?: string }) =>
    request<SendGiftResult>({ url: '/wish/gifts/send', method: 'POST', data }),

  /** 场景礼物墙：某心愿/帖子/直播间的最新送礼记录（cursor 分页） */
  listTargetGiftRecords: (
    targetType: GiftTargetType,
    targetId: number | string,
    params?: { cursor?: string; pageSize?: number },
  ) => {
    const query = Object.entries(params ?? {})
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
      .join('&')
    return request<GiftRecordItem[]>({
      url: `/wish/gifts/targets/${targetType}/${targetId}${query ? `?${query}` : ''}`,
    })
  },
}
