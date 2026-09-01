import { useState, useEffect, useCallback } from 'react'
import { Spin, message, Popconfirm } from 'antd'
import {
  HeartOutlined,
  HeartFilled,
  DeleteOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { getWishlistList, removeWishlist } from '@/api/wishlist'
import { addToCart } from '@/api/cart'
import type { WishlistItem } from '@/api/wishlist'

const cssVars = {
  '--color-bg-primary': 'var(--color-bg-base)',
  '--color-bg-secondary': 'var(--color-bg-footer)',
  '--color-bg-card': 'var(--color-bg-container)',
  '--color-bg-hover': 'var(--color-bg-elevated)',
  '--color-accent': 'var(--color-primary)',
  '--color-accent-hover': '#33DDFF',
  '--color-accent-dim': 'rgba(var(--color-primary-rgb), 0.15)',
  '--color-accent-glow': 'rgba(var(--color-primary-rgb), 0.3)',
  '--color-text-primary': '#FFFFFF',
  '--color-text-secondary': 'var(--color-text-secondary)',
  '--color-text-tertiary': 'var(--color-text-tertiary)',
  '--color-border': 'var(--color-border)',
  '--color-border-hover': 'rgba(var(--color-primary-rgb), 0.3)',
  '--gradient-accent': 'var(--color-gradient-primary)',
  '--shadow-card': '0 4px 24px rgba(0,0,0,0.3)',
  '--shadow-hover': '0 8px 40px rgba(0,0,0,0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)',
  '--radius-sm': '6px',
  '--radius-md': '10px',
  '--radius-lg': '16px',
} as React.CSSProperties

function Checkbox({ checked, indeterminate, onChange }: { checked: boolean; indeterminate?: boolean; onChange: (checked: boolean) => void }) {
  return (
    <div
      onClick={() => onChange(!checked)}
      style={{
        width: 18,
        height: 18,
        borderRadius: 4,
        border: checked
          ? '2px solid var(--color-primary)'
          : indeterminate
            ? '2px solid var(--color-primary)'
            : '2px solid rgba(255,255,255,0.2)',
        background: checked
          ? 'var(--color-gradient-primary)'
          : indeterminate
            ? 'rgba(var(--color-primary-rgb), 0.15)'
            : 'transparent',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        flexShrink: 0,
      }}
    >
      {checked && (
        <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
          <path d="M1 4L3.5 6.5L9 1" stroke="var(--color-bg-base)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
      {indeterminate && !checked && (
        <div style={{ width: 8, height: 2, background: 'var(--color-primary)', borderRadius: 1 }} />
      )}
    </div>
  )
}

function WishlistCard({
  item,
  selected,
  onSelect,
  onRemove,
  onAddToCart,
}: {
  item: WishlistItem
  selected: boolean
  onSelect: (checked: boolean) => void
  onRemove: () => void
  onAddToCart: () => void
}) {
  const [hovered, setHovered] = useState(false)
  const [addingToCart, setAddingToCart] = useState(false)
  const isDiscounted = item.minPrice < ((item as WishlistItem & { originalPrice?: number }).originalPrice ?? Infinity)

  const handleAddToCart = async () => {
    setAddingToCart(true)
    try {
      await onAddToCart()
    } finally {
      setAddingToCart(false)
    }
  }

  return (
    <div
      style={{
        background: hovered ? 'var(--color-bg-elevated)' : 'var(--color-bg-container)',
        borderRadius: 14,
        border: `1px solid ${hovered ? 'rgba(var(--color-primary-rgb), 0.2)' : 'var(--color-border)'}`,
        transition: 'all 0.3s ease',
        boxShadow: hovered ? '0 8px 40px rgba(0,0,0,0.4), 0 0 20px rgba(var(--color-primary-rgb), 0.08)' : '0 4px 24px rgba(0,0,0,0.3)',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div style={{ position: 'relative', paddingTop: '100%', background: 'var(--color-bg-footer)' }}>
        <img
          alt={item.productName}
          src={item.mainImage}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            cursor: 'pointer',
          }}
          onClick={() => history.push(`/products/${item.productId}`)}
        />
        <div
          style={{
            position: 'absolute',
            top: 10,
            left: 10,
          }}
        >
          <Checkbox checked={selected} onChange={onSelect} />
        </div>
        <div
          style={{
            position: 'absolute',
            top: 10,
            right: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 32,
            height: 32,
            borderRadius: '50%',
            background: 'rgba(0,0,0,0.4)',
            backdropFilter: 'blur(4px)',
          }}
        >
          <HeartFilled style={{ color: '#FF6B6B', fontSize: 14 }} />
        </div>
      </div>

      <div style={{ padding: '14px 16px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            fontWeight: 500,
            lineHeight: 1.5,
            height: 42,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            cursor: 'pointer',
            marginBottom: 8,
          }}
          onClick={() => history.push(`/products/${item.productId}`)}
        >
          {item.productName}
        </div>

        {item.brand && (
          <div
            style={{
              display: 'inline-block',
              padding: '1px 8px',
              background: 'rgba(var(--color-primary-rgb), 0.08)',
              borderRadius: 4,
              color: 'var(--color-text-secondary)',
              fontSize: 11,
              marginBottom: 8,
              alignSelf: 'flex-start',
            }}
          >
            {item.brand}
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 12, marginTop: 'auto' }}>
          <span style={{ color: 'var(--color-primary)', fontWeight: 700, fontSize: 18 }}>
            ¥{item.minPrice.toFixed(2)}
          </span>
          {isDiscounted && (
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 13, textDecoration: 'line-through' }}>
              ¥{(item as WishlistItem & { originalPrice?: number }).originalPrice!.toFixed(2)}
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <button
            type="button"
            onClick={handleAddToCart}
            disabled={addingToCart}
            style={{
              flex: 1,
              padding: '8px 0',
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--color-bg-base)',
              background: 'var(--color-gradient-primary)',
              border: 'none',
              borderRadius: 8,
              cursor: addingToCart ? 'wait' : 'pointer',
              transition: 'all 0.2s ease',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 4,
            }}
            onMouseEnter={(e) => {
              if (!addingToCart) {
                e.currentTarget.style.boxShadow = '0 0 16px rgba(var(--color-primary-rgb), 0.4)'
                e.currentTarget.style.transform = 'translateY(-1px)'
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.boxShadow = 'none'
              e.currentTarget.style.transform = 'translateY(0)'
            }}
          >
            <ShoppingCartOutlined style={{ fontSize: 13 }} />
            {addingToCart ? '添加中...' : '加入购物车'}
          </button>

          <Popconfirm
            title="确定要删除吗？"
            onConfirm={onRemove}
            okText="确定"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <button
              type="button"
              style={{
                width: 36,
                height: 36,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'transparent',
                border: '1px solid var(--color-border)',
                borderRadius: 8,
                color: 'var(--color-text-tertiary)',
                cursor: 'pointer',
                transition: 'all 0.2s',
                flexShrink: 0,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = 'rgba(255,77,79,0.5)'
                e.currentTarget.style.color = '#ff4d4f'
                e.currentTarget.style.background = 'rgba(255,77,79,0.1)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--color-border)'
                e.currentTarget.style.color = 'var(--color-text-tertiary)'
                e.currentTarget.style.background = 'transparent'
              }}
            >
              <DeleteOutlined style={{ fontSize: 14 }} />
            </button>
          </Popconfirm>
        </div>
      </div>
    </div>
  )
}

function EmptyWishlist() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '100px 24px',
        background: 'var(--color-bg-container)',
        borderRadius: 16,
        border: '1px solid var(--color-border)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
      }}
    >
      <div
        style={{
          width: 120,
          height: 120,
          borderRadius: '50%',
          background: 'rgba(var(--color-primary-rgb), 0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 24,
        }}
      >
        <HeartOutlined style={{ fontSize: 48, color: 'rgba(var(--color-primary-rgb), 0.4)' }} />
      </div>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 500, marginBottom: 8 }}>
        心愿单是空的，去商城逛逛吧
      </div>
      <div style={{ color: 'var(--color-text-tertiary)', fontSize: 14, marginBottom: 32 }}>
        收藏喜欢的商品，随时查看价格变动
      </div>
      <button
        type="button"
        onClick={() => history.push('/products')}
        style={{
          padding: '12px 40px',
          fontSize: 15,
          fontWeight: 600,
          color: 'var(--color-bg-base)',
          background: 'var(--color-gradient-primary)',
          border: 'none',
          borderRadius: 50,
          cursor: 'pointer',
          boxShadow: '0 0 24px rgba(var(--color-primary-rgb), 0.3)',
          transition: 'all 0.3s ease',
          letterSpacing: 1,
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.boxShadow = '0 0 36px rgba(var(--color-primary-rgb), 0.5)'
          e.currentTarget.style.transform = 'translateY(-2px)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.boxShadow = '0 0 24px rgba(var(--color-primary-rgb), 0.3)'
          e.currentTarget.style.transform = 'translateY(0)'
        }}
      >
        去逛逛
      </button>
    </div>
  )
}

