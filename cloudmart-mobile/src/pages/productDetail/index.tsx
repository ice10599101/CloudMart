import { useState, useEffect, useMemo } from 'react'
import { View, Text, Image, ScrollView, Swiper, SwiperItem } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { productApi } from '@/api/product'
import { cartApi } from '@/api/cart'
import { userApi } from '@/api/user'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Product, Sku } from '@/types'
import styles from './index.module.scss'

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

export default function ProductDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { user } = useAuthStore()
  const id = Taro.getCurrentInstance().router?.params?.id || ''
  const [product, setProduct] = useState<Product | null>(null)
  const [isWishlisted, setIsWishlisted] = useState(false)
  const [selectedSku, setSelectedSku] = useState<Sku | null>(null)
  const [selectedSpecs, setSelectedSpecs] = useState<Record<string, string>>({})
  const [quantity, setQuantity] = useState(1)
  const [activeTab, setActiveTab] = useState<'detail' | 'reviews'>('detail')

  // 评价数据
  const [reviewStats, setReviewStats] = useState<{
    averageRating: number
    totalCount: number
    goodCount: number
    mediumCount: number
    badCount: number
    goodRate: number
  } | null>(null)
  const [reviews, setReviews] = useState<Array<{
    id: number
    rating: number
    content: string
    images?: string[]
    createdAt: string
    user?: { id: number; nickname: string; avatar?: string }
  }>>([])
  const [reviewPage, setReviewPage] = useState(1)
  const [reviewHasMore, setReviewHasMore] = useState(true)
  const [reviewLoading, setReviewLoading] = useState(false)

  const skus = product?.skus ?? []
  const specGroups = useMemo(() => buildSpecGroups(skus), [skus])

  const minPrice = skus.length ? Math.min(...skus.map((s) => s.price)) : undefined
  const maxPrice = skus.length ? Math.max(...skus.map((s) => s.price)) : undefined
  const displayPrice = selectedSku ? selectedSku.price : product?.price
  const displayOriginalPrice = selectedSku ? selectedSku.originalPrice : product?.originalPrice

  /** 根据 selectedSpecs 匹配 SKU（规格未选齐时维持当前选择） */
  const matchSku = (specs: Record<string, string>): Sku | null => {
    if (skus.length === 0) return null
    const keys = specGroups.map((g) => g.name)
    if (keys.length === 0) return skus[0]
    if (!keys.every((k) => specs[k])) return null
    return (
      skus.find((sku) => {
        const attrs = parseSkuAttributes(sku.attributes)
        if (!attrs) return false
        return keys.every((k) => attrs[k] === specs[k])
      }) ?? null
    )
  }

  useEffect(() => {
    if (id) loadProduct()
  }, [id])

  const loadProduct = async () => {
    try {
      const res = await productApi.getDetail(id)
      const detail = res.data?.data
      setProduct(detail)
      const firstSku = detail?.skus?.[0]
      if (firstSku) {
        setSelectedSku(firstSku)
        const attrs = parseSkuAttributes(firstSku.attributes)
        if (attrs) setSelectedSpecs(attrs)
      }
      if (user?.id) {
        // 收藏状态回显（未登录不查）
        userApi
          .checkWishlist(detail?.id ?? id)
          .then((r) => setIsWishlisted(!!r.data?.data))
          .catch(() => {})
        // 浏览足迹上报：登录用户静默上报，失败不打扰
        communityApi
          .recordBrowseHistory({
            targetType: 'PRODUCT',
            targetId: detail?.id ?? id,
            title: detail?.name,
            cover: detail?.mainImage,
          })
          .catch(() => {})
      }
    } catch {
      // API unavailable
    }
  }

  const loadReviewStats = async () => {
    try {
      const res = await productApi.getReviewStats(id)
      setReviewStats(res.data?.data ?? null)
    } catch {
      // 评价统计不可用时静默
    }
  }

  const loadReviews = async (page: number, append = false) => {
    if (reviewLoading) return
    setReviewLoading(true)
    try {
      const res = await productApi.getReviews(id, { page, pageSize: 10 })
      const list = res.data?.data?.list ?? []
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

  const handleSpecSelect = (name: string, value: string) => {
    const next = { ...selectedSpecs, [name]: value }
    setSelectedSpecs(next)
    const matched = matchSku(next)
    if (matched) setSelectedSku(matched)
  }

  const requireLogin = (): boolean => {
    if (!user?.id) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return false
    }
    return true
  }

  const handleAddToCart = async () => {
    if (!requireLogin()) return
    if (skus.length > 0 && !selectedSku) {
      Taro.showToast({ title: '请选择规格', icon: 'none' })
      return
    }
    try {
      await cartApi.addItem({ skuId: selectedSku ? selectedSku.id : (skus[0]?.id ?? Number(id)), quantity })
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch {
      Taro.showToast({ title: '添加失败', icon: 'none' })
    }
  }

  const handleToggleWishlist = async () => {
    if (!requireLogin()) return
    try {
      if (isWishlisted) {
        await userApi.removeFromWishlist(id)
      } else {
        await userApi.addToWishlist(id)
      }
      setIsWishlisted(!isWishlisted)
      Taro.showToast({ title: isWishlisted ? '已取消收藏' : '已收藏', icon: 'success' })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleBuyNow = () => {
    if (!requireLogin()) return
    if (skus.length > 0 && !selectedSku) {
      Taro.showToast({ title: '请选择规格', icon: 'none' })
      return
    }
    const skuId = selectedSku ? selectedSku.id : skus[0]?.id ?? ''
    Taro.navigateTo({ url: `/pages/checkout/index?productId=${id}&skuId=${skuId}&quantity=${quantity}` })
  }

  const galleryImages = useMemo(() => {
    if (!product) return []
    const list = [product.mainImage, ...(product.images ?? [])].filter(Boolean)
    // 与 Web 一致：SKU 主图并入画廊
    for (const sku of skus) {
      if (sku.image && !list.includes(sku.image)) list.push(sku.image)
    }
    return list.length > 0 ? list : [product.mainImage]
  }, [product, skus])

  if (!product) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <Text>加载中...</Text>
      </View>
    )
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        <Swiper className={styles.gallery} indicatorDots circular displayMultipleItems={1}>
          {galleryImages.map((img, i) => (
            <SwiperItem key={`${img}-${i}`}>
              <Image className={styles.mainImage} src={img} mode='aspectFill' />
            </SwiperItem>
          ))}
        </Swiper>

        <View className={styles.info}>
          <View className={styles.priceRow}>
            <Text className={styles.price}>
              ¥{displayPrice != null ? Number(displayPrice).toFixed(2) : '--'}
              {minPrice != null && maxPrice != null && minPrice !== maxPrice && !selectedSku && (
                <Text className={styles.priceRange}> ~ ¥{maxPrice.toFixed(2)}</Text>
              )}
            </Text>
            {displayOriginalPrice && displayPrice != null && Number(displayOriginalPrice) > Number(displayPrice) && (
              <Text className={styles.originalPrice}>¥{Number(displayOriginalPrice).toFixed(2)}</Text>
            )}
          </View>
          <Text className={styles.name}>{product.name}</Text>
          <Text className={styles.description}>{product.description}</Text>
          <View className={styles.stats}>
            <Text className={styles.stat}>销量 {product.sales}</Text>
            <Text className={styles.stat}>评价 {product.reviewCount}</Text>
            {product.rating != null && <Text className={styles.stat}>评分 {product.rating}</Text>}
          </View>
        </View>

        {/* SKU 规格选择（对齐 Web 端 SKU 选择器） */}
        {specGroups.map((group) => (
          <View key={group.name} className={styles.specSection}>
            <Text className={styles.specName}>{group.name}</Text>
            <View className={styles.specValues}>
              {group.values.map((value) => (
                <View
                  key={value}
                  className={`${styles.specChip} ${selectedSpecs[group.name] === value ? styles.specChipActive : ''}`}
                  onClick={() => handleSpecSelect(group.name, value)}
                >
                  <Text className={selectedSpecs[group.name] === value ? styles.specChipTextActive : styles.specChipText}>
                    {value}
                  </Text>
                </View>
              ))}
            </View>
          </View>
        ))}

        {/* 数量选择 */}
        <View className={styles.specSection}>
          <Text className={styles.specName}>数量</Text>
          <View className={styles.qtyStepper}>
            <View
              className={`${styles.qtyBtn} ${quantity <= 1 ? styles.qtyBtnDisabled : ''}`}
              onClick={() => quantity > 1 && setQuantity(quantity - 1)}
            >
              <Text className={styles.qtyBtnText}>-</Text>
            </View>
            <Text className={styles.qtyValue}>{quantity}</Text>
            <View
              className={`${styles.qtyBtn} ${selectedSku && quantity >= selectedSku.stock ? styles.qtyBtnDisabled : ''}`}
              onClick={() => {
                const max = selectedSku ? selectedSku.stock : 99
                if (quantity < max) setQuantity(quantity + 1)
              }}
            >
              <Text className={styles.qtyBtnText}>+</Text>
            </View>
          </View>
          {selectedSku && <Text className={styles.stockText}>库存 {selectedSku.stock}</Text>}
        </View>

        {/* 服务保障条（对齐 Web 端服务保障） */}
        <View className={styles.guarantee}>
          <Text className={styles.guaranteeItem}>✅ 正品保障</Text>
          <Text className={styles.guaranteeItem}>↩️ 7天无理由</Text>
          <Text className={styles.guaranteeItem}>🚀 极速发货</Text>
          <Text className={styles.guaranteeItem}>🛡️ 运费险</Text>
        </View>

        {/* 详情 / 评价 Tab */}
        <View className={styles.tabBar}>
          <View className={`${styles.tabItem} ${activeTab === 'detail' ? styles.tabActive : ''}`} onClick={() => handleSwitchTab('detail')}>
            <Text className={activeTab === 'detail' ? styles.tabTextActive : styles.tabText}>商品详情</Text>
          </View>
          <View className={`${styles.tabItem} ${activeTab === 'reviews' ? styles.tabActive : ''}`} onClick={() => handleSwitchTab('reviews')}>
            <Text className={activeTab === 'reviews' ? styles.tabTextActive : styles.tabText}>
              商品评价{reviewStats ? `(${reviewStats.totalCount})` : ''}
            </Text>
          </View>
        </View>

        {activeTab === 'detail' ? (
          <View className={styles.detailSection}>
            <Text className={styles.detailText}>{product.description}</Text>
          </View>
        ) : (
          <View className={styles.reviewSection}>
            {reviewStats && (
              <View className={styles.reviewSummary}>
                <View className={styles.reviewScore}>
                  <Text className={styles.reviewScoreNum}>{reviewStats.averageRating?.toFixed(1) ?? '--'}</Text>
                  <Text className={styles.reviewScoreLabel}>平均评分</Text>
                </View>
                <View className={styles.reviewDist}>
                  <Text className={styles.reviewDistItem}>好评 {reviewStats.goodCount}</Text>
                  <Text className={styles.reviewDistItem}>中评 {reviewStats.mediumCount}</Text>
                  <Text className={styles.reviewDistItem}>差评 {reviewStats.badCount}</Text>
                  <Text className={styles.reviewDistItem}>好评率 {Math.round((reviewStats.goodRate ?? 0) * 100)}%</Text>
                </View>
              </View>
            )}
            {reviews.map((review) => (
              <View key={review.id} className={styles.reviewCard}>
                <View className={styles.reviewHeader}>
                  <Text className={styles.reviewUser}>{review.user?.nickname ?? '匿名用户'}</Text>
                  <Text className={styles.reviewStars}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</Text>
                </View>
                <Text className={styles.reviewContent}>{review.content}</Text>
                {review.images && review.images.length > 0 && (
                  <View className={styles.reviewImages}>
                    {review.images.map((img, i) => (
                      <Image key={`${img}-${i}`} className={styles.reviewImage} src={img} mode='aspectFill' onClick={() => Taro.previewImage({ urls: review.images!, current: img })} />
                    ))}
                  </View>
                )}
                <Text className={styles.reviewTime}>{review.createdAt?.slice(0, 10)}</Text>
              </View>
            ))}
            {reviews.length === 0 && !reviewLoading && (
              <View className={styles.reviewEmpty}>
                <Text className={styles.emptyText}>暂无评价</Text>
              </View>
            )}
            {reviewHasMore && reviews.length > 0 && (
              <View className={styles.loadMore} onClick={() => !reviewLoading && loadReviews(reviewPage + 1, true)}>
                <Text className={styles.loadMoreText}>{reviewLoading ? '加载中...' : '加载更多评价'}</Text>
              </View>
            )}
          </View>
        )}
      </ScrollView>

      <View className={styles.bottomBar}>
        <View className={styles.actionBtn} onClick={handleToggleWishlist}>
          <Text>{isWishlisted ? '❤️' : '🤍'}</Text>
          <Text className={styles.actionLabel}>收藏</Text>
        </View>
        <View className={styles.actionBtn} onClick={() => Taro.switchTab({ url: '/pages/message/index' })}>
          <Text>💬</Text>
          <Text className={styles.actionLabel}>客服</Text>
        </View>
        <View className={styles.cartBtn} onClick={handleAddToCart}>
          <Text className={styles.cartBtnText}>加入购物车</Text>
        </View>
        <View className={styles.buyBtn} onClick={handleBuyNow}>
          <Text className={styles.buyBtnText}>立即购买</Text>
        </View>
      </View>
    </View>
  )
}
