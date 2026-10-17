import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface AfterSaleTimelineEntry {
  action: string
  operator: string | null
  detail: string | null
  createdAt: string
}

export interface AfterSaleCase {
  /** BASE-01：案件/订单 ID 字符串联合（雪花防精度损失） */
  id: number | string
  caseNo: string
  orderId: number | string
  orderNo: string | null
  userId: number
  itemId: number | null
  /** REFUND_ONLY-仅退款 / RETURN_REFUND-退货退款 */
  type: string
  reason: string
  attachmentFileIds: string | null
  quantity: number
  /** PENDING/APPROVED/REJECTED/REFUNDED/CLOSED（页面不自造状态，展示服务端口径） */
  status: string
  refundNo: string | null
  refundAmount: string | null
  rejectReason: string | null
  handledAt: string | null
  createdAt: string
  timeline: AfterSaleTimelineEntry[]
}

/** TRADE-01：售后可申请性（与 apply 校验同源，前端不自造规则） */
export interface AfterSaleEligibility {
  allowedTypes: string[]
  remainingQuantity: number
  remainingRefundAmount: string
  applyDeadlineAt: string | null
  rejectReason: string | null
}

/** WEB-01：我的售后分页（后端返回 MyBatis-Plus Page：records/total/current/size） */
export interface AfterSalePage {
  records: AfterSaleCase[]
  total: number
  current?: number
  size?: number
}

/** T11：申请售后（PAID/SHIPPED；未发货仅退款，已发货按项退货退款；COMPLETED 走窗口） */
export function applyAfterSale(
  orderId: number | string,
  data: {
    type: string
    reason: string
    itemId?: number | string
    attachmentFileIds?: string
    quantity?: number
  },
) {
  return request.post<ApiResponse<AfterSaleCase>>(`/order/orders/${orderId}/after-sale`, data)
}

/** TRADE-01：可申请性查询（allowedTypes/剩余数量/可退上限/窗口截止），页面据此渲染 */
export function getAfterSaleEligibility(orderId: number | string, itemId?: number | string) {
  return request.get<ApiResponse<AfterSaleEligibility>>(
    `/order/orders/${orderId}/after-sale/eligibility`,
    { params: itemId !== undefined ? { itemId } : undefined },
  )
}

/** WISH-01 验收附注：未知结果（网络超时）用原幂等键查询/重试——apply 由请求层意图键收敛 */

/** 撤销 PENDING 申请（本人） */
export function cancelAfterSale(caseId: number | string) {
  return request.post<ApiResponse<void>>(`/order/orders/after-sale/${caseId}/cancel`)
}

/** 查询本人案件详情（含时间线） */
export function getAfterSaleDetail(caseId: number | string) {
  return request.get<ApiResponse<AfterSaleCase>>(`/order/orders/after-sale/${caseId}`)
}

/** T05：我的售后分页（服务端归属过滤；返回 page 对象） */
export function pageMyAfterSales(params: { page?: number; pageSize?: number; status?: string }) {
  return request.get<ApiResponse<AfterSalePage>>('/order/orders/after-sale/my', { params })
}

/** WEB-01：登记退货运单（RETURN_REFUND 且 APPROVED；承运商+单号必填） */
export function submitReturnShipping(caseId: number | string, carrier: string, trackingNo: string) {
  return request.post<ApiResponse<void>>(`/order/orders/after-sale/${caseId}/return-shipping`, {
    carrier,
    trackingNo,
  })
}

/** T05：订单下全部售后案件（本人归属校验；订单详情展示各明细 case） */
export function listOrderAfterSales(orderId: number) {
  return request.get<ApiResponse<AfterSaleCase[]>>(`/order/orders/${orderId}/after-sale/cases`)
}

/** T11 后台：售后案件分页（business:order:refund） */
export function pageAfterSaleCases(params: {
  page?: number
  pageSize?: number
  status?: string
  /** BASE-01：订单 ID 保持字符串联合（雪花/大整数防精度损失） */
  orderId?: number | string
}) {
  return request.get<ApiResponse<AfterSaleCase[]>>('/admin/business/orders/after-sale', { params })
}

/** T04 后台：受理（批准金额；退款号服务端派生 RFC{caseId}，退款经 Outbox 异步提交） */
export function approveAfterSaleCase(caseId: number | string, refundAmount: number) {
  return request.post<ApiResponse<AfterSaleCase>>(`/admin/business/orders/after-sale/${caseId}/approve`, {
    refundAmount,
  })
}

/** T11 后台：拒绝（留原因） */
export function rejectAfterSaleCase(caseId: number | string, rejectReason: string) {
  return request.post<ApiResponse<AfterSaleCase>>(`/admin/business/orders/after-sale/${caseId}/reject`, {
    rejectReason,
  })
}

/** T11 后台：质检结果录入（PASSED/REJECTED，需已登记退货运单） */
export function inspectAfterSaleCase(caseId: number | string, result: 'PASSED' | 'REJECTED', note?: string) {
  return request.post<ApiResponse<void>>(`/admin/business/orders/after-sale/${caseId}/inspection`, {
    result,
    note,
  })
}
