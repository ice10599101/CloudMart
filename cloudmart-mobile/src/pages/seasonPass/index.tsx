import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { petApi, type SeasonPassVO, type SeasonVO } from '@/api/pet'
import { errorMessage } from '@/utils/request'
import { PET_CREAM_STYLE } from '@/styles/petCream'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 赛季通行证（§6）：进行中赛季 + 10 档奖励阶梯。
 * 经验来自每日任务领取（+5）/全清宝箱（+15）；档位奖励宠物币，第 10 档含皮肤。
 */
export default function SeasonPassPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [pass, setPass] = useState<SeasonPassVO | null>(null)
  const [claiming, setClaiming] = useState<number | null>(null)
  const [noSeason, setNoSeason] = useState(false)
  // T19：历史赛季（API 已就绪——查看历史进度；宽限期内补领走 claimSeasonPassTierInSeason）
  const [mySeasons, setMySeasons] = useState<SeasonVO[] | null>(null)
  const [viewingSeasonId, setViewingSeasonId] = useState<number | string | null>(null)

  const load = async () => {
    try {
      const { data: res } = await petApi.getSeasonPass()
      if (res.data) {
        setPass(res.data)
        setNoSeason(res.data.seasonId === null)
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '通行证加载失败'), icon: 'none' })
    }
  }

  // T19：查看历史赛季（服务端返回该季快照进度；已领不可重领由服务端判定）
  const viewSeason = async (seasonId: number | string) => {
    setViewingSeasonId(seasonId)
    try {
      const { data: res } = await petApi.getSeasonPass(seasonId)
      if (res.data) {
        setPass(res.data)
        setNoSeason(false)
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '历史赛季加载失败'), icon: 'none' })
    }
  }

  const backToCurrent = async () => {
    setViewingSeasonId(null)
    await load()
  }

  // T19：我的赛季列表（本人参与过，按结束倒序）
  const loadMySeasons = async () => {
    if (mySeasons != null) {
      setMySeasons(null)
      return
    }
    try {
      const { data: res } = await petApi.getSeasonPassSeasons()
      setMySeasons(res.data ?? [])
    } catch {
      setMySeasons([])
    }
  }

  useEffect(() => { void load() }, [])

  const handleClaim = async (tier: number) => {
    if (claiming !== null) return
    setClaiming(tier)
    try {
      const { data: res } = await petApi.claimSeasonPassTier(tier)
      if (res.data) {
        setPass(res.data)
        Taro.showToast({ title: `第 ${tier} 档奖励已领取`, icon: 'success' })
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      const title = code === 'PET_VALIDATION_ERROR'
        ? '奖励还没达标或已领取'
        : errorMessage(error, '领取失败，请稍后再试')
      Taro.showToast({ title, icon: 'none' })
    } finally {
      setClaiming(null)
    }
  }

  return (
    <View className={styles.page} style={{ ...PET_CREAM_STYLE, paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="赛季通行证" back />
      <ScrollView scrollY className={styles.scroll}>
        {noSeason ? (
          <View className={styles.empty}><Text>当前没有进行中的赛季</Text></View>
        ) : !pass ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : (
          <>
            <View className={styles.hero}>
              <Text className={styles.seasonName}>{pass.seasonName ?? '当前赛季'}</Text>
              <Text className={styles.exp}>通行证经验 {pass.passExp}</Text>
              {/* T19：历史赛季选择（查看快照进度；宽限期内补领由服务端判定） */}
              <Text
                className={styles.exp}
                style={{ textDecoration: 'underline', marginTop: 4 }}
                onClick={() => void loadMySeasons()}
              >
                {mySeasons != null ? '收起历史赛季' : '查看历史赛季'}
              </Text>
              {viewingSeasonId != null ? (
                <Text className={styles.exp} style={{ color: '#3d5afe', marginTop: 4 }} onClick={() => void backToCurrent()}>
                  ← 返回当前赛季
                </Text>
              ) : null}
            </View>
            {mySeasons != null ? (
              <View className={styles.empty}>
                {mySeasons.length === 0 ? (
                  <Text>暂无参与过的历史赛季</Text>
                ) : (
                  mySeasons.map((season) => (
                    <View
                      key={String(season.id)}
                      onClick={() => void viewSeason(season.id)}
                      style={{ padding: '8rpx 0' }}
                    >
                      <Text>
                        {season.name}（{season.endsAt?.slice(0, 10) ?? ''} 结束）
                        {String(season.id) === String(viewingSeasonId) ? ' · 当前查看' : ''}
                      </Text>
                    </View>
                  ))
                )}
              </View>
            ) : null}
            {pass.tiers.map(t => {
              const pending = t.claimStatus === 'CLAIM_PENDING'
              const canClaim = t.reached && !t.claimed && !pending
              return (
                <View key={t.tier} className={`${styles.tierCard} ${t.claimed ? styles.tierClaimed : ''}`}>
                  <View className={styles.tierInfo}>
                    <Text className={styles.tierNo}>第 {t.tier} 档</Text>
                    <Text className={styles.tierReq}>{t.requiredExp} 经验{t.reached ? ' · 已达标' : ` · 还差 ${t.requiredExp - pass.passExp}`}</Text>
                    <Text className={styles.tierReward}>{t.rewardDesc}</Text>
                  </View>
                  {t.claimed ? (
                    <Text className={styles.claimedTag}>已领取</Text>
                  ) : pending ? (
                    <Text className={styles.pendingTag}>发放中…</Text>
                  ) : (
                    <View
                      className={`${styles.claimBtn} ${canClaim ? '' : styles.claimBtnDisabled}`}
                      onClick={canClaim && claiming !== t.tier ? () => handleClaim(t.tier) : undefined}
                    >
                      <Text className={styles.claimBtnText}>{claiming === t.tier ? '领取中...' : '领取'}</Text>
                    </View>
                  )}
                </View>
              )
            })}
          </>
        )}
      </ScrollView>
    </View>
  )
}
