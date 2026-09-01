import { useState, useEffect, useMemo } from 'react'
import { Spin, Empty, Pagination, Tag, message } from 'antd'
import { history, useSearchParams } from 'umi'
import { searchProducts } from '@/api/product'
import { searchPosts, getSearchHistory, clearSearchHistory, getHotSearches } from '@/api/community'
import type { ProductSearchItem, BrandBucket, CategoryBucket } from '@/types'
import type { Post } from '@/api/community'

type SearchTab = 'product' | 'post'

const SORT_OPTIONS = [
  { label: '综合', value: 'relevance' },
  { label: '价格从低到高', value: 'price_asc' },
  { label: '价格从高到低', value: 'price_desc' },
  { label: '销量优先', value: 'sales_desc' },
  { label: '评分优先', value: 'rating_desc' },
  { label: '新品上架', value: 'created' },
]

const TAB_OPTIONS: { key: SearchTab; label: string }[] = [
  { key: 'product', label: '商品' },
  { key: 'post', label: '帖子' },
]

const PRICE_RANGES = [
  { label: '全部', min: undefined, max: undefined },
  { label: '¥0-100', min: 0, max: 100 },
  { label: '¥100-500', min: 100, max: 500 },
  { label: '¥500-2000', min: 500, max: 2000 },
  { label: '¥2000以上', min: 2000, max: undefined },
]

function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '')
}

/**
 * 将 ES 高亮后的商品名转换为安全的 HTML 字符串。
 * 仅允许 <em></em> 标签通过，其余 HTML 字符全部转义，避免 XSS。
 */
function renderHighlightHtml(name: string): string {
  if (!name) return ''
  // 1. 把 ES 加的 <em></em> 替换为私有占位符
  const PH_START = '\u0001EM_S\u0001'
  const PH_END = '\u0001EM_E\u0001'
  const withPlaceholders = name
    .replace(/<em>/g, PH_START)
    .replace(/<\/em>/g, PH_END)
  // 2. 转义所有 HTML 特殊字符（防止商品名本身含恶意标签）
  const escaped = withPlaceholders
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
  // 3. 还原 <em></em>
  return escaped
    .replace(new RegExp(PH_START, 'g'), '<em>')
    .replace(new RegExp(PH_END, 'g'), '</em>')
}

function ProductCard({ product }: { product: ProductSearchItem }) {
  const minPrice = product.price ?? 0
  const originalPrice = product.originalPrice ?? 0
  const hasDiscount = originalPrice > minPrice && originalPrice > 0
  const discountPercent = hasDiscount
    ? Math.round((1 - minPrice / originalPrice) * 100)
    : 0

  return (
    <div
      onClick={() => history.push(`/products/${product.id}`)}
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '10px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-4px)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0, 0, 0, 0.3)'
        e.currentTarget.style.borderColor = 'var(--color-border)'
      }}
    >
      <div
        style={{
          height: 220,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: product.mainImage
            ? 'var(--color-bg-footer)'
            : 'linear-gradient(135deg, var(--color-bg-input) 0%, var(--color-bg-container) 50%, var(--color-bg-elevated) 100%)',
          position: 'relative',
        }}
      >
        {product.mainImage ? (
          <img
            alt={stripHtml(product.name)}
            src={product.mainImage}
            style={{
              maxWidth: '100%',
              maxHeight: '100%',
              objectFit: 'contain',
              transition: 'transform 0.3s ease',
            }}
          />
        ) : (
          <div style={{ fontSize: 40, color: 'var(--color-text-tertiary)', opacity: 0.3 }}>▣</div>
        )}
        {hasDiscount && (
          <div
            style={{
              position: 'absolute',
              top: 12,
              right: 12,
              background: 'var(--color-gradient-primary)',
              color: 'var(--color-bg-base)',
              padding: '2px 10px',
              borderRadius: '6px',
              fontSize: 12,
              fontWeight: 700,
              letterSpacing: '0.5px',
            }}
          >
            {discountPercent}% OFF
          </div>
        )}
      </div>
      <div style={{ padding: '14px 16px 16px' }}>
        <div
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            fontWeight: 500,
            marginBottom: 8,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {/* ES 命中关键词时 name 含 <em> 标签，经 renderHighlightHtml 转义后安全渲染 */}
          <span dangerouslySetInnerHTML={{ __html: renderHighlightHtml(product.name) }} />
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 6 }}>
          <span
            style={{
              color: 'var(--color-primary)',
              fontSize: 20,
              fontWeight: 700,
              letterSpacing: '-0.5px',
            }}
          >
            ¥{minPrice.toFixed(2)}
          </span>
          {hasDiscount && (
            <span
              style={{
                color: 'var(--color-text-tertiary)',
                fontSize: 12,
                textDecoration: 'line-through',
              }}
            >
              ¥{originalPrice.toFixed(2)}
            </span>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            color: 'var(--color-text-tertiary)',
            fontSize: 12,
          }}
        >
          <span>
            {product.brandName ? `${product.brandName} · ` : ''}
            {product.categoryName ?? ''}
          </span>
          {typeof product.sales === 'number' && product.sales > 0 && (
            <span>已售 {product.sales}</span>
          )}
        </div>
      </div>
    </div>
  )
}

