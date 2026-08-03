import { useState, useEffect, useMemo, useRef } from 'react'
import { Spin, Empty, Pagination } from 'antd'
import { history, useSearchParams } from 'umi'
import { searchProducts, listCategories } from '@/api/product'
import { listActivities, listProductsByActivity } from '@/api/seckill'
import { listGroupActivities } from '@/api/marketing'
import type { GroupActivity } from '@/api/marketing'
import type { ProductSearchItem, Category, SeckillActivity, SeckillProduct } from '@/types'

const SORT_OPTIONS = [
  { label: '综合', value: 'relevance' },
  { label: '价格从低到高', value: 'price_asc' },
  { label: '价格从高到低', value: 'price_desc' },
  { label: '销量', value: 'sales_desc' },
  { label: '评分', value: 'rating_desc' },
  { label: '新品', value: 'created' },
]

const PROMO_BANNERS = [
  { icon: '⚡', title: '限时秒杀', subtitle: '超值好物手慢无', gradient: 'linear-gradient(135deg, #FF4757 0%, #FF6B81 100%)', path: '/shop/seckill' },
  { icon: '👥', title: '拼团特惠', subtitle: '人多力量大更优惠', gradient: 'linear-gradient(135deg, #A855F7 0%, #7C3AED 100%)', path: '/shop/group-buy' },
  { icon: '🎫', title: '领券中心', subtitle: '领券立享优惠', gradient: 'var(--color-gradient-primary)', path: '/shop/coupons' },
  { icon: '🔥', title: '新品推荐', subtitle: '最新上架好物', gradient: 'linear-gradient(135deg, #FF8C00 0%, #FF6347 100%)', path: '#products' },
]

