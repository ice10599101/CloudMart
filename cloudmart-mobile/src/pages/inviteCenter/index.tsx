import { useCallback, useEffect, useState } from 'react'
import { View, Text, ScrollView, Input, Button } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { inviteApi } from '@/api/user'
import type { MyInviteInfo } from '@/api/user'
import { wishApi } from '@/api/wish'
import type { ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { errorMessage } from '@/utils/request'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20

/**
 * R19 邀请中心：我的邀请码（复制）+ 邀请统计 + 绑定邀请人 +
 * 邀请奖励星光流水（source=INVITE_REWARD，游标分页）。
 */
export default function InviteCenterPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [invite, setInvite] = useState<MyInviteInfo | null>(null)
  const [bound, setBound] = useState(false)
  const [bindCode, setBindCode] = useState('')
  const [binding, setBinding] = useState(false)

  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [cursor, setCursor] = useState<string | number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const fetchLogs = useCallback(
    async (reset: boolean, currentCursor?: string | number | null) => {
      try {
        const res = await wishApi.getMyResourceLogs({
          source: 'INVITE_REWARD',
          cursor: reset ? undefined : (currentCursor ?? undefined),
          pageSize: PAGE_SIZE,
        })
        const page = res.data.data ?? []
        setLogs((prev) => (reset ? page : [...prev, ...page]))
        setCursor(page.length > 0 ? String(page[page.length - 1].id) : null)
        setHasMore(page.length >= PAGE_SIZE)
      } catch {
        // 奖励记录加载失败保持已有列表，静默处理
      }
    },
    [],
  )

  useEffect(() => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    const loadInitial = async () => {
      setLoading(true)
      try {
        const [inviteRes] = await Promise.all([
          inviteApi.getMyInvites().catch(() => null),
          fetchLogs(true),
        ])
        if (inviteRes) setInvite(inviteRes.data.data)
      } finally {
        setLoading(false)
      }
    }
    loadInitial()
  }, [isLoggedIn, fetchLogs])

  const handleCopyCode = async () => {
    if (!invite?.code) return
    try {
      await Taro.setClipboardData({ data: invite.code })
      Taro.showToast({ title: '邀请码已复制', icon: 'success' })
    } catch {
      Taro.showToast({ title: '复制失败，请长按复制', icon: 'none' })
    }
  }

  const handleBind = async () => {
    if (binding) return
    const code = bindCode.trim()
    if (!code) {
      Taro.showToast({ title: '请输入邀请码', icon: 'none' })
      return
    }
    setBinding(true)
    try {
      await inviteApi.bindInvite(code)
      Taro.showToast({ title: '绑定成功', icon: 'success' })
      setBound(true)
      setBindCode('')
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '绑定失败'), icon: 'none' })
    } finally {
      setBinding(false)
    }
  }

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore) return
    setLoadingMore(true)
    try {
      await fetchLogs(false, cursor)
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title='邀请有礼' back />
      <ScrollView className={styles.scroll} scrollY onScrollToLower={handleLoadMore}>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : (
          <>
            <View className={styles.codeCard}>
              <Text className={styles.codeTitle}>我的邀请码</Text>
              <Text className={styles.codeValue} selectable>{invite?.code ?? '------'}</Text>
              <Button className={styles.copyBtn} onClick={handleCopyCode}>复制邀请码</Button>
              <View className={styles.statsRow}>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>{invite?.invitedCount ?? 0}</Text>
                  <Text className={styles.statLabel}>已邀请好友</Text>
                </View>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>{invite?.rewardPerInvite ?? 0}</Text>
                  <Text className={styles.statLabel}>每位奖励星光</Text>
                </View>
              </View>
            </View>

            {!bound && (
              <View className={styles.bindCard}>
                <Text className={styles.bindTitle}>绑定邀请人</Text>
                <Text className={styles.bindHint}>填写好友的邀请码，绑定后双方均可获得星光奖励</Text>
                <View className={styles.bindRow}>
                  <Input
                    className={styles.bindInput}
                    placeholder='请输入邀请码'
                    placeholderClass={styles.placeholder}
                    value={bindCode}
                    onInput={(e) => setBindCode(e.detail.value)}
                  />
                  <Button
                    className={styles.bindBtn}
                    loading={binding}
                    disabled={binding}
                    onClick={handleBind}
                  >
                    {binding ? '绑定中...' : '绑定'}
                  </Button>
                </View>
              </View>
            )}

            <Text className={styles.sectionTitle}>邀请奖励记录</Text>
            {loading ? (
              <View className={styles.empty}><Text>加载中...</Text></View>
            ) : logs.length === 0 ? (
              <View className={styles.empty}><Text>暂无邀请奖励记录</Text></View>
            ) : (
              logs.map((log) => (
                <View key={String(log.id)} className={styles.logCard}>
                  <View className={styles.logLeft}>
                    <Text className={styles.logReason}>{log.reason}</Text>
                    <Text className={styles.logTime}>
                      {new Date(log.createdAt).toLocaleString('zh-CN')} · 余额 {log.balanceAfter}
                    </Text>
                  </View>
                  <Text className={styles.logAmount}>+{log.amount}</Text>
                </View>
              ))
            )}
            {loadingMore && <View className={styles.empty}><Text>加载更多...</Text></View>}
            {!loading && !loadingMore && logs.length > 0 && (
              <View className={styles.listFooter}>
                <Text>{hasMore ? '上拉加载更多' : '没有更多了'}</Text>
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  )
}
