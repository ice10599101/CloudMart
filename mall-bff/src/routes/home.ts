import { Router, Request, Response } from 'express'
import { gatewayClient } from '../services/gatewayClient.js'

interface Product {
  id: number
  name: string
  mainImage: string
  categoryName: string
  skus: Array<{ price: number; originalPrice: number }>
}

interface GroupActivity {
  id: number
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  groupPrice: number
  requiredMembers: number
  currentMembers: number
  status: string
  endTime: string
}

interface LiveRoom {
  id: number
  title: string
  hostName: string
  coverImage: string
  status: string
  viewerCount: number
  productName: string
}

interface HomeResponse {
  featuredProducts: Product[]
  promotions: GroupActivity[]
  liveRooms: LiveRoom[]
  degraded: string[]
}

const router = Router()

router.get('/', async (req: Request, res: Response) => {
  const token = req.authToken
  const degraded: string[] = []

  const [productsResult, promotionsResult, liveResult] = await Promise.allSettled([
    gatewayClient.get<Product[]>('/product/products/search', token, {
      page: '1',
      size: '8',
      sort: 'createdAt,desc',
    }),
    gatewayClient.get<GroupActivity[]>('/marketing/group/activities', token, {
      page: '1',
      size: '4',
    }),
    gatewayClient.get<LiveRoom[]>('/live/rooms', token, {
      page: '1',
      size: '4',
    }),
  ])

  let featuredProducts: Product[] = []
  if (productsResult.status === 'fulfilled' && productsResult.value.success) {
    featuredProducts = productsResult.value.data
  } else {
    degraded.push('featuredProducts')
  }

  let promotions: GroupActivity[] = []
  if (promotionsResult.status === 'fulfilled' && promotionsResult.value.success) {
    promotions = promotionsResult.value.data
  } else {
    degraded.push('promotions')
  }

  let liveRooms: LiveRoom[] = []
  if (liveResult.status === 'fulfilled' && liveResult.value.success) {
    liveRooms = liveResult.value.data
  } else {
    degraded.push('liveRooms')
  }

  const response: HomeResponse = {
    featuredProducts,
    promotions,
    liveRooms,
    degraded,
  }

  res.json({
    success: true,
    data: response,
  })
})

export default router
