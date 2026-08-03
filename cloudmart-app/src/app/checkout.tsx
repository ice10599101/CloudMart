import { View, Text, TextInput, ScrollView, TouchableOpacity, Alert, ActivityIndicator, Image } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams, useFocusEffect } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { orderApi } from '@/api/order'
import { userApi } from '@/api/user'
import { cartApi } from '@/api/cart'
import { productApi } from '@/api/product'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Address, CartItem, Product } from '@/types'

interface OrderItemInput {
  skuId: number
  quantity: number
}

interface DisplayItem {
  skuId: number
  productId: number
  name: string
  image: string
  skuName?: string
  price: number
  quantity: number
}

const FREE_SHIPPING_THRESHOLD = 99
const SHIPPING_FEE = 10

export default function CheckoutPage() {
  const theme = useTheme()
  const { productId: productIdParam, quantity: quantityParam } = useLocalSearchParams<{
    productId?: string
    quantity?: string
  }>()

  const [address, setAddress] = useState<Address | null>(null)
  const [items, setItems] = useState<DisplayItem[]>([])
  const [remark, setRemark] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  const isDirectBuy = !!productIdParam

  const loadAddress = async () => {
    try {
      const res = await userApi.getDefaultAddress()
      setAddress(res.data?.data ?? null)
    } catch {
      setAddress(null)
    }
  }

  const loadDirectBuyItems = async () => {
    const productId = Number(productIdParam)
    const quantity = Number(quantityParam) || 1
    try {
      const res = await productApi.getDetail(productId)
      const product: Product = res.data?.data
      if (!product) return
      setItems([{
        skuId: productId,
        productId: product.id,
        name: product.name,
        image: product.mainImage,
        price: product.price,
        quantity,
      }])
    } catch {
      Alert.alert('错误', '商品信息加载失败')
    }
  }

  const loadCartItems = async () => {
    try {
      const res = await cartApi.getList()
      const cartItems: CartItem[] = res.data?.data || []
      const checkedItems = cartItems.filter((item) => item.checked)
      if (checkedItems.length === 0) {
        Alert.alert('提示', '请先选择要结算的商品', [
          { text: '返回', onPress: () => router.back() },
        ])
        return
      }
      setItems(checkedItems.map((item) => ({
        skuId: item.skuId,
        productId: item.productId,
        name: item.productName,
        image: item.productImage,
        skuName: item.skuName,
        price: item.price,
        quantity: item.quantity,
      })))
    } catch {
      Alert.alert('错误', '购物车加载失败')
    }
  }

  const loadData = async () => {
    setLoading(true)
    try {
      await Promise.all([
        loadAddress(),
        isDirectBuy ? loadDirectBuyItems() : loadCartItems(),
      ])
    } finally {
      setLoading(false)
    }
  }

  useFocusEffect(
    useCallback(() => {
      loadAddress()
    }, []),
  )

  useEffect(() => {
    loadData()
  }, [productIdParam, quantityParam])

  const itemTotal = items.reduce((sum, item) => sum + item.price * item.quantity, 0)
  const shippingFee = itemTotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE
  const totalAmount = itemTotal + shippingFee

  const handleSubmit = () => {
    if (!address) {
      Alert.alert('提示', '请添加收货地址')
      return
    }
    if (items.length === 0) {
      Alert.alert('提示', '没有可结算的商品')
      return
    }

    Alert.alert('确认', `确定提交订单？共 ¥${totalAmount.toFixed(2)}`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        onPress: async () => {
          setSubmitting(true)
          try {
            const orderItems: OrderItemInput[] = items.map((item) => ({
              skuId: item.skuId,
              quantity: item.quantity,
            }))
            const res = await orderApi.create({
              addressId: address.id,
              items: orderItems,
              remark: remark.trim() || undefined,
            })
            const orderId = res.data?.data?.id ?? res.data?.data
            if (orderId) {
              router.replace(`/order-detail?id=${orderId}`)
            } else {
              Alert.alert('成功', '订单创建成功')
              router.back()
            }
          } catch {
            Alert.alert('错误', '订单创建失败，请重试')
          } finally {
            setSubmitting(false)
          }
        },
      },
    ])
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
        <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginTop: Spacing.md }}>加载中...</Text>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: 100 }}
        keyboardShouldPersistTaps="handled"
      >
        {/* Address Section */}
        <TouchableOpacity
          onPress={() => router.push('/address')}
          activeOpacity={0.7}
          style={{
            marginHorizontal: Spacing.lg,
            marginTop: Spacing.lg,
            backgroundColor: theme.bgContainer,
            borderRadius: BorderRadius.lg,
            padding: Spacing.lg,
          }}
        >
          {address ? (
            <View>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginBottom: Spacing.xs }}>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>{address.name}</Text>
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>{address.phone}</Text>
                {address.isDefault && (
                  <View style={{ paddingHorizontal: 8, paddingVertical: 2, backgroundColor: theme.primary, borderRadius: 4 }}>
                    <Text style={{ fontSize: FontSize.xs, color: '#FFFFFF', fontWeight: '500' }}>默认</Text>
                  </View>
                )}
              </View>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 20 }}>
                {address.province}{address.city}{address.district} {address.detail}
              </Text>
            </View>
          ) : (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.md }}>
              <Text style={{ fontSize: 20, marginRight: Spacing.sm }}>📍</Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.primary, fontWeight: '500' }}>请添加收货地址</Text>
            </View>
          )}
          <View style={{ position: 'absolute', right: Spacing.lg, top: '50%', marginTop: -8 }}>
            <Text style={{ fontSize: FontSize.xl, color: theme.textTertiary }}>›</Text>
          </View>
        </TouchableOpacity>

        {/* Order Items Section */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.sm }}>
            商品清单 ({items.length})
          </Text>
          <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
            {items.map((item, index) => (
              <View
                key={`${item.skuId}-${item.productId}`}
                style={{
                  flexDirection: 'row',
                  padding: Spacing.lg,
                  borderBottomWidth: index < items.length - 1 ? 1 : 0,
                  borderBottomColor: theme.border,
                }}
              >
                <Image
                  source={{ uri: item.image }}
                  style={{
                    width: 80,
                    height: 80,
                    borderRadius: BorderRadius.md,
                    backgroundColor: theme.bgElevated,
                  }}
                  resizeMode="cover"
                />
                <View style={{ flex: 1, marginLeft: Spacing.md, justifyContent: 'space-between' }}>
                  <Text
                    style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', lineHeight: 20 }}
                    numberOfLines={2}
                    ellipsizeMode="tail"
                  >
                    {item.name}
                  </Text>
                  {item.skuName ? (
                    <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: 2 }}>
                      {item.skuName}
                    </Text>
                  ) : null}
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.xs }}>
                    <Text style={{ fontSize: FontSize.lg, color: theme.accentRed, fontWeight: '600' }}>
                      ¥{item.price.toFixed(2)}
                    </Text>
                    <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>
                      x{item.quantity}
                    </Text>
                  </View>
                </View>
              </View>
            ))}
          </View>
        </View>

        {/* Price Summary */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>商品合计</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.text }}>¥{itemTotal.toFixed(2)}</Text>
          </View>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>运费</Text>
            <Text style={{ fontSize: FontSize.md, color: shippingFee === 0 ? theme.accentGreen : theme.text }}>
              {shippingFee === 0 ? '免运费' : `¥${shippingFee.toFixed(2)}`}
            </Text>
          </View>
          {itemTotal < FREE_SHIPPING_THRESHOLD && itemTotal > 0 && (
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.xs }}>
              再购 ¥{(FREE_SHIPPING_THRESHOLD - itemTotal).toFixed(2)} 即可免运费
            </Text>
          )}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.md, paddingTop: Spacing.md, borderTopWidth: 1, borderTopColor: theme.border }}>
            <Text style={{ fontSize: FontSize.lg, color: theme.text, fontWeight: '600' }}>合计</Text>
            <Text style={{ fontSize: FontSize.xl, color: theme.accentRed, fontWeight: '700' }}>
              ¥{totalAmount.toFixed(2)}
            </Text>
          </View>
        </View>

        {/* Remark */}
        <View style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500', marginBottom: Spacing.sm }}>订单备注</Text>
          <TextInput
            value={remark}
            onChangeText={setRemark}
            placeholder="选填，请输入备注信息"
            placeholderTextColor={theme.textTertiary}
            maxLength={200}
            multiline
            style={{
              fontSize: FontSize.md,
              color: theme.text,
              borderWidth: 1,
              borderColor: theme.border,
              borderRadius: BorderRadius.md,
              paddingHorizontal: Spacing.md,
              paddingVertical: Spacing.sm,
              backgroundColor: theme.bgBase,
              minHeight: 60,
              textAlignVertical: 'top',
            }}
          />
        </View>
      </ScrollView>

      {/* Bottom Fixed Bar */}
      <View style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.md,
        backgroundColor: theme.bgContainer,
        borderTopWidth: 1,
        borderTopColor: theme.border,
      }}>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4 }}>
          <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>合计：</Text>
          <Text style={{ fontSize: FontSize.xxl, color: theme.accentRed, fontWeight: '700' }}>
            ¥{totalAmount.toFixed(2)}
          </Text>
        </View>
        <TouchableOpacity
          onPress={submitting ? undefined : handleSubmit}
          activeOpacity={0.8}
          style={{
            paddingHorizontal: Spacing.xxl,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.xl,
            backgroundColor: theme.primary,
            opacity: submitting ? 0.6 : 1,
            minWidth: 120,
            alignItems: 'center',
          }}
        >
          {submitting ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <Text style={{ fontSize: FontSize.lg, color: '#FFFFFF', fontWeight: '600' }}>提交订单</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  )
}
