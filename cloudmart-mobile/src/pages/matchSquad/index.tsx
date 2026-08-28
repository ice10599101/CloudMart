import { useCallback, useEffect, useRef, useState } from 'react'
import { Input, Picker, Text, View } from '@tarojs/components'
import Taro, { useShareAppMessage } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { MatchGroupDetail, MatchGroupItem } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

const MAX_KEYWORD = 60
/** 组员多少天未打卡视为需提醒（与后端 match.remind_idle_days 默认对齐） */
const IDLE_DAYS = 3

const STATUS_LABEL: Record<MatchGroupDetail['status'], string> = {
  OPEN: '招募中',
  FULL: '已满员',
  CLOSED: '已解散',
}

export default function MatchSquadPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn, user } = useAuthStore()

  const [keyword, setKeyword] = useState('')
  const [recommend, setRecommend] = useState<MatchGroupItem[]>([])
  const [loading, setLoading] = useState(true)
  const [myGroups, setMyGroups] = useState<MatchGroupDetail[]>([])
  const [creating, setCreating] = useState(false)
  const [createKeyword, setCreateKeyword] = useState('')
  const [createMax, setCreateMax] = useState<number>(4)
  /** 建组仪式：星尘聚合（1.6s 后自动消散） */
  const [ceremony, setCeremony] = useState<string | null>(null)
  const ceremonyTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 小程序原生分享（wx.share）；H5 端为 no-op
  useShareAppMessage(() => ({
    title: '来和我组个同路人小队，一起打卡还愿吧',
    path: `/pages/matchSquad/index`,
  }))

  const loadRecommend = useCallback(async (kw?: string) => {
    setLoading(true)
    try {
      const res = await wishApi.recommendMatchGroups({ keyword: kw?.trim() || undefined, pageSize: 20 })
      if (res.data.success) setRecommend(res.data.data ?? [])
    } catch {
      // 推荐失败保持空态
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMyGroups = useCallback(async () => {
    if (!isLoggedIn) {
      setMyGroups([])
      return
    }
    try {
      const res = await wishApi.listMyMatchGroups()
      if (res.data.success) setMyGroups(res.data.data ?? [])
    } catch {
      // 静默
    }
  }, [isLoggedIn])

  useEffect(() => {
    loadRecommend()
    loadMyGroups()
  }, [loadRecommend, loadMyGroups])

  useEffect(() => () => {
    if (ceremonyTimer.current) clearTimeout(ceremonyTimer.current)
  }, [])

  const requireLogin = () => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return false
    }
    return true
  }

  const handleJoin = async (item: MatchGroupItem) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.joinMatchGroup(item.groupId)
      if (res.data.success) {
        Taro.showToast({ title: '已加入小队，一起加油', icon: 'none' })
        loadRecommend(keyword)
        loadMyGroups()
      } else {
        Taro.showToast({ title: res.data.error?.message || '加入失败', icon: 'none' })
      }
    } catch (error) {
      const body = (error as { data?: { error?: { message?: string } } })?.data
      Taro.showToast({ title: body?.error?.message || '加入失败，请稍后重试', icon: 'none' })
    }
  }

  const handleCreate = async () => {
    if (!requireLogin()) return
    const kw = createKeyword.trim()
    if (!kw) {
      Taro.showToast({ title: '先给小队起个主题吧', icon: 'none' })
      return
    }
    setCreating(true)
    try {
      const res = await wishApi.createMatchGroup({ keyword: kw, maxMembers: createMax })
      if (res.data.success) {
        setCreateKeyword('')
        setCeremony(kw)
        if (ceremonyTimer.current) clearTimeout(ceremonyTimer.current)
        ceremonyTimer.current = setTimeout(() => setCeremony(null), 1700)
        loadRecommend()
        loadMyGroups()
      }
    } catch {
      Taro.showToast({ title: '创建失败，请稍后重试', icon: 'none' })
    } finally {
      setCreating(false)
    }
  }

  const handleLeave = async (groupId: number) => {
    if (!user) return
    try {
      await wishApi.leaveMatchGroup(groupId, user.id)
      Taro.showToast({ title: '已退出小队', icon: 'none' })
      loadMyGroups()
      loadRecommend(keyword)
    } catch {
      // 静默
    }
  }

  const handleKick = async (groupId: number, targetUserId: number) => {
    try {
      await wishApi.leaveMatchGroup(groupId, targetUserId)
      Taro.showToast({ title: '已移出该成员', icon: 'none' })
      loadMyGroups()
    } catch {
      // 静默
    }
  }

  const handleDissolve = async (groupId: number) => {
    try {
      await wishApi.dissolveMatchGroup(groupId)
      Taro.showToast({ title: '小队已解散', icon: 'none' })
      loadMyGroups()
    } catch {
      // 静默
    }
  }

  const handleRemind = async (groupId: number, targetUserId?: number) => {
    try {
      await wishApi.remindSquadMembers(groupId, targetUserId)
      Taro.showToast({ title: '提醒已送达', icon: 'none' })
    } catch {
      Taro.showToast({ title: '提醒发送失败或已达今日上限', icon: 'none' })
    }
  }

  const handleShare = (group: MatchGroupDetail) => {
    // H5 复制文案降级；小程序走 useShareAppMessage 原生分享
    Taro.setClipboardData({
      data: `来和我组个同路人小队「${group.keyword}」，一起打卡还愿吧`,
    }).catch(() => undefined)
  }

  return (
    <View className={styles.container}>
      <CustomNavBar title="同路人小队" back />
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <View className={styles.body}>
          {/* 建组 */}
          <View className={styles.card}>
            <Text className={styles.cardTitle}>创建同路人小队</Text>
            <Input
              className={styles.input}
              value={createKeyword}
              onInput={(e) => setCreateKeyword(e.detail.value)}
              maxlength={MAX_KEYWORD}
              placeholder="小队主题，如「坚持晨跑一百天」"
            />
            <View className={styles.createFooter}>
              <Picker
                mode='selector'
                range={['2 人小队', '3 人小队', '4 人小队']}
                value={createMax - 2}
                onChange={(e) => setCreateMax(Number(e.detail.value) + 2)}
              >
                <View className={styles.maxPicker}>
                  <Text>{createMax} 人小队 ▾</Text>
                </View>
              </Picker>
              <View
                className={`${styles.primaryBtn} ${creating || !createKeyword.trim() ? styles.btnDisabled : ''}`}
                onClick={handleCreate}
              >
                <Text className={styles.primaryBtnText}>{creating ? '创建中...' : '召唤同路人'}</Text>
              </View>
            </View>
          </View>

          {/* 匹配推荐 */}
          <View className={styles.card}>
            <Text className={styles.cardTitle}>同路人匹配</Text>
            <View className={styles.searchRow}>
              <Input
                className={styles.searchInput}
                value={keyword}
                onInput={(e) => setKeyword(e.detail.value)}
                maxlength={MAX_KEYWORD}
                placeholder='输入关键词，如「看极光」'
              />
              <View className={styles.searchBtn} onClick={() => loadRecommend(keyword)}>
                <Text>匹配</Text>
              </View>
            </View>
            {loading ? (
              <Text className={styles.emptyText}>正在为你寻找同路人...</Text>
            ) : recommend.length === 0 ? (
              <Text className={styles.emptyText}>暂时没有匹配的小队，换个关键词试试</Text>
            ) : (
              recommend.map((item) => (
                <View key={item.groupId} className={styles.groupCard}>
                  <View className={styles.groupTop}>
                    <Text className={styles.keyword}>「{item.keyword}」</Text>
                    <Text className={styles.scorePill}>相似度 {Math.round(item.matchScore * 100)}%</Text>
                  </View>
                  <Text className={styles.reason}>{item.matchReason}</Text>
                  <View className={styles.groupFooter}>
                    <Text className={styles.meta}>
                      {item.leaderNickname} 发起 · {item.memberCount}/{item.maxMembers} 人
                    </Text>
                    <View className={styles.joinBtn} onClick={() => handleJoin(item)}>
                      <Text className={styles.joinBtnText}>加入</Text>
                    </View>
                  </View>
                </View>
              ))
            )}
          </View>

          {/* 我的小队 */}
          <View className={styles.card}>
            <Text className={styles.cardTitle}>我的小队</Text>
            {myGroups.length === 0 ? (
              <Text className={styles.emptyText}>还没有加入任何小队</Text>
            ) : (
              myGroups.map((group) => {
                const isLeader = group.viewerRole === 'LEADER'
                return (
                  <View key={group.groupId} className={styles.groupCard}>
                    <View className={styles.groupTop}>
                      <Text className={styles.keyword}>「{group.keyword}」</Text>
                      <Text className={styles.statusTag}>{STATUS_LABEL[group.status]}</Text>
                    </View>
                    {group.members.map((member) => (
                      <View key={member.userId} className={styles.memberRow}>
                        <Text className={styles.memberName}>
                          {member.role === 'LEADER' ? '👑 ' : ''}
                          {member.nickname}
                          {member.idleDays !== null && member.idleDays >= IDLE_DAYS ? `（${member.idleDays} 天未打卡）` : ''}
                        </Text>
                        <View className={styles.memberActions}>
                          {member.userId !== user?.id && (member.idleDays === null || member.idleDays >= IDLE_DAYS) && (
                            <View className={styles.miniBtn} onClick={() => handleRemind(group.groupId, member.userId)}>
                              <Text className={styles.miniBtnText}>提醒</Text>
                            </View>
                          )}
                          {isLeader && member.role !== 'LEADER' && (
                            <View className={styles.miniBtn} onClick={() => handleKick(group.groupId, member.userId)}>
                              <Text className={styles.miniBtnTextDanger}>移出</Text>
                            </View>
                          )}
                        </View>
                      </View>
                    ))}
                    <View className={styles.groupFooter}>
                      <Text className={styles.meta}>{group.memberCount}/{group.maxMembers} 人</Text>
                      <View className={styles.memberActions}>
                        <View className={styles.miniBtn} onClick={() => handleShare(group)}>
                          <Text className={styles.miniBtnText}>分享</Text>
                        </View>
                        {isLeader ? (
                          <View className={styles.miniBtn} onClick={() => handleDissolve(group.groupId)}>
                            <Text className={styles.miniBtnTextDanger}>解散</Text>
                          </View>
                        ) : (
                          <View className={styles.miniBtn} onClick={() => handleLeave(group.groupId)}>
                            <Text className={styles.miniBtnText}>退出</Text>
                          </View>
                        )}
                      </View>
                    </View>
                  </View>
                )
              })
            )}
          </View>
        </View>
      </View>

      {ceremony && (
        <View className={styles.ceremony}>
          {Array.from({ length: 12 }, (_, i) => (
            <View
              key={i}
              className={styles.ceremonyStar}
              style={{
                left: `${50 + Math.round(Math.cos((i / 12) * Math.PI * 2) * 26)}%`,
                top: `${38 + Math.round(Math.sin((i / 12) * Math.PI * 2) * 14)}%`,
                animationDelay: `${(i % 5) * 0.08}s`,
              }}
            />
          ))}
          <Text className={styles.ceremonyTitle}>「{ceremony}」小队已建立</Text>
        </View>
      )}
      <WishBGM />
    </View>
  )
}
