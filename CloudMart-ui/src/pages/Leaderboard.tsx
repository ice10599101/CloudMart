import { useEffect, useState } from 'react'
import { Skeleton } from 'antd'
import { history } from 'umi'
import { WeakNetworkBanner } from '@/components/StateFeedback'
import { getLeaderboard, LEADERBOARD_LABELS, type LeaderboardEntry, type LeaderboardType } from '@/api/wish'
import WishBGM from '@/components/WishBGM'
import styles from './Leaderboard.module.css'

const BOARDS: LeaderboardType[] = ['HOT', 'WARM', 'PERSISTENCE', 'SPARK']

const BOARD_HINTS: Record<LeaderboardType, string> = {
  HOT: '心愿点亮数 · 每 10 分钟刷新',
  WARM: '心愿祝福数 · 每 10 分钟刷新',
  PERSISTENCE: '累计打卡天数 · 每 10 分钟刷新',
  SPARK: '累计帮助他人次数 · 每 10 分钟刷新',
}

const BOARD_ICONS: Record<LeaderboardType, string> = {
  HOT: '🔥',
  WARM: '🌤️',
  PERSISTENCE: '📅',
  SPARK: '✨',
}

function DeltaBadge({ delta }: { delta: LeaderboardEntry['rankDelta'] }) {
  if (delta === 'NEW') {
    return <span className={`${styles.delta} ${styles.deltaNew}`}>NEW</span>
  }
  if (delta === 'UP') {
    return <span className={`${styles.delta} ${styles.deltaUp}`}>▲</span>
  }
  if (delta === 'DOWN') {
    return <span className={`${styles.delta} ${styles.deltaDown}`}>▼</span>
  }
  return <span className={`${styles.delta} ${styles.deltaFlat}`}>—</span>
}

function BoardPanel({ type }: { type: LeaderboardType }) {
  const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getLeaderboard(type, 100)
      .then((res) => {
        if (!cancelled && res.data.success) setEntries(res.data.data ?? [])
      })
      .catch(() => {
        if (!cancelled) setEntries([])
      })
    return () => {
      cancelled = true
    }
  }, [type])

  if (entries === null) {
    return (
      <div className={styles.boardCard}>
        <h3 className={styles.boardTitle}>{BOARD_ICONS[type]} {LEADERBOARD_LABELS[type]}</h3>
        <Skeleton active paragraph={{ rows: 6 }} title={false} />
      </div>
    )
  }

  return (
    <div className={styles.boardCard}>
      <h3 className={styles.boardTitle}>{BOARD_ICONS[type]} {LEADERBOARD_LABELS[type]}</h3>
      <p className={styles.boardHint}>{BOARD_HINTS[type]} · Top 100</p>
      {entries.length === 0 ? (
        <p className={styles.emptyText}>榜单虚位以待，快去点亮第一颗星</p>
      ) : (
        entries.map((entry) => (
          <div
            key={`${entry.rank}-${entry.userId}`}
            className={styles.entryRow}
            onClick={() => entry.userId && history.push(`/user/${entry.userId}`)}
          >
            <span className={`${styles.rankBadge} ${entry.rank === 1 ? styles.rankTop1 : entry.rank === 2 ? styles.rankTop2 : entry.rank === 3 ? styles.rankTop3 : ''}`}>
              {entry.rank}
            </span>
            <div className={styles.entryMain}>
              <div className={styles.entryName}>{entry.nickname}</div>
              <div className={styles.entrySub}>
                {entry.extra.wishTitle
                  ? `《${entry.extra.wishTitle}》`
                  : `打卡 ${entry.extra.checkinDays ?? 0} 天 · 帮助 ${entry.extra.helpedCount ?? 0} 次`}
              </div>
            </div>
            <span className={styles.entryScore}>{entry.score}</span>
            <DeltaBadge delta={entry.rankDelta} />
          </div>
        ))
      )}
    </div>
  )
}

export default function Leaderboard() {
  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <WeakNetworkBanner />
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>🏆 心愿排行榜</h1>
            <p className={styles.pageSubtitle}>四大榜单 · Top 100 · 每 10 分钟刷新 · 排名变化一目了然</p>
          </div>
        </div>
        <div className={styles.boardGrid}>
          {BOARDS.map((type) => (
            <BoardPanel key={type} type={type} />
          ))}
        </div>
      </div>
      <WishBGM />
    </div>
  )
}
