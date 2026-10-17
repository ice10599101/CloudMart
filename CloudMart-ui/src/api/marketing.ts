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

export interface GroupMember {
  id: number
  groupOrderId: number
  userId: number
  isLeader: boolean
  /** WEB-02：成员关联订单 ID（成团建单成功后由此跳订单/支付） */
  orderId: number | null
  status: string
  joinedAt: string
  /** WEB-02：成团建单任务状态 SUCCESS/FAILED/PENDING/ABSENT_ADDRESS——
   *  FAILED/ABSENT_ADDRESS 为补偿状态，页面如实展示，不伪造全部建单成功 */
  orderTaskStatus: string | null
  orderTaskError: string | null
}

export interface GroupOrder {
  id: number
  activityId: number
  leaderUserId: number
  currentNumber: number
  targetNumber: number
  /** PENDING 拼团中 / SUCCESS 成团 / FAILED·EXPIRED 已失败（仅释放预留权益） */
  status: string
  expireTime: string
  successTime: string | null
  members: GroupMember[]
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

export function joinGroup(data: { activityId: number; groupOrderId?: number; addressId?: number }) {
  // T11：addressId 为参团地址快照权威（成团建单使用参团时地址）
  return request.post<ApiResponse<GroupOrder>>('/marketing/group/join', data)
}

export function openGroup(activityId: number) {
  return request.post<ApiResponse<GroupOrder>>('/marketing/group/join', { activityId })
}

export function getGroupOrders(activityId?: number, page = 1, size = 10) {
  return request.get<ApiResponse<GroupOrderPage>>('/marketing/group/orders', { params: { activityId, page, size } })
}

/** T10：我的拼团（服务端按当前身份过滤，含本人建单结果） */
export function getMyGroups(page = 1, size = 10, status?: string) {
  return request.get<ApiResponse<GroupOrderPage>>('/marketing/group/orders/my', {
    params: { page, size, status },
  })
}

export function getGroupOrder(groupOrderId: number) {
  return request.get<ApiResponse<GroupOrder>>(`/marketing/group/orders/${groupOrderId}`)
}

export function calculateDiscount(data: { productId: number; quantity: number; totalAmount: number }) {
  return request.post<ApiResponse<unknown>>('/marketing/tiered/calculate', data)
}

/** 满减活动详情（含阶梯规则；用户面无列表端点，id 来自结算/计算响应） */
export function getTieredPromotion(id: number | string) {
  return request.get<ApiResponse<Record<string, unknown>>>(`/marketing/tiered/promotions/${id}`)
}
