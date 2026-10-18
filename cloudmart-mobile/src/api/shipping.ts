import request from '@/utils/request'

/**
 * T05：订单物流（WMS 权威事实）。
 * ID 为 Long，服务端可能输出字符串，前端不做 Number 转换。
 */
export interface OrderShipping {
  id: number | string
  orderId: number | string
  shippingNo?: string | null
  trackingNo?: string | null
  carrier?: string | null
  /** PENDING（待出库）/ SHIPPED（已出库）/ DELIVERED（已签收） */
  status: string
  shippedAt?: string | null
  createdAt?: string | null
}

export interface OrderTracking {
  id: number | string
  shippingOrderId: number | string
  location?: string | null
  description?: string | null
  happenedAt?: string | null
}

export const shippingApi = {
  /** 无包裹时后端返回 404，调用方按“待出库”处理 */
  getByOrder: (orderId: number | string) =>
    request<OrderShipping>({ url: `/wms/shipping/order/${orderId}` }),
  /** 无包裹返回空数组 */
  getTrackings: (orderId: number | string) =>
    request<OrderTracking[]>({ url: `/wms/shipping/order/${orderId}/trackings` }),
}
