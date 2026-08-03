import { View, Text, TextInput, TouchableOpacity, FlatList, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { communityApi } from '@/api/community'
import { productApi } from '@/api/product'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Post, Product } from '@/types'

const DEFAULT_HOT_SEARCHES = ['穿搭', '美食', '旅行', '好物推荐', '护肤', '数码']
const PAGE_SIZE = 10

type SearchType = 'post' | 'product'

export default function SearchScreen() {
  const theme = useTheme()
  const params = useLocalSearchParams<{ type?: string }>()
  const initialType: SearchType = params.type === 'product' ? 'product' : 'post'

  const [searchType, setSearchType] = useState<SearchType>(initialType)
  const [keyword, setKeyword] = useState('')
  const [isSearching, setIsSearching] = useState(false)
  const [hotSearches, setHotSearches] = useState<string[]>(DEFAULT_HOT_SEARCHES)
  const [searchHistory, setSearchHistory] = useState<string[]>([])
  const [results, setResults] = useState<Post[] | Product[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const inputRef = useRef<TextInput>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    loadHotSearches()
    loadSearchHistory()
  }, [])

  const loadHotSearches = async () => {
    try {
      const res = await communityApi.getHotSearch()
      const list = res.data?.data
      if (Array.isArray(list) && list.length > 0) {
        setHotSearches(list.map((item: string | { keyword?: string; name?: string }) =>
          typeof item === 'string' ? item : item.keyword || item.name || '',
        ).filter(Boolean))
      }
    } catch {
      // fallback to default hot searches
    }
  }

  const loadSearchHistory = async () => {
    try {
      const res = await communityApi.getSearchHistory()
      const list = res.data?.data
      if (Array.isArray(list)) {
        setSearchHistory(list.map((item: string | { keyword?: string }) =>
          typeof item === 'string' ? item : item.keyword || '',
        ).filter(Boolean))
      }
    } catch {
      // no history available
    }
  }

  const handleSearch = useCallback(async (text: string) => {
    const trimmed = text.trim()
    if (!trimmed) return

    setKeyword(trimmed)
    setIsSearching(true)
    setPage(1)
    setHasMore(true)
    setResults([])

    try {
      if (searchType === 'post') {
        const res = await communityApi.searchPosts({ keyword: trimmed, page: 1, pageSize: PAGE_SIZE })
        const list = res.data?.data?.list || res.data?.data || []
        setResults(list)
        setHasMore(list.length >= PAGE_SIZE)
      } else {
        const res = await productApi.search({ keyword: trimmed, page: 1, size: PAGE_SIZE })
        const list = res.data?.data?.products || res.data?.data || []
        setResults(list)
        setHasMore(list.length >= PAGE_SIZE)
      }
      loadSearchHistory()
    } catch {
      setResults([])
      setHasMore(false)
    } finally {
      setLoading(false)
    }
  }, [searchType])

  const loadMore = useCallback(async () => {
    if (!keyword || loading || !hasMore) return

    const nextPage = page + 1
    setLoading(true)

    try {
      if (searchType === 'post') {
        const res = await communityApi.searchPosts({ keyword, page: nextPage, pageSize: PAGE_SIZE })
        const list = res.data?.data?.list || res.data?.data || []
        setResults((prev) => [...prev, ...list] as Post[])
        setHasMore(list.length >= PAGE_SIZE)
      } else {
        const res = await productApi.search({ keyword, page: nextPage, size: PAGE_SIZE })
        const list = res.data?.data?.products || res.data?.data || []
        setResults((prev) => [...prev, ...list] as Product[])
        setHasMore(list.length >= PAGE_SIZE)
      }
      setPage(nextPage)
    } catch {
      setHasMore(false)
    } finally {
      setLoading(false)
    }
  }, [keyword, page, loading, hasMore, searchType])

  const handleRefresh = useCallback(async () => {
    if (!keyword) return
    setRefreshing(true)
    setPage(1)
    setHasMore(true)

    try {
      if (searchType === 'post') {
        const res = await communityApi.searchPosts({ keyword, page: 1, pageSize: PAGE_SIZE })
        const list = res.data?.data?.list || res.data?.data || []
        setResults(list)
        setHasMore(list.length >= PAGE_SIZE)
      } else {
        const res = await productApi.search({ keyword, page: 1, size: PAGE_SIZE })
        const list = res.data?.data?.products || res.data?.data || []
        setResults(list)
        setHasMore(list.length >= PAGE_SIZE)
      }
    } catch {
      setResults([])
    } finally {
      setRefreshing(false)
    }
  }, [keyword, searchType])

  const handleClearHistory = () => {
    Alert.alert('确认清除', '确定要清除所有搜索历史吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '清除',
        style: 'destructive',
        onPress: async () => {
          try {
            await communityApi.clearSearchHistory()
            setSearchHistory([])
          } catch {
            Alert.alert('错误', '清除失败')
          }
        },
      },
    ])
  }

  const handleSwitchType = (type: SearchType) => {
    setSearchType(type)
    if (keyword.trim()) {
      handleSearch(keyword)
    } else {
      setIsSearching(false)
      setResults([])
    }
  }

  const handleSubmitEditing = () => {
    handleSearch(keyword)
  }

  const renderPostItem = ({ item }: { item: Post }) => (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/post-detail?id=${item.id}`)}
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
      }}
    >
      {item.images?.[0] ? (
        <Image source={{ uri: item.images[0] }} style={{ width: 110, height: 110, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: 110, height: 110, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 28, opacity: 0.3 }}>📝</Text>
        </View>
      )}
      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }} numberOfLines={2}>{item.title}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
          {item.user?.avatar ? (
            <Image source={{ uri: item.user.avatar }} style={{ width: 20, height: 20, borderRadius: 10 }} />
          ) : null}
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }} numberOfLines={1}>
            {item.user?.nickname || '匿名用户'}
          </Text>
        </View>
        <View style={{ flexDirection: 'row', gap: Spacing.md }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤️ {item.likeCount}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>💬 {item.commentCount}</Text>
        </View>
      </View>
    </TouchableOpacity>
  )

  const renderProductItem = ({ item }: { item: Product }) => (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/product/${item.id}`)}
      style={{
        width: '48%',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
      }}
    >
      {item.mainImage ? (
        <Image source={{ uri: item.mainImage }} style={{ width: '100%', height: 140, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: '100%', height: 140, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 28, opacity: 0.3 }}>🛍️</Text>
        </View>
      )}
      <View style={{ padding: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.text, fontWeight: '500' }} numberOfLines={2}>{item.name}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.xs, marginTop: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.lg, color: theme.accentRed, fontWeight: '700' }}>
            ¥{item.price}
          </Text>
          {item.originalPrice && item.originalPrice > item.price && (
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, textDecorationLine: 'line-through' }}>
              ¥{item.originalPrice}
            </Text>
          )}
        </View>
      </View>
    </TouchableOpacity>
  )

  const renderSearchContent = () => {
    if (!isSearching) {
      return (
        <View style={{ padding: Spacing.lg }}>
          {/* Hot Searches */}
          <View style={{ marginBottom: Spacing.xxl }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.md }}>
              🔥 热门搜索
            </Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
              {hotSearches.map((tag) => (
                <TouchableOpacity
                  key={tag}
                  activeOpacity={0.7}
                  onPress={() => handleSearch(tag)}
                  style={{
                    paddingHorizontal: Spacing.md,
                    paddingVertical: Spacing.sm,
                    backgroundColor: theme.bgElevated,
                    borderRadius: BorderRadius.xl,
                    borderWidth: 1,
                    borderColor: theme.border,
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{tag}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          {/* Search History */}
          {searchHistory.length > 0 && (
            <View>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md }}>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>
                  🕐 搜索历史
                </Text>
                <TouchableOpacity activeOpacity={0.7} onPress={handleClearHistory}>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>🗑️ 清除</Text>
                </TouchableOpacity>
              </View>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                {searchHistory.map((item, index) => (
                  <TouchableOpacity
                    key={`${item}-${index}`}
                    activeOpacity={0.7}
                    onPress={() => handleSearch(item)}
                    style={{
                      paddingHorizontal: Spacing.md,
                      paddingVertical: Spacing.sm,
                      backgroundColor: theme.bgElevated,
                      borderRadius: BorderRadius.xl,
                      borderWidth: 1,
                      borderColor: theme.border,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>{item}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </View>
      )
    }

    if (results.length === 0 && !loading) {
      return (
        <View style={{ alignItems: 'center', paddingTop: 100 }}>
          <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>🔍</Text>
          <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>
            未找到相关{searchType === 'post' ? '帖子' : '商品'}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            换个关键词试试吧
          </Text>
        </View>
      )
    }

    if (searchType === 'post') {
      return (
        <FlatList
          data={results as Post[]}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderPostItem}
          contentContainerStyle={{ padding: Spacing.lg }}
          refreshing={refreshing}
          onRefresh={handleRefresh}
          onEndReached={loadMore}
          onEndReachedThreshold={0.3}
          ListFooterComponent={loading ? (
            <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
              <ActivityIndicator size="small" color={theme.primary} />
            </View>
          ) : !hasMore && results.length > 0 ? (
            <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>没有更多了</Text>
            </View>
          ) : null}
        />
      )
    }

    return (
      <FlatList
        data={results as Product[]}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderProductItem}
        numColumns={2}
        columnWrapperStyle={{ gap: Spacing.md }}
        contentContainerStyle={{ padding: Spacing.lg }}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        onEndReached={loadMore}
        onEndReachedThreshold={0.3}
        ListFooterComponent={loading ? (
          <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
            <ActivityIndicator size="small" color={theme.primary} />
          </View>
        ) : !hasMore && results.length > 0 ? (
          <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>没有更多了</Text>
          </View>
        ) : null}
      />
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Search Header */}
      <View style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.md,
        backgroundColor: theme.bgContainer,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
        gap: Spacing.md,
      }}>
        <TouchableOpacity activeOpacity={0.7} onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.xl, color: theme.text }}>←</Text>
        </TouchableOpacity>
        <View style={{
          flex: 1,
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: theme.bgInput,
          borderRadius: BorderRadius.xl,
          paddingHorizontal: Spacing.md,
          height: 40,
        }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textTertiary, marginRight: Spacing.sm }}>🔍</Text>
          <TextInput
            ref={inputRef}
            placeholder={`搜索${searchType === 'post' ? '帖子' : '商品'}...`}
            placeholderTextColor={theme.textTertiary}
            value={keyword}
            onChangeText={setKeyword}
            onSubmitEditing={handleSubmitEditing}
            returnKeyType="search"
            style={{
              flex: 1,
              height: 40,
              color: theme.text,
              fontSize: FontSize.md,
              padding: 0,
            }}
          />
          {keyword.length > 0 && (
            <TouchableOpacity onPress={() => { setKeyword(''); setIsSearching(false); setResults([]) }}>
              <Text style={{ fontSize: FontSize.md, color: theme.textTertiary }}>✕</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Type Toggle */}
      <View style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
      }}>
        {(['post', 'product'] as SearchType[]).map((type) => {
          const isActive = searchType === type
          return (
            <TouchableOpacity
              key={type}
              activeOpacity={0.7}
              onPress={() => handleSwitchType(type)}
              style={{
                flex: 1,
                alignItems: 'center',
                paddingVertical: Spacing.md,
                position: 'relative',
              }}
            >
              <Text style={{
                fontSize: FontSize.md,
                color: isActive ? theme.primary : theme.textTertiary,
                fontWeight: isActive ? '600' : '400',
              }}>
                {type === 'post' ? '帖子' : '商品'}
              </Text>
              {isActive && (
                <View style={{
                  position: 'absolute',
                  bottom: 0,
                  width: 20,
                  height: 3,
                  borderRadius: 1.5,
                  backgroundColor: theme.primary,
                }} />
              )}
            </TouchableOpacity>
          )
        })}
      </View>

      {/* Content */}
      {renderSearchContent()}
    </View>
  )
}
