import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert, Dimensions } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { productApi } from '@/api/product'
import { cartApi } from '@/api/cart'
import { userApi } from '@/api/user'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Product } from '@/types'

const { width: SCREEN_WIDTH } = Dimensions.get('window')
const IMAGE_HEIGHT = SCREEN_WIDTH * 0.85

export default function ProductDetailScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const { isLoggedIn } = useAuthStore()

  const [product, setProduct] = useState<Product | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [isWishlisted, setIsWishlisted] = useState(false)
  const [wishlistLoading, setWishlistLoading] = useState(false)
  const [currentImageIndex, setCurrentImageIndex] = useState(0)

  const fetchProduct = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError(false)
    try {
      const res = await productApi.getDetail(Number(id))
      const data = res.data as { data?: Product }
      if (data?.data) {
        setProduct(data.data)
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

  const handleCustomerService = () => {
    Alert.alert('提示', '客服功能开发中')
  }

  const handleAddToCart = async () => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: () => router.push('/login') },
      ])
      return
    }
    try {
      await cartApi.addItem({ skuId: Number(id), quantity: 1 })
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
    router.push(`/checkout?productId=${id}&quantity=1`)
  }

  const images = product?.images?.length ? product.images : (product?.mainImage ? [product.mainImage] : [])

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
              ¥{product.price}
            </Text>
            {hasDiscount && (
              <Text style={{ fontSize: FontSize.md, color: theme.textTertiary, textDecorationLine: 'line-through' }}>
                ¥{product.originalPrice}
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

        {/* Description */}
        <View style={{ paddingHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.md }}>
            商品详情
          </Text>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, lineHeight: 24 }}>
            {product.description || '暂无详细描述'}
          </Text>
        </View>
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
