import { View, Text, FlatList, TouchableOpacity } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { LeaderboardEntry, LeaderboardType } from '@/types'

const BOARDS: Array<{ type: LeaderboardType; label: string; icon: string; hint: string }> = [
  { type: 'HOT', label: '热门榜', icon: '🔥', hint: '心愿点亮数' },
  { type: 'WARM', label: '温暖榜', icon: '🌤️', hint: '心愿祝福数' },
  { type: 'PERSISTENCE', label: '坚持榜', icon: '📅', hint: '累计打卡天数' },
  { type: 'SPARK', label: '星火榜', icon: '✨', hint: '帮助他人次数' },
]

/** 排名变化徽标（三端一致语义：▲红上升 / ▼绿下降 / NEW 金新上榜） */
function DeltaBadge({ delta }: { delta: LeaderboardEntry['rankDelta'] }) {
  const color = delta === 'UP' ? '#ff6b6b' : delta === 'DOWN' ? '#3ddc97' : delta === 'NEW' ? WishColors.accentGold : 'rgba(255,255,255,0.3)'
  const label = delta === 'NEW' ? 'NEW' : delta === 'UP' ? '▲' : delta === 'DOWN' ? '▼' : '—'
  return (
    <Text style={{ width: 36, textAlign: 'center', fontSize: FontSize.xs, fontWeight: '700', color }}>
      {label}
    </Text>
  )
}

function rankBadgeStyle(rank: number) {
  if (rank === 1) return { backgroundColor: '#ffd700', color: '#1a1a2e' }
  if (rank === 2) return { backgroundColor: '#c0c0c0', color: '#1a1a2e' }
  if (rank === 3) return { backgroundColor: '#cd7f32', color: '#fff' }
  return { backgroundColor: 'rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.6)' }
}

export default function LeaderboardScreen() {
  const insets = useSafeAreaInsets()
  const [activeBoard, setActiveBoard] = useState<LeaderboardType>('HOT')
  const [entries, setEntries] = useState<LeaderboardEntry[]>([])
  const [loading, setLoading] = useState(true)

  const loadBoard = useCallback(async (type: LeaderboardType) => {
    setLoading(true)
    try {
      const res = await wishApi.getLeaderboard(type, 100)
      if (res.data.success) setEntries(res.data.data ?? [])
    } catch {
      setEntries([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadBoard(activeBoard)
  }, [activeBoard, loadBoard])

  const meta = BOARDS.find((b) => b.type === activeBoard)!

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
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>心愿排行榜</Text>
        <View style={{ width: 40 }} />
      </View>

      {/* 榜单切换 */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, padding: Spacing.md }}>
        {BOARDS.map((board) => (
          <TouchableOpacity
            key={board.type}
            accessibilityLabel={`切换到${board.label}`}
            onPress={() => setActiveBoard(board.type)}
            style={{
              flex: 1,
              paddingVertical: 8,
              borderRadius: BorderRadius.full,
              alignItems: 'center',
              backgroundColor: activeBoard === board.type ? WishColors.primary : 'rgba(255,255,255,0.06)',
            }}
          >
            <Text
              style={{
                fontSize: FontSize.xs,
                color: activeBoard === board.type ? '#fff' : WishColors.textSecondary,
                fontWeight: activeBoard === board.type ? '700' : '400',
              }}
            >
              {board.icon} {board.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={entries}
        keyExtractor={(item, index) => `${item.rank}-${item.userId}-${index}`}
        renderItem={({ item }) => {
          const badge = rankBadgeStyle(item.rank)
          return (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: Spacing.sm,
                paddingVertical: Spacing.sm,
                paddingHorizontal: Spacing.md,
                borderBottomWidth: 1,
                borderBottomColor: 'rgba(255,255,255,0.06)',
              }}
            >
              <View
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: 8,
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: badge.backgroundColor,
                }}
              >
                <Text style={{ fontSize: FontSize.sm, fontWeight: '700', color: badge.color }}>{item.rank}</Text>
              </View>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }} numberOfLines={1}>
                  {item.nickname}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }} numberOfLines={1}>
                  {item.extra.wishTitle
                    ? `《${item.extra.wishTitle}》`
                    : `打卡 ${item.extra.checkinDays ?? 0} 天 · 帮助 ${item.extra.helpedCount ?? 0} 次`}
                </Text>
              </View>
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.accentGold }}>
                {item.score}
              </Text>
              <DeltaBadge delta={item.rankDelta} />
            </View>
          )
        }}
        ListHeaderComponent={
          <Text
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: Spacing.sm,
              fontSize: FontSize.xs,
              color: WishColors.textTertiary,
            }}
          >
            {meta.icon} {meta.label} · {meta.hint} · Top 100 · 每 10 分钟刷新
          </Text>
        }
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: WishColors.textTertiary, padding: Spacing.xl, fontSize: FontSize.sm }}>
            {loading ? '加载中...' : '榜单虚位以待，快去点亮第一颗星'}
          </Text>
        }
        contentContainerStyle={{ paddingBottom: insets.bottom + Spacing.xl }}
      />
    </View>
  )
}
