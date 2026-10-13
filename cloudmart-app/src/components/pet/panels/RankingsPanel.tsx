// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { ActivityIndicator, Text, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { petCreamStyles } from '@/components/pet-cream'
import { petApi, type PetRankingResult, type PetRankingType, type PetSeasonRanking, type PetSeasonHistoryItem } from '@/api/pet'
import { RANKING_TABS, SPECIES_EMOJI } from '../shared'

/** 榜单面板：榜单随页签切换拉取；F2 赛季排行切到"赛季"时懒加载一次 */
export function RankingsPanel() {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [rankings, setRankings] = useState<PetRankingResult | null>(null)
  const [rankingType, setRankingType] = useState<PetRankingType>('LEVEL')
  const [showSeason, setShowSeason] = useState(false)
  const [season, setSeason] = useState<PetSeasonRanking | null>(null)
  const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[]>([])

  useEffect(() => {
    void (async () => {
      try {
        const { data: res } = await petApi.getRankings(rankingType)
        setRankings(res.data)
      } catch {
        // 展示型数据：忽略
      }
    })()
  }, [rankingType])

  // F2 赛季排行：切到"赛季"且未加载时拉取（无进行中赛季 season=null）
  useEffect(() => {
    if (!showSeason || season) return
    void (async () => {
      try {
        const [{ data: sr }, { data: sh }] = await Promise.all([
          petApi.getSeasonRanking(),
          petApi.getSeasonHistory(),
        ])
        setSeason(sr.data)
        setSeasonHistory(sh.data ?? [])
      } catch {
        // 展示型数据：忽略
      }
    })()
  }, [season, showSeason])

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
        {RANKING_TABS.map((tab) => (
          <TouchableOpacity
            key={tab.key}
            onPress={() => setRankingType(tab.key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
              backgroundColor: rankingType === tab.key ? colors.primary : colors.bgBase,
              borderWidth: 1, borderColor: rankingType === tab.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: rankingType === tab.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{tab.label}</Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity
          onPress={() => setShowSeason((prev) => !prev)}
          style={{
            paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
            backgroundColor: showSeason ? colors.primary : colors.bgBase,
            borderWidth: 1, borderColor: showSeason ? colors.primary : colors.border,
          }}
        >
          <Text style={{ color: showSeason ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>赛季</Text>
        </TouchableOpacity>
      </View>
      {showSeason ? (
        !season ? <ActivityIndicator color={colors.primary} /> : (
          <View style={{ gap: Spacing.sm }}>
            {season.season === null ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                当前没有进行中的赛季，开赛后这里会亮起来
              </Text>
            ) : (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  {`${season.season.name} · ${season.season.startsAt?.slice(5, 10)} ~ ${season.season.endsAt?.slice(5, 10)} · 我的等级 ${season.myLevel ?? '—'} · 名次 ${season.myRank ?? '未上榜'}`}
                </Text>
                {season.top50.map((item) => (
                  <View key={String(item.petId)} style={{
                    flexDirection: 'row', alignItems: 'center', gap: Spacing.sm,
                    padding: Spacing.sm, borderRadius: BorderRadius.md,
                    backgroundColor: item.isMe ? PetCreamSemantic.meHighlight : colors.bgBase,
                  }}>
                    <Text style={{ width: 36, fontWeight: '700', color: colors.text }}>
                      {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : `#${item.rank}`}
                    </Text>
                    <Text style={{ flex: 1, color: colors.text, fontSize: FontSize.sm }}>
                      {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
                      <Text style={{ color: colors.textTertiary, fontSize: 10 }}> @{item.ownerNickname}</Text>
                    </Text>
                    <Text style={{ color: colors.primary, fontWeight: '600' }}>Lv.{item.level}</Text>
                  </View>
                ))}
                {seasonHistory.length > 0 && (
                  <View style={{ gap: Spacing.xs }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>历届我的名次</Text>
                    {seasonHistory.map((item) => (
                      <Text key={String(item.seasonId)} style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                        {`${item.seasonName} · 第 ${item.rankNo} 名 · Lv.${item.level}${item.endedAt ? ` · 结算 ${item.endedAt.slice(0, 10)}` : ''}`}
                      </Text>
                    ))}
                  </View>
                )}
              </>
            )}
          </View>
        )
      ) : (
        <>
          {rankings?.top20.map((item) => (
            <View key={String(item.petId)} style={[petCreamStyles.questCard, {
              flexDirection: 'row', alignItems: 'center', gap: Spacing.sm,
              paddingVertical: 10,
              backgroundColor: item.isMe ? PetCreamSemantic.meHighlight : '#FFFDF8',
            }]}>
              <Text style={{ width: 36, fontWeight: '700', color: colors.text }}>
                {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : `#${item.rank}`}
              </Text>
              <Text style={{ flex: 1, color: colors.text, fontSize: FontSize.sm }}>
                {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
                <Text style={{ color: colors.textTertiary, fontSize: 10 }}> @{item.ownerNickname}</Text>
              </Text>
              <Text style={{ color: colors.primary, fontWeight: '600' }}>
                {rankingType === 'LEVEL' ? `Lv.${item.value}` : item.value}
              </Text>
            </View>
          ))}
          {rankings?.myRank != null && (
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
              我的成绩：{rankings.myValue} · 全服第 {rankings.myRank} 名
            </Text>
          )}
        </>
      )}
    </View>
  )
}
