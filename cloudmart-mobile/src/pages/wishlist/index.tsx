import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { userApi } from '@/api/user'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

export default function WishlistPage() {
  const [wishlist, setWishlist] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  useEffect(() => {
    loadWishlist()
  }, [])

  const loadWishlist = async () => {
    try {
      const res = await userApi.getWishlist({ page: 1, pageSize: 20 })
      setWishlist(res.data?.data?.list || [])
    } catch {
      // API unavailable
    } finally {
      setLoading(false)
    }
  }

  if (!loading && wishlist.length === 0) {
    return (
      <View className={styles.page}>
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>💝</Text>
          <Text className={styles.emptyText}>还没有收藏的商品</Text>
          <Text className={styles.goShopping} onClick={() => Taro.switchTab({ url: '/pages/mall/index' })}>去逛逛</Text>
        </View>
      </View>
    )
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY>
        {wishlist.map((item) => (
          <View key={item.id} className={styles.wishlistItem} onClick={() => Taro.navigateTo({ url: `/pages/productDetail/index?id=${item.productId}` })}>
            {item.productImage && <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />}
            <View className={styles.itemInfo}>
              <Text className={styles.itemName}>{item.productName}</Text>
              <Text className={styles.itemPrice}>¥{item.productPrice}</Text>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  )
}
