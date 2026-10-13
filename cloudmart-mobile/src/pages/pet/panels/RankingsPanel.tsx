import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Text, View } from '@tarojs/components'
import {
  petApi,
  type PetRankingResult,
  type PetRankingType,
  type PetSeasonHistoryItem,
  type PetSeasonRanking,
  type SeasonPassVO,
} from '@/api/pet'
import { RANKING_TABS, SPECIES_EMOJI, friendlyError } from '../constants'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/**
 * 榜单面板：等级/胜场/捞瓶三榜 + 当前赛季前 3、我的实时名次、历届最佳（F2）
 * + 赛季通行证入口卡（§6）。R30 自 index.tsx 拆出，行为不变。
 */
export function RankingsPanel({ seasonPassEnabled = true }: { seasonPassEnabled?: boolean }) {
  const [rankings, setRankings] = useState<PetRankingResult | null>(null)
  // 赛季通行证（§6）：榜单页签惰性加载
  const [seasonPass, setSeasonPass] = useState<SeasonPassVO | null>(null)
  // F2：赛季榜与历届名次
  const [season, setSeason] = useState<PetSeasonRanking | null>(null)
  const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[]>([])
  const [rankingType, setRankingType] = useState<PetRankingType>('LEVEL')

  useEffect(() => {
    // F2：赛季榜 + 历届名次惰性加载（失败静默）
    void (async () => {
      try {
        const [seasonRes, historyRes] = await Promise.all([
          petApi.getSeasonRanking(),
          petApi.getSeasonHistory(),
        ])
        setSeason(seasonRes.data.data)
        // 赛季通行证（§6）
        petApi.getSeasonPass()
          .then(({ data: r }) => { setSeasonPass(r.data) })
          .catch(() => undefined)
        setSeasonHistory(historyRes.data.data || [])
      } catch {
        // 赛季为可选增强，失败不阻断排行榜
      }
    })()
  }, [])

  const loadRankings = useCallback(async () => {
    try {
      const { data: res } = await petApi.getRankings(rankingType)
      setRankings(res.data)
    } catch (error) {
      Taro.showToast({ title: friendlyError(error), icon: 'none' })
    }
  }, [rankingType])

  useEffect(() => {
    void loadRankings()
  }, [loadRankings])

  return (
    <View className={styles.rankList}>
      {/* 赛季通行证（§6）：入口卡（点击进通行证页） */}
      {seasonPass && seasonPass.seasonId != null && seasonPassEnabled && (
        <View className={styles.passEntryCard} onClick={() => Taro.navigateTo({ url: '/pages/seasonPass/index' })}>
          <View className={styles.passEntryInfo}>
            <Text className={styles.passEntryTitle}>🎟️ 赛季通行证</Text>
            <Text className={styles.passEntryExp}>经验 {seasonPass.passExp} · 完成每日任务累积</Text>
          </View>
          <Text className={styles.passEntryArrow}>›</Text>
        </View>
      )}
      {season?.season && (
        <View className={styles.seasonCard}>
          <Text className={styles.seasonTitle}>
            🏆 {season.season.name}（{season.season.startsAt} ~ {season.season.endsAt}）
          </Text>
          {season.top50.slice(0, 3).map((item) => (
            <Text key={String(item.petId)} className={styles.seasonRow}>
              {['🥇', '🥈', '🥉'][item.rank - 1]} {item.name} Lv.{item.level}
            </Text>
          ))}
          {season.myRank != null && (
            <Text className={styles.seasonRow}>我的实时名次：第 {season.myRank} 名</Text>
          )}
          {seasonHistory.length > 0 && (
            <Text className={styles.seasonRow}>
              历届最好成绩：第 {Math.min(...seasonHistory.map((h) => h.rankNo))} 名
            </Text>
          )}
        </View>
      )}
      <View className={cream.metaRow}>
        {RANKING_TABS.map((tab) => (
          <View
            key={tab.key}
            className={`${cream.tab} ${rankingType === tab.key ? cream.tabActive : ''}`}
            onClick={() => setRankingType(tab.key)}
          >
            <Text>{tab.label}</Text>
          </View>
        ))}
      </View>
      {rankings?.top20.map((item) => (
        <View key={String(item.petId)} className={`${styles.rankRow} ${item.isMe ? styles.rankRowMe : ''}`}>
          <Text className={styles.rankNo}>
            {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : '#' + item.rank}
          </Text>
          <Text className={styles.rankName}>
            {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
            <Text className={styles.rankOwner}> @{item.ownerNickname}</Text>
          </Text>
          <Text className={styles.rankValue}>{rankingType === 'LEVEL' ? `Lv.${item.value}` : item.value}</Text>
        </View>
      ))}
      {rankings?.myRank != null && (
        <Text className={styles.tip}>我的成绩 {rankings.myValue} · 全服第 {rankings.myRank} 名</Text>
      )}
    </View>
  )
}
