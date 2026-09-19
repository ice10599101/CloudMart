import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro, { usePullDownRefresh } from '@tarojs/taro'
import { marketingApi } from '@/api/marketing'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { CouponTemplate, UserCoupon } from '@/types'
import styles from './index.module.scss'

// 对齐 Web 领券中心：可领取 / 我的优惠券 两大区
const MAIN_TABS = ['领券中心', '我的优惠券']
const MY_STATUS_TABS = ['可使用', '已使用', '已过期']
const MY_STATUS_MAP = ['UNUSED', 'USED', 'EXPIRED']

export default function CouponsPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [mainTab, setMainTab] = useState(0)
  const [statusTab, setStatusTab] = useState(0)
  const [templates, setTemplates] = useState<CouponTemplate[]>([])
  const [claimedIds, setClaimedIds] = useState<number[]>([])
  const [coupons, setCoupons] = useState<UserCoupon[]>([])
  const [claimingId, setClaimingId] = useState<number | null>(null)
  useAuthGuard()

  useEffect(() => {
    if (mainTab === 0) {
      loadTemplates()
    } else {
      loadCoupons()
    }
  }, [mainTab, statusTab])

  const loadTemplates = async () => {
    try {
      const res = await marketingApi.getCouponTemplates({ page: 1, pageSize: 50 })
      setTemplates(res.data?.data?.list || [])
      setClaimedIds([])
    } catch {
      // API unavailable
    }
  }

  const loadCoupons = async () => {
    try {
      const res = await marketingApi.getUserCoupons({ status: MY_STATUS_MAP[statusTab], page: 1, pageSize: 50 })
      setCoupons(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  const handleClaim = async (template: CouponTemplate) => {
    if (claimingId != null) return
    setClaimingId(template.id)
    try {
      await marketingApi.claimCoupon(template.id)
      setClaimedIds((prev) => [...prev, template.id])
      Taro.showToast({ title: '领取成功', icon: 'success' })
    } catch (err) {
      const code = (err as { response?: { data?: { error?: { code?: string } } } })?.response?.data?.error?.code
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      if (code === 'COUPON_ALREADY_CLAIMED' || (message && message.includes('已经领取'))) {
        setClaimedIds((prev) => [...prev, template.id])
        Taro.showToast({ title: '已领取过该券', icon: 'none' })
      } else if (message && message.includes('已领完')) {
        Taro.showToast({ title: '券已领完', icon: 'none' })
      } else {
        Taro.showToast({ title: message || '领取失败', icon: 'none' })
      }
    } finally {
      setClaimingId(null)
    }
  }

  const percentLabel = (rate: number) => `${Math.round((1 - rate) * 100)}%off`

  usePullDownRefresh(() => {
    if (mainTab === 0) {
      loadTemplates().finally(() => Taro.stopPullDownRefresh())
    } else {
      loadCoupons().finally(() => Taro.stopPullDownRefresh())
    }
  })

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.mainTabs}>
        {MAIN_TABS.map((tab, i) => (
          <View key={i} className={`${styles.mainTab} ${mainTab === i ? styles.mainTabActive : ''}`} onClick={() => setMainTab(i)}>
            <Text className={mainTab === i ? styles.mainTabTextActive : styles.mainTabText}>{tab}</Text>
          </View>
        ))}
      </View>

      {mainTab === 1 && (
        <View className={styles.tabs}>
          {MY_STATUS_TABS.map((tab, i) => (
            <View key={i} className={`${styles.tab} ${statusTab === i ? styles.tabActive : ''}`} onClick={() => setStatusTab(i)}>
              <Text className={statusTab === i ? styles.tabTextActive : styles.tabText}>{tab}</Text>
            </View>
          ))}
        </View>
      )}

      <ScrollView scrollY>
        {mainTab === 0 ? (
          templates.length > 0 ? (
            templates.map((template) => (
              <View key={template.id} className={styles.couponCard}>
                <View className={styles.couponAmount}>
                  {template.type === 'PERCENT_OFF' && template.discountRate != null ? (
                    <Text className={styles.couponValue}>{percentLabel(template.discountRate)}</Text>
                  ) : (
                    <>
                      <Text className={styles.couponSymbol}>¥</Text>
                      <Text className={styles.couponValue}>{template.discountAmount}</Text>
                    </>
                  )}
                </View>
                <View className={styles.couponInfo}>
                  <Text className={styles.couponName}>{template.name}</Text>
                  <Text className={styles.couponCondition}>{template.thresholdAmount > 0 ? `满${template.thresholdAmount}元可用` : '无门槛'}</Text>
                  <Text className={styles.couponExpiry}>
                    {template.validityType === 'FIXED' && template.endTime
                      ? `有效期至${template.endTime.slice(0, 10)}`
                      : template.validDays
                        ? `领取后${template.validDays}天有效`
                        : ''}
                    {` · 剩余${template.remainingQuantity}张`}
                  </Text>
                </View>
                <View
                  className={`${styles.claimBtn} ${claimedIds.includes(template.id) || template.remainingQuantity <= 0 ? styles.claimBtnDisabled : ''}`}
                  onClick={() => !claimedIds.includes(template.id) && template.remainingQuantity > 0 && handleClaim(template)}
                >
                  <Text className={styles.claimBtnText}>
                    {claimedIds.includes(template.id) ? '已领取' : template.remainingQuantity <= 0 ? '已领完' : claimingId === template.id ? '领取中' : '领取'}
                  </Text>
                </View>
              </View>
            ))
          ) : (
            <View className={styles.empty}>
              <Text className={styles.emptyIcon}>🎟️</Text>
              <Text className={styles.emptyText}>暂无可领取的优惠券</Text>
            </View>
          )
        ) : coupons.length > 0 ? (
          coupons.map((coupon) => (
            <View key={coupon.id} className={`${styles.couponCard} ${coupon.status !== 'UNUSED' ? styles.couponCardDisabled : ''}`}>
              <View className={styles.couponAmount}>
                {coupon.templateType === 'PERCENT_OFF' && coupon.discountRate != null ? (
                  <Text className={styles.couponValue}>{percentLabel(coupon.discountRate)}</Text>
                ) : (
                  <>
                    <Text className={styles.couponSymbol}>¥</Text>
                    <Text className={styles.couponValue}>{coupon.discountAmount}</Text>
                  </>
                )}
              </View>
              <View className={styles.couponInfo}>
                <Text className={styles.couponName}>{coupon.templateName}</Text>
                <Text className={styles.couponCondition}>{coupon.thresholdAmount > 0 ? `满${coupon.thresholdAmount}元可用` : '无门槛'}</Text>
                <Text className={styles.couponExpiry}>
                  {coupon.status === 'USED' ? '已于' : '有效期至'}
                  {(coupon.status === 'USED' ? coupon.usedAt : coupon.expiredAt)?.slice(0, 10) ?? '--'}
                </Text>
              </View>
              {coupon.status !== 'UNUSED' && (
                <View className={styles.statusBadge}>
                  <Text className={styles.statusBadgeText}>{coupon.status === 'USED' ? '已使用' : '已过期'}</Text>
                </View>
              )}
            </View>
          ))
        ) : (
          <View className={styles.empty}>
            <Text className={styles.emptyIcon}>🎫</Text>
            <Text className={styles.emptyText}>暂无优惠券</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
