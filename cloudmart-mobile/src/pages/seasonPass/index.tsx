import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { petApi, type SeasonPassVO } from '@/api/pet'
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

  const load = async () => {
    try {
      const { data: res } = await petApi.getSeasonPass()
      if (res.success && res.data) {
        setPass(res.data)
        setNoSeason(res.data.seasonId === null)
      }
    } catch {
      Taro.showToast({ title: '通行证加载失败', icon: 'none' })
    }
  }

  useEffect(() => { void load() }, [])

  const handleClaim = async (tier: number) => {
    if (claiming !== null) return
    setClaiming(tier)
    try {
      const { data: res } = await petApi.claimSeasonPassTier(tier)
      if (res.success && res.data) {
        setPass(res.data)
        Taro.showToast({ title: `第 ${tier} 档奖励已领取`, icon: 'success' })
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      Taro.showToast({ title: code === 'PET_VALIDATION_ERROR' ? '奖励还没达标或已领取' : '领取失败，请稍后再试', icon: 'none' })
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
            </View>
            {pass.tiers.map(t => {
              const canClaim = t.reached && !t.claimed
              return (
                <View key={t.tier} className={`${styles.tierCard} ${t.claimed ? styles.tierClaimed : ''}`}>
                  <View className={styles.tierInfo}>
                    <Text className={styles.tierNo}>第 {t.tier} 档</Text>
                    <Text className={styles.tierReq}>{t.requiredExp} 经验{t.reached ? ' · 已达标' : ` · 还差 ${t.requiredExp - pass.passExp}`}</Text>
                    <Text className={styles.tierReward}>{t.rewardDesc}</Text>
                  </View>
                  {t.claimed ? (
                    <Text className={styles.claimedTag}>已领取</Text>
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
