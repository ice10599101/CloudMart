import { Router, Request, Response } from 'express'
import { gatewayClient } from '../services/gatewayClient.js'
import { ValidationError } from '../utils/errors.js'

interface OrderItem {
  id: number
  productId: number
  skuId: number
  productName: string
  skuImage: string
  skuAttributes: string
  price: number
  quantity: number
}

interface Order {
  id: number
  orderNo: string
  status: string
  totalAmount: number
  payAmount: number
  discountAmount: number
  couponId: number | null
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  shippedAt: string | null
  completedAt: string | null
  refundReason: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

interface Payment {
  id: number
  orderId: number
  paymentMethod: string
  amount: number
  status: string
  paidAt: string | null
}

interface CheckoutRequest {
  items: Array<{
    productId: number
    skuId: number
    quantity: number
    productName: string
    skuImage: string
    skuAttributes: string
    price: number
  }>
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  couponId?: number
}

interface CheckoutResponse {
  order: Order
  payment: Payment | null
}

interface OrderDetailResponse {
  order: Order
  payment: Payment | null
}

const router = Router()

router.post('/checkout', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    throw new ValidationError('Authentication required for checkout')
  }

  const body = req.body as CheckoutRequest
  if (!body.items || body.items.length === 0) {
    throw new ValidationError('Order items are required')
  }
  if (!body.receiverName || !body.receiverPhone || !body.receiverAddress) {
    throw new ValidationError('Shipping information is required')
  }

  const orderResult = await gatewayClient.post<Order>('/order/orders', body, token)

  if (!orderResult.success) {
    res.json(orderResult)
    return
  }

  const createdOrder = orderResult.data

  let payment: Payment | null = null
  try {
    const paymentResult = await gatewayClient.post<Payment>(
      `/order/orders/${createdOrder.id}/pay`,
      undefined,
      token,
    )
    if (paymentResult.success) {
      payment = paymentResult.data
    }
  } catch {
    // payment initiation failed, order still created
  }

  const response: CheckoutResponse = {
    order: createdOrder,
    payment,
  }

  res.status(201).json({ success: true, data: response })
})

router.get('/:id', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    throw new ValidationError('Authentication required')
  }

  const orderId = Number(req.params.id)
  if (!Number.isFinite(orderId) || orderId <= 0) {
    throw new ValidationError('Invalid order id')
  }

  const [orderResult, paymentResult] = await Promise.allSettled([
    gatewayClient.get<Order>(`/order/orders/${orderId}`, token),
    gatewayClient.get<Payment>(`/order/orders/${orderId}/payment`, token),
  ])

  if (orderResult.status === 'rejected' || (orderResult.status === 'fulfilled' && !orderResult.value.success)) {
    res.json({ success: false, error: { code: 'NOT_FOUND', message: 'Order not found' } })
    return
  }

  let payment: Payment | null = null
  if (paymentResult.status === 'fulfilled' && paymentResult.value.success) {
    payment = paymentResult.value.data
  }

  const response: OrderDetailResponse = {
    order: orderResult.value.data,
    payment,
  }

  res.json({ success: true, data: response })
})

export default router
