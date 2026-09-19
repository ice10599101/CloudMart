import { useState, useEffect, useMemo } from 'react'
import { View, Text, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { userApi } from '@/api/user'
import { cartApi } from '@/api/cart'
import { productApi } from '@/api/product'
import { marketingApi } from '@/api/marketing'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Product, UserCoupon } from '@/types'
import styles from './index.module.scss'

interface Address {
  id: number
  name: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
}

interface CheckoutItem {
  productId: number
  skuId: number
  productName: string
  productImage: string
  skuName?: string
  price: number
  quantity: number
}

/** 券抵扣金额（契约对齐后端 Discount：AMOUNT_OFF 直减；PERCENT_OFF=金额×(1-折扣率)） */
function calcCouponDiscount(coupon: UserCoupon, subtotal: number): number {
  if (subtotal < coupon.thresholdAmount) return 0
  if (coupon.templateType === 'AMOUNT_OFF' && coupon.discountAmount != null) return coupon.discountAmount
  if (coupon.templateType === 'PERCENT_OFF' && coupon.discountRate != null) {
    return Math.round(subtotal * (1 - coupon.discountRate) * 100) / 100
  }
  return 0
}

export default function CheckoutPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const params = Taro.getCurrentInstance().router?.params ?? {}
  const directProductId = params.productId
  const directSkuId = params.skuId
  const directQuantity = Number(params.quantity ?? 1)
  const isDirectBuy = !!directProductId

  const [address, setAddress] = useState<Address | null>(null)
  const [items, setItems] = useState<CheckoutItem[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [coupons, setCoupons] = useState<UserCoupon[]>([])
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null)
  const [couponOpen, setCouponOpen] = useState(false)
  useAuthGuard()

  useEffect(() => {
    initData()
  }, [])

  const initData = async () => {
    setLoading(true)
    try {
      const addrRes = await userApi.getDefaultAddress().catch(() => null)
      setAddress(addrRes?.data?.data || null)

      if (isDirectBuy) {
        // 直购模式：拉商品详情，用指定 SKU/数量
        const prodRes = await productApi.getDetail(directProductId)
        const product = prodRes.data?.data as Product | undefined
        if (product) {
          const sku =
            product.skus?.find((s) => String(s.id) === String(directSkuId)) ?? product.skus?.[0]
          setItems([
            {
              productId: product.id,
              skuId: sku?.id ?? Number(directSkuId ?? 0),
              productName: product.name,
              productImage: sku?.image || product.mainImage,
              skuName: sku?.attributes,
              price: sku?.price ?? product.price,
              quantity: directQuantity || 1,
            },
          ])
        }
      } else {
        // 购物车模式：勾选项
        const cartRes = await cartApi.getCart()
        const cartItems = (cartRes.data?.data || [])
          .filter((item) => item.checked)
          .map((item) => ({
            productId: item.productId,
            skuId: item.skuId,
            productName: item.productName,
            productImage: item.productImage,
            skuName: item.skuName,
            price: item.price,
            quantity: item.quantity,
          }))
        setItems(cartItems)
      }

      // 可用优惠券（UNUSED）
      try {
        const couponRes = await marketingApi.getUserCoupons({ status: 'UNUSED', page: 1, pageSize: 50 })
        setCoupons(couponRes.data?.data?.list ?? [])
      } catch {
        // 券服务不可用时允许无券下单
      }
    } finally {
      setLoading(false)
    }
  }

  const subtotal = useMemo(
    () => items.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [items],
  )
  const availableCoupons = useMemo(
    () => coupons.filter((c) => subtotal >= c.thresholdAmount && calcCouponDiscount(c, subtotal) > 0),
    [coupons, subtotal],
  )
  const selectedCoupon = availableCoupons.find((c) => c.id === selectedCouponId) ?? null
  const discount = selectedCoupon ? calcCouponDiscount(selectedCoupon, subtotal) : 0
  const payable = Math.max(subtotal - discount, 0)

  const couponLabel = (c: UserCoupon) =>
    c.templateType === 'PERCENT_OFF' && c.discountRate != null
      ? `${c.templateName} · ${Math.round((1 - c.discountRate) * 100)}%off`
      : `${c.templateName} · 减${c.discountAmount}`

  const handleSubmit = async () => {
    if (!address) {
      Taro.showToast({ title: '请先添加收货地址', icon: 'none' })
      return
    }
    if (items.length === 0) {
      Taro.showToast({ title: '请选择商品', icon: 'none' })
      return
    }

    setSubmitting(true)
    try {
      // 契约对齐后端 CreateOrderRequest：requestId 幂等 + 每项 price + 收货人三要素
      const res = await orderApi.create({
        requestId: `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
        items: items.map((item) => ({
          productId: item.productId,
          skuId: item.skuId,
          quantity: item.quantity,
          productName: item.productName,
          skuImage: item.productImage,
          skuAttributes: item.skuName,
          price: item.price,
        })),
        receiverName: address.name,
        receiverPhone: address.phone,
        receiverAddress: `${address.province}${address.city}${address.district}${address.detail}`,
        couponId: selectedCoupon?.id,
      })
      const orderId = res.data?.data?.id
      Taro.showToast({ title: '下单成功', icon: 'success' })
      setTimeout(() => {
        Taro.redirectTo({ url: `/pages/payment/index?id=${orderId}` })
      }, 1500)
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message ||
        '下单失败'
      Taro.showToast({ title: message, icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.section} onClick={() => Taro.navigateTo({ url: '/pages/address/index' })}>
        <Text className={styles.label}>收货地址</Text>
        {address ? (
          <View className={styles.addressInfo}>
            <Text className={styles.addressName}>{address.name} {address.phone}</Text>
            <Text className={styles.addressDetail}>{address.province}{address.city}{address.district}{address.detail}</Text>
          </View>
        ) : (
          <Text className={styles.emptyText}>请添加收货地址 ›</Text>
        )}
      </View>

      <View className={styles.section}>
        <Text className={styles.label}>商品清单</Text>
        {items.length > 0 ? (
          items.map((item) => (
            <View key={`${item.skuId}`} className={styles.itemRow}>
              <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />
              <View className={styles.itemInfo}>
                <Text className={styles.itemName}>{item.productName}</Text>
                {item.skuName && <Text className={styles.itemSku}>{item.skuName}</Text>}
                <Text className={styles.itemPrice}>¥{Number(item.price).toFixed(2)} × {item.quantity}</Text>
              </View>
              <Text className={styles.itemSubtotal}>¥{(item.price * item.quantity).toFixed(2)}</Text>
            </View>
          ))
        ) : (
          <Text className={styles.emptyText}>{loading ? '加载中...' : '没有待结算的商品'}</Text>
        )}
      </View>

      {/* 优惠券选择（对齐 Web 端结算页优惠券选择器） */}
      <View className={styles.section}>
        <Text className={styles.label}>优惠券</Text>
        <View
          className={styles.couponSelector}
          onClick={() => availableCoupons.length > 0 && setCouponOpen(!couponOpen)}
        >
          <Text className={selectedCoupon ? styles.couponSelected : styles.couponPlaceholder}>
            {selectedCoupon
              ? couponLabel(selectedCoupon)
              : availableCoupons.length > 0
                ? `选择优惠券 (${availableCoupons.length}张可用)`
                : '暂无可用优惠券'}
          </Text>
          {selectedCoupon && (
            <Text className={styles.couponCancel} onClick={(e) => { e.stopPropagation(); setSelectedCouponId(null) }}>
              不使用
            </Text>
          )}
        </View>
        {couponOpen && availableCoupons.map((coupon) => (
          <View
            key={coupon.id}
            className={`${styles.couponOption} ${coupon.id === selectedCouponId ? styles.couponOptionActive : ''}`}
            onClick={() => {
              setSelectedCouponId(coupon.id)
              setCouponOpen(false)
            }}
          >
            <Text className={styles.couponOptionName}>{coupon.templateName}</Text>
            <Text className={styles.couponOptionDesc}>
              {coupon.templateType === 'PERCENT_OFF' && coupon.discountRate != null
                ? `${Math.round((1 - coupon.discountRate) * 100)}%off`
                : `减${coupon.discountAmount}`}
              （满{coupon.thresholdAmount}可用）
            </Text>
          </View>
        ))}
      </View>

      <View className={styles.section}>
        <View className={styles.summaryRow}>
          <Text className={styles.summaryLabel}>商品金额</Text>
          <Text className={styles.summaryValue}>¥{subtotal.toFixed(2)}</Text>
        </View>
        <View className={styles.summaryRow}>
          <Text className={styles.summaryLabel}>优惠券</Text>
          <Text className={styles.summaryValue}>-¥{discount.toFixed(2)}</Text>
        </View>
        <View className={styles.summaryRow}>
          <Text className={styles.summaryLabel}>应付</Text>
          <Text className={styles.summaryPayable}>¥{payable.toFixed(2)}</Text>
        </View>
      </View>

      <View className={styles.bottomBar}>
        <View className={styles.totalInfo}>
          <Text className={styles.totalLabel}>合计：</Text>
          <Text className={styles.totalPrice}>¥{payable.toFixed(2)}</Text>
        </View>
        <View className={styles.submitBtn} onClick={submitting ? undefined : handleSubmit}>
          <Text className={styles.submitText}>{submitting ? '提交中...' : '提交订单'}</Text>
        </View>
      </View>
    </View>
  )
}
