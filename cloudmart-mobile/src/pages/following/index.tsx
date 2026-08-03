import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

const TABS = ['关注', '粉丝']

export default function FollowingPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const typeParam = Taro.getCurrentInstance().router?.params?.type || 'following'
  const [activeTab, setActiveTab] = useState(typeParam === 'followers' ? 1 : 0)
  const [users, setUsers] = useState<any[]>([])
  const { user } = useAuthStore()
  useAuthGuard()

  useEffect(() => {
    loadUsers()
  }, [activeTab])

  const loadUsers = async () => {
    if (!user?.id) return
    try {
      const res = activeTab === 0
        ? await communityApi.getUserFollowing(user.id, { page: 1, pageSize: 50 })
        : await communityApi.getUserFollowers(user.id, { page: 1, pageSize: 50 })
      setUsers(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  const handleFollow = async (targetUserId: number, isFollowing: boolean) => {
    try {
      if (isFollowing) {
        await communityApi.unfollowUser(targetUserId)
      } else {
        await communityApi.followUser(targetUserId)
      }
      loadUsers()
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
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
        {users.length > 0 ? users.map((u) => (
          <View key={u.id} className={styles.userItem} onClick={() => Taro.navigateTo({ url: `/pages/userProfile/index?id=${u.id}` })}>
            <Image className={styles.userAvatar} src={u.avatar} />
            <View className={styles.userInfo}>
              <Text className={styles.userName}>{u.nickname}</Text>
              <Text className={styles.userBio}>{u.signature || ''}</Text>
            </View>
            <View className={styles.followBtn} onClick={(e) => { e.stopPropagation(); handleFollow(u.id, u.isFollowing) }}>
              <Text className={styles.followBtnText}>{u.isFollowing ? '已关注' : '关注'}</Text>
            </View>
          </View>
        )) : (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>{activeTab === 0 ? '暂无关注' : '暂无粉丝'}</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
