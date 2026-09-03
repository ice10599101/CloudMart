import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, Modal, Alert } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import { ACTIVITY_TYPE_LABELS, type ActivityItem, type ActivityBoardMember } from '@/types'

/**
 * 社区活动（Sprint 3.5，四AC R4 APP 端）：
 * 列表（类型筛选）→ 详情弹层（参与/合伙人申请/组队看板）。
 */
export default function ActivitiesScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)

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
      if (res.data?.success) setActivities(res.data.data ?? [])
    } catch {
      // 列表加载失败保持空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadList(typeFilter)
  }, [typeFilter, loadList])

  const requireLogin = () => {
    if (isLoggedIn) return true
    router.push('/login')
    return false
  }

  const openDetail = async (id: number | string) => {
    try {
      const [detailRes, progressRes] = await Promise.all([wishApi.getActivity(id), wishApi.getActivityProgress(id)])
      if (detailRes.data?.success) setDetail(detailRes.data.data)
      if (progressRes.data?.success) setProgress(progressRes.data.data)
    } catch {
      // 详情加载失败保持列表
    }
  }

  const handleJoin = async (id: number | string) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.joinActivity(id)
      if (res.data?.success) {
        Alert.alert('社区活动', '参与成功')
        setDetail(null)
        loadList(typeFilter)
      }
    } catch {
      Alert.alert('社区活动', '参与失败，请稍后重试')
    }
  }

  const handleViewBoard = async (id: number | string) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.getPartnerBoard(id)
      if (res.data?.success) {
        setBoard(res.data.data?.members ?? [])
        setBoardVisible(true)
      }
    } catch {
      Alert.alert('社区活动', '仅组内成员可查看看板')
    }
  }

  const handleApply = async () => {
    if (!detail || !applyWishId.trim() || applying) return
    const skills = applySkills.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    setApplying(true)
    try {
      const res = await wishApi.applyPartner(detail.id, applyWishId.trim(), skills.length ? skills : undefined)
      if (res.data?.success) {
        Alert.alert('社区活动', '申请已提交，等待招募发起人审批')
        setApplyVisible(false)
        setApplyWishId('')
        setApplySkills('')
      }
    } catch {
      Alert.alert('社区活动', '申请失败，请稍后重试')
    } finally {
      setApplying(false)
    }
  }

  const filterChips: [string | undefined, string][] = [
    [undefined, '全部'],
    ...Object.entries(ACTIVITY_TYPE_LABELS).map(([value, label]) => [value, label] as [string, string]),
  ]

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>←</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>🎪 社区活动</Text>
        <View style={{ width: 24 }} />
      </View>
      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, paddingHorizontal: Spacing.md, marginBottom: Spacing.sm }}>
        世界事件 · 节日活动 · 城市活动 · 心愿合伙人
      </Text>

      {/* 类型筛选 */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, paddingHorizontal: Spacing.md, marginBottom: Spacing.sm }}>
        {filterChips.map(([value, label]) => (
          <TouchableOpacity
            key={label}
            activeOpacity={0.8}
            onPress={() => setTypeFilter(value)}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: 6,
              borderRadius: 20,
              backgroundColor: typeFilter === value ? 'rgba(233, 69, 96, 0.2)' : WishColors.bgElevated,
            }}
          >
            <Text style={{ fontSize: FontSize.xs, fontWeight: typeFilter === value ? '700' : '400', color: typeFilter === value ? WishColors.primary : WishColors.textSecondary }}>
              {label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + Spacing.xl }}>
        {loading ? (
          <ActivityIndicator color={WishColors.primary} style={{ marginTop: Spacing.xl }} />
        ) : activities.length === 0 ? (
          <Text style={{ textAlign: 'center', marginTop: Spacing.xl, color: WishColors.textTertiary }}>暂无进行中的活动</Text>
        ) : (
          activities.map((activity) => (
            <TouchableOpacity
              key={activity.id}
              activeOpacity={0.8}
              onPress={() => openDetail(activity.id)}
              style={{
                backgroundColor: WishColors.bgElevated,
                borderRadius: BorderRadius.xl,
                padding: Spacing.lg,
                marginBottom: Spacing.md,
              }}
            >
              <View style={{ alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 2, borderRadius: 12, backgroundColor: 'rgba(139, 92, 246, 0.16)', marginBottom: Spacing.xs }}>
                <Text style={{ fontSize: FontSize.xs, color: '#a78bfa' }}>{ACTIVITY_TYPE_LABELS[activity.type]}</Text>
              </View>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: 6 }}>{activity.title}</Text>
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginBottom: Spacing.sm }} numberOfLines={2}>
                {activity.description ?? '—'}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{activity.progressCounter} 人参与</Text>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>

      {/* 活动详情弹层 */}
      <Modal visible={!!detail} transparent animationType="slide" onRequestClose={() => setDetail(null)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'flex-end' }}>
          <TouchableOpacity style={{ flex: 1 }} activeOpacity={1} onPress={() => setDetail(null)} />
          <View
            style={{
              backgroundColor: '#131a35',
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.xl,
              paddingBottom: insets.bottom + Spacing.lg,
            }}
          >
            {detail && (
              <>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.sm }}>
                  {detail.title}
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, lineHeight: 20, marginBottom: Spacing.md }}>
                  {detail.description ?? '—'}
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.text, marginBottom: Spacing.lg }}>
                  当前进度：<Text style={{ color: WishColors.accentGold, fontWeight: '700' }}>{progress}</Text> 人参与
                </Text>
                {detail.type !== 'WISH_PARTNER' ? (
                  <TouchableOpacity
                    activeOpacity={0.8}
                    onPress={() => handleJoin(detail.id)}
                    style={{ alignItems: 'center', paddingVertical: Spacing.sm, borderRadius: 24, backgroundColor: 'rgba(233, 69, 96, 0.25)' }}
                  >
                    <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>参与活动</Text>
                  </TouchableOpacity>
                ) : (
                  <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
                    <TouchableOpacity
                      activeOpacity={0.8}
                      onPress={() => setApplyVisible(true)}
                      style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.sm, borderRadius: 24, backgroundColor: 'rgba(233, 69, 96, 0.25)' }}
                    >
                      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>申请加入</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      activeOpacity={0.8}
                      onPress={() => handleViewBoard(detail.id)}
                      style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.sm, borderRadius: 24, backgroundColor: WishColors.bgElevated }}
                    >
                      <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>查看组队看板</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </>
            )}
          </View>
        </View>
      </Modal>

      {/* 合伙人申请弹层 */}
      <Modal visible={applyVisible} transparent animationType="slide" onRequestClose={() => setApplyVisible(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'flex-end' }}>
          <TouchableOpacity style={{ flex: 1 }} activeOpacity={1} onPress={() => setApplyVisible(false)} />
          <View
            style={{
              backgroundColor: '#131a35',
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.xl,
              paddingBottom: insets.bottom + Spacing.lg,
            }}
          >
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.md }}>
              申请加入合伙人
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: 6 }}>
              协作心愿 ID（你公开心愿详情页可复制）
            </Text>
            <TextInput
              value={applyWishId}
              onChangeText={setApplyWishId}
              placeholder="如 1933884726512..."
              placeholderTextColor={WishColors.textTertiary}
              style={{
                backgroundColor: WishColors.bgElevated,
                borderRadius: BorderRadius.lg,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.sm,
                color: WishColors.text,
                marginBottom: Spacing.md,
              }}
            />
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: 6 }}>
              技能标签（逗号分隔，如 design,video）
            </Text>
            <TextInput
              value={applySkills}
              onChangeText={setApplySkills}
              placeholder="design,video"
              placeholderTextColor={WishColors.textTertiary}
              style={{
                backgroundColor: WishColors.bgElevated,
                borderRadius: BorderRadius.lg,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.sm,
                color: WishColors.text,
                marginBottom: Spacing.lg,
              }}
            />
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={handleApply}
              style={{ alignItems: 'center', paddingVertical: Spacing.sm, borderRadius: 24, backgroundColor: 'rgba(233, 69, 96, 0.25)' }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>
                {applying ? '提交中...' : '提交申请'}
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* 组队看板弹层 */}
      <Modal visible={boardVisible} transparent animationType="slide" onRequestClose={() => setBoardVisible(false)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'flex-end' }}>
          <TouchableOpacity style={{ flex: 1 }} activeOpacity={1} onPress={() => setBoardVisible(false)} />
          <View
            style={{
              backgroundColor: '#131a35',
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.xl,
              paddingBottom: insets.bottom + Spacing.lg,
              maxHeight: '70%',
            }}
          >
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.md }}>组队看板</Text>
            <ScrollView>
              {board.map((member) => (
                <View key={member.userId} style={{ flexDirection: 'row', gap: Spacing.sm, paddingVertical: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.lg }}>{member.role === 'LEADER' ? '👑' : '👤'}</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={{ fontSize: FontSize.sm, color: WishColors.text, marginBottom: 2 }}>心愿：{member.title ?? '—'}</Text>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                      进度 {member.progressPercentage}% · 打卡 {member.checkinDays} 天
                    </Text>
                    {member.latestGrowth && (
                      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>📝 {member.latestGrowth}</Text>
                    )}
                  </View>
                </View>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  )
}
