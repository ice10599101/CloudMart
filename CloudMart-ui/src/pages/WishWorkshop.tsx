import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, Modal, Progress, Segmented, Tag, App } from 'antd'
import {
  ArrowLeftOutlined,
  GiftOutlined,
  StarOutlined,
  CrownOutlined,
 SwapOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import AssetIcon from '@/components/AssetIcon'
import {
  getWorkshopAssets,
  exchangeAsset,
  getCollections,
  setActiveSkin,
  setActiveBgm,
  listBrands,
  listBrandPools,
  joinBrandPool,
  type WorkshopAsset,
  type CollectionGroup,
  type BrandItem,
  type BrandPoolItem,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import styles from './MyWishes.module.css'
import Collections3DShowcase from '@/components/Collections3DShowcase'

/**
 * 虚拟工坊（Sprint 3.6，四AB B3/B4 用户侧）：
 * - 工坊：星光兑换树皮肤/BGM/特殊果实（402 余额不足由拦截器提示；uk 重复兑换 409）
 * - 收藏馆：按 BADGE/SKIN/BGM/SPECIAL_FRUIT 分组；皮肤/BGM 可激活；星火收藏品关联心愿
 * - 品牌许愿池：浏览 APPROVED 品牌与 ACTIVE 池，加入（uk 幂等）
 */

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

export default function WishWorkshop() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const [tab, setTab] = useState<TabKey>('workshop')

  // 工坊
  const [assets, setAssets] = useState<WorkshopAsset[]>([])
  const [loading, setLoading] = useState(true)
  const [exchangingId, setExchangingId] = useState<number | null>(null)
  /** 刚兑换成功的资产：卡片播放星光脉冲（兑换动效，规格 3471） */
  const [justExchangedId, setJustExchangedId] = useState<number | null>(null)

  // 收藏馆
  const [groups, setGroups] = useState<CollectionGroup>({})

  // 品牌池
  const [brands, setBrands] = useState<BrandItem[]>([])
  const [poolsByBrand, setPoolsByBrand] = useState<Record<number, BrandPoolItem[]>>({})
  const [joiningPool, setJoiningPool] = useState<string | null>(null)

  const loadWorkshop = useCallback(async () => {
    const res = await getWorkshopAssets()
    if (res.data.success) setAssets(res.data.data ?? [])
  }, [])

  const loadCollections = useCallback(async () => {
    const res = await getCollections()
    if (res.data.success) setGroups(res.data.data ?? {})
  }, [])

  const loadBrands = useCallback(async () => {
    const res = await listBrands()
    if (res.data.success) setBrands(res.data.data ?? [])
  }, [])

  const loadPools = useCallback(async (brandId: number) => {
    const res = await listBrandPools(brandId)
    if (res.data.success) {
      setPoolsByBrand((prev) => ({ ...prev, [brandId]: res.data.data ?? [] }))
    }
  }, [])

  const loadAll = useCallback(async () => {
    setLoading(true)
    try {
      await Promise.all([loadWorkshop(), loadCollections(), loadBrands()])
    } finally {
      setLoading(false)
    }
  }, [loadWorkshop, loadCollections, loadBrands])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/wish/workshop')
      return
    }
    if (user) loadAll()
  }, [user, userLoading, loadAll])

  const handleExchange = (asset: WorkshopAsset) => {
    Modal.confirm({
      title: `兑换「${asset.name}」`,
      content: `将消耗 ${asset.priceStarlight} 星光（库存剩余 ${asset.stock}），确认兑换？`,
      okText: '确认兑换',
      cancelText: '取消',
      onOk: async () => {
        setExchangingId(asset.assetId)
        try {
          const res = await exchangeAsset(asset.assetId)
          if (res.data.success) {
            message.success('兑换成功，已放入收藏馆 🎉')
            setJustExchangedId(asset.assetId)
            setTimeout(() => setJustExchangedId((cur) => (cur === asset.assetId ? null : cur)), 2600)
            await loadWorkshop()
            await loadCollections()
          }
        } catch {
          // 402 余额不足 / 409 已拥有 等业务错误由拦截器提示
        } finally {
          setExchangingId(null)
        }
      },
    })
  }

  const handleActivate = async (assetId: number, kind: 'SKIN' | 'BGM') => {
    try {
      const res = kind === 'SKIN' ? await setActiveSkin(assetId) : await setActiveBgm(assetId)
      if (res.data.success) {
        message.success(kind === 'SKIN' ? '皮肤已切换，世界树即时生效' : 'BGM 已切换')
        await loadCollections()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const handleJoin = async (brand: BrandItem, pool: BrandPoolItem) => {
    const key = `${brand.brandId}:${pool.poolId}`
    setJoiningPool(key)
    try {
      const res = await joinBrandPool(brand.brandId, pool.poolId)
      if (res.data.success) {
        message.success(`已加入「${pool.poolName}」，达成后可获品牌奖励`)
        await loadPools(brand.brandId)
      }
    } catch {
      // 409 重复加入等由拦截器提示
    } finally {
      setJoiningPool(null)
    }
  }

  const assetCards = tab === 'workshop' ? assets : []

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.backBar}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => history.push('/wish/my')}
          className={styles.backBtn}
        >
          我的心愿
        </Button>
      </div>

      <Card className={styles.listCard}>
        <Segmented
          block
          value={tab}
          onChange={(v) => setTab(v as TabKey)}
          options={[
            { value: 'workshop', label: '虚拟工坊' },
            { value: 'collections', label: '收藏馆' },
            { value: 'brands', label: '品牌许愿池' },
          ]}
          style={{ marginBottom: 20 }}
        />

        {loading ? (
          <Skeleton />
        ) : tab === 'workshop' ? (
          assetCards.length === 0 ? (
            <Empty description="工坊暂无上架资产" />
          ) : (
            <div className={styles.wishList}>
              {assets.map((asset) => (
                <Card key={asset.assetId} size="small"
                      className={`${styles.wishCard} ${justExchangedId === asset.assetId ? styles.justExchanged : ''}`}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
                      <AssetIcon icon={asset.icon} alt={asset.name} />
                      <div style={{ flex: 1 }}>
                        <div style={{ marginBottom: 4 }}>
                          <Tag color="purple">{TYPE_LABELS[asset.assetType] ?? asset.assetType}</Tag>
                          <span style={{ fontWeight: 600 }}>{asset.name}</span>
                          {asset.owned && <Tag color="green" style={{ marginLeft: 8 }}>已拥有</Tag>}
                        </div>
                      <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginBottom: 6 }}>
                        {asset.description ?? '—'}
                      </div>
                      <div style={{ fontSize: 13 }}>
                        <StarOutlined style={{ color: '#FFD700' }} /> {asset.priceStarlight} 星光
                        <span style={{ color: 'var(--color-text-secondary)', marginLeft: 12 }}>库存 {asset.stock}</span>
                      </div>
                      </div>
                    </div>
                    <Button
                      type="primary"
                      icon={<GiftOutlined />}
                      disabled={asset.owned || asset.stock <= 0}
                      loading={exchangingId === asset.assetId}
                      onClick={() => handleExchange(asset)}
                    >
                      {justExchangedId === asset.assetId ? '✨ 已入馆' : asset.owned ? '已拥有' : asset.stock <= 0 ? '已售罄' : '兑换'}
                    </Button>
                  </div>
                </Card>
              ))}
            </div>
          )
        ) : tab === 'collections' ? (
          Object.keys(groups).length === 0 ? (
            <Empty description="收藏馆还是空的，去工坊兑换或完成星火收藏吧" />
          ) : (
            <>
            <Collections3DShowcase groups={groups} />
            {Object.entries(groups).map(([type, items]) => (
              <Card key={type} size="small" title={GROUP_LABELS[type] ?? type} style={{ marginBottom: 16 }}>
                {items.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无" />
                ) : (
                  items.map((item) => (
                    <div
                      key={item.id}
                      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0' }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <AssetIcon icon={item.icon} alt={item.name} size={28} />
                        <span style={{ fontWeight: 500 }}>{item.name}</span>
                        {item.isActive && <Tag color="green" style={{ marginLeft: 8 }}>使用中</Tag>}
                      </div>
                      {(type === 'SKIN' || type === 'BGM') && (
                        <Button
                          size="small"
                          icon={<SwapOutlined />}
                          disabled={item.isActive === true}
                          onClick={() => handleActivate(item.assetId, type as 'SKIN' | 'BGM')}
                        >
                          {item.isActive ? '使用中' : '使用'}
                        </Button>
                      )}
                      {type === 'SPECIAL_FRUIT' && item.refWishId && (
                        <Button size="small" type="link" onClick={() => history.push(`/wish/${item.refWishId}`)}>
                          查看心愿
                        </Button>
                      )}
                    </div>
                  ))
                )}
              </Card>
            ))}
            </>
          )
        ) : brands.length === 0 ? (
          <Empty description="暂无合作品牌" />
        ) : (
          brands.map((brand) => (
            <Card
              key={brand.brandId}
              size="small"
              title={<span><CrownOutlined style={{ color: '#FFD700' }} /> {brand.brandName}</span>}
              style={{ marginBottom: 16 }}
              extra={<Tag color="gold">{brand.status}</Tag>}
            >
              <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginBottom: 12 }}>
                {brand.description ?? '—'}
              </div>
              {(poolsByBrand[brand.brandId] ?? []).length === 0 ? (
                <Button size="small" onClick={() => loadPools(brand.brandId)}>
                  查看许愿池
                </Button>
              ) : (
                (poolsByBrand[brand.brandId] ?? []).map((pool) => (
                  <div key={pool.poolId} style={{ borderTop: '1px solid var(--color-border)', paddingTop: 12, marginTop: 12 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ fontWeight: 500 }}>{pool.poolName}</span>
                      <Tag>{pool.status}</Tag>
                    </div>
                    <Progress
                      percent={pool.targetCount > 0 ? Math.min(Math.round((pool.currentCount / pool.targetCount) * 100), 100) : 0}
                      size={['100%', 8]}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
                      <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                        {pool.currentCount}/{pool.targetCount} 人已加入{pool.rewardDescription ? ` · ${pool.rewardDescription}` : ''}
                      </span>
                      <Button
                        size="small"
                        type="primary"
                        icon={<StarOutlined />}
                        loading={joiningPool === `${brand.brandId}:${pool.poolId}`}
                        onClick={() => handleJoin(brand, pool)}
                      >
                        加入
                      </Button>
                    </div>
                  </div>
                ))
              )}
            </Card>
          ))
        )}
      </Card>
    </div>
  )
}