function useCountdown(targetTime: string) {
  const [remaining, setRemaining] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    const update = () => {
      const target = new Date(targetTime).getTime()
      const diff = Math.max(0, Math.floor((target - Date.now()) / 1000))
      setRemaining(diff)
      if (diff <= 0 && timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
    update()
    timerRef.current = setInterval(update, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [targetTime])

  const hours = Math.floor(remaining / 3600).toString().padStart(2, '0')
  const minutes = Math.floor((remaining % 3600) / 60).toString().padStart(2, '0')
  const seconds = (remaining % 60).toString().padStart(2, '0')

  return { remaining, hours, minutes, seconds, isExpired: remaining <= 0 }
}

function buildCategoryTree(categories: Category[]) {
  const map = new Map<number, { category: Category; children: Category[] }>()
  const roots: Category[] = []
  for (const cat of categories) {
    map.set(cat.id, { category: cat, children: [] })
  }
  for (const cat of categories) {
    if (!cat.parentId || !map.has(cat.parentId)) {
      roots.push(cat)
    } else {
      map.get(cat.parentId)!.children.push(cat)
    }
  }
  return { map, roots }
}

function CategorySidebar({
  categories,
  selectedId,
  onSelect,
}: {
  categories: Category[]
  selectedId: number | undefined
  onSelect: (id: number | undefined) => void
}) {
  const { roots, map } = buildCategoryTree(categories)

  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        borderRadius: '10px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: '16px 20px',
          fontSize: 15,
          fontWeight: 600,
          color: 'var(--color-text-secondary)',
          borderBottom: '1px solid var(--color-border)',
          letterSpacing: '0.5px',
        }}
      >
        商品分类
      </div>
      <div
        onClick={() => onSelect(undefined)}
        style={{
          padding: '10px 20px',
          cursor: 'pointer',
          color: !selectedId ? 'var(--color-primary)' : 'var(--color-text-secondary)',
          background: !selectedId ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
          fontWeight: !selectedId ? 600 : 400,
          borderLeft: !selectedId ? '3px solid var(--color-primary)' : '3px solid transparent',
          transition: 'all 0.2s ease',
        }}
      >
        全部商品
      </div>
      {roots.map((root) => (
        <div key={root.id}>
          <div
            onClick={() => onSelect(root.id)}
            style={{
              padding: '10px 20px',
              cursor: 'pointer',
              color: selectedId === root.id ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              background: selectedId === root.id ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
              fontWeight: selectedId === root.id ? 600 : 500,
              borderLeft: selectedId === root.id ? '3px solid var(--color-primary)' : '3px solid transparent',
              transition: 'all 0.2s ease',
            }}
          >
            {root.name}
          </div>
          {map.get(root.id)?.children.map((child) => (
            <div
              key={child.id}
              onClick={() => onSelect(child.id)}
              style={{
                padding: '8px 20px 8px 36px',
                cursor: 'pointer',
                color: selectedId === child.id ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
                background: selectedId === child.id ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
                fontWeight: selectedId === child.id ? 500 : 400,
                fontSize: 13,
                borderLeft: selectedId === child.id ? '3px solid var(--color-primary)' : '3px solid transparent',
                transition: 'all 0.2s ease',
              }}
            >
              {child.name}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function ProductCard({ product }: { product: ProductSearchItem }) {
  const minPrice = product.price ?? 0
  const originalPrice = product.originalPrice ?? 0
  const hasDiscount = originalPrice > minPrice

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
            alt={product.name}
            src={product.mainImage}
            style={{
              maxWidth: '100%',
              maxHeight: '100%',
              objectFit: 'contain',
              transition: 'transform 0.3s ease',
            }}
          />
        ) : (
          <div
            style={{
              fontSize: 40,
              color: 'var(--color-text-tertiary)',
              opacity: 0.3,
            }}
          >
            ▣
          </div>
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
            {Math.round((1 - minPrice / originalPrice) * 100)}% OFF
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
          {product.name}
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
            color: 'var(--color-text-tertiary)',
            fontSize: 12,
          }}
        >
          {product.brandName ? `${product.brandName} · ` : ''}{product.categoryName ?? ''}
        </div>
      </div>
    </div>
  )
}

function ProductListCard({
  product,
}: {
  product: ProductSearchItem
}) {
  const minPrice = product.price ?? 0
  const originalPrice = product.originalPrice ?? 0
  const hasDiscount = originalPrice > minPrice

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
        display: 'flex',
        gap: 16,
        padding: 16,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = '0 8px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)'
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = '0 4px 24px rgba(0, 0, 0, 0.3)'
        e.currentTarget.style.borderColor = 'var(--color-border)'
      }}
    >
      <div
        style={{
          width: 120,
          height: 120,
          flexShrink: 0,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: product.mainImage
            ? 'var(--color-bg-footer)'
            : 'linear-gradient(135deg, var(--color-bg-input) 0%, var(--color-bg-container) 100%)',
          borderRadius: '6px',
        }}
      >
        {product.mainImage ? (
          <img
            alt={product.name}
            src={product.mainImage}
            style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
          />
        ) : (
          <div style={{ fontSize: 28, color: 'var(--color-text-tertiary)', opacity: 0.3 }}>▣</div>
        )}
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <div
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 15,
            fontWeight: 500,
            marginBottom: 8,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {product.name}
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 4 }}>
          <span style={{ color: 'var(--color-primary)', fontSize: 20, fontWeight: 700 }}>
            ¥{minPrice.toFixed(2)}
          </span>
          {hasDiscount && (
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12, textDecoration: 'line-through' }}>
              ¥{originalPrice.toFixed(2)}
            </span>
          )}
        </div>
        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
          {product.brandName ? `${product.brandName} · ` : ''}{product.categoryName ?? ''}
        </div>
      </div>
    </div>
  )
}

function PromoBannerCarousel() {
  const scrollRef = useRef<HTMLDivElement>(null)

  const handleClick = (path: string) => {
    if (path === '#products') {
      const el = document.getElementById('products-section')
      el?.scrollIntoView({ behavior: 'smooth' })
    } else {
      history.push(path)
    }
  }

  return (
    <div
      ref={scrollRef}
      style={{
        display: 'flex',
        gap: 16,
        overflowX: 'auto',
        paddingBottom: 4,
        scrollbarWidth: 'none',
      }}
    >
      {PROMO_BANNERS.map((banner) => (
        <div
          key={banner.title}
          onClick={() => handleClick(banner.path)}
          style={{
            minWidth: 200,
            height: 100,
            borderRadius: '12px',
            background: banner.gradient,
            padding: '16px 20px',
            cursor: 'pointer',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            flexShrink: 0,
            transition: 'all 0.3s ease',
            boxShadow: '0 4px 16px rgba(0,0,0,0.3)',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'translateY(-2px)'
            e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.4)'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.3)'
          }}
        >
          <div style={{ fontSize: 22, marginBottom: 4 }}>{banner.icon}</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--color-text-secondary)' }}>{banner.title}</div>
          <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.75)', marginTop: 2 }}>{banner.subtitle}</div>
        </div>
      ))}
    </div>
  )
}

