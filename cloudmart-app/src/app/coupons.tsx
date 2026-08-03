import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { marketingApi } from '@/api/marketing'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface CouponTemplate {
  id: number
  name: string
  type: number
  value: number
  minAmount: number
  startTime: string
  endTime: string
  status: number
  claimed?: boolean
}

type MyCouponTab = 'available' | 'used' | 'expired'

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}.${month}.${day}`
}

function CouponCard({
  coupon,
  theme,
  isInactive,
  rightContent,
}: {
  coupon: CouponTemplate
  theme: ReturnType<typeof useTheme>
  isInactive: boolean
  rightContent: React.ReactNode
}) {
  const discountLabel = coupon.type === 1 ? `¥${coupon.value}` : `${coupon.value}折`
  const conditionText = coupon.minAmount > 0 ? `满${coupon.minAmount}元可用` : '无门槛'

  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
        opacity: isInactive ? 0.5 : 1,
        ...theme.shadowCard,
      }}
    >
      {/* Left: Discount amount */}
      <View
        style={{
          width: 100,
          justifyContent: 'center',
          alignItems: 'center',
          backgroundColor: isInactive ? theme.bgInput : `${theme.primary}20`,
          paddingVertical: Spacing.lg,
          borderRightWidth: 1,
          borderRightColor: theme.border,
        }}
      >
        <Text
          style={{
            fontSize: FontSize.xxl,
            fontWeight: '800',
            color: isInactive ? theme.textTertiary : theme.primary,
            textDecorationLine: isInactive ? 'line-through' : 'none',
          }}
        >
          {discountLabel}
        </Text>
        <Text
          style={{
            fontSize: FontSize.xs,
            color: isInactive ? theme.textTertiary : theme.primary,
            marginTop: Spacing.xs,
          }}
        >
          {coupon.type === 1 ? '满减券' : '折扣券'}
        </Text>
      </View>

      {/* Right: Info + action */}
      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        <View>
          <Text
            numberOfLines={1}
            style={{
              fontSize: FontSize.md,
              fontWeight: '600',
              color: isInactive ? theme.textTertiary : theme.text,
              textDecorationLine: isInactive ? 'line-through' : 'none',
            }}
          >
            {coupon.name}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
            {conditionText}
          </Text>
        </View>

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {formatDate(coupon.startTime)} - {formatDate(coupon.endTime)}
          </Text>
          {rightContent}
        </View>
      </View>
    </View>
  )
}

export default function CouponsPage() {
  const theme = useTheme()

  // 领券中心
  const [availableCoupons, setAvailableCoupons] = useState<CouponTemplate[]>([])
  const [availableLoading, setAvailableLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [claimingIds, setClaimingIds] = useState<Set<number>>(new Set())

  // 我的优惠券
  const [myCoupons, setMyCoupons] = useState<CouponTemplate[]>([])
  const [myLoading, setMyLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<MyCouponTab>('available')

  const tabs: { key: MyCouponTab; label: string }[] = [
    { key: 'available', label: '可使用' },
    { key: 'used', label: '已使用' },
    { key: 'expired', label: '已过期' },
  ]

  const loadAvailableCoupons = useCallback(async () => {
    setAvailableLoading(true)
    try {
      const res = await marketingApi.getCoupons({ page: 1, pageSize: 20 })
      const list = (res.data as any)?.data?.list || (res.data as any)?.data || []
      setAvailableCoupons(Array.isArray(list) ? list : [])
    } catch {
      setAvailableCoupons([])
    } finally {
      setAvailableLoading(false)
      setRefreshing(false)
    }
  }, [])

  const loadMyCoupons = useCallback(async () => {
    setMyLoading(true)
    try {
      const res = await marketingApi.getMyCoupons()
      const list = (res.data as any)?.data || []
      setMyCoupons(Array.isArray(list) ? list : [])
    } catch {
      setMyCoupons([])
    } finally {
      setMyLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAvailableCoupons()
    loadMyCoupons()
  }, [loadAvailableCoupons, loadMyCoupons])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    Promise.all([loadAvailableCoupons(), loadMyCoupons()]).finally(() => setRefreshing(false))
  }, [loadAvailableCoupons, loadMyCoupons])

  const handleClaim = useCallback(
    async (id: number) => {
      if (claimingIds.has(id)) return
      setClaimingIds((prev) => new Set(prev).add(id))
      try {
        await marketingApi.claimCoupon(id)
        setAvailableCoupons((prev) => prev.map((c) => (c.id === id ? { ...c, claimed: true } : c)))
        Alert.alert('领取成功', '优惠券已发放至您的账户')
        loadMyCoupons()
      } catch {
        Alert.alert('领取失败', '请稍后重试')
      } finally {
        setClaimingIds((prev) => {
          const next = new Set(prev)
          next.delete(id)
          return next
        })
      }
    },
    [claimingIds, loadMyCoupons],
  )

  const filteredMyCoupons = myCoupons.filter((c) => {
    if (activeTab === 'available') return c.status === 0
    if (activeTab === 'used') return c.status === 1
    return c.status === 2
  })

  const isInactive = (coupon: CouponTemplate) => coupon.status === 1 || coupon.status === 2

  const statusLabel = (coupon: CouponTemplate): string | null => {
    if (coupon.status === 1) return '已使用'
    if (coupon.status === 2) return '已过期'
    return null
  }

  if (availableLoading && myLoading && availableCoupons.length === 0 && myCoupons.length === 0) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: Spacing.xxxl }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        showsVerticalScrollIndicator={false}
      >
        {/* Section 1: 领券中心 */}
        <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text, marginBottom: Spacing.md }}>
            🎟️ 领券中心
          </Text>

          {availableLoading && availableCoupons.length === 0 ? (
            <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xxl }} />
          ) : availableCoupons.length === 0 ? (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxl }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无可领取的优惠券</Text>
            </View>
          ) : (
            availableCoupons.map((coupon) => (
              <CouponCard
                key={coupon.id}
                coupon={coupon}
                theme={theme}
                isInactive={false}
                rightContent={
                  coupon.claimed ? (
                    <View
                      style={{
                        paddingHorizontal: Spacing.md,
                        paddingVertical: Spacing.xs,
                        borderRadius: BorderRadius.xl,
                        backgroundColor: theme.bgInput,
                      }}
                    >
                      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, fontWeight: '600' }}>已领取</Text>
                    </View>
                  ) : (
                    <TouchableOpacity
                      activeOpacity={0.7}
                      disabled={claimingIds.has(coupon.id)}
                      onPress={() => handleClaim(coupon.id)}
                      style={{
                        paddingHorizontal: Spacing.md,
                        paddingVertical: Spacing.xs,
                        borderRadius: BorderRadius.xl,
                        backgroundColor: claimingIds.has(coupon.id) ? theme.bgInput : theme.primary,
                      }}
                    >
                      <Text
                        style={{
                          fontSize: FontSize.sm,
                          fontWeight: '600',
                          color: claimingIds.has(coupon.id) ? theme.textTertiary : '#FFFFFF',
                        }}
                      >
                        {claimingIds.has(coupon.id) ? '领取中...' : '领取'}
                      </Text>
                    </TouchableOpacity>
                  )
                }
              />
            ))
          )}
        </View>

        {/* Divider */}
        <View
          style={{
            height: 1,
            backgroundColor: theme.border,
            marginHorizontal: Spacing.lg,
            marginVertical: Spacing.lg,
          }}
        />

        {/* Section 2: 我的优惠券 */}
        <View style={{ paddingHorizontal: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text, marginBottom: Spacing.md }}>
            🎫 我的优惠券
          </Text>

          {/* Tabs */}
          <View style={{ flexDirection: 'row', marginBottom: Spacing.lg, backgroundColor: theme.bgInput, borderRadius: BorderRadius.xl, padding: Spacing.xs }}>
            {tabs.map((tab) => (
              <TouchableOpacity
                key={tab.key}
                activeOpacity={0.7}
                onPress={() => setActiveTab(tab.key)}
                style={{
                  flex: 1,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.lg,
                  alignItems: 'center',
                  backgroundColor: activeTab === tab.key ? theme.bgElevated : 'transparent',
                }}
              >
                <Text
                  style={{
                    fontSize: FontSize.md,
                    fontWeight: activeTab === tab.key ? '600' : '400',
                    color: activeTab === tab.key ? theme.primary : theme.textTertiary,
                  }}
                >
                  {tab.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          {/* Coupon list */}
          {myLoading && myCoupons.length === 0 ? (
            <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xxl }} />
          ) : filteredMyCoupons.length === 0 ? (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl }}>
              <Text style={{ fontSize: 36, marginBottom: Spacing.md, opacity: 0.3 }}>🎫</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>
                {activeTab === 'available' ? '暂无可用优惠券' : activeTab === 'used' ? '暂无已使用的优惠券' : '暂无已过期的优惠券'}
              </Text>
            </View>
          ) : (
            filteredMyCoupons.map((coupon) => (
              <CouponCard
                key={coupon.id}
                coupon={coupon}
                theme={theme}
                isInactive={isInactive(coupon)}
                rightContent={
                  statusLabel(coupon) ? (
                    <View
                      style={{
                        paddingHorizontal: Spacing.md,
                        paddingVertical: Spacing.xs,
                        borderRadius: BorderRadius.xl,
                        backgroundColor: theme.bgInput,
                      }}
                    >
                      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, fontWeight: '600' }}>
                        {statusLabel(coupon)}
                      </Text>
                    </View>
                  ) : null
                }
              />
            ))
          )}
        </View>
      </ScrollView>
    </View>
  )
}
