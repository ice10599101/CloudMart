import { Router, Request, Response } from 'express'
import { gatewayClient } from '../services/gatewayClient.js'
import { ValidationError } from '../utils/errors.js'

interface CartItem {
  id: number
  userId: number
  productId: number
  skuId: number
  quantity: number
  checked: number
  productName: string
  skuImage: string
  skuAttributes: string
  price: number
}

interface Cart {
  items: CartItem[]
  totalQuantity: number
  totalPrice: number
}

interface Product {
  id: number
  name: string
  mainImage: string
  skus: Array<{
    id: number
    price: number
    originalPrice: number
    stock: number
    attributes: string
    image: string
  }>
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

interface EnrichedCartItem extends CartItem {
  productImage: string
  originalPrice: number
  currentStock: number
  isInStock: boolean
}

interface EnrichedCart {
  items: EnrichedCartItem[]
  totalQuantity: number
  totalPrice: number
  totalOriginalPrice: number
}

interface CheckoutPreviewRequest {
  couponId?: number
}

interface CheckoutPreviewResponse {
  items: EnrichedCartItem[]
  subtotal: number
  discount: number
  couponDiscount: number
  totalAmount: number
  coupon: UserCoupon | null
  allInStock: boolean
}

const router = Router()

router.get('/', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    res.json({ success: false, error: { code: 'UNAUTHORIZED', message: 'Authentication required' } })
    return
  }

  const cartResult = await gatewayClient.get<Cart>('/cart/carts', token)

  if (!cartResult.success) {
    res.json(cartResult)
    return
  }

  const cart = cartResult.data
  if (cart.items.length === 0) {
    const emptyResponse: EnrichedCart = {
      items: [],
      totalQuantity: 0,
      totalPrice: 0,
      totalOriginalPrice: 0,
    }
    res.json({ success: true, data: emptyResponse })
    return
  }

  const productIds = [...new Set(cart.items.map((item) => item.productId))]
  const productResults = await Promise.allSettled(
    productIds.map((pid) => gatewayClient.get<Product>(`/product/products/${pid}`, token)),
  )

  const productMap = new Map<number, Product>()
  productResults.forEach((result, index) => {
    if (result.status === 'fulfilled' && result.value.success) {
      productMap.set(productIds[index], result.value.data)
    }
  })

  const enrichedItems: EnrichedCartItem[] = cart.items.map((item) => {
    const product = productMap.get(item.productId)
    const matchingSku = product?.skus.find((s) => s.id === item.skuId)
    return {
      ...item,
      productImage: product?.mainImage ?? item.skuImage,
      originalPrice: matchingSku?.originalPrice ?? item.price,
      currentStock: matchingSku?.stock ?? 0,
      isInStock: (matchingSku?.stock ?? 0) >= item.quantity,
    }
  })

  const totalOriginalPrice = enrichedItems.reduce(
    (sum, item) => sum + item.originalPrice * item.quantity,
    0,
  )

  const response: EnrichedCart = {
    items: enrichedItems,
    totalQuantity: cart.totalQuantity,
    totalPrice: cart.totalPrice,
    totalOriginalPrice,
  }

  res.json({ success: true, data: response })
})

router.post('/checkout-preview', async (req: Request, res: Response) => {
  const token = req.authToken
  if (!token) {
    res.json({ success: false, error: { code: 'UNAUTHORIZED', message: 'Authentication required' } })
    return
  }

  const { couponId } = req.body as CheckoutPreviewRequest

  const [cartResult, couponsResult] = await Promise.allSettled([
    gatewayClient.get<Cart>('/cart/carts', token),
    couponId
      ? gatewayClient.get<UserCoupon[]>('/coupon/user-coupons', token, { status: 'UNUSED', page: '1', pageSize: '100' })
      : Promise.resolve({ success: true, data: [] as UserCoupon[] }),
  ])

  if (cartResult.status === 'rejected' || (cartResult.status === 'fulfilled' && !cartResult.value.success)) {
    res.json({ success: false, error: { code: 'CART_ERROR', message: 'Failed to fetch cart' } })
    return
  }

  const cart = cartResult.value.data
  const checkedItems = cart.items.filter((item) => item.checked === 1)

  if (checkedItems.length === 0) {
    throw new ValidationError('No items selected for checkout')
  }

  const productIds = [...new Set(checkedItems.map((item) => item.productId))]
  const productResults = await Promise.allSettled(
    productIds.map((pid) => gatewayClient.get<Product>(`/product/products/${pid}`, token)),
  )

  const productMap = new Map<number, Product>()
  productResults.forEach((result, index) => {
    if (result.status === 'fulfilled' && result.value.success) {
      productMap.set(productIds[index], result.value.data)
    }
  })

  const enrichedItems: EnrichedCartItem[] = checkedItems.map((item) => {
    const product = productMap.get(item.productId)
    const matchingSku = product?.skus.find((s) => s.id === item.skuId)
    return {
      ...item,
      productImage: product?.mainImage ?? item.skuImage,
      originalPrice: matchingSku?.originalPrice ?? item.price,
      currentStock: matchingSku?.stock ?? 0,
      isInStock: (matchingSku?.stock ?? 0) >= item.quantity,
    }
  })

  const subtotal = enrichedItems.reduce((sum, item) => sum + item.price * item.quantity, 0)
  const originalTotal = enrichedItems.reduce((sum, item) => sum + item.originalPrice * item.quantity, 0)
  const itemDiscount = originalTotal - subtotal

  let coupon: UserCoupon | null = null
  let couponDiscount = 0

  if (couponId && couponsResult.status === 'fulfilled' && couponsResult.value.success) {
    const found = couponsResult.value.data.find((c) => c.id === couponId && c.status === 'UNUSED')
    if (found) {
      coupon = found
      if (found.templateType === 'AMOUNT_OFF' && found.discountAmount !== null) {
        if (subtotal >= found.thresholdAmount) {
          couponDiscount = found.discountAmount
        }
      } else if (found.templateType === 'PERCENT_OFF' && found.discountRate !== null) {
        if (subtotal >= found.thresholdAmount) {
          couponDiscount = subtotal * (1 - found.discountRate)
        }
      }
    }
  }

  const allInStock = enrichedItems.every((item) => item.isInStock)
  const totalAmount = Math.max(0, subtotal - couponDiscount)

  const response: CheckoutPreviewResponse = {
    items: enrichedItems,
    subtotal,
    discount: itemDiscount,
    couponDiscount,
    totalAmount,
    coupon,
    allInStock,
  }

  res.json({ success: true, data: response })
})

export default router
