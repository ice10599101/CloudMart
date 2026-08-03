import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface GroupActivity {
  id: number
  name: string
  description: string
  productId: number
  skuId: number
  originalPrice: number
  groupPrice: number
  targetNumber: number
  maxGroups: number
  currentGroups: number
  status: string
  startTime: string
  endTime: string
}

export interface GroupOrder {
  id: number
  activityId: number
  leaderUserId: number
  currentNumber: number
  targetNumber: number
  status: string
  expireTime: string
}

export interface GroupActivityPage {
  records: GroupActivity[]
  total: number
  current: number
  size: number
}

export interface GroupOrderPage {
  records: GroupOrder[]
  total: number
  current: number
  size: number
}

export function listGroupActivities(page = 1, size = 10) {
  return request.get<ApiResponse<GroupActivityPage>>('/marketing/group/activities', { params: { page, size } })
}

export function getGroupActivity(id: number) {
  return request.get<ApiResponse<GroupActivity>>(`/marketing/group/activities/${id}`)
}

export function joinGroup(data: { activityId: number; groupOrderId?: number }) {
  return request.post<ApiResponse<GroupOrder>>('/marketing/group/join', data)
}

export function openGroup(activityId: number) {
  return request.post<ApiResponse<GroupOrder>>('/marketing/group/join', { activityId })
}

export function getGroupOrders(activityId?: number, page = 1, size = 10) {
  return request.get<ApiResponse<GroupOrderPage>>('/marketing/group/orders', { params: { activityId, page, size } })
}

export function getGroupOrder(groupOrderId: number) {
  return request.get<ApiResponse<GroupOrder>>(`/marketing/group/orders/${groupOrderId}`)
}

export function calculateDiscount(data: { productId: number; quantity: number; totalAmount: number }) {
  return request.post<ApiResponse<unknown>>('/marketing/tiered/calculate', data)
}
