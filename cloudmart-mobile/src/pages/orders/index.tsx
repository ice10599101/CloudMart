import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

const TABS = ['全部', '待付款', '待发货', '待收货', '已完成']
const STATUS_MAP = [-1, 0, 1, 2, 3]

export default function OrdersPage() {
  const statusParam = Number(Taro.getCurrentInstance().router?.params?.status) || -1
  const [activeTab, setActiveTab] = useState(statusParam >= 0 ? STATUS_MAP.indexOf(statusParam) : 0)
  const [orders, setOrders] = useState<any[]>([])
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  useEffect(() => {
    loadOrders()
  }, [activeTab])

  const loadOrders = async () => {
    try {
      const params: any = { page: 1, pageSize: 20 }
      if (activeTab > 0) params.status = STATUS_MAP[activeTab]
      const res = await orderApi.getList(params)
      setOrders(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  const formatTime = (time: string) => {
    const d = new Date(time)
    return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
  }

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
              <Text className={styles.orderStatus}>{order.statusText || TABS[STATUS_MAP.indexOf(order.status)] || '未知'}</Text>
            </View>
            {order.items && order.items.map((item: any, idx: number) => (
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
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>暂无订单</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
