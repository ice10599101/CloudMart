import { useState } from 'react'
import { ScrollView, Text, View } from '@tarojs/components'
import { wishApi } from '@/api/wish'
import type { LeaderboardEntry, LeaderboardType } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import styles from './index.module.scss'

const BOARDS: Array<{ type: LeaderboardType; label: string; icon: string; hint: string }> = [
  { type: 'HOT', label: '热门榜', icon: '🔥', hint: '心愿点亮数' },
  { type: 'WARM', label: '温暖榜', icon: '🌤️', hint: '心愿祝福数' },
  { type: 'PERSISTENCE', label: '坚持榜', icon: '📅', hint: '累计打卡天数' },
  { type: 'SPARK', label: '星火榜', icon: '✨', hint: '帮助他人次数' },
]

/** 排名变化徽标（三端一致语义：▲红上升 / ▼绿下降 / NEW 金新上榜） */
function DeltaBadge({ delta, cls }: { delta: LeaderboardEntry['rankDelta']; cls: Record<string, string> }) {
  if (delta === 'NEW') return <Text className={`${cls.delta} ${cls.deltaNew}`}>NEW</Text>
  if (delta === 'UP') return <Text className={`${cls.delta} ${cls.deltaUp}`}>▲</Text>
  if (delta === 'DOWN') return <Text className={`${cls.delta} ${cls.deltaDown}`}>▼</Text>
  return <Text className={`${cls.delta} ${cls.deltaFlat}`}>—</Text>
}

function BoardPanel({ type, cls }: { type: LeaderboardType; cls: Record<string, string> }) {
  const meta = BOARDS.find((b) => b.type === type)!
  const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null)

  // 子组件挂载即拉取（父级 Tab 切换渲染，无跨 Tab 缓存一致性问题）
  useState(() => {
    wishApi
      .getLeaderboard(type, 100)
      .then((res) => {
        if (res.data.success) setEntries(res.data.data ?? [])
        else setEntries([])
      })
      .catch(() => setEntries([]))
  })

  return (
    <View className={styles.boardCard}>
      <Text className={styles.boardTitle}>
        {meta.icon} {meta.label}
      </Text>
      <Text className={styles.boardHint}>{meta.hint} · Top 100 · 每 10 分钟刷新</Text>
      {entries === null ? (
        <Text className={styles.emptyText}>加载中...</Text>
      ) : entries.length === 0 ? (
        <Text className={styles.emptyText}>榜单虚位以待，快去点亮第一颗星</Text>
      ) : (
        entries.map((entry) => (
          <View key={`${entry.rank}-${entry.userId}`} className={styles.entryRow}>
            <Text className={`${styles.rankBadge} ${entry.rank <= 3 ? styles.rankTopN : ''}`}>{entry.rank}</Text>
            <View className={styles.entryMain}>
              <Text className={styles.entryName}>{entry.nickname}</Text>
              <Text className={styles.entrySub}>
                {entry.extra.wishTitle
                  ? `《${entry.extra.wishTitle}》`
                  : `打卡 ${entry.extra.checkinDays ?? 0} 天 · 帮助 ${entry.extra.helpedCount ?? 0} 次`}
              </Text>
            </View>
            <Text className={styles.entryScore}>{entry.score}</Text>
            <DeltaBadge delta={entry.rankDelta} cls={cls} />
          </View>
        ))
      )}
    </View>
  )
}

export default function LeaderboardPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [activeBoard, setActiveBoard] = useState<LeaderboardType>('HOT')

  return (
    <View className={styles.container}>
      <CustomNavBar title="排行榜" back />
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <View className={styles.boardTabs}>
          {BOARDS.map((board) => (
            <View
              key={board.type}
              className={`${styles.boardTab} ${activeBoard === board.type ? styles.boardTabActive : ''}`}
              onClick={() => setActiveBoard(board.type)}
            >
              <Text className={activeBoard === board.type ? styles.tabTextActive : styles.tabText}>
                {board.icon} {board.label}
              </Text>
            </View>
          ))}
        </View>
        <ScrollView scrollY className={styles.scrollArea} style={{ height: '72vh' }}>
          <View className={styles.body}>
            <BoardPanel type={activeBoard} cls={styles} />
          </View>
        </ScrollView>
      </View>
      <WishBGM />
    </View>
  )
}
