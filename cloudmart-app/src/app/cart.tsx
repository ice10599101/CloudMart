import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  Image,
  RefreshControl,
  ActivityIndicator,
  Alert,
} from 'react-native'
import { useState, useEffect, useCallback, useMemo } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { cartApi } from '@/api/cart'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { CartItem } from '@/types'

function Checkbox({ checked, onPress, theme }: { checked: boolean; onPress: () => void; theme: ReturnType<typeof useTheme> }) {
  return (
    <TouchableOpacity onPress={onPress} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
      <View
        style={{
          width: 22,
          height: 22,
          borderRadius: BorderRadius.full,
          borderWidth: 2,
          borderColor: checked ? theme.primary : theme.border,
          backgroundColor: checked ? theme.primary : 'transparent',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {checked && <Text style={{ color: '#FFFFFF', fontSize: 12, fontWeight: 'bold' }}>✓</Text>}
      </View>
    </TouchableOpacity>
  )
}

function QuantityControl({
  quantity,
  stock,
  onDecrease,
  onIncrease,
  theme,
}: {
  quantity: number
  stock: number
  onDecrease: () => void
  onIncrease: () => void
  theme: ReturnType<typeof useTheme>
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
      <TouchableOpacity
        onPress={onDecrease}
        disabled={quantity <= 1}
        style={{
          width: 28,
          height: 28,
          borderRadius: BorderRadius.sm,
          borderWidth: 1,
          borderColor: quantity <= 1 ? theme.border : theme.primary,
          backgroundColor: quantity <= 1 ? 'transparent' : theme.primaryGlow,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text style={{ fontSize: FontSize.md, color: quantity <= 1 ? theme.textTertiary : theme.primary }}>-</Text>
      </TouchableOpacity>
      <Text style={{ minWidth: 32, textAlign: 'center', fontSize: FontSize.md, color: theme.text, fontWeight: '600' }}>
        {quantity}
      </Text>
      <TouchableOpacity
        onPress={onIncrease}
        disabled={quantity >= stock}
        style={{
          width: 28,
          height: 28,
          borderRadius: BorderRadius.sm,
          borderWidth: 1,
          borderColor: quantity >= stock ? theme.border : theme.primary,
          backgroundColor: quantity >= stock ? 'transparent' : theme.primaryGlow,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text style={{ fontSize: FontSize.md, color: quantity >= stock ? theme.textTertiary : theme.primary }}>+</Text>
      </TouchableOpacity>
    </View>
  )
}

function CartItemCard({
  item,
  theme,
  onToggleCheck,
  onUpdateQuantity,
  onRemove,
}: {
  item: CartItem
  theme: ReturnType<typeof useTheme>
  onToggleCheck: () => void
  onUpdateQuantity: (quantity: number) => void
  onRemove: () => void
}) {
  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        borderWidth: 1,
        borderColor: theme.border,
        ...theme.shadowCard,
      }}
    >
      <Checkbox checked={item.checked} onPress={onToggleCheck} theme={theme} />

      {item.productImage ? (
        <Image
          source={{ uri: item.productImage }}
          style={{ width: 90, height: 90, borderRadius: BorderRadius.md, marginLeft: Spacing.md, resizeMode: 'cover' }}
        />
      ) : (
        <View
          style={{
            width: 90,
            height: 90,
            borderRadius: BorderRadius.md,
            marginLeft: Spacing.md,
            backgroundColor: theme.bgInput,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={{ fontSize: 28, opacity: 0.3 }}>📦</Text>
        </View>
      )}

      <View style={{ flex: 1, marginLeft: Spacing.md, justifyContent: 'space-between' }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Text numberOfLines={2} style={{ flex: 1, fontSize: FontSize.md, color: theme.text, fontWeight: '500', lineHeight: 20 }}>
            {item.productName}
          </Text>
          <TouchableOpacity onPress={onRemove} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={{ fontSize: 16, color: theme.textTertiary, marginLeft: Spacing.sm }}>✕</Text>
          </TouchableOpacity>
        </View>

        {item.skuName ? (
          <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            {item.skuName}
          </Text>
        ) : null}

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: 'bold', color: theme.accentRed }}>¥{item.price.toFixed(2)}</Text>
          <QuantityControl
            quantity={item.quantity}
            stock={item.stock}
            onDecrease={() => {
              if (item.quantity > 1) onUpdateQuantity(item.quantity - 1)
            }}
            onIncrease={() => {
              if (item.quantity < item.stock) onUpdateQuantity(item.quantity + 1)
            }}
            theme={theme}
          />
        </View>

        {item.stock < 10 && item.stock > 0 && (
          <Text style={{ fontSize: FontSize.xs, color: theme.accentOrange, marginTop: Spacing.xs }}>
            仅剩{item.stock}件
          </Text>
        )}
        {item.stock === 0 && (
          <Text style={{ fontSize: FontSize.xs, color: theme.accentRed, marginTop: Spacing.xs }}>已售罄</Text>
        )}
      </View>
    </View>
  )
}

export default function CartScreen() {
  const theme = useTheme()
  const [cartItems, setCartItems] = useState<CartItem[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const fetchCart = useCallback(async () => {
    setLoading(true)
    try {
      const res = await cartApi.getList()
      const list = (res.data as any)?.data || []
      setCartItems(Array.isArray(list) ? list : [])
    } catch {
      setCartItems([])
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    fetchCart()
  }, [fetchCart])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    fetchCart()
  }, [fetchCart])

  const isAllChecked = useMemo(() => cartItems.length > 0 && cartItems.every((item) => item.checked), [cartItems])

  const checkedCount = useMemo(() => cartItems.filter((item) => item.checked).length, [cartItems])

  const totalPrice = useMemo(
    () => cartItems.reduce((sum, item) => (item.checked ? sum + item.price * item.quantity : sum), 0),
    [cartItems],
  )

  const handleToggleCheck = useCallback(async (id: number) => {
    setCartItems((prev) => prev.map((item) => (item.id === id ? { ...item, checked: !item.checked } : item)))
    const item = cartItems.find((i) => i.id === id)
    if (item) {
      try {
        await cartApi.updateItem(id, { checked: !item.checked })
      } catch {
        // 回滚
        setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, checked: item.checked } : i)))
      }
    }
  }, [cartItems])

  const handleToggleAll = useCallback(async () => {
    const newChecked = !isAllChecked
    setCartItems((prev) => prev.map((item) => ({ ...item, checked: newChecked })))
    try {
      await Promise.all(cartItems.map((item) => cartApi.updateItem(item.skuId ?? item.id, { checked: newChecked })))
    } catch {
      setCartItems((prev) => prev.map((item) => ({ ...item, checked: !newChecked })))
    }
  }, [isAllChecked, cartItems])

  const handleUpdateQuantity = useCallback(async (id: number, quantity: number) => {
    setCartItems((prev) => prev.map((item) => (item.id === id ? { ...item, quantity } : item)))
    try {
      await cartApi.updateItem(id, { quantity })
    } catch {
      const item = cartItems.find((i) => i.id === id)
      if (item) {
        setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, quantity: item.quantity } : i)))
      }
    }
  }, [cartItems])

  const handleRemoveItem = useCallback((id: number) => {
    Alert.alert('确认删除', '确定要删除该商品吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          setCartItems((prev) => prev.filter((item) => item.id !== id))
          try {
            await cartApi.removeItem(id)
          } catch {
            Alert.alert('错误', '删除失败')
            fetchCart()
          }
        },
      },
    ])
  }, [fetchCart])

  const handleCheckout = useCallback(() => {
    const checkedItems = cartItems.filter((item) => item.checked)
    if (checkedItems.length === 0) {
      Alert.alert('提示', '请先选择商品')
      return
    }
    router.push('/checkout')
  }, [cartItems])

  if (loading && cartItems.length === 0) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  if (cartItems.length === 0) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
        <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.md, backgroundColor: theme.bgBase }}>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>购物车</Text>
        </View>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ fontSize: 64, marginBottom: Spacing.lg, opacity: 0.3 }}>🛒</Text>
          <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>购物车空空如也</Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>快去挑选心仪的商品吧</Text>
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => router.navigate({ pathname: '/(tabs)/mall' })}
            style={{
              marginTop: Spacing.xl,
              paddingHorizontal: Spacing.xxl,
              paddingVertical: Spacing.md,
              borderRadius: BorderRadius.xl,
              backgroundColor: theme.primary,
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>去逛逛</Text>
          </TouchableOpacity>
        </View>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.md, backgroundColor: theme.bgBase }}>
        <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>购物车</Text>
      </View>

      {/* Cart List */}
      <ScrollView
        contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingBottom: 120 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        showsVerticalScrollIndicator={false}
      >
        <View style={{ gap: Spacing.md }}>
          {cartItems.map((item) => (
            <CartItemCard
              key={item.id}
              item={item}
              theme={theme}
              onToggleCheck={() => handleToggleCheck(item.id)}
              onUpdateQuantity={(quantity) => handleUpdateQuantity(item.id, quantity)}
              onRemove={() => handleRemoveItem(item.id)}
            />
          ))}
        </View>
      </ScrollView>

      {/* Bottom Bar */}
      <View
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: theme.bgElevated,
          borderTopWidth: 1,
          borderTopColor: theme.border,
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          paddingBottom: Spacing.xl,
        }}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <Checkbox checked={isAllChecked} onPress={handleToggleAll} theme={theme} />
          <Text style={{ fontSize: FontSize.md, color: theme.text, marginLeft: Spacing.sm }}>全选</Text>
        </View>

        <View style={{ flex: 1, flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center' }}>
          <View style={{ marginRight: Spacing.lg }}>
            <View style={{ flexDirection: 'row', alignItems: 'baseline' }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>合计：</Text>
              <Text style={{ fontSize: FontSize.xl, fontWeight: 'bold', color: theme.accentRed }}>
                ¥{totalPrice.toFixed(2)}
              </Text>
            </View>
            {checkedCount > 0 && (
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, textAlign: 'right' }}>
                已选{checkedCount}件
              </Text>
            )}
          </View>

          <TouchableOpacity
            activeOpacity={0.7}
            onPress={handleCheckout}
            style={{
              paddingHorizontal: Spacing.xxl,
              paddingVertical: Spacing.md,
              borderRadius: BorderRadius.xl,
              backgroundColor: checkedCount > 0 ? theme.primary : theme.bgInput,
              minWidth: 100,
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: checkedCount > 0 ? '#FFFFFF' : theme.textTertiary, fontWeight: '600' }}>
              结算{checkedCount > 0 ? `(${checkedCount})` : ''}
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  )
}
