import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import WorldTree3D from '@/components/WorldTree3D'
import { resolveTreeEnvTheme } from '@/utils/tree-env'
import type {
  EnvConfigItem,
  FruitType,
  TreeEnvSnapshot,
  TreeFruit,
  TreeFruitsQuery,
  TreeSeason,
  TreeWeather,
  WorldTreeAggregation,
} from '@/types'
import styles from './index.module.scss'

const FRUIT_LABELS: Record<FruitType, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<FruitType, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

const SEASON_LABELS: Record<TreeSeason, string> = {
  SPRING: '春',
  SUMMER: '夏',
  AUTUMN: '秋',
  WINTER: '冬',
}

const WEATHER_LABELS: Record<TreeWeather, string> = {
  SUNNY: '☀️ 晴',
  CLOUDY: '☁️ 云',
  RAIN: '🌧️ 雨',
  SNOW: '❄️ 雪',
  RAINBOW: '🌈 虹',
}

/** 环境快照缺失时回退旧版三维环境标签（Sprint 2.1 契约） */
const LEGACY_ENVIRONMENT_LABELS: Record<string, string> = {
  SUNNY: '☀️ 晴',
  RAIN: '🌧️ 雨',
  RAINBOW: '🌈 虹',
}

/** 首屏全量拉取条数（无 bounds；H5 后续由视口变化增量拉取） */
const FIRST_PAGE_SIZE = 100

/** 环境快照轮询间隔（ms）：特殊事件全站同步 + 情绪扫描 5min，1min 拉取足够实时 */
const TREE_ENV_POLL_MS = 60000

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/**
 * 世界树页面（Sprint 2.1）：
 * H5 端 three.js 真 3D（拖拽旋转 + 视口变化增量拉取）；
 * 小程序端由 WorldTree3D 组件降级为伪 3D 星图（仅首屏数据）。
 */
