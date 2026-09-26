import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { notificationApi } from '@/api/notification'
import { wishApi } from '@/api/wish'
import { communityApi } from '@/api/community'
import DecoratedAvatar from '@/components/DecoratedAvatar'
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
      const params: { page: number; pageSize: number; type?: number } = { page: 1, pageSize: 20 }
      if (activeTab > 0) params.type = TAB_TYPES[activeTab]
      const res = await notificationApi.getList(params)
      setNotifications(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  /** 通知点击跳转（对齐 Web 端 Messages：帖子/用户/话题按 bizType 跳对应详情） */
  const goNotificationTarget = (n: { type?: string; bizType?: string; bizId?: number | null; actorId?: number | null }) => {
    if (n.type === 'LIKE' || n.type === 'COMMENT' || n.type === 'COLLECT' || n.type === 'SHARE' || n.type === 'MENTION') {
      if (n.bizType === 'POST' && n.bizId) {
        Taro.navigateTo({ url: `/pages/postDetail/index?id=${n.bizId}` })
        return
      }
    }
    if (n.type === 'TAG_NEW_POST' && n.bizId) {
      Taro.navigateTo({ url: `/pages/topicDetail/index?tagId=${n.bizId}` })
      return
    }
    if (n.type === 'FOLLOW' && n.actorId) {
      Taro.navigateTo({ url: `/pages/userProfile/index?userId=${n.actorId}` })
      return
    }
    if (n.bizType === 'POST' && n.bizId) {
      Taro.navigateTo({ url: `/pages/postDetail/index?id=${n.bizId}` })
    }
  }

  /** 点击通知：跳转目标页并标记已读（对齐 Web 端） */
  const handleNotificationClick = (n: { id: number; isRead?: boolean; type?: string; bizType?: string; bizId?: number | null; actorId?: number | null }) => {
    if (!n.isRead) {
      setNotifications((prev) => prev.map((item) => (item.id === n.id ? { ...item, isRead: true } : item)))
      notificationApi.markRead(n.id).catch(() => {})
    }
    if (n.type === 'WISH_FULFILL' || n.type === 'ENCOUNTER_LETTER' || n.type === 'CHECKIN_REMINDER') return
    goNotificationTarget(n)
  }

  /** 回关/取消关注（对齐 Web 端 Messages 关注通知内联按钮） */
  const [followStates, setFollowStates] = useState<Record<number, boolean>>({})
  const handleFollowBack = async (actorId: number) => {
    const isFollowing = followStates[actorId]
    try {
      if (isFollowing) {
        await communityApi.unfollowUser(actorId)
      } else {
        await communityApi.followUser(actorId)
      }
      setFollowStates((prev) => ({ ...prev, [actorId]: !isFollowing }))
      Taro.showToast({ title: isFollowing ? '已取消关注' : '关注成功', icon: 'none' })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  /** 全部已读（对齐 Web 端） */
  const handleMarkAllRead = async () => {
    try {
      await notificationApi.markAllRead()
      setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })))
      Taro.showToast({ title: '已全部标记为已读', icon: 'success' })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
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
        <View className={styles.markAllBtn} onClick={handleMarkAllRead}>
          <Text className={styles.markAllText}>全部已读</Text>
        </View>
      </View>
      <ScrollView scrollY>
        {notifications.length > 0 ? notifications.map((n) => (
          <View key={n.id} className={`${styles.notificationItem} ${!n.isRead ? styles.notificationUnread : ''}`} onClick={() => handleNotificationClick(n)}>
            {n.senderAvatar && (
              <View
                onClick={(e) => {
                  e.stopPropagation()
                  if (n.actorId) Taro.navigateTo({ url: `/pages/userProfile/index?userId=${n.actorId}` })
                }}
              >
                <DecoratedAvatar src={n.senderAvatar} userId={n.actorId} size={40} fallbackText='?' />
              </View>
            )}
            <View className={styles.notificationBody}>
              <Text className={styles.notificationContent}>{n.content}</Text>
              <Text className={styles.notificationTime}>{formatTime(n.createdAt)}</Text>
              {n.type === 'FOLLOW' && !!n.actorId && (
                <View
                  className={styles.followBackBtn}
                  onClick={(e) => {
                    e.stopPropagation()
                    handleFollowBack(n.actorId!)
                  }}
                >
                  <Text className={styles.followBackText}>{followStates[n.actorId!] ? '已关注' : '回关'}</Text>
                </View>
              )}
              {n.type === 'WISH_FULFILL' && n.bizType === 'FULFILLMENT_LEGACY' && n.bizId && (
                <View className={styles.expectedActions} onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${n.bizId}` })}>
                  <View className={styles.expectedBtn}>
                    <Text className={styles.expectedBtnText}>查看同愿的故事</Text>
                  </View>
                </View>
              )}
              {n.type === 'ENCOUNTER_LETTER' && (
                <View className={styles.expectedActions} onClick={() => Taro.navigateTo({ url: '/pages/encounterLetters/index' })}>
                  <View className={styles.expectedBtn}>
                    <Text className={styles.expectedBtnText}>查看漂流瓶</Text>
                  </View>
                </View>
              )}
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
