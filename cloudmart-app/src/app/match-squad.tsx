import { View, Text, FlatList, TextInput, TouchableOpacity, Share, Alert } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Haptics from 'expo-haptics'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { MatchGroupDetail, MatchGroupItem } from '@/types'

const MAX_KEYWORD = 60
/** 组员多少天未打卡视为需提醒（与后端 match.remind_idle_days 默认对齐） */
const IDLE_DAYS = 3

const STATUS_LABEL: Record<MatchGroupDetail['status'], string> = {
  OPEN: '招募中',
  FULL: '已满员',
  CLOSED: '已解散',
}

/** 从 axios 异常体提取业务错误信封（与树洞页同模式） */
function extractBusinessError(error: unknown): { code?: string; message?: string } | undefined {
  return (error as { response?: { data?: { error?: { code?: string; message?: string } } } })
    ?.response?.data?.error
}

export default function MatchSquadScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ keyword?: string }>()
  const user = useAuthStore((s) => s.user)

  const [keyword, setKeyword] = useState(params.keyword ?? '')
  const [recommend, setRecommend] = useState<MatchGroupItem[]>([])
  const [loading, setLoading] = useState(true)
  const [myGroups, setMyGroups] = useState<MatchGroupDetail[]>([])
  const [createKeyword, setCreateKeyword] = useState('')
  const [creating, setCreating] = useState(false)

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
    if (!user) {
      setMyGroups([])
      return
    }
    try {
      const res = await wishApi.listMyMatchGroups()
      if (res.data.success) setMyGroups(res.data.data ?? [])
    } catch {
      // 静默
    }
  }, [user])

  useEffect(() => {
    loadRecommend(keyword)
    loadMyGroups()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadRecommend, loadMyGroups])

  const requireLogin = () => {
    if (!user) {
      router.replace('/login')
      return false
    }
    return true
  }

  const handleJoin = async (item: MatchGroupItem) => {
    if (!requireLogin()) return
    try {
      const res = await wishApi.joinMatchGroup(item.groupId)
      if (res.data.success) {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => undefined)
        loadRecommend(keyword)
        loadMyGroups()
      } else {
        Alert.alert('提示', res.data.error?.message ?? '加入失败')
      }
    } catch (error) {
      const business = extractBusinessError(error)
      Alert.alert('提示', business?.code === 'WISH_GROUP_FULL'
        ? '来晚一步，小队刚好满员了'
        : business?.message ?? '加入失败，请稍后重试')
    }
  }

  const handleCreate = async () => {
    if (!requireLogin()) return
    const kw = createKeyword.trim()
    if (!kw) return
    setCreating(true)
    try {
      const res = await wishApi.createMatchGroup({ keyword: kw, maxMembers: 4 })
      if (res.data.success) {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => undefined)
        setCreateKeyword('')
        loadRecommend()
        loadMyGroups()
      }
    } catch {
      Alert.alert('提示', '创建失败，请稍后重试')
    } finally {
      setCreating(false)
    }
  }

  const handleLeave = async (groupId: number) => {
    if (!user) return
    try {
      await wishApi.leaveMatchGroup(groupId, user.id)
      loadMyGroups()
      loadRecommend(keyword)
    } catch {
      // 静默
    }
  }

  const handleKick = (groupId: number, targetUserId: number, nickname: string) => {
    Alert.alert(
      '移出成员',
      `确定移出 ${nickname} 吗？被移出者 24 小时内无法加入同主题小队`,
      [
        { text: '取消', style: 'cancel' },
        {
          text: '确定',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              try {
                await wishApi.leaveMatchGroup(groupId, targetUserId)
                loadMyGroups()
              } catch {
                // 静默
              }
            })()
          },
        },
      ],
    )
  }

  const handleDissolve = (groupId: number) => {
    Alert.alert('解散小队', '解散后所有成员都会收到通知，确定吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '确定解散',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            try {
              await wishApi.dissolveMatchGroup(groupId)
              loadMyGroups()
            } catch {
              // 静默
            }
          })()
        },
      },
    ])
  }

  const handleRemind = async (groupId: number, targetUserId?: number) => {
    try {
      await wishApi.remindSquadMembers(groupId, targetUserId)
      Alert.alert('完成', '提醒已送达，等待伙伴回归吧')
    } catch (error) {
      const business = extractBusinessError(error)
      Alert.alert('提示', business?.message ?? '提醒发送失败或已达今日上限')
    }
  }

  /** 系统分享（文档 2.6：APP 走系统分享） */
  const handleShare = (group: MatchGroupDetail) => {
    Share.share({
      message: `来和我组个同路人小队「${group.keyword}」，一起打卡还愿吧`,
    }).catch(() => undefined)
  }

  const renderRecommendItem = ({ item }: { item: MatchGroupItem }) => (
    <View
      style={{
        backgroundColor: WishColors.bgContainer,
        borderWidth: 1,
        borderColor: WishColors.border,
        borderRadius: BorderRadius.xl,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.accentGold }}>
          「{item.keyword}」
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.primary, fontWeight: '600' }}>
          相似度 {Math.round(item.matchScore * 100)}%
        </Text>
      </View>
      <Text
        style={{
          fontSize: FontSize.sm,
          color: WishColors.textSecondary,
          borderLeftWidth: 3,
          borderLeftColor: WishColors.primary,
          paddingLeft: Spacing.sm,
          marginVertical: Spacing.sm,
        }}
      >
        {item.matchReason}
      </Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
          {item.leaderNickname} 发起 · {item.memberCount}/{item.maxMembers} 人
        </Text>
        <TouchableOpacity
          accessibilityLabel={`加入小队「${item.keyword}」`}
          onPress={() => handleJoin(item)}
          style={{
            paddingHorizontal: Spacing.lg,
            paddingVertical: 6,
            borderRadius: BorderRadius.full,
            backgroundColor: WishColors.primary,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: '#fff' }}>加入</Text>
        </TouchableOpacity>
      </View>
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>同路人小队</Text>
        <View style={{ width: 40 }} />
      </View>

      <FlatList
        data={recommend}
        keyExtractor={(item) => String(item.groupId)}
        renderItem={renderRecommendItem}
        keyboardShouldPersistTaps="handled"
        ListHeaderComponent={
          <View style={{ padding: Spacing.md, paddingBottom: 0 }}>
            {/* 建组 */}
            <View
              style={{
                backgroundColor: WishColors.bgContainer,
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.xl,
                padding: Spacing.md,
                marginBottom: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
                创建同路人小队
              </Text>
              <TextInput
                value={createKeyword}
                onChangeText={setCreateKeyword}
                maxLength={MAX_KEYWORD}
                placeholder="小队主题，如「坚持晨跑一百天」"
                placeholderTextColor={WishColors.textTertiary}
                style={{
                  height: 44,
                  borderRadius: BorderRadius.md,
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  borderWidth: 1,
                  borderColor: 'rgba(255,255,255,0.12)',
                  color: WishColors.text,
                  paddingHorizontal: Spacing.sm,
                  fontSize: FontSize.sm,
                  marginBottom: Spacing.sm,
                }}
              />
              <TouchableOpacity
                accessibilityLabel="创建小队"
                onPress={handleCreate}
                disabled={creating}
                style={{
                  backgroundColor: creating || !createKeyword.trim() ? 'rgba(233,69,96,0.4)' : WishColors.primary,
                  borderRadius: BorderRadius.full,
                  paddingVertical: Spacing.sm,
                  alignItems: 'center',
                }}
              >
                <Text style={{ color: '#fff', fontSize: FontSize.sm }}>
                  {creating ? '创建中...' : '召唤同路人（4 人小队）'}
                </Text>
              </TouchableOpacity>
            </View>

            {/* 搜索 */}
            <View style={{ flexDirection: 'row', gap: Spacing.sm, marginBottom: Spacing.md }}>
              <TextInput
                value={keyword}
                onChangeText={setKeyword}
                maxLength={MAX_KEYWORD}
                placeholder="输入关键词，如「看极光」"
                placeholderTextColor={WishColors.textTertiary}
                onSubmitEditing={() => loadRecommend(keyword)}
                style={{
                  flex: 1,
                  height: 40,
                  borderRadius: BorderRadius.full,
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  borderWidth: 1,
                  borderColor: 'rgba(255,255,255,0.12)',
                  color: WishColors.text,
                  paddingHorizontal: Spacing.md,
                  fontSize: FontSize.sm,
                }}
              />
              <TouchableOpacity
                accessibilityLabel="匹配"
                onPress={() => loadRecommend(keyword)}
                style={{
                  paddingHorizontal: Spacing.lg,
                  borderRadius: BorderRadius.full,
                  borderWidth: 1,
                  borderColor: 'rgba(233,69,96,0.5)',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Text style={{ color: WishColors.primary, fontSize: FontSize.sm }}>匹配</Text>
              </TouchableOpacity>
            </View>

            {/* 我的小队 */}
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
              我的小队
            </Text>
            {myGroups.length === 0 ? (
              <Text
                style={{
                  fontSize: FontSize.sm,
                  color: WishColors.textTertiary,
                  textAlign: 'center',
                  paddingVertical: Spacing.md,
                }}
              >
                还没有加入任何小队
              </Text>
            ) : (
              myGroups.map((group) => {
                const isLeader = group.viewerRole === 'LEADER'
                return (
                  <View
                    key={group.groupId}
                    style={{
                      backgroundColor: WishColors.bgContainer,
                      borderWidth: 1,
                      borderColor: WishColors.border,
                      borderRadius: BorderRadius.xl,
                      padding: Spacing.md,
                      marginBottom: Spacing.sm,
                    }}
                  >
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.accentGold }}>
                        「{group.keyword}」
                      </Text>
                      <Text style={{ fontSize: FontSize.xs, color: '#3ddc97' }}>{STATUS_LABEL[group.status]}</Text>
                    </View>
                    {group.members.map((member) => (
                      <View
                        key={member.userId}
                        style={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          paddingVertical: 6,
                          borderBottomWidth: 1,
                          borderBottomColor: 'rgba(255,255,255,0.06)',
                        }}
                      >
                        <Text style={{ fontSize: FontSize.sm, color: WishColors.text }}>
                          {member.role === 'LEADER' ? '👑 ' : ''}
                          {member.nickname}
                          {member.idleDays !== null && member.idleDays >= IDLE_DAYS
                            ? `（${member.idleDays} 天未打卡）`
                            : ''}
                        </Text>
                        <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
                          {member.userId !== user?.id && (member.idleDays === null || member.idleDays >= IDLE_DAYS) && (
                            <TouchableOpacity
                              accessibilityLabel={`提醒 ${member.nickname}`}
                              onPress={() => handleRemind(group.groupId, member.userId)}
                            >
                              <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>提醒</Text>
                            </TouchableOpacity>
                          )}
                          {isLeader && member.role !== 'LEADER' && (
                            <TouchableOpacity
                              accessibilityLabel={`移出 ${member.nickname}`}
                              onPress={() => handleKick(group.groupId, member.userId, member.nickname)}
                            >
                              <Text style={{ fontSize: FontSize.xs, color: '#ffb347' }}>移出</Text>
                            </TouchableOpacity>
                          )}
                        </View>
                      </View>
                    ))}
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.sm }}>
                      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
                        {group.memberCount}/{group.maxMembers} 人
                      </Text>
                      <View style={{ flexDirection: 'row', gap: Spacing.md }}>
                        <TouchableOpacity accessibilityLabel="分享小队" onPress={() => handleShare(group)}>
                          <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>分享</Text>
                        </TouchableOpacity>
                        {isLeader ? (
                          <TouchableOpacity accessibilityLabel="解散小队" onPress={() => handleDissolve(group.groupId)}>
                            <Text style={{ fontSize: FontSize.xs, color: '#ffb347' }}>解散</Text>
                          </TouchableOpacity>
                        ) : (
                          <TouchableOpacity accessibilityLabel="退出小队" onPress={() => handleLeave(group.groupId)}>
                            <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>退出</Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  </View>
                )
              })
            )}

            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginVertical: Spacing.sm }}>
              同路人推荐
            </Text>
          </View>
        }
        ListEmptyComponent={
          loading ? (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, padding: Spacing.lg }}>
              正在为你寻找同路人...
            </Text>
          ) : (
            <Text style={{ textAlign: 'center', color: WishColors.textTertiary, padding: Spacing.lg }}>
              暂时没有匹配的小队，换个关键词试试
            </Text>
          )
        }
        contentContainerStyle={{ paddingBottom: insets.bottom + Spacing.xxl }}
      />
    </View>
  )
}
