import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { notificationApi } from '@/api/notification'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import type { Conversation } from '@/types'
import styles from './index.module.scss'

const QUICK_ENTRIES = [
  { icon: '❤️', name: '赞和收藏', type: 1, gradient: 'linear-gradient(135deg, #FF4D6A, #FF8FA3)' },
  { icon: '💬', name: '评论', type: 2, gradient: 'linear-gradient(135deg, #9370DB, #B06AB3)' },
  { icon: '👤', name: '新增关注', type: 3, gradient: 'linear-gradient(135deg, #00D4FF, #0099CC)' },
  { icon: '📢', name: '系统通知', type: 4, gradient: 'linear-gradient(135deg, #FFD700, #FFA500)' },
]

export default function MessagePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [unreadCounts, setUnreadCounts] = useState<Record<number, number>>({})
  const { isLoggedIn } = useAuthStore()
  useAuthGuard()

  useEffect(() => {
    if (isLoggedIn) loadData()
  }, [isLoggedIn])

  const loadData = async () => {
    // 使用 allSettled：单个接口超时不影响其他接口数据显示
    const [convResult, countResult] = await Promise.allSettled([
      notificationApi.getConversations({ page: 1, pageSize: 20 }),
      notificationApi.getUnreadCount(),
    ])
    if (convResult.status === 'fulfilled') {
      setConversations(convResult.value.data?.data?.list || [])
    }
    if (countResult.status === 'fulfilled') {
      setUnreadCounts((countResult.value.data?.data as unknown as Record<number, number>) || {})
    }
  }

  const handleQuickEntry = (type: number) => {
    Taro.navigateTo({ url: `/pages/notifications/index?type=${type}` })
  }

  const handleConversationClick = (id: number) => {
    Taro.navigateTo({ url: `/pages/chat/index?id=${id}` })
  }

  const formatTime = (time: string) => {
    const date = new Date(time)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
    return `${date.getMonth() + 1}/${date.getDate()}`
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="CloudMart" />
<View className={styles.quickRow}>
        {QUICK_ENTRIES.map((entry) => (
          <View key={entry.type} className={styles.quickItem} onClick={() => handleQuickEntry(entry.type)}>
            <View className={styles.quickIconWrap} style={{ background: entry.gradient }}>
              <Text className={styles.quickIcon}>{entry.icon}</Text>
              {unreadCounts[entry.type] && (
                <View className={styles.badge}>
                  <Text className={styles.badgeText}>{unreadCounts[entry.type] > 99 ? '99+' : unreadCounts[entry.type]}</Text>
                </View>
              )}
            </View>
            <Text className={styles.quickName}>{entry.name}</Text>
          </View>
        ))}
      </View>

      <View className={styles.sectionHeader}>
        <Text className={styles.sectionTitle}>私信</Text>
      </View>

      <ScrollView scrollY className={styles.conversationList}>
        {conversations.map((conv) => (
          <View key={conv.id} className={styles.conversationItem} onClick={() => handleConversationClick(conv.id)}>
            <Image className={styles.avatar} src={conv.targetUser.avatar} />
            <View className={styles.conversationInfo}>
              <View className={styles.conversationTop}>
                <Text className={styles.nickname}>{conv.targetUser.nickname}</Text>
                <Text className={styles.time}>{formatTime(conv.lastMessageTime)}</Text>
              </View>
              <Text className={styles.lastMessage}>{conv.lastMessage}</Text>
            </View>
            {conv.unreadCount > 0 && (
              <View className={styles.unreadBadge}>
                <Text className={styles.unreadText}>{conv.unreadCount > 99 ? '99+' : conv.unreadCount}</Text>
              </View>
            )}
          </View>
        ))}
      </ScrollView>
      <CustomTabBar />
    </View>
  )
}
