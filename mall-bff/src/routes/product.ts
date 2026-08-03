import { Router, Request, Response } from 'express'
import { gatewayClient } from '../services/gatewayClient.js'
import { ValidationError, NotFoundError } from '../utils/errors.js'

interface Product {
  id: number
  name: string
  description: string
  categoryId: number
  categoryName: string
  brand: string
  mainImage: string
  skus: Array<{
    id: number
    skuCode: string
    attributes: string
    price: number
    originalPrice: number
    stock: number
    image: string
  }>
  rating?: number
  createdAt: string
}

interface Category {
  id: number
  name: string
  parentId: number
  sortOrder: number
  icon: string
  status: number
}

interface ReviewItem {
  id: number
  username: string
  userAvatar: string | null
  rating: number
  content: string
  images: string[]
  createdAt: string
}

interface ReviewStats {
  averageRating: number
  totalReviews: number
  fiveStarCount: number
  fourStarCount: number
  threeStarCount: number
  twoStarCount: number
  oneStarCount: number
}

interface ProductListResponse {
  products: Array<Product & { category: Category | null }>
  meta: { page: number; pageSize: number; total: number }
}

interface ProductDetailResponse {
  product: Product
  reviews: ReviewItem[]
  reviewStats: ReviewStats | null
  minPrice: number
  maxPrice: number
  totalStock: number
}

const router = Router()

router.get('/', async (req: Request, res: Response) => {
  const token = req.authToken
  const { page = '1', size = '20', categoryId, keyword, sort } = req.query as Record<string, string>

  const params: Record<string, string> = { page, size }
  if (categoryId) params.categoryId = categoryId
  if (keyword) params.keyword = keyword
  if (sort) params.sort = sort

  const [productsResult, categoriesResult] = await Promise.allSettled([
    gatewayClient.get<Product[]>('/product/products/search', token, params),
    gatewayClient.get<Category[]>('/product/categories', token),
  ])

  let products: Product[] = []
  let meta: { page: number; pageSize: number; total: number } = { page: Number(page), pageSize: Number(size), total: 0 }

  if (productsResult.status === 'fulfilled' && productsResult.value.success) {
    products = productsResult.value.data
    if (productsResult.value.meta) {
      meta = productsResult.value.meta
    }
  }

  let categoryMap = new Map<number, Category>()
  if (categoriesResult.status === 'fulfilled' && categoriesResult.value.success) {
    for (const cat of categoriesResult.value.data) {
      categoryMap.set(cat.id, cat)
    }
  }

  const enrichedProducts = products.map((p) => ({
    ...p,
    category: categoryMap.get(p.categoryId) ?? null,
  }))

  const response: ProductListResponse = {
    products: enrichedProducts,
    meta,
  }

  res.json({ success: true, data: response, meta })
})

router.get('/search', async (req: Request, res: Response) => {
  const token = req.authToken
  const { keyword, page = '1', pageSize = '20', sort } = req.query as Record<string, string>

  if (!keyword) {
    throw new ValidationError('keyword is required')
  }

  const params: Record<string, string> = { keyword, page, pageSize }
  if (sort) params.sort = sort

  const result = await gatewayClient.get<Product[]>('/products/search', token, params)

  res.json({
    success: result.success,
    data: result.data,
    meta: result.meta,
  })
})

router.get('/:id', async (req: Request, res: Response) => {
  const token = req.authToken
  const productId = Number(req.params.id)

  if (!Number.isFinite(productId) || productId <= 0) {
    throw new ValidationError('Invalid product id')
  }

  const [productResult, reviewsResult, statsResult] = await Promise.allSettled([
    gatewayClient.get<Product>(`/product/products/${productId}`, token),
    gatewayClient.get<ReviewItem[]>(`/product/reviews/product/${productId}`, token, { page: '1', size: '10' }),
    gatewayClient.get<ReviewStats>(`/product/reviews/stats/${productId}`, token),
  ])

  if (productResult.status === 'rejected' || (productResult.status === 'fulfilled' && !productResult.value.success)) {
    throw new NotFoundError('Product')
  }

  const product = productResult.value.data

  let reviews: ReviewItem[] = []
  if (reviewsResult.status === 'fulfilled' && reviewsResult.value.success) {
    reviews = reviewsResult.value.data
  }

  let reviewStats: ReviewStats | null = null
  if (statsResult.status === 'fulfilled' && statsResult.value.success) {
    reviewStats = statsResult.value.data
  }

  const prices = product.skus.map((s) => s.price)
  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const totalStock = product.skus.reduce((sum, s) => sum + s.stock, 0)

  const response: ProductDetailResponse = {
    product,
    reviews,
    reviewStats,
    minPrice,
    maxPrice,
    totalStock,
  }

  res.json({ success: true, data: response })
})

export default router
