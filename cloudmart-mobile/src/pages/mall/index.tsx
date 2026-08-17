import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { productApi } from '@/api/product'
import type { Product } from '@/types'
import { ICON_BASE64 } from '@/components/Icon'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import styles from './index.module.scss'

const QUICK_ENTRIES = [
  { icon: '⚡', name: '秒杀', path: '/pages/seckill/index', gradient: 'linear-gradient(135deg, #FF4757, #FF6B35)' },
  { icon: '👥', name: '拼团', path: '/pages/groupBuy/index', gradient: 'linear-gradient(135deg, #9370DB, #B06AB3)' },
  { icon: '🎫', name: '优惠券', path: '/pages/coupons/index', gradient: 'linear-gradient(135deg, #FFD700, #FFA500)' },
  { icon: '📺', name: '直播', path: '/pages/live/index', gradient: 'linear-gradient(135deg, #00D4FF, #0099CC)' },
  { icon: '🆕', name: '新品', path: '/pages/search/index?type=product&sort=newest', gradient: 'linear-gradient(135deg, #32CD32, #4CAF50)' },
  { icon: '🔥', name: '热销', path: '/pages/search/index?type=product&sort=hot', gradient: 'linear-gradient(135deg, #FF6B35, #FF4757)' },
  { icon: '💡', name: '推荐', path: '/pages/search/index?type=product&sort=recommended', gradient: 'linear-gradient(135deg, #9370DB, #00D4FF)' },
  { icon: '📋', name: '更多', path: '/pages/search/index?type=product', gradient: 'linear-gradient(135deg, #8B9DC3, #5A6F8E)' },
]

export default function MallPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [products, setProducts] = useState<Product[]>([])
  const [, setLoading] = useState(false)

  useEffect(() => {
    loadProducts()
  }, [])

  const loadProducts = async () => {
    setLoading(true)
    try {
      const res = await productApi.search({ page: 1, size: 20 })
      setProducts(res.data?.data?.list || [])
    } catch {
      // API unavailable
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    Taro.navigateTo({ url: '/pages/search/index?type=product' })
  }

  const handleQuickEntry = (entry: typeof QUICK_ENTRIES[0]) => {
    Taro.navigateTo({ url: entry.path })
  }

  const handleProductClick = (id: number) => {
    Taro.navigateTo({ url: `/pages/productDetail/index?id=${id}` })
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="CloudMart" />
<View className={styles.searchBar} onClick={handleSearch}>
        <Image src={ICON_BASE64.search.default} style={{ width: '18px', height: '18px' }} mode='aspectFit' />
        <Text className={styles.searchPlaceholder}>搜索商品</Text>
      </View>

      <ScrollView scrollY className={styles.content}>
        <View className={styles.quickGrid}>
          {QUICK_ENTRIES.map((entry) => (
            <View key={entry.name} className={styles.quickItem} onClick={() => handleQuickEntry(entry)}>
              <View className={styles.quickIconWrap} style={{ background: entry.gradient }}>
                <Text className={styles.quickIcon}>{entry.icon}</Text>
              </View>
              <Text className={styles.quickName}>{entry.name}</Text>
            </View>
          ))}
        </View>

        <View className={styles.sectionHeader}>
          <Text className={styles.sectionTitle}>为你推荐</Text>
        </View>

        <View className={styles.productGrid}>
          {products.map((product) => (
            <View key={product.id} className={styles.productCard} onClick={() => handleProductClick(product.id)}>
              <Image className={styles.productImage} src={product.mainImage} mode='aspectFill' />
              <View className={styles.productInfo}>
                <Text className={styles.productName}>{product.name}</Text>
                <View className={styles.priceRow}>
                  <Text className={styles.productPrice}>¥{product.price}</Text>
                  {product.originalPrice && product.originalPrice > product.price && (
                    <Text className={styles.originalPrice}>¥{product.originalPrice}</Text>
                  )}
                </View>
                <Text className={styles.productSales}>{product.sales > 0 ? `${product.sales}人已购` : ''}</Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>
      <CustomTabBar />
    </View>
  )
}
