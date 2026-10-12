import request from '@/utils/request'
import { generateAttachmentId } from '@/utils/postAttachments'

/**
 * R09/PAY-01 支付 API（mall-payment）。
 *
 * 渠道列表唯一来自 GET /payment/channels（按 H5/MINIAPP 场景）；收银台禁止本地
 * 写死渠道。创建尝试提交 orderId/channel/scene/requestKey——归属/状态/金额全部
 * 服务端判定；同键稳定返回同一笔，换渠道换新键。
 */

export type PaymentScene = 'H5' | 'MINIAPP' | 'APP'
export type PaymentAction = 'REDIRECT' | 'QR' | 'SDK' | 'NONE'

/** 可用支付渠道（凭据白名单+场景能力；空列表=暂不可支付；不含密钥） */
export interface PaymentChannel {
  channel: string
  name: string
  action: PaymentAction
  scene: PaymentScene
  /** 维护中：展示但置灰不可选 */
  maintenance: boolean
}

/** MOCK 渠道回调载荷（仅测试环境由创建尝试返回，签名由服务端签发） */
export interface MockCallbackPayload {
  merchantPaymentNo: string
  amount: string
  providerTxnNo: string
  notificationId: string
  signature: string
}

/** 创建/查询尝试返回（R09：attemptId/status/action + 渠道/场景） */
export interface PaymentAttemptResult {
  attemptId: number | string
  merchantPaymentNo: string
  channel: string
  scene: PaymentScene
  action: PaymentAction
  status: string
  amount: number | string
  expiresAt: string | null
  /** R09：本次创建是否关闭了另一渠道的活动尝试（换渠道明示） */
  replacedPrevious?: boolean
  previousAttemptId?: number | string | null
  mockCallback?: MockCallbackPayload
}

/** 生成创建尝试幂等键（每次用户主动发起新尝试生成） */
export function newPaymentRequestKey(): string {
  return generateAttachmentId().replace(/-/g, '')
}

export const paymentApi = {
  /** R09 查询当前场景可用渠道（收银台渠道唯一来源） */
  getChannels: (scene: PaymentScene) =>
    request<PaymentChannel[]>({ url: `/payment/channels?scene=${encodeURIComponent(scene)}` }),
  /** 创建支付尝试（单订单单活动尝试；金额服务端判定，客户端不传金额） */
  createAttempt: (data: {
    orderId: number | string
    channel: string
    scene: PaymentScene
    requestKey: string
    returnUrl?: string
  }) =>
    request<PaymentAttemptResult>({
      url: '/payment/payment-attempts',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** MOCK 渠道回调（HMAC 验签 + 重放防护 + 金额核对；仅测试环境启用） */
  submitMockCallback: (data: MockCallbackPayload) =>
    request<{ result: string }>({
      url: '/payment/payment-attempts/mock-callbacks',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 按订单查最近一次支付尝试状态（收银台轮询真值源；归属服务端校验） */
  getByOrder: (orderId: number | string) =>
    request<PaymentAttemptResult>({ url: `/payment/payment-attempts/order/${orderId}` }),
}
