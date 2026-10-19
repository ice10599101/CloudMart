import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Alert } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi, type MyPoolJoin, type MyPoolReward } from '@/api/wish'
import { errorMessage } from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import AssetIcon from '@/components/AssetIcon'
import type { WorkshopAsset, CollectionAssetGroup, BrandItem, BrandPoolItem } from '@/types'

const TYPE_LABELS: Record<string, string> = {
  SKIN: '树皮肤',
  BGM: '背景音乐',
  SPECIAL_FRUIT: '特殊果实',
  BADGE: '徽章',
}

const GROUP_LABELS: Record<string, string> = {
  SKIN: '🌳 树皮肤',
  BGM: '🎵 背景音乐',
  SPECIAL_FRUIT: '🍎 特殊果实（星火收藏）',
  BADGE: '🏅 徽章',
}

type TabKey = 'workshop' | 'collections' | 'brands' | 'mine'

/**
 * 虚拟工坊（Sprint 3.6，四AC R2 APP 端）：
 * - 工坊：星光兑换树皮肤/BGM/特殊果实（402 余额不足/409 已拥有由异常分支提示）
 * - 收藏馆：按类型分组；皮肤/BGM 可激活；星火收藏品关联心愿
 * - 品牌许愿池：浏览品牌与池，加入（uk 幂等）
 */
