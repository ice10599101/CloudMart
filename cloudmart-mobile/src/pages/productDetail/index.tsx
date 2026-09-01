import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { productApi } from '@/api/product'
import { cartApi } from '@/api/cart'
import { userApi } from '@/api/user'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Product } from '@/types'
import styles from './index.module.scss'

export default function ProductDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const id = Taro.getCurrentInstance().router?.params?.id || ''
  const [product, setProduct] = useState<Product | null>(null)
  const [isWishlisted, setIsWishlisted] = useState(false)

  useEffect(() => {
    if (id) loadProduct()
  }, [id])

  const loadProduct = async () => {
    try {
      const res = await productApi.getDetail(id)
      setProduct(res.data?.data)
    } catch {
      // API unavailable
    }
  }

  const handleAddToCart = async () => {
    try {
      await cartApi.addItem({ skuId: id, quantity: 1 })
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch {
      Taro.showToast({ title: '添加失败', icon: 'none' })
    }
  }

  const handleToggleWishlist = async () => {
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
    Taro.navigateTo({ url: `/pages/checkout/index?productId=${id}&quantity=1` })
  }

  if (!product) {
    return <View data-theme={dataTheme} className={styles.page} style={themeStyle}><Text>加载中...</Text></View>
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        <Image className={styles.mainImage} src={product.mainImage} mode='aspectFill' />
        <View className={styles.info}>
          <View className={styles.priceRow}>
            <Text className={styles.price}>¥{product.price}</Text>
            {product.originalPrice && product.originalPrice > product.price && (
              <Text className={styles.originalPrice}>¥{product.originalPrice}</Text>
            )}
          </View>
          <Text className={styles.name}>{product.name}</Text>
          <Text className={styles.description}>{product.description}</Text>
          <View className={styles.stats}>
            <Text className={styles.stat}>销量 {product.sales}</Text>
            <Text className={styles.stat}>评价 {product.reviewCount}</Text>
          </View>
        </View>
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
