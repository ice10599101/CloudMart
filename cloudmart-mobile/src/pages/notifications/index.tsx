import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { notificationApi } from '@/api/notification'
import { wishApi } from '@/api/wish'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

const TABS = ['全部', '点赞', '评论', '关注', '系统']
const TAB_TYPES = [0, 1, 2, 3, 4]

export default function NotificationsPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [activeTab, setActiveTab] = useState(0)
  const [notifications, setNotifications] = useState<any[]>([])
  useAuthGuard()

  useEffect(() => {
    loadNotifications()
  }, [activeTab])

  const loadNotifications = async () => {
    try {
      const params: any = { page: 1, pageSize: 20 }
      if (activeTab > 0) params.type = TAB_TYPES[activeTab]
      const res = await notificationApi.getList(params)
      setNotifications(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  /** 预期管理通知 3 选项（延长预期/调整目标/转入时间胶囊，Sprint 2.5） */
  const handleExpectedAction = async (n: { bizId: number }, action: 'EXTEND' | 'ADJUST' | 'TO_CAPSULE') => {
    const wishId = n.bizId
    // 埋点失败不阻断跳转（转化率数据允许少量丢失）
    try {
      await wishApi.recordExpectedAction(wishId, action)
    } catch {
      // ignore
    }
    if (action === 'EXTEND') {
      Taro.navigateTo({ url: `/pages/wishDetail/index?id=${wishId}&extend=1` })
    } else if (action === 'ADJUST') {
      Taro.navigateTo({ url: `/pages/aiAssistant/index?wishId=${wishId}` })
    } else {
      Taro.navigateTo({ url: `/pages/capsuleCreate/index?wishId=${wishId}` })
    }
  }

  const formatTime = (time: string) => {
    const diff = Date.now() - new Date(time).getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return `${new Date(time).getMonth() + 1}月${new Date(time).getDate()}日`
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
        {notifications.length > 0 ? notifications.map((n) => (
          <View key={n.id} className={styles.notificationItem}>
            {n.senderAvatar && <Image className={styles.avatar} src={n.senderAvatar} />}
            <View className={styles.notificationBody}>
              <Text className={styles.notificationContent}>{n.content}</Text>
              <Text className={styles.notificationTime}>{formatTime(n.createdAt)}</Text>
              {n.type === 'CHECKIN_REMINDER' && n.bizType === 'EXPECTED_MANAGEMENT' && n.bizId && (
                <View className={styles.expectedActions}>
                  <View className={styles.expectedBtn} onClick={() => handleExpectedAction(n, 'EXTEND')}>
                    <Text className={styles.expectedBtnText}>延长预期</Text>
                  </View>
                  <View className={styles.expectedBtn} onClick={() => handleExpectedAction(n, 'ADJUST')}>
                    <Text className={styles.expectedBtnText}>调整目标</Text>
                  </View>
                  <View className={styles.expectedBtn} onClick={() => handleExpectedAction(n, 'TO_CAPSULE')}>
                    <Text className={styles.expectedBtnText}>转入胶囊</Text>
                  </View>
                </View>
              )}
            </View>
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyIcon}>🔔</Text>
            <Text className={styles.emptyText}>暂无通知</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
