import { useState, useEffect } from 'react'
import { Rate, InputNumber, Spin, Empty, message } from 'antd'
import DOMPurify from 'dompurify'
import { history, useParams } from 'umi'
import { getProductById, listCategories } from '@/api/product'
import { getProductReviews, getReviewStats } from '@/api/review'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'
import type { Product, Category, Sku } from '@/types'
import type { ReviewItem, ReviewStats } from '@/api/review'

function SkuSelector({
  skus,
  selectedSku,
  onSelect,
}: {
  skus: Sku[]
  selectedSku: Sku | null
  onSelect: (sku: Sku) => void
}) {
  const attrGroups = new Map<string, Set<string>>()
  for (const sku of skus) {
    try {
      const attrs = JSON.parse(sku.attributes) as Record<string, string>
      for (const [key, value] of Object.entries(attrs)) {
        if (!attrGroups.has(key)) {
          attrGroups.set(key, new Set())
        }
        attrGroups.get(key)!.add(value)
      }
    } catch {
      // skip invalid attributes
    }
  }

  const [selectedAttrs, setSelectedAttrs] = useState<Record<string, string>>({})

  useEffect(() => {
    if (selectedSku && selectedSku.attributes) {
      try {
        const attrs = JSON.parse(selectedSku.attributes) as Record<string, string>
        setSelectedAttrs(attrs)
      } catch {
        // ignore
      }
    }
  }, [selectedSku])

  const handleAttrSelect = (key: string, value: string) => {
    const newAttrs = { ...selectedAttrs, [key]: value }
    setSelectedAttrs(newAttrs)
    const matched = skus.find((sku) => {
      try {
        const attrs = JSON.parse(sku.attributes) as Record<string, string>
        return Object.entries(newAttrs).every(([k, v]) => attrs[k] === v)
      } catch {
        return false
      }
    })
    if (matched) {
      onSelect(matched)
    }
  }

  return (
    <div>
      {Array.from(attrGroups.entries()).map(([key, values]) => (
        <div key={key} style={{ marginBottom: 20 }}>
          <div
            style={{
              color: 'var(--color-text-secondary)',
              fontSize: 13,
              marginBottom: 10,
              fontWeight: 500,
            }}
          >
            {key}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {Array.from(values).map((value) => {
              const isSelected = selectedAttrs[key] === value
              return (
                <div
                  key={value}
                  onClick={() => handleAttrSelect(key, value)}
                  style={{
                    padding: '6px 18px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontSize: 13,
                    fontWeight: isSelected ? 600 : 400,
                    color: isSelected ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                    background: isSelected ? 'rgba(var(--color-primary-rgb), 0.15)' : 'var(--color-bg-input)',
                    border: isSelected ? '1px solid var(--color-primary)' : '1px solid var(--color-border)',
                    transition: 'all 0.2s ease',
                  }}
                >
                  {value}
                </div>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

const SERVICE_GUARANTEES = [
  { icon: '✓', text: '正品保障' },
  { icon: '↻', text: '7天无理由' },
  { icon: '⚡', text: '极速发货' },
  { icon: '🛡', text: '运费险' },
]

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>()
  const [product, setProduct] = useState<Product | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [selectedSku, setSelectedSku] = useState<Sku | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [reviews, setReviews] = useState<ReviewItem[]>([])
  const [reviewStats, setReviewStats] = useState<ReviewStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<'detail' | 'reviews'>('detail')
  const [mainImage, setMainImage] = useState<string>('')

  const addItem = useCartStore((s) => s.addItem)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getProductById(Number(id))
      .then((res) => {
        const prod = res.data.data
        setProduct(prod)
        if (prod.skus?.length > 0) {
          setSelectedSku(prod.skus[0])
        }
        setMainImage(prod.mainImage)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    listCategories()
      .then((res) => setCategories(res.data.data ?? []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!id) return
    getReviewStats(id)
      .then((res) => setReviewStats(res.data.data))
      .catch(() => {})
    fetchReviews(1)
  }, [id])

  const fetchReviews = (page: number) => {
    if (!id) return
    getProductReviews(id, page, 10)
      .then((res) => {
        setReviews(res.data.data ?? [])
      })
      .catch(() => {})
  }

  const handleAddToCart = async () => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      history.push('/login')
      return
    }
    if (!selectedSku || !product) return
    try {
      await addItem(product.id, selectedSku.id, quantity)
    } catch {
      // error handled by store
    }
  }

  const handleBuyNow = async () => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      history.push('/login')
      return
    }
    if (!selectedSku || !product) return
    try {
      await addItem(product.id, selectedSku.id, quantity)
      history.push('/cart')
    } catch {
      // error handled by store
    }
  }

  const findCategoryPath = (catId: number, cats: Category[]): Category[] => {
    const path: Category[] = []
    let current = cats.find((c) => c.id === catId)
    while (current) {
      path.unshift(current)
      current = cats.find((c) => c.id === current!.parentId)
    }
    return path
  }

  if (loading) {
    return (
      <div
        style={{
          background: 'var(--color-bg-base)',
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" />
      </div>
    )
  }

  if (!product) {
    return (
      <div
        style={{
          background: 'var(--color-bg-base)',
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Empty description={<span style={{ color: 'var(--color-text-tertiary)' }}>商品不存在</span>} />
      </div>
    )
  }

  const categoryPath = findCategoryPath(product.categoryId, categories)
  const images = product.skus?.length
    ? [
        product.mainImage,
        ...product.skus
          .map((s) => s.image)
          .filter((img) => img && img !== product.mainImage),
      ]
    : [product.mainImage]

  const minPrice = product.skus?.length
    ? Math.min(...product.skus.map((s) => s.price))
    : 0
  const maxPrice = product.skus?.length
    ? Math.max(...product.skus.map((s) => s.price))
    : 0

  const displayPrice = selectedSku
    ? selectedSku.price.toFixed(2)
    : minPrice === maxPrice
      ? minPrice.toFixed(2)
      : `${minPrice.toFixed(2)} - ${maxPrice.toFixed(2)}`

  return (
    <div style={{ background: 'var(--color-bg-base)', minHeight: '100vh' }}>
      <div style={{ maxWidth: 1320, margin: '0 auto', padding: '24px 32px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 28,
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
          {categoryPath.map((cat) => (
            <span key={cat.id} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ color: 'var(--color-text-tertiary)' }}>/</span>
              <span
                style={{ cursor: 'pointer', color: 'var(--color-text-secondary)' }}
                onClick={() => history.push(`/products?categoryId=${cat.id}`)}
              >
                {cat.name}
              </span>
            </span>
          ))}
          <span style={{ color: 'var(--color-text-tertiary)' }}>/</span>
          <span style={{ color: 'var(--color-text-secondary)' }}>{product.name}</span>
        </div>

        <div style={{ display: 'flex', gap: 40, marginBottom: 48 }}>
          <div style={{ width: 480, flexShrink: 0 }}>
            <div
              style={{
                width: '100%',
                height: 480,
                borderRadius: '16px',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: mainImage
                  ? 'var(--color-bg-footer)'
                  : 'linear-gradient(135deg, var(--color-bg-input) 0%, var(--color-bg-container) 50%, var(--color-bg-elevated) 100%)',
                border: '1px solid var(--color-border)',
                boxShadow: '0 4px 24px rgba(0, 0, 0, 0.3)',
                marginBottom: 16,
              }}
            >
              {mainImage ? (
                <img
                  alt={product.name}
                  src={mainImage}
                  style={{
                    maxWidth: '100%',
                    maxHeight: '100%',
                    objectFit: 'contain',
                  }}
                />
              ) : (
                <div style={{ fontSize: 64, color: 'var(--color-text-tertiary)', opacity: 0.2 }}>▣</div>
              )}
            </div>
            {images.length > 1 && (
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {images.map((img, index) => {
                  const isActive = mainImage === img
                  return (
                    <div
                      key={index}
                      onClick={() => {
                        setMainImage(img)
                        const sku = product.skus?.find((s) => s.image === img)
                        if (sku) setSelectedSku(sku)
                      }}
                      style={{
                        width: 72,
                        height: 72,
                        borderRadius: '6px',
                        overflow: 'hidden',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: 'var(--color-bg-footer)',
                        border: isActive
                          ? '2px solid var(--color-primary)'
                          : '1px solid var(--color-border)',
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        boxShadow: isActive ? '0 0 12px rgba(var(--color-primary-rgb), 0.2)' : 'none',
                      }}
                    >
                      <img
                        alt=""
                        src={img}
                        style={{
                          maxWidth: '100%',
                          maxHeight: '100%',
                          objectFit: 'contain',
                        }}
                      />
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          <div style={{ flex: 1, minWidth: 0 }}>
            <h1
              style={{
                color: 'var(--color-text-secondary)',
                fontSize: 26,
                fontWeight: 600,
                marginBottom: 12,
                lineHeight: 1.4,
              }}
            >
              {product.name}
            </h1>

            <div
              style={{
                background: 'var(--color-bg-footer)',
                borderRadius: '10px',
                border: '1px solid var(--color-border)',
                padding: '20px 24px',
                marginBottom: 24,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
                <span style={{ color: 'var(--color-primary)', fontSize: 32, fontWeight: 700, letterSpacing: '-0.5px' }}>
                  ¥{displayPrice}
                </span>
                {selectedSku && selectedSku.originalPrice > selectedSku.price && (
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 14, textDecoration: 'line-through' }}>
                    ¥{selectedSku.originalPrice.toFixed(2)}
                  </span>
                )}
              </div>
            </div>

            {product.skus?.length > 1 && (
              <SkuSelector
                skus={product.skus}
                selectedSku={selectedSku}
                onSelect={setSelectedSku}
              />
            )}

            <div style={{ marginBottom: 20 }}>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginRight: 12 }}>
                库存
              </span>
              <span style={{ color: selectedSku ? '#FFFFFF' : 'var(--color-text-tertiary)', fontSize: 14 }}>
                {selectedSku ? selectedSku.stock : '请选择规格'}
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                marginBottom: 28,
              }}
            >
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginRight: 4 }}>
                数量
              </span>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  background: 'var(--color-bg-input)',
                  borderRadius: '6px',
                  border: '1px solid var(--color-border)',
                  overflow: 'hidden',
                }}
              >
                <button
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  disabled={quantity <= 1}
                  style={{
                    width: 36,
                    height: 36,
                    border: 'none',
                    background: 'transparent',
                    color: quantity <= 1 ? 'var(--color-text-tertiary)' : '#FFFFFF',
                    cursor: quantity <= 1 ? 'not-allowed' : 'pointer',
                    fontSize: 16,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  −
                </button>
                <InputNumber
                  min={1}
                  max={selectedSku?.stock ?? 999}
                  value={quantity}
                  onChange={(v) => setQuantity(v ?? 1)}
                  controls={false}
                  style={{
                    width: 56,
                    height: 36,
                    background: 'transparent',
                    border: 'none',
                    textAlign: 'center',
                  }}
                />
                <button
                  onClick={() => setQuantity(Math.min(selectedSku?.stock ?? 999, quantity + 1))}
                  style={{
                    width: 36,
                    height: 36,
                    border: 'none',
                    background: 'transparent',
                    color: 'var(--color-text-secondary)',
                    cursor: 'pointer',
                    fontSize: 16,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  +
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 16, marginBottom: 32 }}>
              <button
                onClick={handleAddToCart}
                disabled={!selectedSku || selectedSku.stock <= 0}
                style={{
                  flex: 1,
                  height: 52,
                  borderRadius: '10px',
                  border: 'none',
                  background: 'var(--color-gradient-primary)',
                  color: 'var(--color-bg-base)',
                  fontSize: 16,
                  fontWeight: 700,
                  cursor: !selectedSku || selectedSku.stock <= 0 ? 'not-allowed' : 'pointer',
                  opacity: !selectedSku || selectedSku.stock <= 0 ? 0.5 : 1,
                  transition: 'all 0.2s ease',
                  letterSpacing: '1px',
                  boxShadow: '0 4px 16px rgba(var(--color-primary-rgb), 0.25)',
                }}
              >
                加入购物车
              </button>
              <button
                onClick={handleBuyNow}
                disabled={!selectedSku || selectedSku.stock <= 0}
                style={{
                  flex: 1,
                  height: 52,
                  borderRadius: '10px',
                  border: '1px solid var(--color-primary)',
                  background: 'transparent',
                  color: 'var(--color-primary)',
                  fontSize: 16,
                  fontWeight: 700,
                  cursor: !selectedSku || selectedSku.stock <= 0 ? 'not-allowed' : 'pointer',
                  opacity: !selectedSku || selectedSku.stock <= 0 ? 0.5 : 1,
                  transition: 'all 0.2s ease',
                  letterSpacing: '1px',
                }}
              >
                立即购买
              </button>
            </div>

            <div
              style={{
                display: 'flex',
                gap: 24,
                padding: '16px 0',
                borderTop: '1px solid var(--color-border)',
              }}
            >
              {SERVICE_GUARANTEES.map((item) => (
                <div
                  key={item.text}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    color: 'var(--color-text-tertiary)',
                    fontSize: 12,
                  }}
                >
                  <span style={{ color: 'var(--color-primary)', fontSize: 14 }}>{item.icon}</span>
                  {item.text}
                </div>
              ))}
            </div>
          </div>
        </div>

        <div
          style={{
            background: 'var(--color-bg-container)',
            borderRadius: '16px',
            border: '1px solid var(--color-border)',
            overflow: 'hidden',
          }}
        >
          <div style={{ display: 'flex', borderBottom: '1px solid var(--color-border)' }}>
            <div
              onClick={() => setActiveTab('detail')}
              style={{
                padding: '16px 32px',
                cursor: 'pointer',
                fontSize: 15,
                fontWeight: activeTab === 'detail' ? 600 : 400,
                color: activeTab === 'detail' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                borderBottom: activeTab === 'detail' ? '2px solid var(--color-primary)' : '2px solid transparent',
                transition: 'all 0.2s ease',
              }}
            >
              商品详情
            </div>
            <div
              onClick={() => setActiveTab('reviews')}
              style={{
                padding: '16px 32px',
                cursor: 'pointer',
                fontSize: 15,
                fontWeight: activeTab === 'reviews' ? 600 : 400,
                color: activeTab === 'reviews' ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                borderBottom: activeTab === 'reviews' ? '2px solid var(--color-primary)' : '2px solid transparent',
                transition: 'all 0.2s ease',
              }}
            >
              商品评价
              {reviewStats && reviewStats.totalReviews > 0 && (
                <span style={{ marginLeft: 6, fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                  ({reviewStats.totalReviews})
                </span>
              )}
            </div>
          </div>

          <div style={{ padding: '32px' }}>
            {activeTab === 'detail' && (
              <>
                {product.description ? (
                  <div
                    dangerouslySetInnerHTML={{
                      __html: DOMPurify.sanitize(product.description),
                    }}
                    style={{
                      color: 'var(--color-text-secondary)',
                      lineHeight: 1.8,
                      fontSize: 14,
                    }}
                  />
                ) : (
                  <Empty
                    description={<span style={{ color: 'var(--color-text-tertiary)' }}>暂无详情</span>}
                    style={{ padding: '60px 0' }}
                  />
                )}
              </>
            )}

            {activeTab === 'reviews' && (
              <>
                {reviewStats && reviewStats.totalReviews > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 24,
                      marginBottom: 28,
                      padding: '20px 24px',
                      background: 'var(--color-bg-footer)',
                      borderRadius: '10px',
                      border: '1px solid var(--color-border)',
                    }}
                  >
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ color: 'var(--color-primary)', fontSize: 36, fontWeight: 700 }}>
                        {reviewStats.averageRating.toFixed(1)}
                      </div>
                      <Rate disabled value={reviewStats.averageRating} allowHalf style={{ fontSize: 14 }} />
                    </div>
                    <div style={{ flex: 1 }}>
                      {[
                        { label: '5星', count: reviewStats.fiveStarCount },
                        { label: '4星', count: reviewStats.fourStarCount },
                        { label: '3星', count: reviewStats.threeStarCount },
                        { label: '2星', count: reviewStats.twoStarCount },
                        { label: '1星', count: reviewStats.oneStarCount },
                      ].map((item) => (
                        <div key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12, width: 28 }}>{item.label}</span>
                          <div
                            style={{
                              flex: 1,
                              height: 6,
                              background: 'var(--color-bg-input)',
                              borderRadius: 3,
                              overflow: 'hidden',
                            }}
                          >
                            <div
                              style={{
                                height: '100%',
                                width: `${reviewStats.totalReviews > 0 ? (item.count / reviewStats.totalReviews) * 100 : 0}%`,
                                background: 'var(--color-gradient-primary)',
                                borderRadius: 3,
                                transition: 'width 0.3s ease',
                              }}
                            />
                          </div>
                          <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12, width: 28, textAlign: 'right' }}>
                            {item.count}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {reviews.length === 0 ? (
                  <Empty
                    description={<span style={{ color: 'var(--color-text-tertiary)' }}>暂无评价</span>}
                    style={{ padding: '60px 0' }}
                  />
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                    {reviews.map((review) => (
                      <div
                        key={review.id}
                        style={{
                          padding: '20px',
                          background: 'var(--color-bg-footer)',
                          borderRadius: '10px',
                          border: '1px solid var(--color-border)',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                          <div
                            style={{
                              width: 36,
                              height: 36,
                              borderRadius: '50%',
                              background: 'rgba(var(--color-primary-rgb), 0.15)',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              color: 'var(--color-primary)',
                              fontSize: 14,
                              fontWeight: 600,
                            }}
                          >
                            {review.username.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, fontWeight: 500 }}>
                              {review.username}
                            </div>
                            <Rate disabled value={review.rating} style={{ fontSize: 11 }} />
                          </div>
                          <div style={{ marginLeft: 'auto', color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                            {review.createdAt}
                          </div>
                        </div>
                        <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, lineHeight: 1.6, marginBottom: 8 }}>
                          {review.content}
                        </div>
                        {review.images?.length > 0 && (
                          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                            {review.images.map((img, idx) => (
                              <img
                                key={idx}
                                src={img}
                                alt=""
                                style={{
                                  width: 72,
                                  height: 72,
                                  objectFit: 'cover',
                                  borderRadius: '6px',
                                  border: '1px solid var(--color-border)',
                                }}
                              />
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>

      <style>{`
        .ant-rate-star-full .ant-rate-star-first,
        .ant-rate-star-full .ant-rate-star-second {
          color: var(--color-primary) !important;
        }
        .ant-rate-star-half .ant-rate-star-first {
          color: var(--color-primary) !important;
        }
        .ant-rate-star-half .ant-rate-star-second {
          color: var(--color-border) !important;
        }
        .ant-rate-star-zero .ant-rate-star-first,
        .ant-rate-star-zero .ant-rate-star-second {
          color: var(--color-border) !important;
        }
        .ant-input-number-input {
          background: transparent !important;
          color: #FFFFFF !important;
          text-align: center !important;
        }
        .ant-input-number {
          background: transparent !important;
        }
        .ant-spin-text { color: var(--color-text-secondary) !important; }
        .ant-empty-description { color: var(--color-text-tertiary) !important; }
        .ant-message-notice-content {
          background: var(--color-bg-container) !important;
          color: #FFFFFF !important;
          border: 1px solid var(--color-border) !important;
        }
      `}</style>
    </div>
  )
}
