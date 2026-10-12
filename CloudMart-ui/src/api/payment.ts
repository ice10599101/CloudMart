import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// ==================== R09 渠道可用性（三端收银台渠道唯一来源） ====================

export type PaymentScene = 'H5' | 'MINIAPP' | 'APP'
export type PaymentAction = 'REDIRECT' | 'QR' | 'SDK' | 'NONE'

/** 可用支付渠道（后端按环境凭据白名单+场景能力返回；空列表=暂不可支付；不含任何密钥） */
export interface PaymentChannel {
  channel: string
  name: string
  action: PaymentAction
  scene: PaymentScene
  /** 维护中：展示但置灰不可选 */
  maintenance: boolean
}

/** 查询当前场景可用渠道（Web 固定 H5） */
export function getPaymentChannels(scene: PaymentScene = 'H5') {
  return request.get<ApiResponse<PaymentChannel[]>>('/payment/channels', { params: { scene } })
}

/** R09 创建尝试请求幂等键（每次用户主动发起新尝试生成；网络重发复用同一键） */
export function newPaymentRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `pay${Date.now()}${Math.random().toString(36).slice(2, 18)}`
}

// ==================== PAY-01 支付尝试（唯一支付链路，T01） ====================

/** MOCK 渠道回调载荷（仅测试环境由创建尝试返回，签名由服务端签发） */
export interface MockCallbackPayload {
  merchantPaymentNo: string
  amount: string
  providerTxnNo: string
  notificationId: string
  signature: string
}

/** 创建尝试返回（R09：attemptId/status/action + 渠道/场景；MOCK 渠道附带 mockCallback） */
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

/**
 * 创建支付尝试（R09：单订单单活动尝试；金额服务端判定，客户端不传金额）。
 * requestKey 必填——同键稳定返回同一笔尝试；换渠道必须换新键。
 */
export function createPaymentAttempt(data: {
  orderId: number | string
  channel: string
  scene: PaymentScene
  requestKey: string
  returnUrl?: string
}) {
  return request.post<ApiResponse<PaymentAttemptResult>>('/payment/payment-attempts', data)
}

/** 按订单查最近一次支付尝试状态（收银台轮询真值源；归属服务端校验） */
export function getPaymentAttemptByOrderId(orderId: number | string) {
  return request.get<ApiResponse<PaymentAttemptResult>>(`/payment/payment-attempts/order/${orderId}`)
}

/** MOCK 渠道回调（HMAC 验签 + 重放防护 + 金额核对；仅测试环境启用） */
export function submitMockPaymentCallback(data: MockCallbackPayload) {
  return request.post<ApiResponse<{ result: string }>>('/payment/payment-attempts/mock-callbacks', data)
}