export default function WorkshopScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [tab, setTab] = useState<TabKey>('workshop')

  const [assets, setAssets] = useState<WorkshopAsset[]>([])
  const [groups, setGroups] = useState<CollectionAssetGroup>({})
  const [brands, setBrands] = useState<BrandItem[]>([])
  const [poolsByBrand, setPoolsByBrand] = useState<Record<string, BrandPoolItem[]>>({})
  const [loading, setLoading] = useState(true)
  const [exchangingId, setExchangingId] = useState<number | null>(null)
  const [joiningPool, setJoiningPool] = useState<string | null>(null)
  // T11：我的参与/奖励结果（切换到 mine 页签时加载）
  const [myJoins, setMyJoins] = useState<MyPoolJoin[]>([])
  const [myRewards, setMyRewards] = useState<MyPoolReward[]>([])
  const [mineLoaded, setMineLoaded] = useState(false)

  const loadAll = useCallback(async () => {
    setLoading(true)
    try {
      const [assetsRes, groupsRes, brandsRes] = await Promise.all([
        wishApi.getWorkshopAssets(),
        wishApi.getCollectionAssets(),
        wishApi.listBrands(),
      ])
      setAssets(assetsRes.data.data ?? [])
      setGroups(groupsRes.data.data ?? {})
      setBrands(brandsRes.data.data ?? [])
    } catch {
      // 后台聚合加载失败静默，保留空态
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    async function run() {
      await loadAll()
    }
    void run()
  }, [isLoggedIn, loadAll])

  const toastError = (error: unknown, fallback: string) => {
    Alert.alert('虚拟工坊', errorMessage(error, fallback))
  }

  const handleExchange = (asset: WorkshopAsset) => {
    Alert.alert(`兑换「${asset.name}」`, `将消耗 ${asset.priceStarlight} 星光（库存剩余 ${asset.stock}），确认兑换？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确认兑换',
        onPress: async () => {
          setExchangingId(asset.assetId)
          try {
            await wishApi.exchangeAsset(asset.assetId)
            Alert.alert('虚拟工坊', '兑换成功，已放入收藏馆 🎉')
            loadAll()
          } catch (error) {
            toastError(error, '兑换失败，请稍后重试')
          } finally {
            setExchangingId(null)
          }
        },
      },
    ])
  }

  const handleActivate = async (assetId: number, kind: 'SKIN' | 'BGM') => {
    try {
      await (kind === 'SKIN' ? wishApi.setActiveSkin(assetId) : wishApi.setActiveBgm(assetId))
      Alert.alert('虚拟工坊', kind === 'SKIN' ? '皮肤已切换，世界树即时生效' : 'BGM 已切换')
      const groupsRes = await wishApi.getCollectionAssets()
      setGroups(groupsRes.data.data ?? {})
    } catch (error) {
      toastError(error, '激活失败，请稍后重试')
    }
  }

  const loadPools = async (brand: BrandItem) => {
    try {
      const res = await wishApi.listBrandPools(brand.id)
      setPoolsByBrand((prev) => ({ ...prev, [brand.id]: res.data.data ?? [] }))
    } catch (error) {
      toastError(error, '加载许愿池失败')
    }
  }

  const handleJoinPool = async (brand: BrandItem, pool: BrandPoolItem) => {
    const key = `${brand.id}:${pool.id}`
    setJoiningPool(key)
    try {
      await wishApi.joinBrandPool(brand.id, pool.id)
      Alert.alert('虚拟工坊', `已加入「${pool.name}」，达成后可获品牌奖励`)
      loadPools(brand)
    } catch (error) {
      toastError(error, '加入失败，请稍后重试')
    } finally {
      setJoiningPool(null)
    }
  }

  // T11：我的参与/奖励（登录态切页签加载；结果回跳对应池）
  useEffect(() => {
    if (tab !== 'mine' || !isLoggedIn || mineLoaded) return
    void (async () => {
      try {
        const [joinsRes, rewardsRes] = await Promise.all([
          wishApi.myPoolJoins(),
          wishApi.myPoolRewards(),
        ])
        setMyJoins(joinsRes.data.data ?? [])
        setMyRewards(rewardsRes.data.data ?? [])
        setMineLoaded(true)
      } catch {
        // 静默：保留空态
      }
    })()
  }, [tab, isLoggedIn, mineLoaded])

  /** T11：回跳——切到品牌页签并展开对应品牌池 */
  const jumpToPool = (brandId: number | string | null) => {
    if (brandId == null) return
    const brand = brands.find((b) => String(b.id) === String(brandId))
    if (!brand) return
    setTab('brands')
    void loadPools(brand)
  }

  const groupEntries = Object.entries(groups)

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
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>虚拟工坊</Text>
        <View style={{ width: 24 }} />
      </View>

      {/* 三 Tab */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.md, marginBottom: Spacing.sm }}>
        {(
          [
            ['workshop', '虚拟工坊'],
            ['collections', '收藏馆'],
            ['brands', '品牌许愿池'],
          ] as [TabKey, string][]
        ).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            activeOpacity={0.8}
            onPress={() => setTab(key)}
            style={{
              flex: 1,
              alignItems: 'center',
              paddingVertical: Spacing.sm,
              borderRadius: 24,
              backgroundColor: tab === key ? 'rgba(255, 215, 0, 0.16)' : WishColors.bgElevated,
            }}
          >
            <Text style={{ fontSize: FontSize.sm, fontWeight: tab === key ? '700' : '400', color: tab === key ? WishColors.accentGold : WishColors.textSecondary }}>
              {label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + Spacing.xl }}>
        {loading ? (
          <ActivityIndicator color={WishColors.primary} style={{ marginTop: Spacing.xl }} />
        ) : tab === 'workshop' ? (
          assets.length === 0 ? (
            <Text style={{ textAlign: 'center', marginTop: Spacing.xl, color: WishColors.textTertiary }}>工坊暂无上架资产</Text>
          ) : (
            assets.map((asset) => (
              <View
                key={asset.assetId}
                style={{
                  backgroundColor: WishColors.bgElevated,
                  borderRadius: BorderRadius.xl,
                  padding: Spacing.lg,
                  marginBottom: Spacing.md,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap', marginBottom: Spacing.xs }}>
                  <AssetIcon icon={asset.icon} size={36} />
                  <View style={{ paddingHorizontal: 10, paddingVertical: 2, borderRadius: 12, backgroundColor: 'rgba(139, 92, 246, 0.16)' }}>
                    <Text style={{ fontSize: FontSize.xs, color: '#a78bfa' }}>{TYPE_LABELS[asset.assetType] ?? asset.assetType}</Text>
                  </View>
                  <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text, flex: 1 }}>{asset.name}</Text>
                  {asset.owned && (
                    <View style={{ paddingHorizontal: 10, paddingVertical: 2, borderRadius: 12, backgroundColor: 'rgba(74, 185, 106, 0.16)' }}>
                      <Text style={{ fontSize: FontSize.xs, color: '#4ab96a' }}>已拥有</Text>
                    </View>
                  )}
                </View>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
                  {asset.description ?? '—'}
                </Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold }}>
                    ⭐ {asset.priceStarlight} 星光 · 库存 {asset.stock}
                  </Text>
                  <TouchableOpacity
                    activeOpacity={0.8}
                    disabled={asset.owned || asset.stock <= 0}
                    onPress={() => handleExchange(asset)}
                    style={{
                      paddingHorizontal: Spacing.xl,
                      paddingVertical: Spacing.xs,
                      borderRadius: 24,
                      backgroundColor: asset.owned || asset.stock <= 0 ? 'rgba(255,255,255,0.08)' : 'rgba(255, 215, 0, 0.2)',
                    }}
                  >
                    <Text
                      style={{
                        fontSize: FontSize.sm,
                        fontWeight: '600',
                        color: asset.owned || asset.stock <= 0 ? WishColors.textTertiary : WishColors.accentGold,
                      }}
                    >
                      {asset.owned ? '已拥有' : asset.stock <= 0 ? '已售罄' : exchangingId === asset.assetId ? '兑换中...' : '兑换'}
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))
          )
        ) : tab === 'collections' ? (
          groupEntries.length === 0 ? (
            <Text style={{ textAlign: 'center', marginTop: Spacing.xl, color: WishColors.textTertiary }}>
              收藏馆还是空的，去工坊兑换或完成星火收藏吧
            </Text>
          ) : (
            groupEntries.map(([type, items]) => (
              <View
                key={type}
                style={{
                  backgroundColor: WishColors.bgElevated,
                  borderRadius: BorderRadius.xl,
                  padding: Spacing.lg,
                  marginBottom: Spacing.md,
                }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.sm }}>
                  {GROUP_LABELS[type] ?? type}
                </Text>
                {items.map((item) => (
                  <View
                    key={item.id}
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      paddingVertical: Spacing.sm,
                      borderBottomWidth: 1,
                      borderBottomColor: 'rgba(255,255,255,0.06)',
                    }}
                  >
                    <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1, gap: Spacing.xs }}>
                      <AssetIcon icon={item.icon} size={28} />
                      <Text style={{ fontSize: FontSize.sm, color: WishColors.text }}>
                        {item.name}
                      </Text>
                      {item.isActive && (
                        <View style={{ paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10, backgroundColor: 'rgba(74, 185, 106, 0.16)' }}>
                          <Text style={{ fontSize: 10, color: '#4ab96a' }}>使用中</Text>
                        </View>
                      )}
                    </View>
                    {(type === 'SKIN' || type === 'BGM') && (
                      <TouchableOpacity
                        activeOpacity={0.8}
                        disabled={item.isActive === true}
                        onPress={() => handleActivate(item.assetId, type as 'SKIN' | 'BGM')}
                        style={{
                          paddingHorizontal: Spacing.lg,
                          paddingVertical: 6,
                          borderRadius: 20,
                          borderWidth: 1,
                          borderColor: item.isActive ? 'rgba(255,255,255,0.12)' : 'rgba(255, 215, 0, 0.5)',
                        }}
                      >
                        <Text style={{ fontSize: FontSize.xs, color: item.isActive ? WishColors.textTertiary : WishColors.accentGold }}>
                          {item.isActive ? '使用中' : '使用'}
                        </Text>
                      </TouchableOpacity>
                    )}
                    {type === 'SPECIAL_FRUIT' && item.refWishId && (
                      <TouchableOpacity onPress={() => router.push(`/wish-detail?id=${item.refWishId}`)}>
                        <Text style={{ fontSize: FontSize.xs, color: '#4a90d9' }}>查看心愿</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                ))}
              </View>
            ))
          )
        ) : tab === 'mine' ? (
          // T11：我的参与（点击回跳池）+ 奖励结果（义务状态/承诺/入账如实展示）
          <View>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.sm }}>
              我的参与
            </Text>
            {myJoins.length === 0 ? (
              <Text style={{ textAlign: 'center', marginTop: Spacing.md, color: WishColors.textTertiary }}>
                还没有参与任何许愿池
              </Text>
            ) : (
              myJoins.map((join) => (
                <TouchableOpacity
                  key={String(join.poolId)}
                  onPress={() => jumpToPool(join.brandId)}
                  style={{
                    padding: Spacing.md,
                    borderRadius: 12,
                    backgroundColor: WishColors.bgElevated,
                    marginBottom: Spacing.sm,
                  }}
                >
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text, flex: 1 }}>
                      {join.poolName}
                    </Text>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                      {join.currentCount}/{join.targetCount} 人
                    </Text>
                  </View>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
                    {join.joinedAt.slice(0, 10)} 加入 · {join.poolStatus === 'ACTIVE' ? '进行中' : join.poolStatus === 'SETTLING' ? '结算中' : join.poolStatus === 'COMPLETED' ? '已完成' : join.poolStatus === 'ENDED' ? '已结束' : join.poolStatus}
                  </Text>
                </TouchableOpacity>
              ))
            )}
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginTop: Spacing.md, marginBottom: Spacing.sm }}>
              奖励结果
            </Text>
            {myRewards.length === 0 ? (
              <Text style={{ textAlign: 'center', marginTop: Spacing.md, color: WishColors.textTertiary }}>
                暂无奖励记录
              </Text>
            ) : (
              myRewards.map((reward, idx) => (
                <View
                  key={`${reward.poolId}-${idx}`}
                  style={{
                    padding: Spacing.md,
                    borderRadius: 12,
                    backgroundColor: WishColors.bgElevated,
                    marginBottom: Spacing.sm,
                  }}
                >
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text, flex: 1 }}>
                      {reward.poolName}
                    </Text>
                    <Text style={{
                      fontSize: FontSize.xs,
                      color: reward.status === 'SETTLED' ? '#3a9e5f' : reward.status === 'FAILED' ? '#d3392f' : WishColors.textSecondary,
                    }}>
                      {reward.status === 'PENDING' ? '待发放' : reward.status === 'PARTIAL' ? '部分到账' : reward.status === 'SETTLED' ? '已到账' : reward.status === 'FAILED' ? '发放失败' : reward.status}
                    </Text>
                  </View>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
                    承诺 {reward.promisedAmount} · 实际入账 {reward.creditedAmount}
                  </Text>
                </View>
              ))
            )}
          </View>
        ) : brands.length === 0 ? (
          <Text style={{ textAlign: 'center', marginTop: Spacing.xl, color: WishColors.textTertiary }}>暂无合作品牌</Text>
        ) : (
          brands.map((brand) => {
            const pools = poolsByBrand[brand.id]
            return (
              <View
                key={brand.id}
                style={{
                  backgroundColor: WishColors.bgElevated,
                  borderRadius: BorderRadius.xl,
                  padding: Spacing.lg,
                  marginBottom: Spacing.md,
                }}
              >
                <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>👑 {brand.name}</Text>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginVertical: Spacing.xs }}>
                  {brand.description ?? '—'}
                </Text>
                {!pools ? (
                  <TouchableOpacity onPress={() => loadPools(brand)}>
                    <Text style={{ fontSize: FontSize.sm, color: '#4a90d9' }}>查看许愿池 ›</Text>
                  </TouchableOpacity>
                ) : (
                  pools.map((pool) => {
                    const percent =
                      pool.targetCount > 0 ? Math.min(Math.round((pool.currentCount / pool.targetCount) * 100), 100) : 0
                    return (
                      <View
                        key={pool.id}
                        style={{ borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.08)', paddingTop: Spacing.sm, marginTop: Spacing.sm }}
                      >
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 }}>
                          <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>{pool.name}</Text>
                          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>{pool.status}</Text>
                        </View>
                        <View style={{ height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.08)', overflow: 'hidden' }}>
                          <View style={{ height: '100%', width: `${percent}%`, borderRadius: 3, backgroundColor: WishColors.accentGold }} />
                        </View>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.xs }}>
                          <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, flex: 1 }}>
                            {pool.currentCount}/{pool.targetCount} 人已加入{pool.rewardDescription ? ` · ${pool.rewardDescription}` : ''}
                          </Text>
                          <TouchableOpacity
                            activeOpacity={0.8}
                            disabled={joiningPool === `${brand.id}:${pool.id}`}
                            onPress={() => handleJoinPool(brand, pool)}
                            style={{
                              paddingHorizontal: Spacing.lg,
                              paddingVertical: 6,
                              borderRadius: 20,
                              backgroundColor: 'rgba(255, 215, 0, 0.2)',
                            }}
                          >
                            <Text style={{ fontSize: FontSize.xs, fontWeight: '600', color: WishColors.accentGold }}>
                              {joiningPool === `${brand.id}:${pool.id}` ? '加入中...' : '加入'}
                            </Text>
                          </TouchableOpacity>
                        </View>
                      </View>
                    )
                  })
                )}
              </View>
            )
          })
        )}
      </ScrollView>
    </View>
  )
}
