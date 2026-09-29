import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { Product, Category, ProductSearchRequest, ProductSearchResult } from '@/types'

export function searchProducts(params: ProductSearchRequest) {
  return request.get<ApiResponse<ProductSearchResult>>('/product/products/search', { params })
}

// ==================== 品牌目录（用户面；管理面走 /admin/brands） ====================

export interface BrandInfo {
  id: number
  name: string
  logoUrl?: string | null
  description?: string | null
  status?: string
}

/** 品牌分页列表（name 模糊 / status 筛选） */
export function listBrands(params: { page?: number; size?: number; name?: string; status?: string } = {}) {
  return request.get<ApiResponse<{ records: BrandInfo[]; total: number }>>('/product/brands', { params })
}

/** 品牌详情 */
export function getBrandById(id: number | string) {
  return request.get<ApiResponse<BrandInfo>>(`/product/brands/${id}`)
}

export function getProductById(id: number | string) {
  return request.get<ApiResponse<Product>>(`/product/products/${id}`)
}

export function listCategories() {
  return request.get<ApiResponse<Category[]>>('/product/categories')
}
