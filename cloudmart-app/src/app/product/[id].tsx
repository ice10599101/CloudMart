import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert, Dimensions, TextInput } from 'react-native'
import { useState, useEffect, useCallback, useMemo } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { productApi } from '@/api/product'
import { cartApi } from '@/api/cart'
import { userApi } from '@/api/user'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Product, Sku } from '@/types'

const { width: SCREEN_WIDTH } = Dimensions.get('window')
const IMAGE_HEIGHT = SCREEN_WIDTH * 0.85

/** 解析 SKU attributes（JSON 对象，异常时降级为原始字符串展示） */
function parseSkuAttributes(attributes: string): Record<string, string> | null {
  try {
    const parsed = JSON.parse(attributes)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
    return null
  } catch {
    return null
  }
}

/** SKU 规格组：{ 颜色: ['红','蓝'], 尺码: ['M','L'] } */
function buildSpecGroups(skus: Sku[]): Array<{ name: string; values: string[] }> {
  const groups = new Map<string, Set<string>>()
  for (const sku of skus) {
    const attrs = parseSkuAttributes(sku.attributes)
    if (!attrs) continue
    for (const [name, value] of Object.entries(attrs)) {
      if (!groups.has(name)) groups.set(name, new Set())
      groups.get(name)!.add(value)
    }
  }
  return Array.from(groups.entries()).map(([name, values]) => ({ name, values: Array.from(values) }))
}

interface ReviewStatsShape {
  averageRating: number
  totalCount: number
  goodCount: number
  mediumCount: number
  badCount: number
  goodRate: number
}

interface ReviewItemShape {
  id: number
  rating: number
  content: string
  images?: string[]
  createdAt: string
  user?: { id: number; nickname: string; avatar?: string }
}

