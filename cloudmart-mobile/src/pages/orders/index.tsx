import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro, { usePullDownRefresh, useReachBottom } from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { errorMessage } from '@/utils/request'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Order, OrderStatus } from '@/types'
import { toPageResult } from '@/api/paging'
import styles from './index.module.scss'

// 状态语义对齐 Web 端订单列表：全部/待付款/已付款/已发货/已完成/已取消
const TABS = ['全部', '待付款', '已付款', '已发货', '已完成', '已取消']
const STATUS_MAP: Array<OrderStatus | ''> = [
  '',
  'PENDING_PAYMENT',
  'PAID',
  'SHIPPED',
  'COMPLETED',
  'CANCELLED',
]
/** 旧链接数字参数兼容（0-4） */
const LEGACY_STATUS_MAP: Record<string, OrderStatus> = {
  '0': 'PENDING_PAYMENT',
  '1': 'PAID',
  '2': 'SHIPPED',
  '3': 'COMPLETED',
  '4': 'CANCELLED',
}
const PAGE_SIZE = 10

export default function OrdersPage() {
  const statusParam = Taro.getCurrentInstance().router?.params?.status
  const initIndex = (() => {
    if (!statusParam) return 0
    const status = LEGACY_STATUS_MAP[statusParam] ?? (statusParam as OrderStatus)
    const idx = STATUS_MAP.indexOf(status)
    return idx >= 0 ? idx : 0
  })()
  const [activeTab, setActiveTab] = useState(initIndex)
  const [orders, setOrders] = useState<Order[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [actingId, setActingId] = useState<number | null>(null)
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  useEffect(() => {
    setOrders([])
    setPage(1)
    setHasMore(true)
    loadOrders(1, activeTab, false)
  }, [activeTab])

  const loadOrders = async (pageNum: number, tab: number, append: boolean) => {
    if (loading) return
    setLoading(true)
    try {
      const params: { page: number; pageSize: number; status?: OrderStatus } = { page: pageNum, pageSize: PAGE_SIZE }
      const tabStatus = STATUS_MAP[tab]
      if (tab > 0 && tabStatus) params.status = tabStatus
      const res = await orderApi.getList(params)
      const pageResult = toPageResult<Order>(res.data, { page, pageSize: PAGE_SIZE })
      const list = pageResult.items
      setOrders((prev) => (append ? [...prev, ...list] : list))
      setPage(pageNum)
      setHasMore(list.length >= PAGE_SIZE)
    } catch {
      setHasMore(false)
    } finally {
      setLoading(false)
    }
  }

  useReachBottom(() => {
    if (hasMore && !loading) loadOrders(page + 1, activeTab, true)
  })

  usePullDownRefresh(() => {
    loadOrders(1, activeTab, false).finally(() => Taro.stopPullDownRefresh())
  })

  const cancelOrder = (order: Order) => {
    Taro.showModal({
      title: '取消订单',
      content: `确定取消订单 ${order.orderNo} 吗？`,
      success: async (res) => {
        if (!res.confirm) return
        setActingId(order.id)
        try {
          await orderApi.cancel(order.id)
          Taro.showToast({ title: '订单已取消', icon: 'success' })
          loadOrders(1, activeTab, false)
        } catch (err) {
          Taro.showToast({ title: errorMessage(err, '取消失败'), icon: 'none' })
        } finally {
          setActingId(null)
        }
      },
    })
  }

  const confirmReceipt = (order: Order) => {
    Taro.showModal({
      title: '确认收货',
      content: '请确认已收到全部商品',
      success: async (res) => {
        if (!res.confirm) return
        setActingId(order.id)
        try {
          await orderApi.confirm(order.id)
          Taro.showToast({ title: '已确认收货', icon: 'success' })
          loadOrders(1, activeTab, false)
        } catch (err) {
          Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' })
        } finally {
          setActingId(null)
        }
      },
    })
  }

  const goPay = (order: Order) => {
    Taro.navigateTo({ url: `/pages/payment/index?id=${order.id}` })
  }

  const formatTime = (time: string) => {
    const d = new Date(time)
    return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  const statusTextOf = (order: Order) => order.statusText || TABS[STATUS_MAP.indexOf(order.status)] || '未知'

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.tabs}>
        {TABS.map((tab, i) => (
          <View key={i} className={`${styles.tab} ${activeTab === i ? styles.tabActive : ''}`} onClick={() => setActiveTab(i)}>
            <Text className={activeTab === i ? styles.tabTextActive : styles.tabText}>{tab}</Text>
          </View>
        ))}
      </View>
      <ScrollView scrollY>
        {orders.length > 0 ? orders.map((order) => (
          <View key={order.id} className={styles.orderCard} onClick={() => Taro.navigateTo({ url: `/pages/orderDetail/index?id=${order.id}` })}>
            <View className={styles.orderHeader}>
              <Text className={styles.orderNo}>订单号: {order.orderNo}</Text>
              <Text className={styles.orderStatus}>{statusTextOf(order)}</Text>
            </View>
            {order.items && order.items.map((item, idx: number) => (
              <View key={idx} className={styles.orderItem}>
                {item.productImage && <Image className={styles.productImage} src={item.productImage} mode='aspectFill' />}
                <View className={styles.productInfo}>
                  <Text className={styles.productName}>{item.productName}</Text>
                  <Text className={styles.productPrice}>¥{item.price} x{item.quantity}</Text>
                </View>
              </View>
            ))}
            <View className={styles.orderFooter}>
              <Text className={styles.orderTotal}>合计: ¥{order.totalAmount}</Text>
              <Text className={styles.orderTime}>{formatTime(order.createdAt)}</Text>
            </View>
            {/* 列表快捷操作（对齐 Web 端：取消订单/去支付/确认收货/查看详情） */}
            <View className={styles.orderActions} onClick={(e) => e.stopPropagation()}>
              {(order.allowedActions ?? []).includes('CANCEL') && (
                <View className={styles.actionGhostBtn} onClick={() => cancelOrder(order)}>
                  <Text className={styles.actionGhostText}>{actingId === order.id ? '处理中...' : '取消订单'}</Text>
                </View>
              )}
              {(order.allowedActions ?? []).includes('PAY') && (
                <View className={styles.actionPrimaryBtn} onClick={() => goPay(order)}>
                  <Text className={styles.actionPrimaryText}>去支付</Text>
                </View>
              )}
              {(order.allowedActions ?? []).includes('CONFIRM_RECEIPT') && (
                <View className={styles.actionPrimaryBtn} onClick={() => confirmReceipt(order)}>
                  <Text className={styles.actionPrimaryText}>{actingId === order.id ? '处理中...' : '确认收货'}</Text>
                </View>
              )}
              <View className={styles.actionGhostBtn} onClick={() => Taro.navigateTo({ url: `/pages/orderDetail/index?id=${order.id}` })}>
                <Text className={styles.actionGhostText}>查看详情</Text>
              </View>
            </View>
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>暂无订单</Text>
          </View>
        )}
        {orders.length > 0 && (
          <View className={styles.footerHint}>
            <Text className={styles.footerHintText}>{hasMore ? (loading ? '加载中...' : '上拉加载更多') : '没有更多订单了'}</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
