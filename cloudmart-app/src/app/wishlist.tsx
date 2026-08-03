import { View, Text, FlatList, TouchableOpacity, Image, ActivityIndicator, Alert, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { userApi } from '@/api/user'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { WishlistItem } from '@/types'

const PAGE_SIZE = 20

export default function WishlistScreen() {
  const theme = useTheme()
  const [items, setItems] = useState<WishlistItem[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadWishlist = useCallback(async (pageNum: number, reset = false) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await userApi.getWishlist({ page: pageNum, pageSize: PAGE_SIZE })
      const newItems: WishlistItem[] = (res.data as any)?.data?.list || []
      setItems(reset ? newItems : [...items, ...newItems])
      setHasMore(newItems.length >= PAGE_SIZE)
      setPage(pageNum)
    } catch {
      if (reset) setItems([])
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [loading, items])

  useEffect(() => {
    loadWishlist(1, true)
  }, [])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadWishlist(1, true)
  }, [loadWishlist])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) {
      loadWishlist(page + 1)
    }
  }, [hasMore, loading, page, loadWishlist])

  const handleRemove = (item: WishlistItem) => {
    Alert.alert('取消收藏', `确定要取消收藏「${item.productName}」吗？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        style: 'destructive',
        onPress: async () => {
          try {
            await userApi.removeFromWishlist(item.productId)
            setItems((prev) => prev.filter((i) => i.productId !== item.productId))
          } catch {
            Alert.alert('错误', '取消收藏失败，请稍后重试')
          }
        },
      },
    ])
  }

  const renderItem = ({ item }: { item: WishlistItem }) => (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/product/${item.productId}`)}
      onLongPress={() => handleRemove(item)}
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
        ...theme.shadowCard,
      }}
    >
      {item.productImage ? (
        <Image source={{ uri: item.productImage }} style={{ width: 100, height: 100, resizeMode: 'cover' }} />
      ) : (
        <View style={{
          width: 100,
          height: 100,
          backgroundColor: theme.bgInput,
          justifyContent: 'center',
          alignItems: 'center',
        }}>
          <Text style={{ fontSize: 28, opacity: 0.3 }}>🛍️</Text>
        </View>
      )}

      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        <View>
          <Text
            numberOfLines={2}
            style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, lineHeight: 20 }}
          >
            {item.productName}
          </Text>
        </View>

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: 'bold', color: theme.accentRed }}>
            ¥{item.price}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {new Date(item.addedAt).toLocaleDateString()}
          </Text>
        </View>
      </View>

      <TouchableOpacity
        onPress={() => handleRemove(item)}
        hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
        style={{ justifyContent: 'center', paddingHorizontal: Spacing.lg }}
      >
        <Text style={{ fontSize: 18 }}>💔</Text>
      </TouchableOpacity>
    </TouchableOpacity>
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>💝</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>还没有收藏的商品</Text>
      <TouchableOpacity
        activeOpacity={0.7}
        onPress={() => router.push('/(tabs)/mall')}
        style={{
          marginTop: Spacing.xl,
          paddingHorizontal: Spacing.xxl,
          paddingVertical: Spacing.md,
          borderRadius: BorderRadius.xl,
          backgroundColor: theme.primary,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#FFFFFF' }}>去逛逛</Text>
      </TouchableOpacity>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
    }
    if (!hasMore && items.length > 0) {
      return (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.xl }}>
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>到底啦</Text>
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
        </View>
      )
    }
    return null
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <FlatList
        data={items}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xxl }}
        renderItem={renderItem}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
      />
    </View>
  )
}