function PostCard({ post }: { post: Post }) {
  const preview = stripHtml(post.content).slice(0, 100)

  return (
    <div
      onClick={() => history.push(`/post/${post.id}`)}
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '10px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-4px)'
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0, 0, 0, 0.3)'
        e.currentTarget.style.borderColor = 'var(--color-border)'
      }}
    >
      <div style={{ display: 'flex', gap: 16, padding: 20 }}>
        {post.coverImage ? (
          <div
            style={{
              width: 120,
              height: 120,
              borderRadius: 8,
              overflow: 'hidden',
              flexShrink: 0,
              background: 'var(--color-bg-footer)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <img
              alt={post.title}
              src={post.coverImage}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          </div>
        ) : (
          <div
            style={{
              width: 120,
              height: 120,
              borderRadius: 8,
              flexShrink: 0,
              background: 'linear-gradient(135deg, var(--color-bg-input) 0%, var(--color-bg-container) 50%, var(--color-bg-elevated) 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 32,
              color: 'var(--color-text-tertiary)',
              opacity: 0.3,
            }}
          >
            📝
          </div>
        )}
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <div
            style={{
              color: 'var(--color-text-secondary)',
              fontSize: 15,
              fontWeight: 600,
              marginBottom: 8,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {post.title}
          </div>
          <div
            style={{
              color: 'var(--color-text-secondary)',
              fontSize: 13,
              lineHeight: 1.6,
              marginBottom: 12,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
            }}
          >
            {preview}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {post.authorAvatar ? (
                <img
                  alt={post.authorNickname}
                  src={post.authorAvatar}
                  style={{ width: 20, height: 20, borderRadius: '50%', objectFit: 'cover' }}
                />
              ) : (
                <div
                  style={{
                    width: 20,
                    height: 20,
                    borderRadius: '50%',
                    background: 'var(--color-gradient-primary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--color-bg-base)',
                    fontSize: 10,
                    fontWeight: 700,
                  }}
                >
                  {post.authorNickname?.charAt(0) ?? '?'}
                </div>
              )}
              <span style={{ color: 'var(--color-text-secondary)' }}>{post.authorNickname}</span>
            </div>
            <span>❤ {post.likeCount}</span>
            <span>💬 {post.commentCount}</span>
            <span>{new Date(post.createdAt).toLocaleDateString()}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * 筛选侧边栏：展示品牌、分类聚合分面，点击后回写 URL 参数触发新查询。
 */
function FilterSidebar({
  brands,
  categories,
  selectedBrand,
  selectedCategoryId,
  selectedPriceRange,
  onBrandChange,
  onCategoryChange,
  onPriceRangeChange,
  onReset,
}: {
  brands: BrandBucket[]
  categories: CategoryBucket[]
  selectedBrand?: string
  selectedCategoryId?: number
  selectedPriceRange: { min?: number; max?: number }
  onBrandChange: (brand: string | undefined) => void
  onCategoryChange: (id: number | undefined) => void
  onPriceRangeChange: (range: { min?: number; max?: number }) => void
  onReset: () => void
}) {
  const hasActiveFilter =
    !!selectedBrand || !!selectedCategoryId || selectedPriceRange.min !== undefined || selectedPriceRange.max !== undefined

  return (
    <div
      style={{
        width: 240,
        flexShrink: 0,
        background: 'var(--color-bg-container)',
        borderRadius: '12px',
        border: '1px solid var(--color-border)',
        padding: '20px 18px',
        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
        position: 'sticky',
        top: 16,
        alignSelf: 'flex-start',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>
          筛选
        </span>
        {hasActiveFilter && (
          <span
            onClick={onReset}
            style={{
              fontSize: 12,
              color: 'var(--color-primary)',
              cursor: 'pointer',
            }}
          >
            清空
          </span>
        )}
      </div>

      {/* 价格区间 */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 10 }}>
          价格区间
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {PRICE_RANGES.map((range) => {
            const isActive =
              selectedPriceRange.min === range.min && selectedPriceRange.max === range.max
            return (
              <div
                key={range.label}
                onClick={() => onPriceRangeChange({ min: range.min, max: range.max })}
                style={{
                  padding: '6px 10px',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                  color: isActive ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                  background: isActive ? 'rgba(var(--color-primary-rgb), 0.12)' : 'transparent',
                  transition: 'all 0.2s ease',
                }}
              >
                {range.label}
              </div>
            )
          })}
        </div>
      </div>

      {/* 品牌聚合分面 */}
      {brands.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 10 }}>
            品牌
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {brands.map((bucket) => {
              const isActive = selectedBrand === bucket.brand
              return (
                <div
                  key={bucket.brand}
                  onClick={() => onBrandChange(isActive ? undefined : bucket.brand)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '6px 10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 13,
                    color: isActive ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                    background: isActive ? 'rgba(var(--color-primary-rgb), 0.12)' : 'transparent',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {bucket.brand}
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{bucket.count}</span>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* 分类聚合分面 */}
      {categories.length > 0 && (
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 10 }}>
            分类
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {categories.map((bucket) => {
              const isActive = selectedCategoryId === bucket.categoryId
              return (
                <div
                  key={bucket.categoryId}
                  onClick={() => onCategoryChange(isActive ? undefined : bucket.categoryId)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '6px 10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 13,
                    color: isActive ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                    background: isActive ? 'rgba(var(--color-primary-rgb), 0.12)' : 'transparent',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <span>分类 #{bucket.categoryId}</span>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{bucket.count}</span>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {brands.length === 0 && categories.length === 0 && (
        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12, textAlign: 'center', padding: '20px 0' }}>
          暂无可筛选项
        </div>
      )}
    </div>
  )
}

export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [products, setProducts] = useState<ProductSearchItem[]>([])
  const [brands, setBrands] = useState<BrandBucket[]>([])
  const [categories, setCategories] = useState<CategoryBucket[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [posts, setPosts] = useState<Post[]>([])
  const [postTotal, setPostTotal] = useState(0)
  const [postLoading, setPostLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<SearchTab>(
    (searchParams.get('tab') as SearchTab) || 'product',
  )
  const [searchValue, setSearchValue] = useState(searchParams.get('q') || '')
  const [searchHistory, setSearchHistory] = useState<string[]>([])
  const [hotSearches, setHotSearches] = useState<string[]>([])

  const keyword = searchParams.get('q') || ''
  const sort = searchParams.get('sort') || 'relevance'
  const page = Number(searchParams.get('page')) || 1
  const brandParam = searchParams.get('brand') || undefined
  const categoryIdParam = searchParams.get('categoryId')
    ? Number(searchParams.get('categoryId'))
    : undefined
  const minPriceParam = searchParams.get('minPrice')
    ? Number(searchParams.get('minPrice'))
    : undefined
  const maxPriceParam = searchParams.get('maxPrice')
    ? Number(searchParams.get('maxPrice'))
    : undefined
  const pageSize = activeTab === 'product' ? 12 : 10

  const selectedPriceRange = useMemo(
    () => ({ min: minPriceParam, max: maxPriceParam }),
    [minPriceParam, maxPriceParam],
  )

  useEffect(() => {
    if (!keyword || activeTab !== 'product') return
    setLoading(true)
    searchProducts({
      keyword,
      sort,
      page,
      size: pageSize,
      brand: brandParam,
      categoryId: categoryIdParam,
      minPrice: minPriceParam,
      maxPrice: maxPriceParam,
    })
      .then((res) => {
        const data = res.data.data
        setProducts(data?.products ?? [])
        setBrands(data?.brands ?? [])
        setCategories(data?.categories ?? [])
        setTotal(data?.total ?? res.data.meta?.total ?? 0)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [keyword, sort, page, activeTab, brandParam, categoryIdParam, minPriceParam, maxPriceParam])

  useEffect(() => {
    if (!keyword || activeTab !== 'post') return
    setPostLoading(true)
    searchPosts(keyword, page, pageSize)
      .then((res) => {
        setPosts(res.data.data ?? [])
        setPostTotal(res.data.meta?.total ?? 0)
      })
      .catch(() => {})
      .finally(() => setPostLoading(false))
  }, [keyword, page, activeTab])

  useEffect(() => {
    getSearchHistory()
      .then((res) => {
        setSearchHistory(res.data.data ?? [])
      })
      .catch(() => {})
    getHotSearches()
      .then((res) => {
        setHotSearches(res.data.data ?? [])
      })
      .catch(() => {})
  }, [keyword])

  const handleSearch = () => {
    const params = new URLSearchParams(searchParams)
    if (searchValue.trim()) {
      params.set('q', searchValue.trim())
    } else {
      params.delete('q')
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const updateParam = (key: string, value: string) => {
    const params = new URLSearchParams(searchParams)
    if (value) {
      params.set(key, value)
    } else {
      params.delete(key)
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const handleBrandChange = (nextBrand: string | undefined) => {
    const params = new URLSearchParams(searchParams)
    if (nextBrand) {
      params.set('brand', nextBrand)
    } else {
      params.delete('brand')
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const handleCategoryChange = (nextId: number | undefined) => {
    const params = new URLSearchParams(searchParams)
    if (nextId !== undefined) {
      params.set('categoryId', String(nextId))
    } else {
      params.delete('categoryId')
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const handlePriceRangeChange = (range: { min?: number; max?: number }) => {
    const params = new URLSearchParams(searchParams)
    if (range.min !== undefined) {
      params.set('minPrice', String(range.min))
    } else {
      params.delete('minPrice')
    }
    if (range.max !== undefined) {
      params.set('maxPrice', String(range.max))
    } else {
      params.delete('maxPrice')
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const handleResetFilters = () => {
    const params = new URLSearchParams(searchParams)
    params.delete('brand')
    params.delete('categoryId')
    params.delete('minPrice')
    params.delete('maxPrice')
    params.set('page', '1')
    setSearchParams(params)
  }

  const handleTabChange = (tab: SearchTab) => {
    setActiveTab(tab)
    const params = new URLSearchParams(searchParams)
    params.set('tab', tab)
    params.set('page', '1')
    setSearchParams(params)
  }

  const isLoading = activeTab === 'product' ? loading : postLoading
  const currentTotal = activeTab === 'product' ? total : postTotal
  const resultLabel = activeTab === 'product' ? '件商品' : '篇帖子'

  return (
    <div style={{ background: 'var(--color-bg-base)', minHeight: '100vh' }}>
      <div style={{ maxWidth: 1320, margin: '0 auto', padding: '24px 32px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 24,
            fontSize: 13,
            color: 'var(--color-text-tertiary)',
          }}
        >
          <span
            style={{ cursor: 'pointer', color: 'var(--color-text-secondary)' }}
            onClick={() => history.push('/')}
          >
            首页
          </span>
          <span style={{ color: 'var(--color-text-tertiary)' }}>/</span>
          <span style={{ color: 'var(--color-text-secondary)' }}>搜索</span>
        </div>

        <div
          style={{
            background: 'var(--color-bg-container)',
            borderRadius: '16px',
            border: '1px solid var(--color-border)',
            padding: '32px',
            marginBottom: 24,
            boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              maxWidth: 640,
              margin: '0 auto',
            }}
          >
            <div
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                background: 'var(--color-bg-input)',
                borderRadius: '10px',
                border: '1px solid var(--color-border)',
                overflow: 'hidden',
                transition: 'border-color 0.2s ease',
              }}
            >
              <div style={{ paddingLeft: 16, color: 'var(--color-text-tertiary)', display: 'flex', alignItems: 'center' }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" />
                  <path d="m21 21-4.35-4.35" />
                </svg>
              </div>
              <input
                placeholder={activeTab === 'product' ? '搜索商品...' : '搜索帖子...'}
                value={searchValue}
                onChange={(e) => setSearchValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleSearch()
                }}
                style={{
                  flex: 1,
                  background: 'transparent',
                  border: 'none',
                  outline: 'none',
                  color: 'var(--color-text-secondary)',
                  fontSize: 15,
                  padding: '12px 16px',
                }}
              />
            </div>
            <button
              type="button"
              onClick={handleSearch}
              style={{
                padding: '12px 28px',
                borderRadius: '10px',
                border: 'none',
                background: 'var(--color-gradient-primary)',
                color: 'var(--color-bg-base)',
                fontSize: 15,
                fontWeight: 700,
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                letterSpacing: '0.5px',
                boxShadow: '0 4px 12px rgba(var(--color-primary-rgb), 0.2)',
              }}
            >
              搜索
            </button>
          </div>
        </div>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 0,
            marginBottom: 24,
            background: 'var(--color-bg-container)',
            borderRadius: '10px',
            border: '1px solid var(--color-border)',
            padding: 4,
            width: 'fit-content',
          }}
        >
          {TAB_OPTIONS.map((tab) => (
            <div
              key={tab.key}
              onClick={() => handleTabChange(tab.key)}
              style={{
                padding: '8px 28px',
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: activeTab === tab.key ? 600 : 400,
                color: activeTab === tab.key ? '#FFFFFF' : 'var(--color-text-secondary)',
                background: activeTab === tab.key ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
                transition: 'all 0.2s ease',
                userSelect: 'none',
              }}
            >
              {tab.label}
            </div>
          ))}
        </div>

        {keyword && (
          <div
            style={{
              marginBottom: 24,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14 }}>
                搜索
              </span>
              <span style={{ color: 'var(--color-primary)', fontSize: 18, fontWeight: 600 }}>
                &quot;{keyword}&quot;
              </span>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14 }}>
                共找到 {currentTotal} {resultLabel}
              </span>
            </div>
            {activeTab === 'product' && (
              <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {SORT_OPTIONS.map((opt) => (
                  <div
                    key={opt.value}
                    onClick={() => updateParam('sort', opt.value)}
                    style={{
                      padding: '6px 16px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: 13,
                      fontWeight: sort === opt.value ? 600 : 400,
                      color: sort === opt.value ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                      background: sort === opt.value ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
                      transition: 'all 0.2s ease',
                    }}
                  >
                    {opt.label}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <Spin spinning={isLoading}>
          {!keyword ? (
            <div style={{ display: 'flex', gap: 32, padding: '24px 0' }}>
              {searchHistory.length > 0 && (
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                    <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text-secondary)' }}>搜索历史</span>
                    <span
                      onClick={async () => {
                        try {
                          await clearSearchHistory()
                          setSearchHistory([])
                          message.success('已清空搜索历史')
                        } catch {
                          message.error('清空失败')
                        }
                      }}
                      style={{ fontSize: 13, color: 'var(--color-text-secondary)', cursor: 'pointer' }}
                    >
                      清空
                    </span>
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {searchHistory.map((item) => (
                      <Tag
                        key={item}
                        onClick={() => {
                          setSearchValue(item)
                          const params = new URLSearchParams(searchParams)
                          params.set('q', item)
                          params.set('page', '1')
                          setSearchParams(params)
                        }}
                        style={{
                          background: 'var(--color-bg-elevated)',
                          border: '1px solid var(--color-border)',
                          color: 'var(--color-text-secondary)',
                          cursor: 'pointer',
                          borderRadius: 6,
                          padding: '4px 12px',
                          fontSize: 13,
                        }}
                      >
                        {item}
                      </Tag>
                    ))}
                  </div>
                </div>
              )}
              {hotSearches.length > 0 && (
                <div style={{ flex: 1 }}>
                  <div style={{ marginBottom: 16 }}>
                    <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--color-text-secondary)' }}>热搜词</span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    {hotSearches.map((item, index) => (
                      <div
                        key={item}
                        onClick={() => {
                          setSearchValue(item)
                          const params = new URLSearchParams(searchParams)
                          params.set('q', item)
                          params.set('page', '1')
                          setSearchParams(params)
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          cursor: 'pointer',
                          padding: '8px 12px',
                          borderRadius: 8,
                          transition: 'background 0.2s ease',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background = 'var(--color-border)'
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = 'transparent'
                        }}
                      >
                        <span
                          style={{
                            width: 24,
                            height: 24,
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 12,
                            fontWeight: 700,
                            background: index < 3 ? 'rgba(var(--color-primary-rgb), 0.15)' : 'var(--color-border)',
                            color: index < 3 ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                            flexShrink: 0,
                          }}
                        >
                          {index + 1}
                        </span>
                        <span style={{ fontSize: 14, color: index < 3 ? 'var(--color-primary)' : 'var(--color-text-secondary)', fontWeight: index < 3 ? 600 : 400 }}>
                          {item}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {searchHistory.length === 0 && hotSearches.length === 0 && (
                <div style={{ textAlign: 'center', padding: '100px 0' }}>
                  <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.15 }}>🔍</div>
                  <div style={{ color: 'var(--color-text-tertiary)', fontSize: 16 }}>
                    请输入搜索关键词
                  </div>
                </div>
              )}
            </div>
          ) : activeTab === 'product' ? (
            <div style={{ display: 'flex', gap: 24 }}>
              <FilterSidebar
                brands={brands}
                categories={categories}
                selectedBrand={brandParam}
                selectedCategoryId={categoryIdParam}
                selectedPriceRange={selectedPriceRange}
                onBrandChange={handleBrandChange}
                onCategoryChange={handleCategoryChange}
                onPriceRangeChange={handlePriceRangeChange}
                onReset={handleResetFilters}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                {products.length === 0 && !loading ? (
                  <Empty
                    description={<span style={{ color: 'var(--color-text-tertiary)' }}>未找到相关商品</span>}
                    style={{ padding: '80px 0' }}
                  />
                ) : (
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(3, 1fr)',
                      gap: 20,
                    }}
                  >
                    {products.map((product) => (
                      <ProductCard key={product.id} product={product} />
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : posts.length === 0 && !postLoading ? (
            <Empty
              description={<span style={{ color: 'var(--color-text-tertiary)' }}>未找到相关帖子</span>}
              style={{ padding: '80px 0' }}
            />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {posts.map((post) => (
                <PostCard key={post.id} post={post} />
              ))}
            </div>
          )}
        </Spin>

        {currentTotal > pageSize && (
          <div style={{ textAlign: 'center', marginTop: 32, paddingBottom: 16 }}>
            <Pagination
              current={page}
              total={currentTotal}
              pageSize={pageSize}
              onChange={(p) => updateParam('page', String(p))}
              showSizeChanger={false}
            />
          </div>
        )}
      </div>

      <style>{`
        @media (max-width: 1024px) {
          div[style*="grid-template-columns: repeat(3"] {
            grid-template-columns: repeat(2, 1fr) !important;
          }
        }
        @media (max-width: 768px) {
          div[style*="grid-template-columns: repeat(3"] {
            grid-template-columns: repeat(2, 1fr) !important;
          }
        }
        .ant-pagination .ant-pagination-item {
          background: var(--color-bg-container) !important;
          border-color: var(--color-border) !important;
        }
        .ant-pagination .ant-pagination-item a {
          color: var(--color-text-secondary) !important;
        }
        .ant-pagination .ant-pagination-item-active {
          background: rgba(var(--color-primary-rgb), 0.15) !important;
          border-color: rgba(var(--color-primary-rgb), 0.3) !important;
        }
        .ant-pagination .ant-pagination-item-active a {
          color: var(--color-primary) !important;
        }
        .ant-pagination .ant-pagination-prev button,
        .ant-pagination .ant-pagination-next button {
          color: var(--color-text-secondary) !important;
        }
        .ant-spin-text { color: var(--color-text-secondary) !important; }
        .ant-empty-description { color: var(--color-text-tertiary) !important; }
        /* ES 高亮 <em> 标签样式 */
        em {
          color: var(--color-primary) !important;
          font-style: normal !important;
          font-weight: 700 !important;
        }
      `}</style>
    </div>
  )
}
