import { useState, useEffect } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { userApi } from '@/api/user'
import { cartApi } from '@/api/cart'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
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

export default function CheckoutPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [address, setAddress] = useState<Address | null>(null)
  const [submitting, setSubmitting] = useState(false)
  useAuthGuard()

  useEffect(() => {
    loadDefaultAddress()
  }, [])

  const loadDefaultAddress = async () => {
    try {
      const res = await userApi.getDefaultAddress()
      setAddress(res.data?.data || null)
    } catch {
      // No default address
    }
  }

  const handleSubmit = async () => {
    if (!address) {
      Taro.showToast({ title: '请先添加收货地址', icon: 'none' })
      return
    }

    setSubmitting(true)
    try {
      const cartRes = await cartApi.getCart()
      const items = (cartRes.data?.data || [])
        .filter((item) => item.checked)
        .map((item) => ({ skuId: item.skuId, quantity: item.quantity }))

      if (items.length === 0) {
        Taro.showToast({ title: '请选择商品', icon: 'none' })
        return
      }

      const res = await orderApi.create({ addressId: address.id, items })
      const orderId = res.data?.data?.id
      Taro.showToast({ title: '下单成功', icon: 'success' })
      setTimeout(() => {
        Taro.navigateTo({ url: `/pages/orderDetail/index?id=${orderId}` })
      }, 1500)
    } catch (err: any) {
      Taro.showToast({ title: err?.response?.data?.error?.message || '下单失败', icon: 'none' })
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
        <Text className={styles.emptyText}>确认订单后显示</Text>
      </View>
      <View className={styles.bottomBar}>
        <View className={styles.totalInfo}>
          <Text className={styles.totalLabel}>合计：</Text>
          <Text className={styles.totalPrice}>¥--</Text>
        </View>
        <View className={styles.submitBtn} onClick={submitting ? undefined : handleSubmit}>
          <Text className={styles.submitText}>{submitting ? '提交中...' : '提交订单'}</Text>
        </View>
      </View>
    </View>
  )
}
