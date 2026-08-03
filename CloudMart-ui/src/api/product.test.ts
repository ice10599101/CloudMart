import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { searchProducts, getProductById, listCategories } from './product'

describe('product API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('searchProducts() calls GET /product/products/search with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await searchProducts({ keyword: 'phone', page: 1, size: 20 })

    expect(request.get).toHaveBeenCalledWith('/product/products/search', { params: { keyword: 'phone', page: 1, size: 20 } })
  })

  it('getProductById() calls GET /product/products/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getProductById(42)

    expect(request.get).toHaveBeenCalledWith('/product/products/42')
  })

  it('listCategories() calls GET /product/categories', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listCategories()

    expect(request.get).toHaveBeenCalledWith('/product/categories')
  })
})