export default function Wishlist() {
  const [items, setItems] = useState<WishlistItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const pageSize = 10

  const loadWishlist = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getWishlistList(page, pageSize)
      const data = res.data.data ?? []
      if (page === 1) {
        setItems(data)
      } else {
        setItems((prev) => [...prev, ...data])
      }
      setTotal(res.data.meta?.total ?? 0)
    } catch {
      message.error('加载心愿单失败')
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    loadWishlist()
  }, [loadWishlist])

  const isAllSelected = items.length > 0 && selectedIds.length === items.length
  const hasMore = items.length < total

  const handleSelectAll = (checked: boolean) => {
    setSelectedIds(checked ? items.map((item) => item.id) : [])
  }

  const handleSelectItem = (id: number, checked: boolean) => {
    setSelectedIds((prev) =>
      checked ? [...prev, id] : prev.filter((itemId) => itemId !== id)
    )
  }

  const handleRemove = async (productId: number) => {
    try {
      await removeWishlist(productId)
      message.success('已从心愿单移除')
      setItems((prev) => prev.filter((item) => item.productId !== productId))
      setSelectedIds((prev) => {
        const target = items.find((item) => item.productId === productId)
        return target ? prev.filter((id) => id !== target.id) : prev
      })
      setTotal((prev) => prev - 1)
    } catch {
      message.error('移除失败')
    }
  }

  const handleBatchRemove = async () => {
    const selectedItems = items.filter((item) => selectedIds.includes(item.id))
    if (selectedItems.length === 0) return
    try {
      await Promise.all(selectedItems.map((item) => removeWishlist(item.productId)))
      message.success(`已移除 ${selectedItems.length} 件商品`)
      setItems((prev) => prev.filter((item) => !selectedIds.includes(item.id)))
      setTotal((prev) => prev - selectedItems.length)
      setSelectedIds([])
    } catch {
      message.error('批量移除失败')
    }
  }

  const handleAddToCart = async (item: WishlistItem) => {
    try {
      await addToCart({ productId: item.productId, skuId: 0, quantity: 1 })
      message.success('已加入购物车')
    } catch {
      message.error('加入购物车失败')
    }
  }

  const handleLoadMore = () => {
    setPage((prev) => prev + 1)
  }

  return (
    <div style={{ ...cssVars, background: 'var(--color-bg-base)', minHeight: '100vh', padding: '32px 24px' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 32 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <HeartFilled style={{ fontSize: 24, color: '#FF6B6B' }} />
            <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, color: 'var(--color-text-secondary)' }}>
              我的心愿单
            </h1>
            {total > 0 && (
              <span
                style={{
                  padding: '2px 12px',
                  background: 'rgba(255,107,107,0.15)',
                  borderRadius: 20,
                  color: '#FF6B6B',
                  fontSize: 13,
                  fontWeight: 600,
                }}
              >
                {total} 件
              </span>
            )}
          </div>
        </div>

        <Spin spinning={loading && page === 1}>
          {items.length === 0 && !loading ? (
            <EmptyWishlist />
          ) : (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  marginBottom: 20,
                  padding: '12px 20px',
                  background: 'var(--color-bg-container)',
                  borderRadius: 10,
                  border: '1px solid var(--color-border)',
                }}
              >
                <Checkbox
                  checked={isAllSelected}
                  indeterminate={selectedIds.length > 0 && selectedIds.length < items.length}
                  onChange={handleSelectAll}
                />
                <span style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>全选</span>
                {selectedIds.length > 0 && (
                  <Popconfirm
                    title={`确定要删除选中的 ${selectedIds.length} 件商品吗？`}
                    onConfirm={handleBatchRemove}
                    okText="确定"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                  >
                    <button
                      type="button"
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#ff4d4f',
                        fontSize: 13,
                        cursor: 'pointer',
                        padding: '4px 12px',
                        borderRadius: 6,
                        transition: 'all 0.2s',
                        fontWeight: 500,
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.background = 'rgba(255,77,79,0.1)'
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.background = 'none'
                      }}
                    >
                      删除选中 ({selectedIds.length})
                    </button>
                  </Popconfirm>
                )}
              </div>

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(2, 1fr)',
                  gap: 16,
                }}
              >
                {items.map((item) => (
                  <WishlistCard
                    key={item.id}
                    item={item}
                    selected={selectedIds.includes(item.id)}
                    onSelect={(checked) => handleSelectItem(item.id, checked)}
                    onRemove={() => handleRemove(item.productId)}
                    onAddToCart={() => handleAddToCart(item)}
                  />
                ))}
              </div>

              {hasMore && (
                <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
                  <button
                    type="button"
                    onClick={handleLoadMore}
                    disabled={loading}
                    style={{
                      padding: '10px 32px',
                      border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
                      borderRadius: 8,
                      background: 'rgba(var(--color-primary-rgb), 0.06)',
                      color: 'var(--color-primary)',
                      fontSize: 14,
                      fontWeight: 500,
                      cursor: loading ? 'not-allowed' : 'pointer',
                      transition: 'all 0.25s ease',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      opacity: loading ? 0.5 : 1,
                    }}
                    onMouseEnter={(e) => {
                      if (!loading) {
                        e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.12)'
                        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.5)'
                        e.currentTarget.style.boxShadow = '0 2px 12px rgba(var(--color-primary-rgb), 0.15)'
                      }
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.06)'
                      e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
                      e.currentTarget.style.boxShadow = 'none'
                    }}
                  >
                    <ShoppingOutlined style={{ fontSize: 14 }} />
                    加载更多
                  </button>
                </div>
              )}
            </>
          )}
        </Spin>
      </div>
    </div>
  )
}
