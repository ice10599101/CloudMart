import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { userApi } from '@/api/user'
import { cartApi } from '@/api/cart'
import { productApi } from '@/api/product'
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

  /** 加入购物车（对齐 Web 端心愿单：取商品默认 SKU） */
  const handleAddToCart = async (productId: number, name: string) => {
    try {
      const res = await productApi.getDetail(productId)
      const product = res.data?.data
      const skuId = product?.skus?.[0]?.id
      if (!skuId) {
        Taro.showToast({ title: '商品暂无可售规格', icon: 'none' })
        return
      }
      await cartApi.addItem({ skuId, quantity: 1 })
      Taro.showToast({ title: `已将「${name}」加入购物车`, icon: 'success' })
    } catch {
      Taro.showToast({ title: '加入购物车失败', icon: 'none' })
    }
  }

  const handleRemove = (productId: number, name: string) => {
    Taro.showModal({
      title: '移除收藏',
      content: `确定将「${name}」移出心愿单吗？`,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await userApi.removeFromWishlist(productId)
          setWishlist((prev) => prev.filter((w) => w.productId !== productId))
          Taro.showToast({ title: '已移除', icon: 'success' })
        } catch {
          Taro.showToast({ title: '移除失败', icon: 'none' })
        }
      },
    })
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
            <Text
              className={styles.cartAddBtn}
              onClick={(e) => {
                e.stopPropagation()
                handleAddToCart(item.productId, item.productName)
              }}
            >
              加购
            </Text>
            <Text
              className={styles.removeBtn}
              onClick={(e) => {
                e.stopPropagation()
                handleRemove(item.productId, item.productName)
              }}
            >
              移除
            </Text>
          </View>
        ))}
      </ScrollView>
    </View>
  )
}
