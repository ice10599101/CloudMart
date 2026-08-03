import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  skuName: string
  price: number
  quantity: number
}

interface OrderDetail {
  id: number
  orderNo: string
  status: number
  totalAmount: number
  shippingFee: number
  payAmount: number
  recipientName: string
  recipientPhone: string
  recipientAddress: string
  items: OrderItem[]
  createdAt: string
  paidAt?: string
  shippedAt?: string
  receivedAt?: string
}

const STATUS_CONFIG: Record<number, { icon: string; text: string }> = {
  0: { icon: '💰', text: '待付款' },
  1: { icon: '📦', text: '待发货' },
  2: { icon: '🚚', text: '待收货' },
  3: { icon: '✅', text: '已完成' },
  4: { icon: '❌', text: '已取消' },
}

function formatDateTime(time?: string) {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function OrderDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const id = Number(Taro.getCurrentInstance().router?.params?.id)
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (id) loadOrder()
  }, [id])

  const loadOrder = async () => {
    try {
      setLoading(true)
      const res = await orderApi.getDetail(id)
      setOrder(res.data?.data as unknown as OrderDetail)
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = () => {
    Taro.showModal({
      title: '提示',
      content: '确定要取消该订单吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await orderApi.cancel(id)
            Taro.showToast({ title: '已取消', icon: 'success' })
            loadOrder()
          } catch {
            Taro.showToast({ title: '取消失败', icon: 'none' })
          }
        }
      },
    })
  }

  const handlePay = () => {
    Taro.navigateTo({ url: `/pages/payment/index?id=${order!.id}` })
  }

  const handleConfirm = () => {
    Taro.showModal({
      title: '提示',
      content: '确认已收到商品？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await orderApi.confirm(id)
            Taro.showToast({ title: '已确认收货', icon: 'success' })
            loadOrder()
          } catch {
            Taro.showToast({ title: '操作失败', icon: 'none' })
          }
        }
      },
    })
  }

  const handleRebuy = () => {
    Taro.navigateTo({ url: '/pages/mall/index' })
  }

  if (loading) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.loading}>
          <View className={styles.spinner} />
          <Text className={styles.loadingText}>加载中</Text>
        </View>
      </View>
    )
  }

  if (!order) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>📋</Text>
          <Text className={styles.emptyText}>订单不存在</Text>
        </View>
      </View>
    )
  }

  const statusConfig = STATUS_CONFIG[order.status] || { icon: '❓', text: '未知' }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {/* 状态区域 */}
        <View className={styles.statusSection}>
          <Text className={styles.statusIcon}>{statusConfig.icon}</Text>
          <Text className={styles.statusText}>{statusConfig.text}</Text>
        </View>

        {/* 收货地址 */}
        <View className={styles.section}>
          <View className={styles.addressHeader}>
            <Text className={styles.addressIcon}>📍</Text>
            <Text className={styles.sectionTitle}>收货信息</Text>
          </View>
          <View className={styles.addressBody}>
            <View className={styles.addressRow}>
              <Text className={styles.recipientName}>{order.recipientName}</Text>
              <Text className={styles.recipientPhone}>{order.recipientPhone}</Text>
            </View>
            <Text className={styles.addressDetail}>{order.recipientAddress}</Text>
          </View>
        </View>

        {/* 商品列表 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>商品信息</Text>
          </View>
          {order.items.map((item) => (
            <View key={item.id} className={styles.orderItem}>
              <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />
              <View className={styles.itemInfo}>
                <Text className={styles.itemName}>{item.productName}</Text>
                <Text className={styles.itemSku}>{item.skuName}</Text>
                <View className={styles.itemBottom}>
                  <Text className={styles.itemPrice}>¥{item.price}</Text>
                  <Text className={styles.itemQuantity}>x{item.quantity}</Text>
                </View>
              </View>
            </View>
          ))}
        </View>

        {/* 价格明细 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>价格明细</Text>
          </View>
          <View className={styles.priceRow}>
            <Text className={styles.priceLabel}>商品合计</Text>
            <Text className={styles.priceValue}>¥{order.totalAmount}</Text>
          </View>
          <View className={styles.priceRow}>
            <Text className={styles.priceLabel}>运费</Text>
            <Text className={styles.priceValue}>{order.shippingFee > 0 ? `¥${order.shippingFee}` : '免运费'}</Text>
          </View>
          <View className={styles.priceDivider} />
          <View className={styles.priceRow}>
            <Text className={styles.priceLabelBold}>实付金额</Text>
            <Text className={styles.priceTotal}>¥{order.payAmount}</Text>
          </View>
        </View>

        {/* 订单信息 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>订单信息</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>订单号</Text>
            <Text className={styles.infoValue}>{order.orderNo}</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>下单时间</Text>
            <Text className={styles.infoValue}>{formatDateTime(order.createdAt)}</Text>
          </View>
          {order.paidAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>支付时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.paidAt)}</Text>
            </View>
          )}
          {order.shippedAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>发货时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.shippedAt)}</Text>
            </View>
          )}
          {order.receivedAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>收货时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.receivedAt)}</Text>
            </View>
          )}
        </View>
      </ScrollView>

      {/* 底部操作栏 */}
      {(order.status === 0 || order.status === 2 || order.status === 3) && (
        <View className={styles.bottomBar}>
          {order.status === 0 && (
            <>
              <View className={styles.btnSecondary} onClick={handleCancel}>
                <Text className={styles.btnSecondaryText}>取消订单</Text>
              </View>
              <View className={styles.btnPrimary} onClick={handlePay}>
                <Text className={styles.btnPrimaryText}>去支付</Text>
              </View>
            </>
          )}
          {order.status === 2 && (
            <View className={styles.btnPrimary} onClick={handleConfirm}>
              <Text className={styles.btnPrimaryText}>确认收货</Text>
            </View>
          )}
          {order.status === 3 && (
            <View className={styles.btnPrimary} onClick={handleRebuy}>
              <Text className={styles.btnPrimaryText}>再次购买</Text>
            </View>
          )}
        </View>
      )}
    </View>
  )
}
