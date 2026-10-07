import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro, { usePullDownRefresh, useReachBottom } from '@tarojs/taro'
import {
  pageMyAfterSales,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/afterSale'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

// 状态语义对齐后端 AfterSaleCase：PENDING/APPROVED/REJECTED/REFUNDED/CLOSED
const TABS: Array<{ label: string; value?: string }> = [
  { label: '全部' },
  { label: '待处理', value: 'PENDING' },
  { label: '已同意', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已关闭', value: 'CLOSED' },
]
const PAGE_SIZE = 10

function formatTime(time?: string | null) {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function AfterSalePage() {
  const statusParam = Taro.getCurrentInstance().router?.params?.status
  const initIndex = (() => {
    if (!statusParam) return 0
    const idx = TABS.findIndex((t) => t.value === statusParam)
    return idx >= 0 ? idx : 0
  })()
  const [activeTab, setActiveTab] = useState(initIndex)
  const [cases, setCases] = useState<AfterSaleCase[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  useEffect(() => {
    setCases([])
    setPage(1)
    setHasMore(true)
    loadCases(1, activeTab, false)
  }, [activeTab])

  const loadCases = async (pageNum: number, tab: number, append: boolean) => {
    if (loading) return
    setLoading(true)
    try {
      const params: { page: number; pageSize: number; status?: string } = { page: pageNum, pageSize: PAGE_SIZE }
      const status = TABS[tab]?.value
      if (status) params.status = status
      const res = await pageMyAfterSales(params)
      const list = res.data?.data?.records || []
      setCases((prev) => (append ? [...prev, ...list] : list))
      setPage(pageNum)
      setHasMore(list.length >= PAGE_SIZE)
    } catch {
      setHasMore(false)
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  useReachBottom(() => {
    if (hasMore && !loading) loadCases(page + 1, activeTab, true)
  })

  usePullDownRefresh(() => {
    loadCases(1, activeTab, false).finally(() => Taro.stopPullDownRefresh())
  })

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.tabs}>
        {TABS.map((tab, i) => (
          <View key={tab.label} className={`${styles.tab} ${activeTab === i ? styles.tabActive : ''}`} onClick={() => setActiveTab(i)}>
            <Text className={activeTab === i ? styles.tabTextActive : styles.tabText}>{tab.label}</Text>
          </View>
        ))}
      </View>
      <ScrollView scrollY className={styles.listWrap}>
        {cases.length > 0 ? cases.map((c) => (
          <View
            key={c.id}
            className={styles.caseCard}
            onClick={() => Taro.navigateTo({ url: `/pages/afterSaleDetail/index?id=${c.id}` })}
          >
            <View className={styles.caseHeader}>
              <Text className={styles.caseNo}>售后单号: {c.caseNo}</Text>
              <Text className={`${styles.caseStatus} ${styles[`status_${c.status}`] || ''}`}>
                {AFTER_SALE_STATUS_TEXT[c.status] || c.status}
              </Text>
            </View>
            <View className={styles.caseBody}>
              <Text className={styles.caseType}>{AFTER_SALE_TYPE_TEXT[c.type] || c.type}{c.quantity > 0 ? ` · ${c.quantity} 件` : ' · 整单'}</Text>
              <Text className={styles.caseReason}>{c.reason}</Text>
            </View>
            <View className={styles.caseFooter}>
              <Text className={styles.caseOrder}>订单 {c.orderNo || c.orderId}</Text>
              {c.refundAmount != null && <Text className={styles.caseRefund}>退款 ¥{c.refundAmount}</Text>}
              <Text className={styles.caseTime}>{formatTime(c.createdAt)}</Text>
            </View>
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyIcon}>🛠️</Text>
            <Text className={styles.emptyText}>暂无售后记录</Text>
          </View>
        )}
        {cases.length > 0 && (
          <View className={styles.footerHint}>
            <Text className={styles.footerHintText}>{hasMore ? (loading ? '加载中...' : '上拉加载更多') : '没有更多售后单了'}</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
