import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Input, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import {
  getAfterSaleDetail,
  cancelAfterSale,
  registerReturnShipping,
  resolveAssetUrls,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/afterSale'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

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

function formatTime(time?: string | null) {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function AfterSaleDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const caseId = Taro.getCurrentInstance().router?.params?.id || ''
  const [detail, setDetail] = useState<AfterSaleCase | null>(null)
  const [loading, setLoading] = useState(true)
  const [attachmentUrls, setAttachmentUrls] = useState<string[]>([])
  const [cancelling, setCancelling] = useState(false)

  // 回填物流表单
  const [shippingOpen, setShippingOpen] = useState(false)
  const [carrier, setCarrier] = useState('')
  const [trackingNo, setTrackingNo] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const loadDetail = useCallback(async () => {
    if (!caseId) return
    try {
      setLoading(true)
      const res = await getAfterSaleDetail(caseId)
      const data = res.data?.data as unknown as AfterSaleCase
      setDetail(data)
      if (data) {
        resolveAssetUrls(data.attachmentFileIds).then(setAttachmentUrls).catch(() => setAttachmentUrls([]))
      }
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [caseId])

  useEffect(() => {
    loadDetail()
  }, [loadDetail])

  const handleCancel = () => {
    Taro.showModal({
      title: '撤销售后',
      content: '确定要撤销该售后申请吗？撤销后需重新提交。',
      success: async (res) => {
        if (!res.confirm) return
        setCancelling(true)
        try {
          await cancelAfterSale(caseId)
          Taro.showToast({ title: '已撤销', icon: 'success' })
          loadDetail()
        } catch (err) {
          const message =
            (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '撤销失败'
          Taro.showToast({ title: message, icon: 'none' })
        } finally {
          setCancelling(false)
        }
      },
    })
  }

  const submitShipping = async () => {
    if (!carrier.trim() || !trackingNo.trim()) {
      Taro.showToast({ title: '请填写承运商与运单号', icon: 'none' })
      return
    }
    setSubmitting(true)
    try {
      await registerReturnShipping(caseId, carrier.trim(), trackingNo.trim())
      Taro.showToast({ title: '运单已登记', icon: 'success' })
      setShippingOpen(false)
      loadDetail()
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '登记失败'
      Taro.showToast({ title: message, icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.loading}>
          <View className={styles.spinner} />
          <Text className={styles.loadingText}>加载中</Text>
        </View>
      </View>
    )
  }

  if (!detail) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🛠️</Text>
          <Text className={styles.emptyText}>售后单不存在</Text>
        </View>
      </View>
    )
  }

  const canCancel = detail.status === 'PENDING'
  // RETURN_REFUND 且 APPROVED：等待买家寄回（时间线无 RETURN_SHIPPED 时可登记运单）
  const canRegisterShipping =
    detail.status === 'APPROVED' && detail.type === 'RETURN_REFUND'
      && !(detail.timeline || []).some((t) => t.action === 'RETURN_SHIPPED')

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {/* 状态区 */}
        <View className={styles.statusSection}>
          <Text className={styles.statusText}>{AFTER_SALE_STATUS_TEXT[detail.status] || detail.status}</Text>
          <Text className={styles.statusSub}>
            {AFTER_SALE_TYPE_TEXT[detail.type] || detail.type}{detail.quantity > 0 ? ` · ${detail.quantity} 件` : ' · 整单'}
          </Text>
        </View>

        {/* 案件信息 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>售后信息</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>售后单号</Text>
            <Text className={styles.infoValue}>{detail.caseNo}</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>关联订单</Text>
            <Text
              className={styles.infoLink}
              onClick={() => Taro.navigateTo({ url: `/pages/orderDetail/index?id=${detail.orderId}` })}
            >
              {detail.orderNo || detail.orderId} ›
            </Text>
          </View>
          <View className={styles.reasonBlock}>
            <Text className={styles.infoLabel}>申请原因</Text>
            <Text className={styles.reasonText}>{detail.reason}</Text>
          </View>
          {detail.refundAmount != null && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>退款金额</Text>
              <Text className={styles.refundAmount}>¥{detail.refundAmount}</Text>
            </View>
          )}
          {detail.refundNo && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>退款单号</Text>
              <Text className={styles.infoValue}>{detail.refundNo}</Text>
            </View>
          )}
          {detail.rejectReason && (
            <View className={styles.reasonBlock}>
              <Text className={styles.infoLabel}>拒绝原因</Text>
              <Text className={styles.rejectText}>{detail.rejectReason}</Text>
            </View>
          )}
          {attachmentUrls.length > 0 && (
            <View className={styles.reasonBlock}>
              <Text className={styles.infoLabel}>凭证图片</Text>
              <View className={styles.attachmentRow}>
                {attachmentUrls.map((url, i) => (
                  <Image
                    key={i}
                    className={styles.attachmentImage}
                    src={url}
                    mode='aspectFill'
                    onClick={() => Taro.previewImage({ urls: attachmentUrls, current: url })}
                  />
                ))}
              </View>
            </View>
          )}
        </View>

        {/* 时间线 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>处理进度</Text>
          </View>
          {(detail.timeline || []).length > 0 ? (
            <View className={styles.timeline}>
              {detail.timeline.map((entry, i) => (
                <View key={i} className={styles.timelineItem}>
                  <View className={styles.timelineDotWrap}>
                    <View className={`${styles.timelineDot} ${i === 0 ? styles.timelineDotCurrent : ''}`} />
                    {i < detail.timeline.length - 1 && <View className={styles.timelineLine} />}
                  </View>
                  <View className={styles.timelineBody}>
                    <Text className={styles.timelineAction}>{ACTION_TEXT[entry.action] || entry.action}</Text>
                    {entry.detail && <Text className={styles.timelineDetail}>{entry.detail}</Text>}
                    <Text className={styles.timelineTime}>{formatTime(entry.createdAt)}</Text>
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <Text className={styles.timelineEmpty}>暂无处理记录</Text>
          )}
        </View>
      </ScrollView>

      {/* 底部操作 */}
      {(canCancel || canRegisterShipping) && (
        <View className={styles.bottomBar}>
          {canCancel && (
            <View className={styles.btnSecondary} onClick={handleCancel}>
              <Text className={styles.btnSecondaryText}>{cancelling ? '撤销中...' : '撤销申请'}</Text>
            </View>
          )}
          {canRegisterShipping && (
            <View className={styles.btnPrimary} onClick={() => setShippingOpen(true)}>
              <Text className={styles.btnPrimaryText}>登记退货运单</Text>
            </View>
          )}
        </View>
      )}

      {/* 回填物流弹窗 */}
      {shippingOpen && (
        <View className={styles.mask} onClick={() => !submitting && setShippingOpen(false)}>
          <View className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>登记退货运单</Text>
            <Text className={styles.modalHint}>请将商品寄回平台仓库，并填写承运商与运单号</Text>
            <Input
              className={styles.modalInput}
              value={carrier}
              placeholder='承运商（如：顺丰速运）'
              maxlength={50}
              onInput={(e) => setCarrier(e.detail.value)}
            />
            <Input
              className={styles.modalInput}
              value={trackingNo}
              placeholder='运单号'
              maxlength={50}
              onInput={(e) => setTrackingNo(e.detail.value)}
            />
            <View className={styles.modalActions}>
              <View className={styles.btnCancel} onClick={() => setShippingOpen(false)}>
                <Text className={styles.btnCancelText}>取消</Text>
              </View>
              <View className={styles.btnSubmit} onClick={submitting ? undefined : submitShipping}>
                <Text className={styles.btnSubmitText}>{submitting ? '提交中...' : '提交'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
