import { useState, useEffect } from 'react'
import { Spin } from 'antd'
import { message } from '@/utils/appMessage'
import { DeleteOutlined, ShoppingCartOutlined, PlusOutlined, MinusOutlined, ShoppingOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { useCartStore } from '@/stores/cart'
import { useThemeStore } from '@/stores/theme'
import { getThemeTokens } from '@/theme/tokens'
import type { ThemeTokens } from '@/theme/tokens'
import type { CartItem } from '@/types'

function Checkbox({ checked, indeterminate, onChange, tokens }: { checked: boolean; indeterminate?: boolean; onChange: (checked: boolean) => void; tokens: ThemeTokens }) {
  return (
    <div
      onClick={() => onChange(!checked)}
      style={{
        width: 18,
        height: 18,
        borderRadius: 4,
        border: checked
          ? `2px solid ${tokens.colorPrimary}`
          : indeterminate
            ? `2px solid ${tokens.colorPrimary}`
            : `2px solid ${tokens.isDark ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.15)'}`,
        background: checked
          ? tokens.colorGradientPrimary
          : indeterminate
            ? `rgba(${tokens.colorPrimaryRgb}, 0.15)`
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
          <path d="M1 4L3.5 6.5L9 1" stroke={tokens.colorBgBase} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
      {indeterminate && !checked && (
        <div style={{ width: 8, height: 2, background: tokens.colorPrimary, borderRadius: 1 }} />
      )}
    </div>
  )
}

function QuantityControl({ value, onChange, tokens }: { value: number; onChange: (val: number) => void; tokens: ThemeTokens }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 0, borderRadius: 6, overflow: 'hidden', border: `1px solid ${tokens.colorBorder}` }}>
      <button
        type="button"
        onClick={() => value > 1 && onChange(value - 1)}
        style={{
          width: 32,
          height: 32,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: tokens.colorBgFooter,
          border: 'none',
          color: value > 1 ? tokens.colorTextSecondary : tokens.colorTextTertiary,
          cursor: value > 1 ? 'pointer' : 'not-allowed',
          fontSize: 12,
          transition: 'all 0.2s',
        }}
        onMouseEnter={(e) => { if (value > 1) e.currentTarget.style.background = tokens.colorBgElevated }}
        onMouseLeave={(e) => { e.currentTarget.style.background = tokens.colorBgFooter }}
      >
        <MinusOutlined />
      </button>
      <div
        style={{
          width: 44,
          height: 32,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: tokens.colorBgBase,
          color: tokens.colorTextSecondary,
          fontSize: 14,
          fontWeight: 500,
          borderTop: 'none',
          borderBottom: 'none',
        }}
      >
        {value}
      </div>
      <button
        type="button"
        onClick={() => value < 99 && onChange(value + 1)}
        style={{
          width: 32,
          height: 32,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: tokens.colorBgFooter,
          border: 'none',
          color: value < 99 ? tokens.colorTextSecondary : tokens.colorTextTertiary,
          cursor: value < 99 ? 'pointer' : 'not-allowed',
          fontSize: 12,
          transition: 'all 0.2s',
        }}
        onMouseEnter={(e) => { if (value < 99) e.currentTarget.style.background = tokens.colorBgElevated }}
        onMouseLeave={(e) => { e.currentTarget.style.background = tokens.colorBgFooter }}
      >
        <PlusOutlined />
      </button>
    </div>
  )
}

