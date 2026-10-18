import { useCallback, useEffect, useState } from 'react'
import { Image, Input, ScrollView, Text, View } from '@tarojs/components'
import Taro, { useReachBottom } from '@tarojs/taro'
import { softwareApi } from '@/api/software'
import type { SoftwareTopicSummary, SoftwareCard, SoftwareCategory  } from '@/api/software'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'
import { errorMessage } from '@/utils/request'

const SORT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'recommend', label: '推荐' },
  { value: 'latest', label: '最新' },
  { value: 'popular', label: '热门' },
  { value: 'rating', label: '评分' },
]

const LICENSE_LABEL: Record<string, string> = {
  FREE: '免费',
  OPEN_SOURCE: '开源',
  TRIAL: '试用',
  COMMERCIAL: '商业',
}

const PAGE_SIZE = 20

function iconUrl(assetId: string | null): string | undefined {
  return assetId ? `/files/${assetId}` : undefined
}

/**
 * 小答软件广场（S13 移动端）：浏览/搜索/分类/排序 + 触底加载；
 * 卡片不暴露提取码，不把"获取链接"说成"下载量"（方案 §3.2）。
 */
export default function SoftwareList() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [keyword, setKeyword] = useState('')
  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [sort, setSort] = useState('recommend')
  const [cards, setCards] = useState<SoftwareCard[]>([])
  const [page, setPage] = useState(1)
  /** SW11-3：广场三模式——软件 / 榜单 / 专题 */
  const [mode, setMode] = useState<'list' | 'rankings' | 'topics'>('list')
  const [rankingType, setRankingType] = useState<'access' | 'favorite' | 'rating'>('access')
  const [rankItems, setRankItems] = useState<SoftwareCard[]>([])
  const [topics, setTopics] = useState<SoftwareTopicSummary[]>([])
  const [topicsLoaded, setTopicsLoaded] = useState(false)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)

  const load = useCallback(
    async (nextPage: number, replace: boolean) => {
      setLoading(true)
      try {
        const res = await softwareApi.list({
          keyword: keyword.trim() || undefined,
          categoryId,
          sort,
          page: nextPage,
          size: PAGE_SIZE,
        })
        const records = res.data.data?.records ?? []
        setCards((prev) => (replace ? records : [...prev, ...records]))
        setTotal(res.data.data?.total ?? 0)
        setPage(nextPage)
      } catch {
        // 错误提示由 request 层统一处理
      } finally {
        setLoading(false)
      }
    },
    [keyword, categoryId, sort],
  )

  /** SW11-3：榜单/专题加载 */
  const loadRankings = useCallback(async (nextPage: number, replace: boolean) => {
    setLoading(true)
    try {
      const res = await softwareApi.rankings(rankingType, nextPage, 20)
      const records = res.data.data?.records ?? []
      setRankItems((prev) => (replace ? records : [...prev, ...records]))
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '榜单加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [rankingType])

  const loadTopics = useCallback(async () => {
    setLoading(true)
    try {
      const res = await softwareApi.topics()
      setTopics(res.data.data ?? [])
      setTopicsLoaded(true)
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '专题加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (mode === 'rankings') void loadRankings(1, true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, rankingType])

  useEffect(() => {
    if (mode === 'topics' && !topicsLoaded) void loadTopics()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, topicsLoaded])

  useEffect(() => {
    softwareApi
      .categories()
      .then((res) => setCategories(res.data.data ?? []))
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    void load(1, true)
  }, [load])

  useReachBottom(() => {
    if (loading || cards.length >= total) return
    void load(page + 1, false)
  })

  return (
    <View className={styles.page} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="小答软件" back />
      {/* SW11-3：广场三模式 */}
      <View className={styles.modeRow}>
        {([ { key: 'list', label: '软件' }, { key: 'rankings', label: '榜单' }, { key: 'topics', label: '专题' } ] as const).map((entry) => (
          <Text
            key={entry.key}
            className={`${styles.modeItem} ${mode === entry.key ? styles.modeActive : ''}`}
            onClick={() => setMode(entry.key)}
          >
            {entry.label}
          </Text>
        ))}
      </View>
      {mode === 'list' && (
      <>
      <View className={styles.searchRow}>
        <Input
          className={styles.searchInput}
          value={keyword}
          placeholder="搜索软件名称、用途"
          confirmType="search"
          onInput={(event) => setKeyword(event.detail.value)}
          onConfirm={() => void load(1, true)}
        />
        <Text className={styles.searchBtn} onClick={() => void load(1, true)}>
          搜索
        </Text>
        <Text className={styles.mineEntry} onClick={() => Taro.navigateTo({ url: '/pages/softwareMine/index' })}>
          我的软件
        </Text>
      </View>

      <ScrollView className={styles.categoryBar} scrollX enableFlex>
        <Text
          className={`${styles.categoryTag} ${categoryId === undefined ? styles.categoryActive : ''}`}
          onClick={() => {
            setCategoryId(undefined)
          }}
        >
          全部
        </Text>
        {categories.map((category) => (
          <Text
            key={category.id}
            className={`${styles.categoryTag} ${
              categoryId === category.id ? styles.categoryActive : ''
            }`}
            onClick={() => {
              setCategoryId(category.id)
            }}
          >
            {category.name}
          </Text>
        ))}
      </ScrollView>
      </>
      )}

      {mode === 'list' && (
      <View className={styles.sortRow}>
        {SORT_OPTIONS.map((option) => (
          <Text
            key={option.value}
            className={`${styles.sortItem} ${sort === option.value ? styles.sortActive : ''}`}
            onClick={() => setSort(option.value)}
          >
            {option.label}
          </Text>
        ))}
      </View>
      )}

      {mode === 'rankings' && (
      <View className={styles.modeRow}>
        {([ { key: 'access', label: '获取最多' }, { key: 'favorite', label: '收藏最多' }, { key: 'rating', label: '评分最高' } ] as const).map((entry) => (
          <Text
            key={entry.key}
            className={`${styles.modeItem} ${rankingType === entry.key ? styles.modeActive : ''}`}
            onClick={() => setRankingType(entry.key)}
          >
            {entry.label}
          </Text>
        ))}
      </View>
      )}

      {mode === 'list' && (
      <>
      {cards.length === 0 && !loading ? (
        <Text className={styles.emptyText}>没有找到相关软件</Text>
      ) : (
        cards.map((item) => (
          <View
            key={item.id}
            className={styles.card}
            onClick={() => Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${item.id}` })}
          >
            <Image
              className={styles.cardIcon}
              src={iconUrl(item.iconAssetId) ?? '/assets/default-avatar.png'}
              mode="aspectFill"
            />
            <View className={styles.cardBody}>
              <Text className={styles.cardTitle}>{item.name}</Text>
              <Text className={styles.cardSummary}>{item.summary}</Text>
              <View className={styles.cardMeta}>
                {item.categoryName ? (
                  <Text className={styles.metaTag}>{item.categoryName}</Text>
                ) : null}
                {item.licenseType ? (
                  <Text className={styles.metaTag}>
                    {LICENSE_LABEL[item.licenseType] ?? item.licenseType}
                  </Text>
                ) : null}
                <Text className={styles.metaText}>
                  {item.ratingAvg == null
                    ? '暂无评分'
                    : `${item.ratingAvg.toFixed(1)} 分（${item.ratingCount}）`}
                </Text>
                <Text className={styles.metaText}>收藏 {item.favoriteCount}</Text>
              </View>
            </View>
          </View>
        ))
      )}

      <Text className={styles.footerText}>
        {loading ? '加载中…' : cards.length >= total ? '已到底部' : '上拉加载更多'}
      </Text>
      </>
      )}

      {mode === 'rankings' && (
      <>
        {rankItems.length === 0 && !loading ? (
          <Text className={styles.emptyText}>暂无榜单数据</Text>
        ) : (
          rankItems.map((item) => (
            <View
              key={item.id}
              className={styles.card}
              onClick={() => Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${item.id}` })}
            >
              <Image
                className={styles.cardIcon}
                src={iconUrl(item.iconAssetId) ?? '/assets/default-avatar.png'}
                mode="aspectFill"
              />
              <View className={styles.cardBody}>
                <Text className={styles.cardTitle}>{item.name}</Text>
                <Text className={styles.cardSummary}>{item.summary}</Text>
              </View>
            </View>
          ))
        )}
      </>
      )}

      {mode === 'topics' && (
      <>
        {topics.length === 0 && !loading ? (
          <Text className={styles.emptyText}>暂无专题</Text>
        ) : (
          topics.map((topic) => (
            <View
              key={topic.slug}
              className={styles.card}
              onClick={() => Taro.navigateTo({ url: `/pages/softwareTopic/index?slug=${topic.slug}` })}
            >
              <View className={styles.cardBody}>
                <Text className={styles.cardTitle}>{topic.title}</Text>
                <Text className={styles.cardSummary}>{topic.summary ?? ''}</Text>
              </View>
            </View>
          ))
        )}
      </>
      )}
    </View>
  )
}
