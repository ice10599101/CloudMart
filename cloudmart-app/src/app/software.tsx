import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  FlatList,
  Image,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { softwareApi, getSoftwareHome, type SoftwareHomeSection } from '@/api/software'
import type { SoftwareCard, SoftwareCategory, SoftwareTopicSummary } from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

const SORT_OPTIONS = [
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

/**
 * 小答软件广场（S13 App 端）：浏览/搜索/分类/排序 + 触底加载。
 * 卡片不暴露提取码，不把"获取链接"说成"下载量"（方案 §3.2）。
 */
export default function SoftwareScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const [keyword, setKeyword] = useState('')
  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [sort, setSort] = useState('recommend')
  const [cards, setCards] = useState<SoftwareCard[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  // SW11-3：广场三模式——软件 / 榜单 / 专题
  const [mode, setMode] = useState<'list' | 'rankings' | 'topics'>('list')
  const [rankingType, setRankingType] = useState<'access' | 'favorite' | 'rating'>('access')
  const [rankItems, setRankItems] = useState<SoftwareCard[]>([])
  const [rankPage, setRankPage] = useState(1)
  const [rankTotal, setRankTotal] = useState(0)
  const [topics, setTopics] = useState<SoftwareTopicSummary[]>([])
  const [topicsLoaded, setTopicsLoaded] = useState(false)
  // UX01：运营配置区块（无配置=null 降级内置列表；单块失败由后端 ERROR 标记）
  const [homeSections, setHomeSections] = useState<SoftwareHomeSection[] | null>(null)

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

  useEffect(() => {
    softwareApi
      .categories()
      .then((res) => setCategories(res.data.data ?? []))
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    void load(1, true)
  }, [load])
  // UX01：首页运营区块（失败/空配置降级 null）
  useEffect(() => {
    getSoftwareHome('APP')
      .then((res) => {
        const blocks = res.data.data ?? []
        setHomeSections(blocks.length > 0 ? blocks : null)
      })
      .catch(() => setHomeSections(null))
  }, [])

  const loadRankings = useCallback(
    async (nextPage: number, replace: boolean) => {
      setLoading(true)
      try {
        const res = await softwareApi.rankings(rankingType, nextPage, PAGE_SIZE)
        const records = res.data.data?.records ?? []
        setRankItems((prev) => (replace ? records : [...prev, ...records]))
        setRankTotal(res.data.data?.total ?? 0)
        setRankPage(nextPage)
      } catch {
        // 错误提示由 request 层统一处理
      } finally {
        setLoading(false)
      }
    },
    [rankingType],
  )

  const loadTopics = useCallback(async () => {
    setLoading(true)
    try {
      const res = await softwareApi.topics()
      setTopics(res.data.data ?? [])
      setTopicsLoaded(true)
    } catch {
      // 错误提示由 request 层统一处理
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

  const renderCard = ({ item }: { item: SoftwareCard }) => (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/software-detail?id=${item.id}`)}
      style={{
        flexDirection: 'row',
        gap: Spacing.sm,
        marginHorizontal: Spacing.md,
        marginTop: Spacing.sm,
        padding: Spacing.md,
        borderRadius: BorderRadius.md,
        backgroundColor: theme.bgContainer,
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      <Image
        source={{ uri: item.iconAssetId ? `/files/${item.iconAssetId}` : undefined }}
        style={{ width: 48, height: 48, borderRadius: BorderRadius.sm, backgroundColor: theme.bgInput }}
      />
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text numberOfLines={1} style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
          {item.name}
        </Text>
        <Text numberOfLines={2} style={{ fontSize: FontSize.xs, color: theme.textSecondary, marginTop: 2 }}>
          {item.summary}
        </Text>
        <View style={{ flexDirection: 'row', gap: Spacing.xs, marginTop: 6, flexWrap: 'wrap' }}>
          {item.categoryName ? (
            <Text style={{ fontSize: FontSize.xs, color: theme.primary }}>
              {item.categoryName}
            </Text>
          ) : null}
          {item.licenseType ? (
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
              {LICENSE_LABEL[item.licenseType] ?? item.licenseType}
            </Text>
          ) : null}
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {item.ratingAvg == null ? '暂无评分' : `${item.ratingAvg.toFixed(1)} 分`}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            收藏 {item.favoriteCount}
          </Text>
        </View>
      </View>
    </TouchableOpacity>
  )

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top + Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingHorizontal: Spacing.md }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: theme.text }}>‹</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, flex: 1 }}>
          小答软件
        </Text>
      </View>

      {/* SW11-3：广场三模式——软件 / 榜单 / 专题 */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.md, marginTop: Spacing.sm }}>
        {([
          { key: 'list', label: '软件' },
          { key: 'rankings', label: '榜单' },
          { key: 'topics', label: '专题' },
        ] as const).map((entry) => (
          <TouchableOpacity key={entry.key} onPress={() => setMode(entry.key)}>
            <Text
              style={{
                fontSize: FontSize.md,
                fontWeight: mode === entry.key ? '700' : '400',
                color: mode === entry.key ? theme.primary : theme.textTertiary,
              }}
            >
              {entry.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.md, marginTop: Spacing.sm }}>
        <TextInput
          value={keyword}
          onChangeText={setKeyword}
          onSubmitEditing={() => void load(1, true)}
          placeholder="搜索软件名称、用途"
          placeholderTextColor={theme.textTertiary}
          style={{
            flex: 1,
            height: 40,
            paddingHorizontal: Spacing.md,
            borderRadius: BorderRadius.full,
            backgroundColor: theme.bgInput,
            color: theme.text,
          }}
        />
        <TouchableOpacity onPress={() => void load(1, true)}>
          <Text style={{ color: theme.primary, fontSize: FontSize.md, lineHeight: 40 }}>搜索</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={() => router.push('/software-mine')}>
          <Text style={{ color: theme.primary, fontSize: FontSize.md, lineHeight: 40 }}>我的软件</Text>
        </TouchableOpacity>
      </View>

      {mode === 'list' && (
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={{ marginTop: Spacing.sm, maxHeight: 40 }}
        contentContainerStyle={{ paddingHorizontal: Spacing.md, gap: Spacing.sm }}
      >
        {[{ id: undefined, name: '全部' }, ...categories].map((category) => (
          <TouchableOpacity
            key={category.id ?? 'all'}
            onPress={() => setCategoryId(category.id)}
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: 6,
              borderRadius: BorderRadius.full,
              backgroundColor:
                categoryId === category.id ? theme.primary : theme.bgContainer,
            }}
          >
            <Text
              style={{
                fontSize: FontSize.sm,
                color: categoryId === category.id ? '#0B1220' : theme.textSecondary,
              }}
            >
              {category.name}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      )}

      {mode === 'list' && (
      <View style={{ flexDirection: 'row', gap: Spacing.md, paddingHorizontal: Spacing.md, marginTop: Spacing.sm }}>
        {SORT_OPTIONS.map((option) => (
          <TouchableOpacity key={option.value} onPress={() => setSort(option.value)}>
            <Text
              style={{
                fontSize: FontSize.sm,
                color: sort === option.value ? theme.primary : theme.textTertiary,
                fontWeight: sort === option.value ? '700' : '400',
              }}
            >
              {option.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      )}

      {mode === 'list' && (
      <FlatList
        data={cards}
        keyExtractor={(item) => item.id}
        renderItem={renderCard}
        ListHeaderComponent={
          (homeSections ?? []).length > 0 ? (
            <View>
              {(homeSections ?? []).map((section) => (
                <View key={section.id} style={{ paddingHorizontal: Spacing.md, marginTop: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
                    {section.title}
                  </Text>
                  {section.state === 'ERROR' ? (
                    <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4 }}>
                      本区块加载失败，下拉重试
                    </Text>
                  ) : (
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: Spacing.xs }}>
                      {(section.items ?? [])
                        .filter((item) => item.software || item.topic)
                        .map((item) => (
                        <TouchableOpacity
                          key={item.itemId}
                          onPress={() => {
                            if (item.software) router.push(`/software-detail?id=${item.software.id}`)
                            else if (item.topic) router.push(`/software-topic?slug=${item.topic.slug}`)
                          }}
                          style={{
                            width: 132,
                            marginRight: Spacing.sm,
                            padding: Spacing.sm,
                            borderRadius: BorderRadius.md,
                            backgroundColor: theme.bgContainer,
                          }}
                        >
                          <Text numberOfLines={1} style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>
                            {item.headline || item.software?.name || item.topic?.title || item.guide?.title || ''}
                          </Text>
                          {(item.editorNote || item.software?.summary || item.topic?.summary) ? (
                            <Text numberOfLines={2} style={{ fontSize: 11, color: theme.textSecondary, marginTop: 2 }}>
                              {item.editorNote || item.software?.summary || item.topic?.summary || item.guide?.summary}
                            </Text>
                          ) : null}
                        </TouchableOpacity>
                      ))}
                    </ScrollView>
                  )}
                </View>
              ))}
            </View>
          ) : null
        }
        onEndReachedThreshold={0.3}
        onEndReached={() => {
          if (!loading && cards.length < total) {
            void load(page + 1, false)
          }
        }}
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: theme.textTertiary, marginTop: Spacing.xl }}>
            {loading ? '加载中…' : '没有找到相关软件'}
          </Text>
        }
        ListFooterComponent={
          <Text style={{ textAlign: 'center', color: theme.textTertiary, padding: Spacing.lg }}>
            {loading ? '加载中…' : cards.length >= total && cards.length > 0 ? '已到底部' : ''}
          </Text>
        }
      />
      )}

      {mode === 'rankings' && (
      <View style={{ flex: 1 }}>
        <View style={{ flexDirection: 'row', gap: Spacing.md, paddingHorizontal: Spacing.md, marginTop: Spacing.sm }}>
          {([
            { key: 'access', label: '获取最多' },
            { key: 'favorite', label: '收藏最多' },
            { key: 'rating', label: '评分最高' },
          ] as const).map((entry) => (
            <TouchableOpacity key={entry.key} onPress={() => setRankingType(entry.key)}>
              <Text
                style={{
                  fontSize: FontSize.sm,
                  fontWeight: rankingType === entry.key ? '700' : '400',
                  color: rankingType === entry.key ? theme.primary : theme.textTertiary,
                }}
              >
                {entry.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
        <FlatList
          data={rankItems}
          keyExtractor={(item) => item.id}
          renderItem={renderCard}
          onEndReachedThreshold={0.3}
          onEndReached={() => {
            if (!loading && rankItems.length < rankTotal) {
              void loadRankings(rankPage + 1, false)
            }
          }}
          ListEmptyComponent={
            <Text style={{ textAlign: 'center', color: theme.textTertiary, marginTop: Spacing.xl }}>
              {loading ? '加载中…' : '暂无榜单数据'}
            </Text>
          }
        />
      </View>
      )}

      {mode === 'topics' && (
      <FlatList
        data={topics}
        keyExtractor={(item) => item.slug}
        renderItem={({ item }) => (
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={() => router.push(`/software-topic?slug=${item.slug}`)}
            style={{
              marginHorizontal: Spacing.md,
              marginTop: Spacing.sm,
              padding: Spacing.md,
              borderRadius: BorderRadius.md,
              backgroundColor: theme.bgContainer,
              borderWidth: 1,
              borderColor: theme.border,
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
              {item.title}
            </Text>
            {!!item.summary && (
              <Text numberOfLines={2} style={{ marginTop: 4, fontSize: FontSize.sm, color: theme.textSecondary }}>
                {item.summary}
              </Text>
            )}
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: theme.textTertiary, marginTop: Spacing.xl }}>
            {loading ? '加载中…' : '暂无专题'}
          </Text>
        }
      />
      )}
    </View>
  )
}