function CartItemRow({
  item,
  selected,
  onSelect,
  onQuantityChange,
  onRemove,
  tokens,
}: {
  item: CartItem
  selected: boolean
  onSelect: (checked: boolean) => void
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
  tokens: ThemeTokens
}) {
  const [hovered, setHovered] = useState(false)

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '20px 24px',
        background: hovered ? tokens.colorBgElevated : tokens.colorBgContainer,
        borderRadius: 10,
        border: `1px solid ${hovered ? `rgba(${tokens.colorPrimaryRgb}, 0.2)` : tokens.colorBorder}`,
        transition: 'all 0.3s ease',
        boxShadow: hovered
          ? `0 8px 40px rgba(0,0,0,0.4), 0 0 20px rgba(${tokens.colorPrimaryRgb}, 0.08)`
          : '0 4px 24px rgba(0,0,0,0.3)',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <Checkbox checked={selected} onChange={onSelect} tokens={tokens} />

      <img
        alt={item.productName}
        src={item.skuImage}
        style={{
          width: 80,
          height: 80,
          objectFit: 'cover',
          borderRadius: 8,
          background: tokens.colorBgFooter,
          cursor: 'pointer',
          flexShrink: 0,
        }}
        onClick={() => history.push(`/products/${item.productId}`)}
      />

      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            color: tokens.colorTextSecondary,
            fontSize: 15,
            fontWeight: 500,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            cursor: 'pointer',
            marginBottom: 4,
          }}
          onClick={() => history.push(`/products/${item.productId}`)}
        >
          {item.productName}
        </div>
        {item.skuAttributes && (
          <div
            style={{
              display: 'inline-block',
              padding: '2px 10px',
              background: `rgba(${tokens.colorPrimaryRgb}, 0.08)`,
              borderRadius: 4,
              color: tokens.colorTextSecondary,
              fontSize: 12,
            }}
          >
            {item.skuAttributes}
          </div>
        )}
      </div>

      <div style={{ width: 100, textAlign: 'center', flexShrink: 0 }}>
        <span style={{ color: tokens.colorPrimary, fontWeight: 600, fontSize: 15 }}>
          ¥{(item.price ?? 0).toFixed(2)}
        </span>
      </div>

      <div style={{ flexShrink: 0 }}>
        <QuantityControl value={item.quantity} onChange={onQuantityChange} tokens={tokens} />
      </div>

      <div style={{ width: 110, textAlign: 'right', flexShrink: 0 }}>
        <span style={{ color: tokens.colorTextSecondary, fontWeight: 700, fontSize: 16 }}>
          ¥{((item.price ?? 0) * item.quantity).toFixed(2)}
        </span>
      </div>

      <button
        type="button"
        onClick={onRemove}
        style={{
          width: 36,
          height: 36,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'transparent',
          border: `1px solid ${tokens.colorBorder}`,
          borderRadius: 8,
          color: tokens.colorTextTertiary,
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
          e.currentTarget.style.borderColor = tokens.colorBorder
          e.currentTarget.style.color = tokens.colorTextTertiary
          e.currentTarget.style.background = 'transparent'
        }}
      >
        <DeleteOutlined style={{ fontSize: 14 }} />
      </button>
    </div>
  )
}

function EmptyCart({ tokens }: { tokens: ThemeTokens }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '100px 24px',
        background: tokens.colorBgContainer,
        borderRadius: 16,
        border: `1px solid ${tokens.colorBorder}`,
        boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
      }}
    >
      <div
        style={{
          width: 120,
          height: 120,
          borderRadius: '50%',
          background: `rgba(${tokens.colorPrimaryRgb}, 0.08)`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 24,
        }}
      >
        <ShoppingCartOutlined style={{ fontSize: 48, color: `rgba(${tokens.colorPrimaryRgb}, 0.4)` }} />
      </div>
      <div style={{ color: tokens.colorTextSecondary, fontSize: 18, fontWeight: 500, marginBottom: 8 }}>
        购物车是空的
      </div>
      <div style={{ color: tokens.colorTextTertiary, fontSize: 14, marginBottom: 32 }}>
        去发现心仪的好物吧
      </div>
      <button
        type="button"
        onClick={() => history.push('/products')}
        style={{
          padding: '12px 40px',
          fontSize: 15,
          fontWeight: 600,
          color: tokens.colorBgBase,
          background: tokens.colorGradientPrimary,
          border: 'none',
          borderRadius: 50,
          cursor: 'pointer',
          boxShadow: `0 0 24px rgba(${tokens.colorPrimaryRgb}, 0.3)`,
          transition: 'all 0.3s ease',
          letterSpacing: 1,
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.boxShadow = `0 0 36px rgba(${tokens.colorPrimaryRgb}, 0.5)`
          e.currentTarget.style.transform = 'translateY(-2px)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.boxShadow = `0 0 24px rgba(${tokens.colorPrimaryRgb}, 0.3)`
          e.currentTarget.style.transform = 'translateY(0)'
        }}
      >
        去逛逛
      </button>
    </div>
  )
}

