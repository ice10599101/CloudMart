import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { cartApi } from '@/api/cart'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { CartItem } from '@/types'
import styles from './index.module.scss'

export default function CartPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [items, setItems] = useState<CartItem[]>([])
  const [allChecked, setAllChecked] = useState(false)
  const [totalPrice, setTotalPrice] = useState(0)
  useAuthGuard()

  useEffect(() => {
    loadCart()
  }, [])

  const loadCart = async () => {
    try {
      const res = await cartApi.getCart()
      setItems(res.data?.data || [])
    } catch {
      // API unavailable
    }
  }

  const handleCheckItem = (index: number) => {
    const newItems = [...items]
    newItems[index] = { ...newItems[index], checked: !newItems[index].checked }
    setItems(newItems)
    recalculate(newItems)
  }

  const handleCheckAll = () => {
    const newChecked = !allChecked
    const newItems = items.map(item => ({ ...item, checked: newChecked }))
    setItems(newItems)
    setAllChecked(newChecked)
    recalculate(newItems)
  }

  const recalculate = (newItems: CartItem[]) => {
    const total = newItems.filter(i => i.checked).reduce((sum, i) => sum + i.price * i.quantity, 0)
    setTotalPrice(total)
    setAllChecked(newItems.length > 0 && newItems.every(i => i.checked))
  }

  const handleQuantityChange = async (index: number, delta: number) => {
    const item = items[index]
    const newQuantity = item.quantity + delta
    if (newQuantity < 1) return
    try {
      await cartApi.updateItem(item.skuId, { quantity: newQuantity })
      const newItems = [...items]
      newItems[index] = { ...item, quantity: newQuantity }
      setItems(newItems)
      recalculate(newItems)
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleCheckout = () => {
    const checkedItems = items.filter(i => i.checked)
    if (checkedItems.length === 0) {
      Taro.showToast({ title: '请选择商品', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: '/pages/checkout/index' })
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {items.length === 0 ? (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>购物车是空的</Text>
            <Text className={styles.goShopping} onClick={() => Taro.switchTab({ url: '/pages/mall/index' })}>去逛逛</Text>
          </View>
        ) : (
          items.map((item, index) => (
            <View key={item.id} className={styles.cartItem}>
              <View className={styles.checkbox} onClick={() => handleCheckItem(index)}>
                <Text>{item.checked ? '☑️' : '⬜'}</Text>
              </View>
              <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />
              <View className={styles.itemInfo}>
                <Text className={styles.itemName}>{item.productName}</Text>
                {item.skuName && <Text className={styles.skuName}>{item.skuName}</Text>}
                <View className={styles.itemBottom}>
                  <Text className={styles.itemPrice}>¥{item.price}</Text>
                  <View className={styles.quantityControl}>
                    <Text className={styles.quantityBtn} onClick={() => handleQuantityChange(index, -1)}>-</Text>
                    <Text className={styles.quantity}>{item.quantity}</Text>
                    <Text className={styles.quantityBtn} onClick={() => handleQuantityChange(index, 1)}>+</Text>
                  </View>
                </View>
              </View>
            </View>
          ))
        )}
      </ScrollView>

      {items.length > 0 && (
        <View className={styles.bottomBar}>
          <View className={styles.checkAll} onClick={handleCheckAll}>
            <Text>{allChecked ? '☑️' : '⬜'}</Text>
            <Text className={styles.checkAllText}>全选</Text>
          </View>
          <View className={styles.totalInfo}>
            <Text className={styles.totalLabel}>合计：</Text>
            <Text className={styles.totalPrice}>¥{totalPrice.toFixed(2)}</Text>
          </View>
          <View className={styles.checkoutBtn} onClick={handleCheckout}>
            <Text className={styles.checkoutText}>结算({items.filter(i => i.checked).length})</Text>
          </View>
        </View>
      )}
    </View>
  )
}