function CountdownBlock({ value }: { value: string }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: 28,
        height: 28,
        padding: '0 4px',
        background: 'rgba(255,71,87,0.15)',
        border: '1px solid rgba(255,71,87,0.3)',
        borderRadius: '6px',
        fontSize: 14,
        fontWeight: 800,
        color: '#FF4757',
        fontFamily: 'monospace',
      }}
    >
      {value}
    </span>
  )
}

function FlashSalePreview() {
  const [seckillProducts, setSeckillProducts] = useState<SeckillProduct[]>([])
  const [ongoingActivity, setOngoingActivity] = useState<SeckillActivity | null>(null)

  useEffect(() => {
    listActivities('ONGOING')
      .then(({ data: res }) => {
        const activities = res.data ?? []
        if (activities.length > 0) {
          setOngoingActivity(activities[0])
          return listProductsByActivity(activities[0].id)
        }
        return null
      })
      .then((res) => {
        if (res) {
          setSeckillProducts(res.data.data ?? [])
        }
      })
      .catch(() => {
        setSeckillProducts([])
      })
  }, [])

  const countdown = useCountdown(ongoingActivity?.endTime ?? '')

  if (seckillProducts.length === 0) return null

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 20, fontWeight: 700, color: 'var(--color-text-secondary)' }}>⚡ 限时秒杀</span>
          {ongoingActivity && !countdown.isExpired && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{ fontSize: 12, color: '#FF4757', marginRight: 4 }}>距结束</span>
              <CountdownBlock value={countdown.hours} />
              <span style={{ color: '#FF4757', fontWeight: 700 }}>:</span>
              <CountdownBlock value={countdown.minutes} />
              <span style={{ color: '#FF4757', fontWeight: 700 }}>:</span>
              <CountdownBlock value={countdown.seconds} />
            </div>
          )}
        </div>
        <span
          onClick={() => history.push('/shop/seckill')}
          style={{ fontSize: 13, color: 'var(--color-primary)', cursor: 'pointer', fontWeight: 500 }}
        >
          查看更多 &gt;
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          gap: 16,
          overflowX: 'auto',
          paddingBottom: 4,
          scrollbarWidth: 'none',
        }}
      >
        {seckillProducts.slice(0, 4).map((product) => {
          const soldPercent = product.totalStock > 0
            ? Math.round(((product.totalStock - product.availableStock) / product.totalStock) * 100)
            : 100
          return (
            <div
              key={product.id}
              onClick={() => history.push(`/shop/seckill`)}
              style={{
                minWidth: 180,
                background: 'var(--color-bg-container)',
                border: '1px solid var(--color-border)',
                borderRadius: '10px',
                overflow: 'hidden',
                cursor: 'pointer',
                flexShrink: 0,
                transition: 'all 0.3s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
                e.currentTarget.style.transform = 'translateY(-2px)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--color-border)'
                e.currentTarget.style.transform = 'translateY(0)'
              }}
            >
              <div
                style={{
                  height: 120,
                  background: 'linear-gradient(135deg, rgba(255,71,87,0.08), rgba(255,107,129,0.12))',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  position: 'relative',
                }}
              >
                <span style={{ fontSize: 32, opacity: 0.3 }}>⚡</span>
              </div>
              <div style={{ padding: '10px 12px' }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--color-text-secondary)', marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {product.productName}
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 6 }}>
                  <span style={{ fontSize: 18, fontWeight: 800, color: '#FF4757' }}>
                    ¥{product.seckillPrice.toFixed(0)}
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)', textDecoration: 'line-through' }}>
                    ¥{product.originalPrice.toFixed(0)}
                  </span>
                </div>
                <div style={{ height: 4, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${soldPercent}%`,
                      background: soldPercent >= 80
                        ? 'linear-gradient(90deg, #FF4757, #FF6B81)'
                        : 'linear-gradient(90deg, var(--color-primary), var(--color-primary-dark))',
                      borderRadius: 2,
                    }}
                  />
                </div>
                <div style={{ fontSize: 10, color: 'var(--color-text-tertiary)', marginTop: 3 }}>已抢{soldPercent}%</div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function GroupBuyPreview() {
  const [groupActivities, setGroupActivities] = useState<GroupActivity[]>([])

  useEffect(() => {
    listGroupActivities(1, 3)
      .then(({ data: res }) => {
        const records = res.data?.records
        setGroupActivities(records?.length ? records : [])
      })
      .catch(() => {
        setGroupActivities([])
      })
  }, [])

  if (groupActivities.length === 0) return null

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <span style={{ fontSize: 20, fontWeight: 700, color: 'var(--color-text-secondary)' }}>👥 拼团特惠</span>
        <span
          onClick={() => history.push('/shop/group-buy')}
          style={{ fontSize: 13, color: 'var(--color-primary)', cursor: 'pointer', fontWeight: 500 }}
        >
          查看更多 &gt;
        </span>
      </div>
      <div style={{ display: 'flex', gap: 16 }}>
        {groupActivities.slice(0, 3).map((activity) => {
          const progress = activity.targetNumber > 0
            ? Math.min(100, Math.round((activity.currentGroups / activity.maxGroups) * 100))
            : 0
          return (
            <div
              key={activity.id}
              onClick={() => history.push('/group-buy')}
              style={{
                flex: 1,
                background: 'var(--color-bg-container)',
                border: '1px solid var(--color-border)',
                borderRadius: '10px',
                overflow: 'hidden',
                cursor: 'pointer',
                transition: 'all 0.3s ease',
                minWidth: 0,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
                e.currentTarget.style.transform = 'translateY(-2px)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--color-border)'
                e.currentTarget.style.transform = 'translateY(0)'
              }}
            >
              <div
                style={{
                  height: 100,
                  background: 'linear-gradient(135deg, rgba(168,85,247,0.08), rgba(124,58,237,0.12))',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <span style={{ fontSize: 32, opacity: 0.3 }}>👥</span>
              </div>
              <div style={{ padding: '10px 12px' }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--color-text-secondary)', marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {activity.name}
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 8 }}>
                  <span style={{ fontSize: 16, fontWeight: 800, color: '#A855F7' }}>
                    ¥{activity.groupPrice.toFixed(0)}
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)', textDecoration: 'line-through' }}>
                    ¥{activity.originalPrice.toFixed(0)}
                  </span>
                </div>
                <div style={{ height: 4, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden', marginBottom: 4 }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${progress}%`,
                      background: 'linear-gradient(90deg, #A855F7, #7C3AED)',
                      borderRadius: 2,
                    }}
                  />
                </div>
                <div style={{ fontSize: 10, color: 'var(--color-text-secondary)' }}>
                  {activity.currentGroups}/{activity.maxGroups}团 · {activity.targetNumber}人成团
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function ProductList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [products, setProducts] = useState<ProductSearchItem[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')

  const categoryId = searchParams.get('categoryId')
    ? Number(searchParams.get('categoryId'))
    : undefined
  const page = Number(searchParams.get('page')) || 1
  const sort = searchParams.get('sort') || 'relevance'
  const pageSize = 12

  useEffect(() => {
    listCategories()
      .then((res) => setCategories(res.data.data ?? []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    searchProducts({
      categoryId,
      page,
      size: pageSize,
      sort,
    })
      .then((res) => {
        setProducts(res.data.data?.products ?? [])
        setTotal(res.data.data?.total ?? res.data.meta?.total ?? 0)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [categoryId, page, sort])

  const handleCategorySelect = (id: number | undefined) => {
    const params = new URLSearchParams(searchParams)
    if (id !== undefined) {
      params.set('categoryId', String(id))
    } else {
      params.delete('categoryId')
    }
    params.set('page', '1')
    setSearchParams(params)
  }

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams)
    params.set('page', String(newPage))
    setSearchParams(params)
  }

  const handleSortChange = (value: string) => {
    const params = new URLSearchParams(searchParams)
    params.set('sort', value)
    params.set('page', '1')
    setSearchParams(params)
  }

  const selectedCategory = categories.find((c) => c.id === categoryId)

  const gridStyle = useMemo(
    () =>
      viewMode === 'grid'
        ? {
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 20,
          }
        : {
            display: 'flex',
            flexDirection: 'column' as const,
            gap: 12,
          },
    [viewMode],
  )

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
          <span style={{ color: 'var(--color-text-secondary)' }}>商品列表</span>
          {selectedCategory && (
            <>
              <span style={{ color: 'var(--color-text-tertiary)' }}>/</span>
              <span style={{ color: 'var(--color-primary)' }}>{selectedCategory.name}</span>
            </>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 32, marginBottom: 32 }}>
          <PromoBannerCarousel />
          <FlashSalePreview />
          <GroupBuyPreview />
        </div>

        <div id="products-section" style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              width: 240,
              flexShrink: 0,
              position: 'sticky',
              top: 80,
              alignSelf: 'flex-start',
            }}
            className="hidden-mobile"
          >
            <CategorySidebar
              categories={categories}
              selectedId={categoryId}
              onSelect={handleCategorySelect}
            />
          </div>

          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                background: 'var(--color-bg-container)',
                borderRadius: '10px',
                border: '1px solid var(--color-border)',
                padding: '12px 20px',
                marginBottom: 20,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <div style={{ display: 'flex', gap: 4 }}>
                {SORT_OPTIONS.map((opt) => (
                  <div
                    key={opt.value}
                    onClick={() => handleSortChange(opt.value)}
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
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13, marginRight: 8 }}>
                  共 {total} 件
                </span>
                <div
                  onClick={() => setViewMode('grid')}
                  style={{
                    width: 32,
                    height: 32,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    background: viewMode === 'grid' ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
                    border: viewMode === 'grid' ? '1px solid rgba(var(--color-primary-rgb), 0.3)' : '1px solid var(--color-border)',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <rect x="1" y="1" width="6" height="6" rx="1" fill={viewMode === 'grid' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                    <rect x="9" y="1" width="6" height="6" rx="1" fill={viewMode === 'grid' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                    <rect x="1" y="9" width="6" height="6" rx="1" fill={viewMode === 'grid' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                    <rect x="9" y="9" width="6" height="6" rx="1" fill={viewMode === 'grid' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                  </svg>
                </div>
                <div
                  onClick={() => setViewMode('list')}
                  style={{
                    width: 32,
                    height: 32,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    background: viewMode === 'list' ? 'rgba(var(--color-primary-rgb), 0.15)' : 'transparent',
                    border: viewMode === 'list' ? '1px solid rgba(var(--color-primary-rgb), 0.3)' : '1px solid var(--color-border)',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <rect x="1" y="1" width="14" height="3" rx="1" fill={viewMode === 'list' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                    <rect x="1" y="6" width="14" height="3" rx="1" fill={viewMode === 'list' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                    <rect x="1" y="11" width="14" height="3" rx="1" fill={viewMode === 'list' ? 'var(--color-primary)' : 'var(--color-text-tertiary)'} />
                  </svg>
                </div>
              </div>
            </div>

            <Spin spinning={loading}>
              {products.length === 0 && !loading ? (
                <Empty
                  description={<span style={{ color: 'var(--color-text-tertiary)' }}>暂无商品</span>}
                  style={{ padding: '80px 0' }}
                />
              ) : (
                <div style={gridStyle}>
                  {products.map((product) =>
                    viewMode === 'grid' ? (
                      <ProductCard key={product.id} product={product} />
                    ) : (
                      <ProductListCard key={product.id} product={product} />
                    ),
                  )}
                </div>
              )}
            </Spin>

            {total > pageSize && (
              <div style={{ textAlign: 'center', marginTop: 32, paddingBottom: 16 }}>
                <Pagination
                  current={page}
                  total={total}
                  pageSize={pageSize}
                  onChange={handlePageChange}
                  showSizeChanger={false}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      <style>{`
        @media (max-width: 768px) {
          .hidden-mobile { display: none !important; }
        }
        @media (max-width: 1024px) {
          div[style*="grid-template-columns: repeat(4"] {
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
      `}</style>
    </div>
  )
}