export default function Cart() {
  const { items, loading, fetchCart, updateItem, removeItem, clearCheckedItems } = useCartStore()
  const [selectedSkuIds, setSelectedSkuIds] = useState<number[]>([])
  const { mode } = useThemeStore()
  const tokens = getThemeTokens(mode)

  useEffect(() => {
    fetchCart()
  }, [fetchCart])

  const checkedItems = items.filter((item) => selectedSkuIds.includes(item.skuId))
  const totalPrice = checkedItems.reduce((sum, item) => sum + (item.price ?? 0) * item.quantity, 0)
  const totalQuantity = checkedItems.reduce((sum, item) => sum + item.quantity, 0)
  const isAllSelected = items.length > 0 && selectedSkuIds.length === items.length

  const handleSelectAll = (checked: boolean) => {
    setSelectedSkuIds(checked ? items.map((item) => item.skuId) : [])
  }

  const handleSelectItem = (skuId: number, checked: boolean) => {
    setSelectedSkuIds((prev) =>
      checked ? [...prev, skuId] : prev.filter((id) => id !== skuId)
    )
  }

  const handleQuantityChange = async (skuId: number, quantity: number) => {
    if (quantity < 1) return
    try {
      await updateItem(skuId, { quantity })
    } catch {
      message.error('更新数量失败')
    }
  }

  const handleRemove = async (skuId: number) => {
    try {
      await removeItem(skuId)
      setSelectedSkuIds((prev) => prev.filter((id) => id !== skuId))
      message.success('已删除')
    } catch {
      message.error('删除失败')
    }
  }

  const handleClearChecked = async () => {
    try {
      await clearCheckedItems()
      setSelectedSkuIds([])
      message.success('已删除选中商品')
    } catch {
      message.error('删除失败')
    }
  }

  const handleCheckout = () => {
    if (checkedItems.length === 0) {
      message.warning('请选择要结算的商品')
      return
    }
    const skuIds = checkedItems.map((item) => item.skuId).join(',')
    history.push(`/checkout?skuIds=${skuIds}`)
  }

  return (
    <div style={{ background: tokens.colorBgBase, minHeight: '100vh', padding: '32px 24px', color: tokens.colorTextSecondary }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <ShoppingOutlined style={{ fontSize: 24, color: tokens.colorPrimary }} />
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700, color: tokens.colorTextSecondary }}>
            我的购物车
          </h1>
          {items.length > 0 && (
            <span
              style={{
                padding: '2px 12px',
                background: `rgba(${tokens.colorPrimaryRgb}, 0.15)`,
                borderRadius: 20,
                color: tokens.colorPrimary,
                fontSize: 13,
                fontWeight: 600,
              }}
            >
              {items.length} 件
            </span>
          )}
        </div>

        <Spin spinning={loading}>
          {items.length === 0 && !loading ? (
            <EmptyCart tokens={tokens} />
          ) : (
            <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '0 24px 16px',
                  }}
                >
                  <Checkbox
                    checked={isAllSelected}
                    indeterminate={selectedSkuIds.length > 0 && selectedSkuIds.length < items.length}
                    onChange={handleSelectAll}
                    tokens={tokens}
                  />
                  <span style={{ color: tokens.colorTextSecondary, fontSize: 14 }}>全选</span>
                  {selectedSkuIds.length > 0 && (
                    <button
                      type="button"
                      onClick={handleClearChecked}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: tokens.colorTextTertiary,
                        fontSize: 13,
                        cursor: 'pointer',
                        padding: '4px 12px',
                        borderRadius: 6,
                        transition: 'all 0.2s',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.color = '#ff4d4f'
                        e.currentTarget.style.background = 'rgba(255,77,79,0.1)'
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.color = tokens.colorTextTertiary
                        e.currentTarget.style.background = 'none'
                      }}
                    >
                      删除选中
                    </button>
                  )}
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {items.map((item) => (
                    <CartItemRow
                      key={item.skuId}
                      item={item}
                      selected={selectedSkuIds.includes(item.skuId)}
                      onSelect={(checked) => handleSelectItem(item.skuId, checked)}
                      onQuantityChange={(qty) => handleQuantityChange(item.skuId, qty)}
                      onRemove={() => handleRemove(item.skuId)}
                      tokens={tokens}
                    />
                  ))}
                </div>
              </div>

              <div
                style={{
                  width: 320,
                  flexShrink: 0,
                  position: 'sticky',
                  top: 80,
                  background: tokens.colorBgContainer,
                  borderRadius: 16,
                  border: `1px solid ${tokens.colorBorder}`,
                  boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    padding: '24px 24px 0',
                    borderBottom: `1px solid ${tokens.colorBorder}`,
                  }}
                >
                  <div style={{ color: tokens.colorTextSecondary, fontSize: 18, fontWeight: 600, marginBottom: 20 }}>
                    订单摘要
                  </div>
                </div>

                <div style={{ padding: '20px 24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                    <span style={{ color: tokens.colorTextSecondary, fontSize: 14 }}>已选商品</span>
                    <span style={{ color: tokens.colorTextSecondary, fontSize: 14, fontWeight: 500 }}>
                      {totalQuantity} 件
                    </span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
                    <span style={{ color: tokens.colorTextSecondary, fontSize: 14 }}>商品金额</span>
                    <span style={{ color: tokens.colorTextSecondary, fontSize: 14, fontWeight: 500 }}>
                      ¥{totalPrice.toFixed(2)}
                    </span>
                  </div>

                  <div
                    style={{
                      height: 1,
                      background: tokens.colorBorder,
                      margin: '0 -24px 20px',
                    }}
                  />

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 24 }}>
                    <span style={{ color: tokens.colorTextSecondary, fontSize: 16, fontWeight: 600 }}>合计</span>
                    <span style={{ color: tokens.colorPrimary, fontSize: 28, fontWeight: 800 }}>
                      ¥{totalPrice.toFixed(2)}
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={handleCheckout}
                    disabled={checkedItems.length === 0}
                    style={{
                      width: '100%',
                      padding: '14px 0',
                      fontSize: 16,
                      fontWeight: 700,
                      color: checkedItems.length === 0 ? tokens.colorTextTertiary : tokens.colorBgBase,
                      background: checkedItems.length === 0
                        ? tokens.colorBgFooter
                        : tokens.colorGradientPrimary,
                      border: 'none',
                      borderRadius: 10,
                      cursor: checkedItems.length === 0 ? 'not-allowed' : 'pointer',
                      boxShadow: checkedItems.length === 0
                        ? 'none'
                        : `0 0 24px rgba(${tokens.colorPrimaryRgb}, 0.3)`,
                      transition: 'all 0.3s ease',
                      letterSpacing: 2,
                    }}
                    onMouseEnter={(e) => {
                      if (checkedItems.length > 0) {
                        e.currentTarget.style.boxShadow = `0 0 36px rgba(${tokens.colorPrimaryRgb}, 0.5)`
                        e.currentTarget.style.transform = 'translateY(-1px)'
                      }
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.boxShadow = checkedItems.length === 0
                        ? 'none'
                        : `0 0 24px rgba(${tokens.colorPrimaryRgb}, 0.3)`
                      e.currentTarget.style.transform = 'translateY(0)'
                    }}
                  >
                    去结算
                  </button>
                </div>
              </div>
            </div>
          )}
        </Spin>
      </div>
    </div>
  )
}
