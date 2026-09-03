import { View, Text, ScrollView } from '@tarojs/components'
import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import type { WorkshopAsset, CollectionAssetGroup, BrandItem, BrandPoolItem } from '@/types'
import styles from './index.module.scss'

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

type TabKey = 'workshop' | 'collections' | 'brands'

/**
 * 虚拟工坊（Sprint 3.6，四AC R1 Mobile）：
 * - 工坊：星光兑换树皮肤/BGM/特殊果实（402 余额不足/409 已拥有由异常分支提示）
 * - 收藏馆：按类型分组；皮肤/BGM 可激活；星火收藏品关联心愿
 * - 品牌许愿池：浏览品牌与池，加入（uk 幂等）
 */
export default function WorkshopPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [tab, setTab] = useState<TabKey>('workshop')

  const [assets, setAssets] = useState<WorkshopAsset[]>([])
  const [groups, setGroups] = useState<CollectionAssetGroup>({})
  const [brands, setBrands] = useState<BrandItem[]>([])
  const [poolsByBrand, setPoolsByBrand] = useState<Record<string, BrandPoolItem[]>>({})
  const [loading, setLoading] = useState(true)
  const [exchangingId, setExchangingId] = useState<number | null>(null)
  const [joiningPool, setJoiningPool] = useState<string | null>(null)

  const loadAll = useCallback(async () => {
    setLoading(true)
    try {
      const [assetsRes, groupsRes, brandsRes] = await Promise.all([
        wishApi.getWorkshopAssets(),
        wishApi.getCollectionAssets(),
        wishApi.listBrands(),
      ])
      if (assetsRes.data.success) setAssets(assetsRes.data.data ?? [])
      if (groupsRes.data.success) setGroups(groupsRes.data.data ?? {})
      if (brandsRes.data.success) setBrands(brandsRes.data.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.navigateTo({ url: '/pages/login/index' })
      return
    }
    loadAll()
  }, [isLoggedIn, loadAll])

  const toastError = (err: unknown, fallback: string) => {
    const errNode = err as { data?: { error?: { message?: string } } }
    Taro.showToast({ title: errNode?.data?.error?.message || fallback, icon: 'none' })
  }

  const handleExchange = (asset: WorkshopAsset) => {
    Taro.showModal({
      title: `兑换「${asset.name}」`,
      content: `将消耗 ${asset.priceStarlight} 星光（库存剩余 ${asset.stock}），确认兑换？`,
      confirmText: '确认兑换',
      cancelText: '取消',
      success: (r) => {
        if (!r.confirm) return
        setExchangingId(asset.assetId)
        wishApi
          .exchangeAsset(asset.assetId)
          .then((res) => {
            if (res.data.success) {
              Taro.showToast({ title: '兑换成功，已放入收藏馆', icon: 'none' })
              loadAll()
            }
          })
          .catch((err) => toastError(err, '兑换失败，请稍后重试'))
          .finally(() => setExchangingId(null))
      },
    })
  }

  const handleActivate = (assetId: number, kind: 'SKIN' | 'BGM') => {
    const activate = kind === 'SKIN' ? wishApi.setActiveSkin(assetId) : wishApi.setActiveBgm(assetId)
    activate
      .then((res) => {
        if (res.data.success) {
          Taro.showToast({ title: kind === 'SKIN' ? '皮肤已切换' : 'BGM 已切换', icon: 'none' })
          wishApi.getCollectionAssets().then((r) => {
            if (r.data.success) setGroups(r.data.data ?? {})
          })
        }
      })
      .catch((err) => toastError(err, '激活失败，请稍后重试'))
  }

  const loadPools = (brand: BrandItem) => {
    wishApi
      .listBrandPools(brand.brandId)
      .then((res) => {
        if (res.data.success) {
          setPoolsByBrand((prev) => ({ ...prev, [brand.brandId]: res.data.data ?? [] }))
        }
      })
      .catch((err) => toastError(err, '加载许愿池失败'))
  }

  const handleJoinPool = (brand: BrandItem, pool: BrandPoolItem) => {
    const key = `${brand.brandId}:${pool.poolId}`
    setJoiningPool(key)
    wishApi
      .joinBrandPool(brand.brandId, pool.poolId)
      .then((res) => {
        if (res.data.success) {
          Taro.showToast({ title: `已加入「${pool.poolName}」`, icon: 'none' })
          loadPools(brand)
        }
      })
      .catch((err) => toastError(err, '加入失败，请稍后重试'))
      .finally(() => setJoiningPool(null))
  }

  const groupEntries = Object.entries(groups)

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="虚拟工坊" back />
      <ScrollView className={styles.list} scrollY>
        <View className={styles.tabBar}>
          {(
            [
              ['workshop', '虚拟工坊'],
              ['collections', '收藏馆'],
              ['brands', '品牌许愿池'],
            ] as [TabKey, string][]
          ).map(([key, label]) => (
            <View
              key={key}
              className={`${styles.tabItem} ${tab === key ? styles.tabItemActive : ''}`}
              onClick={() => setTab(key)}
            >
              <Text>{label}</Text>
            </View>
          ))}
        </View>

        {loading ? (
          <View className={styles.empty}>
            <Text>加载中...</Text>
          </View>
        ) : tab === 'workshop' ? (
          assets.length === 0 ? (
            <View className={styles.empty}>
              <Text>工坊暂无上架资产</Text>
            </View>
          ) : (
            assets.map((asset) => (
              <View key={asset.assetId} className={styles.card}>
                <View className={styles.cardHeader}>
                  <Text className={styles.typeTag}>{TYPE_LABELS[asset.assetType] ?? asset.assetType}</Text>
                  <Text className={styles.assetName}>{asset.name}</Text>
                  {asset.owned && <Text className={styles.ownedTag}>已拥有</Text>}
                </View>
                <Text className={styles.cardDesc}>{asset.description ?? '—'}</Text>
                <View className={styles.cardFooter}>
                  <View>
                    <Text className={styles.priceRow}>⭐ {asset.priceStarlight} 星光</Text>
                    <Text className={styles.stockRow}>库存 {asset.stock}</Text>
                  </View>
                  <View
                    className={`${styles.exchangeBtn} ${
                      asset.owned || asset.stock <= 0 ? styles.exchangeBtnDisabled : ''
                    }`}
                    onClick={() => {
                      if (asset.owned || asset.stock <= 0 || exchangingId === asset.assetId) return
                      handleExchange(asset)
                    }}
                  >
                    <Text>
                      {asset.owned ? '已拥有' : asset.stock <= 0 ? '已售罄' : exchangingId === asset.assetId ? '兑换中...' : '兑换'}
                    </Text>
                  </View>
                </View>
              </View>
            ))
          )
        ) : tab === 'collections' ? (
          groupEntries.length === 0 ? (
            <View className={styles.empty}>
              <Text>收藏馆还是空的，去工坊兑换或完成星火收藏吧</Text>
            </View>
          ) : (
            groupEntries.map(([type, items]) => (
              <View key={type} className={styles.groupCard}>
                <Text className={styles.groupTitle}>{GROUP_LABELS[type] ?? type}</Text>
                {items.map((item) => (
                  <View key={item.id} className={styles.collectionRow}>
                    <View className={styles.collectionName}>
                      <Text>
                        {item.icon} {item.name}
                      </Text>
                      {item.isActive && <Text className={styles.activeTag}>使用中</Text>}
                    </View>
                    {(type === 'SKIN' || type === 'BGM') && (
                      <View
                        className={`${styles.useBtn} ${item.isActive ? styles.useBtnDisabled : ''}`}
                        onClick={() => {
                          if (item.isActive) return
                          handleActivate(item.assetId, type as 'SKIN' | 'BGM')
                        }}
                      >
                        <Text>{item.isActive ? '使用中' : '使用'}</Text>
                      </View>
                    )}
                    {type === 'SPECIAL_FRUIT' && item.refWishId && (
                      <View
                        className={styles.linkBtn}
                        onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${item.refWishId}` })}
                      >
                        <Text>查看心愿</Text>
                      </View>
                    )}
                  </View>
                ))}
              </View>
            ))
          )
        ) : brands.length === 0 ? (
          <View className={styles.empty}>
            <Text>暂无合作品牌</Text>
          </View>
        ) : (
          brands.map((brand) => {
            const pools = poolsByBrand[brand.brandId]
            return (
              <View key={brand.brandId} className={styles.card}>
                <Text className={styles.brandTitle}>
                  👑 {brand.brandName}
                </Text>
                <Text className={styles.brandDesc}>{brand.description ?? '—'}</Text>
                {!pools ? (
                  <Text className={styles.togglePoolsBtn} onClick={() => loadPools(brand)}>
                    查看许愿池 ›
                  </Text>
                ) : (
                  pools.map((pool) => {
                    const percent =
                      pool.targetCount > 0 ? Math.min(Math.round((pool.currentCount / pool.targetCount) * 100), 100) : 0
                    return (
                      <View key={pool.poolId} className={styles.poolBlock}>
                        <View className={styles.poolHeader}>
                          <Text className={styles.poolName}>{pool.poolName}</Text>
                          <Text className={styles.poolStatus}>{pool.status}</Text>
                        </View>
                        <View className={styles.progressTrack}>
                          <View className={styles.progressFill} style={{ width: `${percent}%` }} />
                        </View>
                        <View className={styles.poolFooter}>
                          <Text className={styles.poolMeta}>
                            {pool.currentCount}/{pool.targetCount} 人已加入
                            {pool.rewardDescription ? ` · ${pool.rewardDescription}` : ''}
                          </Text>
                          <View
                            className={styles.joinBtn}
                            onClick={() => {
                              if (joiningPool === `${brand.brandId}:${pool.poolId}`) return
                              handleJoinPool(brand, pool)
                            }}
                          >
                            <Text>
                              {joiningPool === `${brand.brandId}:${pool.poolId}` ? '加入中...' : '加入'}
                            </Text>
                          </View>
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
