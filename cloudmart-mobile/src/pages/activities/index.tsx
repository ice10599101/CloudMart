import { View, Text, ScrollView, Input } from '@tarojs/components'
import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { ACTIVITY_TYPE_LABELS, type ActivityItem, type ActivityBoardMember } from '@/types'
import styles from './index.module.scss'

/** 申请合伙人时的协作心愿 ID 输入提示文案 */
const WISH_ID_HINT = '协作心愿 ID（你公开心愿详情页可复制）'

/**
 * 社区活动（Sprint 3.5，四AC R3 Mobile）：
 * 列表（类型筛选）→ 详情弹层（参与/合伙人申请/组队看板）。
 */
export default function ActivitiesPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()

  const [activities, setActivities] = useState<ActivityItem[]>([])
  const [loading, setLoading] = useState(true)
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined)

  const [detail, setDetail] = useState<ActivityItem | null>(null)
  const [progress, setProgress] = useState(0)
  const [board, setBoard] = useState<ActivityBoardMember[]>([])
  const [boardVisible, setBoardVisible] = useState(false)
  const [applyVisible, setApplyVisible] = useState(false)
  const [applyWishId, setApplyWishId] = useState('')
  const [applySkills, setApplySkills] = useState('')
  const [applying, setApplying] = useState(false)

  const loadList = useCallback(async (type?: string) => {
    setLoading(true)
    try {
      const res = await wishApi.listActivities(type ? { type } : undefined)
      if (res.data.success) setActivities(res.data.data ?? [])
    } catch {
      // 列表加载失败保持空态即可
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadList(typeFilter)
  }, [typeFilter, loadList])

  const toastError = (err: unknown, fallback: string) => {
    const errNode = err as { data?: { error?: { message?: string } } }
    Taro.showToast({ title: errNode?.data?.error?.message || fallback, icon: 'none' })
  }

  const requireLogin = () => {
    if (isLoggedIn) return true
    Taro.navigateTo({ url: '/pages/login/index' })
    return false
  }

  const openDetail = async (id: number | string) => {
    try {
      const [detailRes, progressRes] = await Promise.all([wishApi.getActivity(id), wishApi.getActivityProgress(id)])
      if (detailRes.data.success) setDetail(detailRes.data.data)
      if (progressRes.data.success) setProgress(progressRes.data.data)
    } catch {
      // 详情加载失败保持列表
    }
  }

  const handleJoin = async (id: number | string) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.joinActivity(id)
      if (res.data.success) {
        Taro.showToast({ title: '参与成功', icon: 'none' })
        setDetail(null)
        loadList(typeFilter)
      }
    } catch (err) {
      toastError(err, '参与失败，请稍后重试')
    }
  }

  const handleViewBoard = async (id: number | string) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.getPartnerBoard(id)
      if (res.data.success) {
        setBoard(res.data.data?.members ?? [])
        setBoardVisible(true)
      }
    } catch {
      Taro.showToast({ title: '仅组内成员可查看看板', icon: 'none' })
    }
  }

  const handleApply = async () => {
    if (!detail || !applyWishId.trim() || applying) return
    const skills = applySkills.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    setApplying(true)
    try {
      const res = await wishApi.applyPartner(detail.id, applyWishId.trim(), skills.length ? skills : undefined)
      if (res.data.success) {
        Taro.showToast({ title: '申请已提交，等待审批', icon: 'none' })
        setApplyVisible(false)
        setApplyWishId('')
        setApplySkills('')
      }
    } catch (err) {
      toastError(err, '申请失败，请稍后重试')
    } finally {
      setApplying(false)
    }
  }

  const filterChips: [string | undefined, string][] = [
    [undefined, '全部'],
    ...Object.entries(ACTIVITY_TYPE_LABELS).map(([value, label]) => [value, label] as [string, string]),
  ]

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="社区活动" back />
      <ScrollView className={styles.body} scrollY>
        <Text className={styles.pageTitle}>🎪 社区活动</Text>
        <Text className={styles.pageSubtitle}>世界事件 · 节日活动 · 城市活动 · 心愿合伙人</Text>

        <View className={styles.filterRow}>
          {filterChips.map(([value, label]) => (
            <View
              key={label}
              className={`${styles.chip} ${typeFilter === value ? styles.chipActive : ''}`}
              onClick={() => setTypeFilter(value)}
            >
              <Text>{label}</Text>
            </View>
          ))}
        </View>

        {loading ? (
          <View className={styles.empty}>
            <Text>加载中...</Text>
          </View>
        ) : activities.length === 0 ? (
          <View className={styles.empty}>
            <Text>暂无进行中的活动</Text>
          </View>
        ) : (
          activities.map((activity) => (
            <View key={activity.id} className={styles.activityCard} onClick={() => openDetail(activity.id)}>
              <Text className={styles.typeTag}>{ACTIVITY_TYPE_LABELS[activity.type]}</Text>
              <Text className={styles.activityTitle}>{activity.title}</Text>
              <Text className={styles.activityDesc}>{activity.description ?? '—'}</Text>
              <Text className={styles.progressNum}>{activity.progressCounter} 人参与</Text>
            </View>
          ))
        )}
      </ScrollView>

      {/* 活动详情弹层 */}
      {detail && (
        <View className={styles.mask} onClick={() => setDetail(null)}>
          <View className={styles.sheet} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.sheetTitle}>{detail.title}</Text>
            <Text className={styles.sheetDesc}>{detail.description ?? '—'}</Text>
            <Text className={styles.sheetProgress}>
              当前进度：<Text className={styles.num}>{progress}</Text> 人参与
            </Text>
            {detail.type !== 'WISH_PARTNER' ? (
              <View className={styles.actionRow}>
                <View className={styles.primaryBtn} onClick={() => handleJoin(detail.id)}>
                  <Text>参与活动</Text>
                </View>
              </View>
            ) : (
              <View className={styles.actionRow}>
                <View className={styles.primaryBtn} onClick={() => setApplyVisible(true)}>
                  <Text>申请加入</Text>
                </View>
                <View className={styles.plainBtn} onClick={() => handleViewBoard(detail.id)}>
                  <Text>查看组队看板</Text>
                </View>
              </View>
            )}
            <View className={styles.closeBtn} onClick={() => setDetail(null)}>
              <Text>关闭</Text>
            </View>
          </View>
        </View>
      )}

      {/* 合伙人申请弹层 */}
      {applyVisible && detail && (
        <View className={styles.mask} onClick={() => setApplyVisible(false)}>
          <View className={styles.sheet} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.sheetTitle}>申请加入合伙人</Text>
            <Text className={styles.inputHint}>{WISH_ID_HINT}</Text>
            <Input
              className={styles.input}
              type="text"
              value={applyWishId}
              placeholder="如 1933884726512..."
              onInput={(e) => setApplyWishId(e.detail.value)}
            />
            <Text className={styles.inputHint}>技能标签（逗号分隔，如 design,video）</Text>
            <Input
              className={styles.input}
              type="text"
              value={applySkills}
              placeholder="design,video"
              onInput={(e) => setApplySkills(e.detail.value)}
            />
            <View className={styles.actionRow}>
              <View className={styles.primaryBtn} onClick={handleApply}>
                <Text>{applying ? '提交中...' : '提交申请'}</Text>
              </View>
            </View>
            <View className={styles.closeBtn} onClick={() => setApplyVisible(false)}>
              <Text>取消</Text>
            </View>
          </View>
        </View>
      )}

      {/* 组队看板弹层 */}
      {boardVisible && (
        <View className={styles.mask} onClick={() => setBoardVisible(false)}>
          <View className={styles.sheet} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.sheetTitle}>组队看板</Text>
            {board.map((member) => (
              <View key={member.userId} className={styles.boardRow}>
                <Text className={styles.boardRole}>{member.role === 'LEADER' ? '👑' : '👤'}</Text>
                <View className={styles.boardMain}>
                  <Text className={styles.boardTitle}>心愿：{member.title ?? '—'}</Text>
                  <Text className={styles.boardStats}>
                    进度 {member.progressPercentage}% · 打卡 {member.checkinDays} 天
                  </Text>
                  {member.latestGrowth && <Text className={styles.boardGrowth}>📝 {member.latestGrowth}</Text>}
                </View>
              </View>
            ))}
            <View className={styles.closeBtn} onClick={() => setBoardVisible(false)}>
              <Text>关闭</Text>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
