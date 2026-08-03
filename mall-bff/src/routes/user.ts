import { Router, Request, Response } from 'express'
import { gatewayClient } from '../services/gatewayClient.js'

interface UserProfile {
  id: number
  username: string
  email: string
  phone: string
  nickname: string
  avatar: string
  status: number
  createdAt: string
}

interface Order {
  id: number
  orderNo: string
  status: string
  totalAmount: number
  payAmount: number
  createdAt: string
}

interface UserCoupon {
  id: number
  templateName: string
  templateType: string
  thresholdAmount: number
  discountAmount: number | null
  discountRate: number | null
  status: string
  expiredAt: string | null
}

interface UnreadCount {
  count: number
}

interface ProfileResponse {
  profile: UserProfile
  recentOrders: Order[]
  ordersCount: number
}

interface DashboardResponse {
  ordersCount: number
  unusedCouponsCount: number
  unreadNotificationsCount: number
  recentOrders: Order[]
}

const router = Router()

router.get('/profile', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    res.json({ success: false, error: { code: 'UNAUTHORIZED', message: 'Authentication required' } })
    return
  }

  const [profileResult, ordersResult] = await Promise.allSettled([
    gatewayClient.get<UserProfile>('/user/users/me', token),
    gatewayClient.get<Order[]>('/order/orders', token, { page: '1', size: '5' }),
  ])

  if (profileResult.status === 'rejected' || (profileResult.status === 'fulfilled' && !profileResult.value.success)) {
    res.json({ success: false, error: { code: 'UNAUTHORIZED', message: 'Failed to fetch profile' } })
    return
  }

  let recentOrders: Order[] = []
  let ordersCount = 0
  if (ordersResult.status === 'fulfilled' && ordersResult.value.success) {
    recentOrders = ordersResult.value.data
    ordersCount = ordersResult.value.meta?.total ?? recentOrders.length
  }

  const response: ProfileResponse = {
    profile: profileResult.value.data,
    recentOrders,
    ordersCount,
  }

  res.json({ success: true, data: response })
})

router.get('/dashboard', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    res.json({ success: false, error: { code: 'UNAUTHORIZED', message: 'Authentication required' } })
    return
  }

  const [ordersResult, couponsResult, notificationsResult] = await Promise.allSettled([
    gatewayClient.get<Order[]>('/order/orders', token, { page: '1', size: '5' }),
    gatewayClient.get<UserCoupon[]>('/coupon/user-coupons', token, { status: 'UNUSED', page: '1', pageSize: '1' }),
    gatewayClient.get<UnreadCount>('/notification/notifications/unread-count', token),
  ])

  let ordersCount = 0
  let recentOrders: Order[] = []
  if (ordersResult.status === 'fulfilled' && ordersResult.value.success) {
    recentOrders = ordersResult.value.data
    ordersCount = ordersResult.value.meta?.total ?? 0
  }

  let unusedCouponsCount = 0
  if (couponsResult.status === 'fulfilled' && couponsResult.value.success) {
    unusedCouponsCount = couponsResult.value.meta?.total ?? 0
  }

  let unreadNotificationsCount = 0
  if (notificationsResult.status === 'fulfilled' && notificationsResult.value.success) {
    unreadNotificationsCount = notificationsResult.value.data.count
  }

  const response: DashboardResponse = {
    ordersCount,
    unusedCouponsCount,
    unreadNotificationsCount,
    recentOrders,
  }

  res.json({ success: true, data: response })
})

export default router
