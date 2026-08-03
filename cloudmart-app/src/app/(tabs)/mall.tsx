import { View, Text, ScrollView, TouchableOpacity, Image, RefreshControl, ActivityIndicator, FlatList } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { productApi } from '@/api/product'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Product, ProductCategory } from '@/types'

function ProductCard({ product, theme }: { product: Product; theme: ReturnType<typeof useTheme> }) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/product/${product.id}`)}
      style={{
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        marginBottom: Spacing.md,
        flex: 1,
        ...theme.shadowCard,
      }}
    >
      {product.mainImage ? (
        <Image source={{ uri: product.mainImage }} style={{ width: '100%', height: 160, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: '100%', height: 160, backgroundColor: theme.bgInput, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 32 }}>🛍️</Text>
        </View>
      )}
      <View style={{ padding: Spacing.md }}>
        <Text numberOfLines={2} style={{ fontSize: FontSize.sm, color: theme.text, lineHeight: 18, minHeight: 36 }}>
          {product.name}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: 'bold', color: theme.accentRed }}>
            ¥{product.price}
          </Text>
          {product.originalPrice && product.originalPrice > product.price ? (
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, textDecorationLine: 'line-through', marginLeft: Spacing.xs }}>
              ¥{product.originalPrice}
            </Text>
          ) : null}
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {product.sales > 0 ? `${product.sales}人已购` : '新品上架'}
          </Text>
          {product.rating && product.rating > 0 ? (
            <Text style={{ fontSize: FontSize.xs, color: theme.accentGold, marginLeft: Spacing.sm }}>
              ⭐ {product.rating.toFixed(1)}
            </Text>
          ) : null}
        </View>
      </View>
    </TouchableOpacity>
  )
}

export default function MallPage() {
  const theme = useTheme()
  const [categories, setCategories] = useState<ProductCategory[]>([])
  const [activeCategory, setActiveCategory] = useState<number | null>(null)
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadCategories = useCallback(async () => {
    try {
      const res = await productApi.getCategories()
      const list = (res.data as any)?.data || []
      setCategories(Array.isArray(list) ? list : [])
    } catch {
      // ignore
    }
  }, [])

  const loadProducts = useCallback(async (pageNum: number, reset = false) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await productApi.search({
        page: pageNum,
        size: 10,
        ...(activeCategory ? { categoryId: activeCategory } : {}),
      })
      const newProducts = (res.data as any)?.data?.products || []
      setProducts(reset ? newProducts : [...products, ...newProducts])
      setHasMore(newProducts.length >= 10)
      setPage(pageNum)
    } catch {
      if (reset) setProducts([])
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [activeCategory, loading, products])

  useEffect(() => {
    loadCategories()
  }, [])

  useEffect(() => {
    loadProducts(1, true)
  }, [activeCategory])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadCategories()
    loadProducts(1, true)
  }, [loadCategories, loadProducts])

  const handleLoadMore = () => {
    if (hasMore && !loading) loadProducts(page + 1)
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.sm, backgroundColor: theme.bgBase }}>
        {/* Search Bar */}
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={() => router.push('/search')}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            backgroundColor: theme.bgInput,
            borderRadius: BorderRadius.xl,
            paddingHorizontal: Spacing.lg,
            paddingVertical: Spacing.md,
            marginBottom: Spacing.md,
          }}
        >
          <Text style={{ fontSize: 16, marginRight: Spacing.sm }}>🔍</Text>
          <Text style={{ fontSize: FontSize.md, color: theme.textTertiary }}>搜索商品</Text>
        </TouchableOpacity>

        {/* Category Pills */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingBottom: Spacing.sm }}>
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => setActiveCategory(null)}
            style={{
              paddingHorizontal: Spacing.xl,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.xl,
              backgroundColor: activeCategory === null ? theme.primary : 'transparent',
              marginRight: Spacing.sm,
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: activeCategory === null ? '600' : '400', color: activeCategory === null ? '#FFFFFF' : theme.textSecondary }}>
              全部
            </Text>
          </TouchableOpacity>
          {categories.map((cat) => (
            <TouchableOpacity
              key={cat.id}
              activeOpacity={0.7}
              onPress={() => setActiveCategory(cat.id)}
              style={{
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.sm,
                borderRadius: BorderRadius.xl,
                backgroundColor: activeCategory === cat.id ? theme.primary : 'transparent',
                marginRight: Spacing.sm,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: activeCategory === cat.id ? '600' : '400', color: activeCategory === cat.id ? '#FFFFFF' : theme.textSecondary }}>
                {cat.name}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Product Grid */}
      <FlatList
        data={products}
        keyExtractor={(item) => String(item.id)}
        numColumns={2}
        columnWrapperStyle={{ paddingHorizontal: Spacing.lg, gap: Spacing.md }}
        contentContainerStyle={{ paddingBottom: Spacing.xxl }}
        renderItem={({ item }) => <ProductCard product={item} theme={theme} />}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={
          !loading ? (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
              <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>🛒</Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无商品</Text>
            </View>
          ) : null
        }
        ListFooterComponent={
          loading ? (
            <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
          ) : !hasMore && products.length > 0 ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.xl }}>
              <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>到底啦</Text>
              <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
            </View>
          ) : null
        }
      />
    </View>
  )
}
