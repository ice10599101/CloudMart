import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface AfterSaleTimelineEntry {
  action: string
  operator: string | null
  detail: string | null
  createdAt: string
}

export interface AfterSaleCase {
  id: number
  caseNo: string
  orderId: number
  orderNo: string | null
  userId: number
  itemId: number | null
  /** REFUND_ONLY-仅退款 / RETURN_REFUND-退货退款 */
  type: string
  reason: string
  attachmentFileIds: string | null
  quantity: number
  /** PENDING/APPROVED/REJECTED/REFUNDED/CLOSED */
  status: string
  refundNo: string | null
  refundAmount: string | null
  rejectReason: string | null
  handledAt: string | null
  createdAt: string
  timeline: AfterSaleTimelineEntry[]
}

/** T11：申请售后（PAID/SHIPPED；未发货仅退款，已发货按项退货退款） */
export function applyAfterSale(
  orderId: number,
  data: { type: string; reason: string; itemId?: number; attachmentFileIds?: string; quantity?: number },
) {
  return request.post<ApiResponse<AfterSaleCase>>(`/order/orders/${orderId}/after-sale`, data)
}

/** 撤销 PENDING 申请（本人） */
export function cancelAfterSale(caseId: number) {
  return request.post<ApiResponse<void>>(`/order/orders/after-sale/${caseId}/cancel`)
}

/** 查询本人案件详情（含时间线） */
export function getAfterSaleDetail(caseId: number) {
  return request.get<ApiResponse<AfterSaleCase>>(`/order/orders/after-sale/${caseId}`)
}

/** T11 后台：售后案件分页（business:order:refund） */
export function pageAfterSaleCases(params: {
  page?: number
  pageSize?: number
  status?: string
  orderId?: number
}) {
  return request.get<ApiResponse<AfterSaleCase[]>>('/admin/business/orders/after-sale', { params })
}

/** T11 后台：受理（批准金额 + 关联 T02 退款单） */
export function approveAfterSaleCase(caseId: number, refundAmount: number, refundNo: string) {
  return request.post<ApiResponse<AfterSaleCase>>(`/admin/business/orders/after-sale/${caseId}/approve`, {
    refundAmount,
    refundNo,
  })
}

/** T11 后台：拒绝（留原因） */
export function rejectAfterSaleCase(caseId: number, rejectReason: string) {
  return request.post<ApiResponse<AfterSaleCase>>(`/admin/business/orders/after-sale/${caseId}/reject`, {
    rejectReason,
  })
}

/** T11 后台：质检结果录入（PASSED/REJECTED，需已登记退货运单） */
export function inspectAfterSaleCase(caseId: number, result: 'PASSED' | 'REJECTED', note?: string) {
  return request.post<ApiResponse<void>>(`/admin/business/orders/after-sale/${caseId}/inspection`, {
    result,
    note,
  })
}
