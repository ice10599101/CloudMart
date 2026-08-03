import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Product, Category, ProductSearchRequest, ProductSearchResult } from '@/types'

export function searchProducts(params: ProductSearchRequest) {
  return request.get<ApiResponse<ProductSearchResult>>('/product/products/search', { params })
}

export function getProductById(id: number) {
  return request.get<ApiResponse<Product>>(`/product/products/${id}`)
}

export function listCategories() {
  return request.get<ApiResponse<Category[]>>('/product/categories')
}
