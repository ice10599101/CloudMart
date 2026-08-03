import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import { marketingApi } from '@/api/marketing'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

const TABS = ['可使用', '已使用', '已过期']
const STATUS_MAP = [0, 1, 2]

export default function CouponsPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [activeTab, setActiveTab] = useState(0)
  const [coupons, setCoupons] = useState<any[]>([])
  useAuthGuard()

  useEffect(() => {
    loadCoupons()
  }, [activeTab])

  const loadCoupons = async () => {
    try {
      const res = await marketingApi.getUserCoupons({ status: STATUS_MAP[activeTab], page: 1, pageSize: 20 })
      setCoupons(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
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
        {coupons.length > 0 ? coupons.map((coupon) => (
          <View key={coupon.id} className={styles.couponCard}>
            <View className={styles.couponAmount}>
              <Text className={styles.couponSymbol}>¥</Text>
              <Text className={styles.couponValue}>{coupon.discountAmount || coupon.amount}</Text>
            </View>
            <View className={styles.couponInfo}>
              <Text className={styles.couponName}>{coupon.name || coupon.templateName}</Text>
              <Text className={styles.couponCondition}>{coupon.minAmount ? `满${coupon.minAmount}元可用` : '无门槛'}</Text>
              <Text className={styles.couponExpiry}>{coupon.endTime ? `有效期至${coupon.endTime}` : ''}</Text>
            </View>
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyIcon}>🎫</Text>
            <Text className={styles.emptyText}>暂无优惠券</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
