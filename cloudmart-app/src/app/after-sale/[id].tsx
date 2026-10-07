import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert, TextInput, Modal } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import {
  getAfterSaleDetail,
  cancelAfterSale,
  registerReturnShipping,
  resolveAssetUrls,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
  type AfterSaleTimelineEntry,
} from '@/api/after-sale'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/** 时间线动作文案（对齐后端 AfterSaleCaseEvent） */
const ACTION_TEXT: Record<string, string> = {
  APPLY: '提交申请',
  APPROVED: '平台同意',
  REJECTED: '平台拒绝',
  RETURN_SHIPPED: '买家已寄回',
  RETURN_RECEIVED: '平台已收货',
  REFUND_SUBMITTED: '退款已提交',
  REFUND_COMPLETED: '退款完成',
  INSPECTION: '质检结果',
  CLOSED: '已关闭',
}

type StatusColorKey = 'accentOrange' | 'primary' | 'accentRed' | 'accentGreen'

const STATUS_COLOR_MAP: Record<string, StatusColorKey> = {
  PENDING: 'accentOrange',
  APPROVED: 'primary',
  REJECTED: 'accentRed',
  REFUNDED: 'accentGreen',
}

export default function AfterSaleDetailScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id?: string }>()

  const [detail, setDetail] = useState<AfterSaleCase | null>(null)
  const [attachmentUrls, setAttachmentUrls] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [shippingOpen, setShippingOpen] = useState(false)
  const [carrier, setCarrier] = useState('')
  const [trackingNo, setTrackingNo] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const fetchDetail = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await getAfterSaleDetail(id)
      const data = (res.data as unknown as { data?: AfterSaleCase })?.data
      if (data) {
        setDetail(data)
        resolveAssetUrls(data.attachmentFileIds).then(setAttachmentUrls).catch(() => setAttachmentUrls([]))
      }
    } catch {
      Alert.alert('错误', '加载售后单失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchDetail()
  }, [fetchDetail])

  const handleCancel = () => {
    if (!detail) return
    Alert.alert('撤销售后', '确定要撤销该售后申请吗？撤销后需重新提交。', [
      { text: '再想想', style: 'cancel' },
      {
        text: '确定撤销',
        style: 'destructive',
        onPress: async () => {
          try {
            await cancelAfterSale(detail.id)
            Alert.alert('提示', '已撤销')
            fetchDetail()
          } catch {
            Alert.alert('错误', '撤销失败，请稍后重试')
          }
        },
      },
    ])
  }

  const submitShipping = async () => {
    if (!detail) return
    if (!carrier.trim() || !trackingNo.trim()) {
      Alert.alert('提示', '请填写承运商与运单号')
      return
    }
    setSubmitting(true)
    try {
      await registerReturnShipping(detail.id, carrier.trim(), trackingNo.trim())
      setShippingOpen(false)
      Alert.alert('提示', '运单已登记')
      fetchDetail()
    } catch {
      Alert.alert('错误', '登记失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={theme.primary} size="large" />
      </View>
    )
  }

  if (!detail) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ fontSize: 48, marginBottom: Spacing.md, opacity: 0.3 }}>🛠️</Text>
        <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>售后单不存在</Text>
      </View>
    )
  }

  const colorKey = STATUS_COLOR_MAP[detail.status]
  const statusColor = colorKey ? theme[colorKey] : theme.textTertiary
  const canCancel = detail.status === 'PENDING'
  // RETURN_REFUND 且 APPROVED：等待买家寄回（时间线无 RETURN_SHIPPED 时可登记运单）
  const canRegisterShipping =
    detail.status === 'APPROVED' && detail.type === 'RETURN_REFUND'
      && !(detail.timeline || []).some((t) => t.action === 'RETURN_SHIPPED')

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: canCancel || canRegisterShipping ? 120 : Spacing.xxl }}>
        {/* 状态区 */}
        <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginBottom: Spacing.md }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: statusColor, marginBottom: Spacing.xs }}>
            {AFTER_SALE_STATUS_TEXT[detail.status] || detail.status}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
            {AFTER_SALE_TYPE_TEXT[detail.type] || detail.type}
            {detail.quantity > 0 ? ` · ${detail.quantity} 件` : ' · 整单'}
          </Text>
        </View>

        {/* 售后信息 */}
        <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginBottom: Spacing.md }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.md }}>售后信息</Text>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>售后单号</Text>
            <Text numberOfLines={1} style={{ fontSize: FontSize.md, color: theme.text, flexShrink: 1 }}>{detail.caseNo}</Text>
          </View>
          <TouchableOpacity
            style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: Spacing.sm }}
            onPress={() => router.push(`/order-detail?id=${detail.orderId}`)}
          >
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>关联订单</Text>
            <Text numberOfLines={1} style={{ fontSize: FontSize.md, color: theme.primary, flexShrink: 1 }}>
              {detail.orderNo || detail.orderId} ›
            </Text>
          </TouchableOpacity>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginBottom: Spacing.xs }}>申请原因</Text>
          <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 22, marginBottom: Spacing.sm }}>{detail.reason}</Text>
          {detail.refundAmount != null && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>退款金额</Text>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.accentRed }}>¥{detail.refundAmount}</Text>
            </View>
          )}
          {detail.refundNo ? (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>退款单号</Text>
              <Text numberOfLines={1} style={{ fontSize: FontSize.md, color: theme.text, flexShrink: 1 }}>{detail.refundNo}</Text>
            </View>
          ) : null}
          {detail.rejectReason ? (
            <>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: Spacing.sm, marginBottom: Spacing.xs }}>拒绝原因</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.accentRed, lineHeight: 22 }}>{detail.rejectReason}</Text>
            </>
          ) : null}
          {attachmentUrls.length > 0 ? (
            <>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: Spacing.sm, marginBottom: Spacing.xs }}>凭证图片</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                {attachmentUrls.map((url, i) => (
                  <Image key={i} source={{ uri: url }} style={{ width: 80, height: 80, borderRadius: BorderRadius.sm, backgroundColor: theme.bgInput }} />
                ))}
              </View>
            </>
          ) : null}
        </View>

        {/* 处理进度时间线 */}
        <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.md }}>处理进度</Text>
          {(detail.timeline || []).length > 0 ? (
            (detail.timeline as AfterSaleTimelineEntry[]).map((entry, i, arr) => (
              <View key={i} style={{ flexDirection: 'row', marginBottom: i < arr.length - 1 ? Spacing.lg : 0 }}>
                <View style={{ alignItems: 'center', marginRight: Spacing.md }}>
                  <View
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: 5,
                      backgroundColor: i === 0 ? theme.primary : theme.border,
                      marginTop: 5,
                    }}
                  />
                  {i < arr.length - 1 && <View style={{ width: 2, flex: 1, backgroundColor: theme.border, marginVertical: 2 }} />}
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>
                    {ACTION_TEXT[entry.action] || entry.action}
                  </Text>
                  {entry.detail ? (
                    <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 2 }}>{entry.detail}</Text>
                  ) : null}
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
                    {new Date(entry.createdAt).toLocaleString()}
                  </Text>
                </View>
              </View>
            ))
          ) : (
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>暂无处理记录</Text>
          )}
        </View>
      </ScrollView>

      {/* 底部操作 */}
      {(canCancel || canRegisterShipping) && (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            flexDirection: 'row',
            gap: Spacing.md,
            padding: Spacing.lg,
            backgroundColor: theme.bgContainer,
            borderTopWidth: 1,
            borderTopColor: theme.border,
          }}
        >
          {canCancel && (
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={handleCancel}
              style={{
                flex: 1,
                alignItems: 'center',
                justifyContent: 'center',
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.full,
                borderWidth: 1,
                borderColor: theme.border,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: theme.text }}>撤销申请</Text>
            </TouchableOpacity>
          )}
          {canRegisterShipping && (
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={() => setShippingOpen(true)}
              style={{
                flex: 1,
                alignItems: 'center',
                justifyContent: 'center',
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.full,
                backgroundColor: theme.primary,
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>登记退货运单</Text>
            </TouchableOpacity>
          )}
        </View>
      )}

      {/* 回填物流弹窗 */}
      <Modal visible={shippingOpen} transparent animationType="fade" onRequestClose={() => setShippingOpen(false)}>
        <TouchableOpacity activeOpacity={1} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center', justifyContent: 'center' }} onPress={() => setShippingOpen(false)}>
          <TouchableOpacity
            activeOpacity={1}
            onPress={() => undefined}
            style={{
              width: '86%',
              backgroundColor: theme.bgContainer,
              borderRadius: BorderRadius.lg,
              padding: Spacing.xl,
            }}
          >
            <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text, marginBottom: Spacing.sm }}>登记退货运单</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginBottom: Spacing.lg }}>
              请将商品寄回平台仓库，并填写承运商与运单号
            </Text>
            <TextInput
              value={carrier}
              onChangeText={setCarrier}
              placeholder="承运商（如：顺丰速运）"
              placeholderTextColor={theme.textTertiary}
              maxLength={50}
              style={{
                borderWidth: 1,
                borderColor: theme.border,
                borderRadius: BorderRadius.md,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.md,
                color: theme.text,
                backgroundColor: theme.bgInput,
                marginBottom: Spacing.md,
              }}
            />
            <TextInput
              value={trackingNo}
              onChangeText={setTrackingNo}
              placeholder="运单号"
              placeholderTextColor={theme.textTertiary}
              maxLength={50}
              style={{
                borderWidth: 1,
                borderColor: theme.border,
                borderRadius: BorderRadius.md,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.md,
                color: theme.text,
                backgroundColor: theme.bgInput,
                marginBottom: Spacing.xl,
              }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={() => setShippingOpen(false)}
                style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border }}
              >
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={submitting ? () => undefined : submitShipping}
                style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, backgroundColor: theme.primary }}
              >
                <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>{submitting ? '提交中...' : '提交'}</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}
