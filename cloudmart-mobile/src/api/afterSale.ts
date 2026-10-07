import Taro from '@tarojs/taro'
import request from '@/utils/request'
import { API_BASE } from '@/utils/request'

/** P0-2 售后案件（契约对齐 mall-order AfterSaleCaseVO） */
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
  /** 附件文件ID（S01 资产，JSON 数组字符串） */
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

/** 分页结果（契约对齐后端 Page：list + total） */
export interface AfterSalePage {
  list: AfterSaleCase[]
  total: number
  page: number
  pageSize: number
}

export const AFTER_SALE_STATUS_TEXT: Record<string, string> = {
  PENDING: '待处理',
  APPROVED: '已同意',
  REJECTED: '已拒绝',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
}

export const AFTER_SALE_TYPE_TEXT: Record<string, string> = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
}

/** 申请售后（PAID/SHIPPED；未发货仅退款，已发货按项退货退款；同单同项 PENDING 唯一） */
export function applyAfterSale(
  orderId: number | string,
  data: { type: string; reason: string; itemId?: number; attachmentFileIds?: string; quantity?: number },
) {
  return request<AfterSaleCase>({ url: `/order/orders/${orderId}/after-sale`, method: 'POST', data: data as unknown as Record<string, unknown> })
}

/** 撤销 PENDING 申请（本人归属校验） */
export function cancelAfterSale(caseId: number | string) {
  return request<void>({ url: `/order/orders/after-sale/${caseId}/cancel`, method: 'POST' })
}

/** 登记退货运单（RETURN_REFUND 且 APPROVED；承运商+单号必填） */
export function registerReturnShipping(caseId: number | string, carrier: string, trackingNo: string) {
  return request<void>({ url: `/order/orders/after-sale/${caseId}/return-shipping`, method: 'POST', data: { carrier, trackingNo } })
}

/** 我的售后分页（服务端归属过滤） */
export function pageMyAfterSales(params: { page?: number; pageSize?: number; status?: string }) {
  return request<AfterSalePage>({ url: '/order/orders/after-sale/my', params: params as Record<string, unknown> })
}

/** 订单下全部售后案件（本人归属校验） */
export function listOrderAfterSales(orderId: number | string) {
  return request<AfterSaleCase[]>({ url: `/order/orders/${orderId}/after-sale/cases` })
}

/** 售后案件详情（含时间线） */
export function getAfterSaleDetail(caseId: number | string) {
  return request<AfterSaleCase>({ url: `/order/orders/after-sale/${caseId}` })
}

/**
 * 凭证图 fileId → 可访问 URL：走 S01 签名授权（download-url 返回 10 分钟短期路径）。
 * H5（/api 代理）直接用签名路径；小程序需拼出网关 host 前缀。
 */
export async function resolveAssetUrls(attachmentFileIds: string | null): Promise<string[]> {
  if (!attachmentFileIds) return []
  let ids: Array<string | number> = []
  try {
    ids = JSON.parse(attachmentFileIds) as Array<string | number>
  } catch {
    return []
  }
  const urls = await Promise.all(
    ids.map(async (id) => {
      try {
        const res = await request<{ downloadPath: string; expiresInSeconds: number }>({
          url: `/file/assets/${id}/download-url`,
        })
        const signedPath = res.data?.data?.downloadPath
        if (!signedPath) return ''
        const isWeapp = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
        if (!isWeapp) return signedPath
        const host = API_BASE.replace(/\/api\/?$/, '')
        return `${host}${signedPath}`
      } catch {
        return ''
      }
    }),
  )
  return urls.filter((u) => u !== '')
}