export default function ProductDetailScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const { isLoggedIn, user } = useAuthStore()

  const [product, setProduct] = useState<Product | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [isWishlisted, setIsWishlisted] = useState(false)
  const [wishlistLoading, setWishlistLoading] = useState(false)
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  // SKU/数量（对齐 Web 端 SKU 选择器）
  const [selectedSku, setSelectedSku] = useState<Sku | null>(null)
  const [selectedSpecs, setSelectedSpecs] = useState<Record<string, string>>({})
  const [quantity, setQuantity] = useState(1)
  // 评价（对齐 Web 端商品评价 Tab）
  const [activeTab, setActiveTab] = useState<'detail' | 'reviews'>('detail')
  const [reviewStats, setReviewStats] = useState<ReviewStatsShape | null>(null)
  const [reviews, setReviews] = useState<ReviewItemShape[]>([])
  const [reviewPage, setReviewPage] = useState(1)
  const [reviewHasMore, setReviewHasMore] = useState(false)
  const [reviewLoading, setReviewLoading] = useState(false)

  const fetchProduct = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError(false)
    try {
      const res = await productApi.getDetail(Number(id))
      const data = res.data as { data?: Product }
      if (data?.data) {
        setProduct(data.data)
        const firstSku = data.data.skus?.[0]
        if (firstSku) {
          setSelectedSku(firstSku)
          const attrs = parseSkuAttributes(firstSku.attributes)
          if (attrs) setSelectedSpecs(attrs)
        }
        // 浏览足迹上报：登录用户静默上报，失败不打扰
        if (user?.id) {
          void communityApi.recordBrowseHistory({
            targetType: 'PRODUCT',
            targetId: data.data.id,
            title: data.data.name,
            cover: data.data.mainImage,
          }).catch(() => {})
        }
      } else {
        setError(true)
      }
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [id])

  const checkWishlistStatus = useCallback(async () => {
    if (!isLoggedIn || !id) return
    try {
      const res = await userApi.checkWishlist(Number(id))
      setIsWishlisted(res.data?.data ?? false)
    } catch {
      // 默认未收藏，不影响使用
    }
  }, [id, isLoggedIn])

  useEffect(() => {
    fetchProduct()
    checkWishlistStatus()
  }, [fetchProduct, checkWishlistStatus])

  const handleWishlistToggle = async () => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: () => router.push('/login') },
      ])
      return
    }
    if (wishlistLoading) return
    setWishlistLoading(true)
    try {
      if (isWishlisted) {
        await userApi.removeFromWishlist(Number(id))
        setIsWishlisted(false)
      } else {
        await userApi.addToWishlist(Number(id))
        setIsWishlisted(true)
      }
    } catch {
      Alert.alert('提示', isWishlisted ? '取消收藏失败' : '收藏失败')
    } finally {
      setWishlistLoading(false)
    }
  }

  /** 客服入口（对齐移动端统一行为：跳私信会话） */
  const handleCustomerService = () => {
    router.push('/(tabs)/message')
  }

  const skus = product?.skus ?? []
  const specGroups = useMemo(() => buildSpecGroups(skus), [skus])

  const minPrice = skus.length ? Math.min(...skus.map((sku) => sku.price)) : undefined
  const maxPrice = skus.length ? Math.max(...skus.map((sku) => sku.price)) : undefined

  const handleSpecSelect = (name: string, value: string) => {
    const next = { ...selectedSpecs, [name]: value }
    setSelectedSpecs(next)
    const keys = specGroups.map((g) => g.name)
    if (keys.every((k) => next[k])) {
      const matched = skus.find((sku) => {
        const attrs = parseSkuAttributes(sku.attributes)
        return attrs ? keys.every((k) => attrs[k] === next[k]) : false
      })
      if (matched) setSelectedSku(matched)
    }
  }

  const loadReviewStats = async () => {
    if (!id) return
    try {
      const res = await productApi.getReviewStats(Number(id))
      setReviewStats((res.data as { data?: ReviewStatsShape })?.data ?? null)
    } catch {
      // 评价统计不可用时静默
    }
  }

  const loadReviews = async (page: number, append = false) => {
    if (!id || reviewLoading) return
    setReviewLoading(true)
    try {
      const res = await productApi.getReviews(Number(id), { page, pageSize: 10 })
      const list = (res.data as { data?: { list?: ReviewItemShape[] } })?.data?.list ?? []
      setReviews((prev) => (append ? [...prev, ...list] : list))
      setReviewPage(page)
      setReviewHasMore(list.length >= 10)
    } catch {
      setReviewHasMore(false)
    } finally {
      setReviewLoading(false)
    }
  }

  const handleSwitchTab = (tab: 'detail' | 'reviews') => {
    setActiveTab(tab)
    if (tab === 'reviews' && reviews.length === 0) {
      void loadReviewStats()
      void loadReviews(1)
    }
  }

  const handleAddToCart = async () => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: () => router.push('/login') },
      ])
      return
    }
    if (skus.length > 0 && !selectedSku) {
      Alert.alert('提示', '请先选择规格')
      return
    }
    try {
      const skuId = selectedSku?.id ?? skus[0]?.id ?? Number(id)
      await cartApi.addItem({ skuId, quantity })
      Alert.alert('提示', '已加入购物车')
    } catch {
      Alert.alert('提示', '加入购物车失败')
    }
  }

  const handleBuyNow = () => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: () => router.push('/login') },
      ])
      return
    }
    if (skus.length > 0 && !selectedSku) {
      Alert.alert('提示', '请先选择规格')
      return
    }
    const skuId = selectedSku?.id ?? skus[0]?.id ?? ''
    router.push(`/checkout?productId=${id}&skuId=${skuId}&quantity=${quantity}`)
  }

  const images = useMemo(() => {
    if (!product) return []
    const list = [product.mainImage, ...(product.images ?? [])].filter(Boolean)
    for (const sku of skus) {
      if (sku.image && !list.includes(sku.image)) list.push(sku.image)
    }
    return list.length > 0 ? list : product.mainImage ? [product.mainImage] : []
  }, [product, skus])

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
        <Text style={{ fontSize: FontSize.md, color: theme.textTertiary, marginTop: Spacing.lg }}>加载中...</Text>
      </View>
    )
  }

  if (error || !product) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>😔</Text>
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>商品不存在或已下架</Text>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={router.back}
          style={{
            marginTop: Spacing.xl,
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: theme.primary,
          }}
        >
          <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>返回</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const hasDiscount = product.originalPrice && product.originalPrice > product.price

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 80 }}>
        {/* Image Carousel */}
        <View>
          <ScrollView
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            onMomentumScrollEnd={(e) => {
              const index = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH)
              setCurrentImageIndex(index)
            }}
          >
            {images.map((uri, index) => (
              <Image
                key={index}
                source={{ uri }}
                style={{ width: SCREEN_WIDTH, height: IMAGE_HEIGHT, resizeMode: 'cover' }}
              />
            ))}
          </ScrollView>
          {images.length > 1 && (
            <View style={{ position: 'absolute', bottom: Spacing.md, flexDirection: 'row', justifyContent: 'center', width: '100%', gap: Spacing.xs }}>
              {images.map((_, index) => (
                <View
                  key={index}
                  style={{
                    width: currentImageIndex === index ? 16 : 6,
                    height: 6,
                    borderRadius: 3,
                    backgroundColor: currentImageIndex === index ? theme.primary : 'rgba(255,255,255,0.5)',
                  }}
                />
              ))}
            </View>
          )}
        </View>

        {/* Price Section */}
        <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg }}>
          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.xxxl, fontWeight: 'bold', color: theme.accentRed }}>
              ¥{selectedSku ? selectedSku.price : product.price}
              {!selectedSku && minPrice != null && maxPrice != null && minPrice !== maxPrice && (
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.accentRed }}> ~ ¥{maxPrice}</Text>
              )}
            </Text>
            {(selectedSku?.originalPrice ?? product.originalPrice) && (selectedSku?.originalPrice ?? product.originalPrice)! > (selectedSku?.price ?? product.price) && (
              <Text style={{ fontSize: FontSize.md, color: theme.textTertiary, textDecorationLine: 'line-through' }}>
                ¥{(selectedSku?.originalPrice ?? product.originalPrice)!}
              </Text>
            )}
            {hasDiscount && (
              <View style={{ backgroundColor: theme.accentRed + '1A', paddingHorizontal: Spacing.sm, paddingVertical: 2, borderRadius: BorderRadius.sm }}>
                <Text style={{ fontSize: FontSize.xs, color: theme.accentRed, fontWeight: '600' }}>
                  {Math.round((1 - product.price / product.originalPrice!) * 100)}% OFF
                </Text>
              </View>
            )}
          </View>
        </View>

        {/* Product Name */}
        <View style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.md }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '600', color: theme.text, lineHeight: 26 }}>
            {product.name}
          </Text>
        </View>

        {/* Stats Row */}
        <View style={{ flexDirection: 'row', paddingHorizontal: Spacing.lg, marginTop: Spacing.md, gap: Spacing.xl }}>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
            📦 {product.sales > 0 ? `${product.sales}人已购` : '新品上架'}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
            💬 {product.reviewCount}条评价
          </Text>
          {product.rating && product.rating > 0 && (
            <Text style={{ fontSize: FontSize.sm, color: theme.accentGold }}>
              ⭐ {product.rating.toFixed(1)}
            </Text>
          )}
        </View>

        {/* Brand / Category */}
        {(product.brandName || product.categoryName) && (
          <View style={{ flexDirection: 'row', paddingHorizontal: Spacing.lg, marginTop: Spacing.md, gap: Spacing.sm }}>
            {product.brandName && (
              <View style={{ backgroundColor: theme.bgInput, paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, borderRadius: BorderRadius.sm }}>
                <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>{product.brandName}</Text>
              </View>
            )}
            {product.categoryName && (
              <View style={{ backgroundColor: theme.bgInput, paddingHorizontal: Spacing.md, paddingVertical: Spacing.xs, borderRadius: BorderRadius.sm }}>
                <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>{product.categoryName}</Text>
              </View>
            )}
          </View>
        )}

        {/* Divider */}
        <View style={{ height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg, marginTop: Spacing.lg }} />

        {/* SKU 规格选择（对齐 Web 端 SKU 选择器） */}
        {specGroups.map((group) => (
          <View key={group.name} style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, marginBottom: Spacing.sm }}>
              {group.name}
            </Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
              {group.values.map((value) => {
                const isActive = selectedSpecs[group.name] === value
                return (
                  <TouchableOpacity
                    key={value}
                    activeOpacity={0.7}
                    onPress={() => handleSpecSelect(group.name, value)}
                    style={{
                      paddingHorizontal: Spacing.lg,
                      paddingVertical: Spacing.sm,
                      borderRadius: BorderRadius.md,
                      borderWidth: 1,
                      borderColor: isActive ? theme.primary : theme.border,
                      backgroundColor: isActive ? theme.primary + '14' : theme.bgBase,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.sm, color: isActive ? theme.primary : theme.textSecondary, fontWeight: isActive ? '600' : '400' }}>
                      {value}
                    </Text>
                  </TouchableOpacity>
                )
              })}
            </View>
          </View>
        ))}

        {/* 数量选择 */}
        <View style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.lg, flexDirection: 'row', alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>数量</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginLeft: Spacing.lg, gap: Spacing.md }}>
            <TouchableOpacity
              activeOpacity={0.7}
              disabled={quantity <= 1}
              onPress={() => setQuantity((q) => Math.max(1, q - 1))}
              style={{ width: 32, height: 32, borderRadius: BorderRadius.sm, borderWidth: 1, borderColor: theme.border, justifyContent: 'center', alignItems: 'center', opacity: quantity <= 1 ? 0.4 : 1 }}
            >
              <Text style={{ fontSize: 18, color: theme.text }}>-</Text>
            </TouchableOpacity>
            <Text style={{ minWidth: 40, textAlign: 'center', fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>{quantity}</Text>
            <TouchableOpacity
              activeOpacity={0.7}
              disabled={!!selectedSku && quantity >= selectedSku.stock}
              onPress={() => setQuantity((q) => (selectedSku ? Math.min(q + 1, selectedSku.stock) : q + 1))}
              style={{ width: 32, height: 32, borderRadius: BorderRadius.sm, borderWidth: 1, borderColor: theme.border, justifyContent: 'center', alignItems: 'center', opacity: selectedSku && quantity >= selectedSku.stock ? 0.4 : 1 }}
            >
              <Text style={{ fontSize: 18, color: theme.text }}>+</Text>
            </TouchableOpacity>
            {selectedSku && (
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginLeft: Spacing.sm }}>库存 {selectedSku.stock}</Text>
            )}
          </View>
        </View>

        {/* 服务保障条（对齐 Web 端） */}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
          {['✅ 正品保障', '↩️ 7天无理由', '🚀 极速发货', '🛡️ 运费险'].map((label) => (
            <Text key={label} style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>{label}</Text>
          ))}
        </View>

        {/* 详情 / 评价 Tab（对齐 Web 端） */}
        <View style={{ flexDirection: 'row', marginTop: Spacing.xl, borderBottomWidth: 1, borderBottomColor: theme.border }}>
          {([
            { key: 'detail', label: '商品详情' },
            { key: 'reviews', label: reviewStats ? `商品评价(${reviewStats.totalCount})` : '商品评价' },
          ] as const).map((tab) => (
            <TouchableOpacity
              key={tab.key}
              activeOpacity={0.7}
              onPress={() => handleSwitchTab(tab.key)}
              style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderBottomWidth: 2, borderBottomColor: activeTab === tab.key ? theme.primary : 'transparent' }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: activeTab === tab.key ? '600' : '400', color: activeTab === tab.key ? theme.primary : theme.textSecondary }}>
                {tab.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {activeTab === 'detail' ? (
          <View style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, lineHeight: 24 }}>
              {product.description || '暂无详细描述'}
            </Text>
          </View>
        ) : (
          <View style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
            {reviewStats && (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.lg, backgroundColor: theme.bgInput, borderRadius: BorderRadius.md, padding: Spacing.md, marginBottom: Spacing.md }}>
                <View style={{ alignItems: 'center' }}>
                  <Text style={{ fontSize: 28, fontWeight: 'bold', color: theme.accentGold }}>{reviewStats.averageRating?.toFixed(1) ?? '--'}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>平均评分</Text>
                </View>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>好评 {reviewStats.goodCount}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>中评 {reviewStats.mediumCount}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>差评 {reviewStats.badCount}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>好评率 {Math.round((reviewStats.goodRate ?? 0) * 100)}%</Text>
                </View>
              </View>
            )}
            {reviews.map((review) => (
              <View key={review.id} style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>{review.user?.nickname ?? '匿名用户'}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.accentGold }}>
                    {'★'.repeat(review.rating)}{'☆'.repeat(Math.max(0, 5 - review.rating))}
                  </Text>
                </View>
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 22, marginTop: 4 }}>{review.content}</Text>
                {review.images && review.images.length > 0 && (
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.sm }}>
                    {review.images.map((img, i) => (
                      <Image key={`${img}-${i}`} source={{ uri: img }} style={{ width: 72, height: 72, borderRadius: BorderRadius.sm }} />
                    ))}
                  </View>
                )}
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 4 }}>{review.createdAt?.slice(0, 10)}</Text>
              </View>
            ))}
            {reviews.length === 0 && !reviewLoading && (
              <Text style={{ textAlign: 'center', paddingVertical: Spacing.xl, fontSize: FontSize.sm, color: theme.textTertiary }}>暂无评价</Text>
            )}
            {reviewHasMore && (
              <TouchableOpacity activeOpacity={0.7} onPress={() => !reviewLoading && loadReviews(reviewPage + 1, true)} style={{ alignItems: 'center', paddingVertical: Spacing.md }}>
                <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>{reviewLoading ? '加载中...' : '加载更多评价'}</Text>
              </TouchableOpacity>
            )}
          </View>
        )}
      </ScrollView>

      {/* Back Button */}
      <TouchableOpacity
        activeOpacity={0.7}
        onPress={router.back}
        style={{
          position: 'absolute',
          top: 50,
          left: Spacing.lg,
          width: 36,
          height: 36,
          borderRadius: 18,
          backgroundColor: 'rgba(0,0,0,0.4)',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <Text style={{ fontSize: 18, color: '#FFFFFF' }}>←</Text>
      </TouchableOpacity>

      {/* Bottom Action Bar */}
      <View style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.md,
        paddingBottom: Spacing.xl,
        backgroundColor: theme.bgHeader,
        borderTopWidth: 1,
        borderTopColor: theme.border,
      }}>
        {/* Wishlist */}
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={handleWishlistToggle}
          disabled={wishlistLoading}
          style={{ alignItems: 'center', width: 50 }}
        >
          <Text style={{ fontSize: 22 }}>{isWishlisted ? '❤️' : '🤍'}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
            {isWishlisted ? '已收藏' : '收藏'}
          </Text>
        </TouchableOpacity>

        {/* Customer Service */}
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={handleCustomerService}
          style={{ alignItems: 'center', width: 50, marginLeft: Spacing.sm }}
        >
          <Text style={{ fontSize: 22 }}>💬</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>客服</Text>
        </TouchableOpacity>

        {/* Add to Cart */}
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={handleAddToCart}
          style={{
            flex: 1,
            height: 44,
            borderRadius: BorderRadius.lg,
            backgroundColor: theme.accentGold,
            justifyContent: 'center',
            alignItems: 'center',
            marginLeft: Spacing.md,
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#000000' }}>加入购物车</Text>
        </TouchableOpacity>

        {/* Buy Now */}
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={handleBuyNow}
          style={{
            flex: 1,
            height: 44,
            borderRadius: BorderRadius.lg,
            backgroundColor: theme.primary,
            justifyContent: 'center',
            alignItems: 'center',
            marginLeft: Spacing.sm,
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#FFFFFF' }}>立即购买</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}
