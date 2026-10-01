import request from '@/utils/request'

/**
 * PAY-01/T01 支付尝试 API（mall-payment /payment/payment-attempts）。
 * 唯一支付链路：只提交 orderId + channel，归属/状态/金额全部服务端判定，
 * 客户端金额不参与。旧 orderApi.pay/getPayment 已随旧支付链路删除。
 */

/** MOCK 渠道回调载荷（仅测试环境由创建尝试返回，签名由服务端签发） */
export interface MockCallbackPayload {
  merchantPaymentNo: string
  amount: string
  providerTxnNo: string
  notificationId: string
  signature: string
}

/** 创建尝试返回（收银台轮询真值源） */
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

  /** 按订单查最近一次支付尝试状态（收银台轮询真值源） */
  getByOrder: (orderId: number | string) =>
    request<PaymentAttemptResult>({ url: `/payment/payment-attempts/order/${orderId}` }),

  /** MOCK 渠道回调（HMAC 验签 + 重放防护 + 金额核对；仅测试环境启用） */
  submitMockCallback: (data: MockCallbackPayload) =>
    request<{ result: string }>({
      url: '/payment/payment-attempts/mock-callbacks',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
}