export default function WorldTreePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [loading, setLoading] = useState(true)
  const [aggregation, setAggregation] = useState<WorldTreeAggregation | null>(null)
  const [fruits, setFruits] = useState<TreeFruit[]>([])
  const [selectedFruit, setSelectedFruit] = useState<TreeFruit | null>(null)
  const [envSnapshot, setEnvSnapshot] = useState<TreeEnvSnapshot | null>(null)
  const [envConfigs, setEnvConfigs] = useState<EnvConfigItem[]>([])
  const fruitsMapRef = useRef<Map<number, TreeFruit>>(new Map())

  /** 环境主题（displayEnv 优先级仲裁：特殊事件 > 情绪天气 > 季节/时段） */
  const envTheme = useMemo(
      () => (envSnapshot || envConfigs.length > 0 ? resolveTreeEnvTheme(envSnapshot, envConfigs) : null),
      [envSnapshot, envConfigs],
  )

  /** 合并果实（按 id 去重）：首屏全量与 H5 视口增量均走此入口 */
  const mergeFruits = useCallback((list: TreeFruit[]) => {
    const map = fruitsMapRef.current
    list.forEach((fruit) => map.set(fruit.id, fruit))
    setFruits(Array.from(map.values()))
  }, [])

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [treeRes, fruitsRes, envRes, configsRes] = await Promise.all([
          wishApi.getWorldTree(),
          wishApi.listTreeFruits({ pageSize: FIRST_PAGE_SIZE }),
          wishApi.getTreeEnv(),
          wishApi.listEnvConfigs(),
        ])
        if (treeRes.data.success && treeRes.data.data) {
          setAggregation(treeRes.data.data)
        }
        if (fruitsRes.data.success && Array.isArray(fruitsRes.data.data)) {
          mergeFruits(fruitsRes.data.data)
        }
        if (envRes.data.success && envRes.data.data) {
          setEnvSnapshot(envRes.data.data)
        }
        if (configsRes.data.success && Array.isArray(configsRes.data.data)) {
          setEnvConfigs(configsRes.data.data)
        }
      } catch {
        // 错误已由 request 统一处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [mergeFruits])

  /** 环境快照轮询：特殊事件触发/到期后 1min 内全端视觉同步 */
  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const res = await wishApi.getTreeEnv()
        if (res.data.success && res.data.data) {
          setEnvSnapshot(res.data.data)
        }
      } catch {
        // 轮询失败静默降级，保留上一轮环境
      }
    }, TREE_ENV_POLL_MS)
    return () => clearInterval(timer)
  }, [])

  /** H5 视口变化增量拉取（小程序降级版不触发） */
  const handleViewportChange = useCallback(
      async (query: TreeFruitsQuery) => {
        try {
          const res = await wishApi.listTreeFruits(query)
          if (res.data.success && Array.isArray(res.data.data)) {
            mergeFruits(res.data.data)
          }
        } catch {
          // 错误已由 request 统一处理
        }
      },
      [mergeFruits],
  )

  const goDetail = () => {
    if (!selectedFruit) return
    Taro.navigateTo({ url: `/pages/wishDetail/index?id=${selectedFruit.id}` })
  }

  if (loading) {
    return (
        <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
          <CustomNavBar title='世界树' />
          <View className={styles.loading}>
            <View className={styles.spinner} />
          </View>
        </View>
    )
  }

  return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
        <CustomNavBar title='世界树' />
        <View className={styles.content} style={{ height: `calc(100vh - ${statusBarHeight + navBarHeight}px)` }}>
          {aggregation ? (
              <View className={styles.statsBar}>
                <View className={styles.statsRow}>
                  <View className={styles.statItem}>
                    <Text className={styles.statValue}>{formatCount(aggregation.totalFruits)}</Text>
                    <Text className={styles.statLabel}>果实</Text>
                  </View>
                  <View className={styles.statItem}>
                    <Text className={styles.statValue}>{formatCount(aggregation.totalBloom)}</Text>
                    <Text className={styles.statLabel}>绽放</Text>
                  </View>
                  <View className={styles.statItem}>
                    <Text className={styles.statValue}>{formatCount(aggregation.totalLight)}</Text>
                    <Text className={styles.statLabel}>星光</Text>
                  </View>
                </View>
                <View className={styles.envTags}>
                  <Text className={styles.envTag}>{SEASON_LABELS[envSnapshot?.season ?? aggregation.season]}</Text>
                  {envSnapshot ? (
                      <Text className={styles.envTag}>{WEATHER_LABELS[envSnapshot.weather]}</Text>
                  ) : (
                      <Text className={styles.envTag}>
                        {LEGACY_ENVIRONMENT_LABELS[aggregation.environment] ?? aggregation.environment}
                      </Text>
                  )}
                  {envSnapshot?.specialEvent && (
                      <Text className={styles.envTag}>{envSnapshot.specialEvent.title}</Text>
                  )}
                </View>
              </View>
          ) : (
              !loading && (
                  <View className={styles.statsBar}>
                    <Text className={styles.statLabel}>树语暂不可读</Text>
                  </View>
              )
          )}

          <View className={styles.treeStage}>
            <View className={styles.treeStageInner}>
              <WorldTree3D
                  fruits={fruits}
                  theme={envTheme}
                  onFruitSelect={setSelectedFruit}
                  onViewportChange={handleViewportChange}
              />
            </View>
          </View>

          <View className={styles.legend}>
            {(Object.keys(FRUIT_LABELS) as FruitType[]).map((type) => (
                <View className={styles.legendItem} key={type}>
                  <View className={styles.legendDot} style={{ background: FRUIT_COLORS[type] }} />
                  <Text className={styles.legendText}>{FRUIT_LABELS[type]}</Text>
                </View>
            ))}
          </View>
        </View>

        {selectedFruit && (
            <View className={styles.detailMask} onClick={() => setSelectedFruit(null)}>
              <View className={styles.detailCard} onClick={(e) => e.stopPropagation()}>
                <View className={styles.detailHeader}>
                  <View
                      className={styles.detailDot}
                      style={{ background: FRUIT_COLORS[selectedFruit.fruitType] }}
                  />
                  <Text className={styles.detailTitle}>{selectedFruit.title}</Text>
                </View>
                <View className={styles.detailMeta}>
                  <Text className={styles.detailAuthor}>{selectedFruit.authorNickname}</Text>
                  <Text className={styles.detailLight}>✨ {formatCount(selectedFruit.lightCount)} 星光</Text>
                </View>
                <View className={styles.detailActions}>
                  <View className={styles.detailBtnSecondary} onClick={() => setSelectedFruit(null)}>
                    <Text className={styles.detailBtnSecondaryText}>关闭</Text>
                  </View>
                  <View className={styles.detailBtnPrimary} onClick={goDetail}>
                    <Text className={styles.detailBtnText}>查看详情</Text>
                  </View>
                </View>
              </View>
            </View>
        )}
        <WishBGM />
      </View>
  )
}
