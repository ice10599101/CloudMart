import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { SeckillActivity, SeckillProduct, SeckillResult } from '@/types'

export function listActivities(status?: string) {
  return request.get<ApiResponse<SeckillActivity[]>>('/seckill/activities', { params: { status } })
}

export function getActivity(activityId: number) {
  return request.get<ApiResponse<SeckillActivity>>(`/seckill/activities/${activityId}`)
}

export function listProductsByActivity(activityId: number) {
  return request.get<ApiResponse<SeckillProduct[]>>(`/seckill/products/activity/${activityId}`)
}

export function executeSeckill(activityId: number, seckillProductId: number) {
  return request.post<ApiResponse<SeckillResult>>('/seckill/execute', { activityId, seckillProductId })
}

export function getSeckillResult(activityId: number, seckillProductId: number) {
  return request.get<ApiResponse<SeckillResult>>('/seckill/result', { params: { activityId, seckillProductId } })
}
