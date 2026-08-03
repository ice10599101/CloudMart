import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import { notificationApi } from '@/api/notification'
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
