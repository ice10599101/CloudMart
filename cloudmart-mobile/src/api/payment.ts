import request from '@/utils/request'

/**
 * PAY-01 支付尝试 API（mall-payment /payment/payment-attempts）。
 *
 * 全端收银台统一链路：创建尝试（归属/状态/金额服务端判定）→ 渠道回调（MOCK 为
 * 测试环境代提交）→ 轮询尝试状态（attempts 台账独立于 order 侧支付视图，必须
 * 打 /payment-attempts/order/{id} 真值源）。旧 orderApi.pay 保留为兜底链路。
 */

/** MOCK 渠道回调载荷（仅测试环境由创建尝试返回，签名由服务端签发） */
export interface MockCallbackPayload {
  merchantPaymentNo: string
  amount: string
  providerTxnNo: string
  notificationId: string
  signature: string
}

/** 创建/查询尝试返回（归属/状态/金额全部服务端判定） */
export interface PaymentAttemptResult {
  merchantPaymentNo: string
  status: string
  amount: number | string
  expiresAt: string | null
  mockCallback?: MockCallbackPayload
}

export const paymentApi = {
  /** 创建支付尝试（单订单单活动尝试；只收 orderId + channel） */
  createAttempt: (data: { orderId: number | string; channel: string }) =>
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
