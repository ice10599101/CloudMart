import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/**
 * T05：订单物流（WMS 权威事实）。
 * ID 为雪花/自增 Long，服务端可能以字符串输出，前端不做 Number 转换。
 */
export interface OrderShipping {
  id: number | string
  orderId: number | string
  warehouseId?: number | string | null
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
  createdAt?: string | null
}

/** 查询订单物流；无包裹返回 404（页面显示“待出库”） */
export function fetchOrderShipping(orderId: number | string) {
  return request.get<ApiResponse<OrderShipping>>(`/wms/shipping/order/${orderId}`)
}

/** 查询订单物流轨迹（时间线，按发生时间升序） */
export function fetchOrderTrackings(orderId: number | string) {
  return request.get<ApiResponse<OrderTracking[]>>(`/wms/shipping/order/${orderId}/trackings`)
}
