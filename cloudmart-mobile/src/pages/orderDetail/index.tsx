import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import {
  applyAfterSale,
  listOrderAfterSales,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/afterSale'
import { fileApi } from '@/api/file'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  skuName: string
  price: number
  quantity: number
}

interface OrderDetail {
  id: number
  orderNo: string
  status: number
  totalAmount: number
  shippingFee: number
  payAmount: number
  /** T05：退款汇总状态（NONE/PARTIAL/FULL）与已退累计金额 */
  refundStatus?: 'NONE' | 'PARTIAL' | 'FULL' | string
  refundedAmount?: number
  recipientName: string
  recipientPhone: string
  recipientAddress: string
  items: OrderItem[]
  createdAt: string
  paidAt?: string
  shippedAt?: string
  receivedAt?: string
}

const STATUS_CONFIG: Record<number, { icon: string; text: string }> = {
  0: { icon: '💰', text: '待付款' },
  1: { icon: '📦', text: '待发货' },
  2: { icon: '🚚', text: '待收货' },
  3: { icon: '✅', text: '已完成' },
  4: { icon: '❌', text: '已取消' },
}

function formatDateTime(time?: string) {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function OrderDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()

  const id = Taro.getCurrentInstance().router?.params?.id || ''
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [refundReason, setRefundReason] = useState('')
  const [refundOpen, setRefundOpen] = useState(false)
  const [refunding, setRefunding] = useState(false)

  // T11/P0-2：售后案件（PAID/SHIPPED 可申请；本单已有案件列表）
  const [afterSaleCases, setAfterSaleCases] = useState<AfterSaleCase[]>([])
  const [afterSaleOpen, setAfterSaleOpen] = useState(false)
  const [afterSaleType, setAfterSaleType] = useState<'REFUND_ONLY' | 'RETURN_REFUND'>('REFUND_ONLY')
  const [afterSaleItemId, setAfterSaleItemId] = useState<number | null>(null)
  const [afterSaleQuantity, setAfterSaleQuantity] = useState(1)
  const [afterSaleReason, setAfterSaleReason] = useState('')
  const [afterSaleFileIds, setAfterSaleFileIds] = useState<string[]>([])
  const [afterSaleUploading, setAfterSaleUploading] = useState(false)
  const [afterSaleSubmitting, setAfterSaleSubmitting] = useState(false)

  useEffect(() => {
    if (id) {
      loadOrder()
      loadAfterSaleCases()
    }
  }, [id])

  const loadOrder = async () => {
    try {
      setLoading(true)
      const res = await orderApi.getDetail(id)
      setOrder(res.data?.data as unknown as OrderDetail)
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const loadAfterSaleCases = async () => {
    try {
      const res = await listOrderAfterSales(id)
      setAfterSaleCases(res.data?.data as unknown as AfterSaleCase[])
    } catch {
      // 售后列表加载失败不阻塞订单详情主流程
      setAfterSaleCases([])
    }
  }

  const handleCancel = () => {
    Taro.showModal({
      title: '提示',
      content: '确定要取消该订单吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await orderApi.cancel(id)
            Taro.showToast({ title: '已取消', icon: 'success' })
            loadOrder()
          } catch {
            Taro.showToast({ title: '取消失败', icon: 'none' })
          }
        }
      },
    })
  }

  const handlePay = () => {
    Taro.navigateTo({ url: `/pages/payment/index?id=${order!.id}` })
  }

  const handleConfirm = () => {
    Taro.showModal({
      title: '提示',
      content: '确认已收到商品？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await orderApi.confirm(id)
            Taro.showToast({ title: '已确认收货', icon: 'success' })
            loadOrder()
          } catch {
            Taro.showToast({ title: '操作失败', icon: 'none' })
          }
        }
      },
    })
  }

  const handleRebuy = () => {
    const firstItem = order?.items?.[0]
    if (firstItem?.productId) {
      Taro.navigateTo({ url: `/pages/productDetail/index?id=${firstItem.productId}` })
    } else {
      Taro.switchTab({ url: '/pages/mall/index' })
    }
  }

  /** 申请退款（对齐 Web 端：已付款/已发货可申请，需填写原因） */
  const handleRefund = () => {
    setRefundReason('')
    setRefundOpen(true)
  }

  const submitRefund = async () => {
    if (!refundReason.trim()) {
      Taro.showToast({ title: '请填写退款原因', icon: 'none' })
      return
    }
    setRefunding(true)
    try {
      await orderApi.refund(id, refundReason.trim())
      setRefundOpen(false)
      Taro.showToast({ title: '退款申请已提交', icon: 'success' })
      loadOrder()
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '退款申请失败'
      Taro.showToast({ title: message, icon: 'none' })
    } finally {
      setRefunding(false)
    }
  }

  // ==================== T11/P0-2：申请售后 ====================

  /** 打开弹窗并重置表单；已发货默认退货退款，待发货仅退款（未发货无货可退） */
  const openAfterSale = () => {
    setAfterSaleType(order?.status === 2 ? 'RETURN_REFUND' : 'REFUND_ONLY')
    setAfterSaleItemId(null)
    setAfterSaleQuantity(1)
    setAfterSaleReason('')
    setAfterSaleFileIds([])
    setAfterSaleOpen(true)
  }

  const selectAfterSaleType = (type: 'REFUND_ONLY' | 'RETURN_REFUND') => {
    setAfterSaleType(type)
    // 已发货整单退货退款后端首期不开放（需按商品项拆分）
    if (type === 'RETURN_REFUND' && afterSaleItemId == null) {
      const firstItem = order?.items?.[0]
      if (firstItem) {
        setAfterSaleItemId(firstItem.id)
        setAfterSaleQuantity(firstItem.quantity)
      }
    }
  }

  const selectAfterSaleItem = (itemId: number | null) => {
    setAfterSaleItemId(itemId)
    if (itemId != null) {
      const item = order?.items?.find((it) => it.id === itemId)
      setAfterSaleQuantity(item?.quantity ?? 1)
    }
  }

  const chooseAttachment = () => {
    if (afterSaleFileIds.length >= 3) {
      Taro.showToast({ title: '最多上传 3 张凭证', icon: 'none' })
      return
    }
    Taro.chooseImage({
      count: 3 - afterSaleFileIds.length,
      sizeType: ['compressed'],
      success: async (res) => {
        setAfterSaleUploading(true)
        try {
          const uploaded: string[] = []
          for (const filePath of res.tempFilePaths) {
            const uploadRes = await fileApi.uploadAsset(filePath, 'PRIVATE')
            const fileId = (uploadRes.data?.data as unknown as { fileId?: string })?.fileId
            if (fileId) uploaded.push(fileId)
          }
          setAfterSaleFileIds((prev) => [...prev, ...uploaded])
        } catch {
          Taro.showToast({ title: '凭证上传失败', icon: 'none' })
        } finally {
          setAfterSaleUploading(false)
        }
      },
    })
  }

  const submitAfterSale = async () => {
    if (!order) return
    if (!afterSaleReason.trim()) {
      Taro.showToast({ title: '请填写售后原因', icon: 'none' })
      return
    }
    // 已发货整单退货退款后端不开放，前端先行拦截
    if (afterSaleType === 'RETURN_REFUND' && afterSaleItemId == null) {
      Taro.showToast({ title: '已发货订单请按商品项申请退货退款', icon: 'none' })
      return
    }
    setAfterSaleSubmitting(true)
    try {
      await applyAfterSale(order.id, {
        type: afterSaleType,
        reason: afterSaleReason.trim(),
        itemId: afterSaleItemId ?? undefined,
        quantity: afterSaleItemId != null ? afterSaleQuantity : undefined,
        attachmentFileIds: afterSaleFileIds.length > 0 ? JSON.stringify(afterSaleFileIds) : undefined,
      })
      setAfterSaleOpen(false)
      Taro.showToast({ title: '售后申请已提交', icon: 'success' })
      loadAfterSaleCases()
      loadOrder()
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '售后申请失败'
      Taro.showToast({ title: message, icon: 'none' })
    } finally {
      setAfterSaleSubmitting(false)
    }
  }

  /** 状态进度条（对齐 Web 端：提交订单→支付成功→已发货→已完成） */
  const progressSteps = [
    { key: 'created', label: '提交订单', done: true },
    { key: 'paid', label: '支付成功', done: !!order?.paidAt },
    { key: 'shipped', label: '已发货', done: !!order?.shippedAt },
    { key: 'completed', label: '已完成', done: !!order?.receivedAt },
  ]

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

  if (!order) {
    return (
      <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>📋</Text>
          <Text className={styles.emptyText}>订单不存在</Text>
        </View>
      </View>
    )
  }

  const statusConfig = STATUS_CONFIG[order.status] || { icon: '❓', text: '未知' }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {/* 状态区域 */}
        <View className={styles.statusSection}>
          <Text className={styles.statusIcon}>{statusConfig.icon}</Text>
          <Text className={styles.statusText}>{statusConfig.text}</Text>
        </View>

        {/* 状态进度条（对齐 Web 端订单进度） */}
        {order.status !== 4 && (
          <View className={styles.section}>
            <View className={styles.progressRow}>
              {progressSteps.map((step, i) => (
                <View key={step.key} className={styles.progressStep}>
                  <View className={`${styles.progressDot} ${step.done ? styles.progressDotDone : ''}`}>
                    <Text className={styles.progressDotText}>{step.done ? '✓' : i + 1}</Text>
                  </View>
                  <Text className={`${styles.progressLabel} ${step.done ? styles.progressLabelDone : ''}`}>{step.label}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* 收货地址 */}
        <View className={styles.section}>
          <View className={styles.addressHeader}>
            <Text className={styles.addressIcon}>📍</Text>
            <Text className={styles.sectionTitle}>收货信息</Text>
          </View>
          <View className={styles.addressBody}>
            <View className={styles.addressRow}>
              <Text className={styles.recipientName}>{order.recipientName}</Text>
              <Text className={styles.recipientPhone}>{order.recipientPhone}</Text>
            </View>
            <Text className={styles.addressDetail}>{order.recipientAddress}</Text>
          </View>
        </View>

        {/* 商品列表 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>商品信息</Text>
          </View>
          {order.items.map((item) => (
            <View key={item.id} className={styles.orderItem}>
              <Image className={styles.itemImage} src={item.productImage} mode='aspectFill' />
              <View className={styles.itemInfo}>
                <Text className={styles.itemName}>{item.productName}</Text>
                <Text className={styles.itemSku}>{item.skuName}</Text>
                <View className={styles.itemBottom}>
                  <Text className={styles.itemPrice}>¥{item.price}</Text>
                  <Text className={styles.itemQuantity}>x{item.quantity}</Text>
                </View>
              </View>
            </View>
          ))}
        </View>

        {/* 价格明细 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>价格明细</Text>
          </View>
          <View className={styles.priceRow}>
            <Text className={styles.priceLabel}>商品合计</Text>
            <Text className={styles.priceValue}>¥{order.totalAmount}</Text>
          </View>
          <View className={styles.priceRow}>
            <Text className={styles.priceLabel}>运费</Text>
            <Text className={styles.priceValue}>{order.shippingFee > 0 ? `¥${order.shippingFee}` : '免运费'}</Text>
          </View>
          <View className={styles.priceDivider} />
          <View className={styles.priceRow}>
            <Text className={styles.priceLabelBold}>实付金额</Text>
            <Text className={styles.priceTotal}>¥{order.payAmount}</Text>
          </View>
          {order.refundedAmount != null && order.refundedAmount > 0 && (
            <View className={styles.priceRow}>
              <Text className={styles.priceLabel}>
                已退金额{order.refundStatus === 'PARTIAL' ? '（部分退款）' : '（全额退款）'}
              </Text>
              <Text className={styles.priceValue}>¥{order.refundedAmount}</Text>
            </View>
          )}
        </View>

        {/* 售后案件（T11：本单已有申请与进度） */}
        {afterSaleCases.length > 0 && (
          <View className={styles.section}>
            <View className={styles.sectionHeader}>
              <Text className={styles.sectionTitle}>售后进度</Text>
              <Text className={styles.afterSaleAllLink} onClick={() => Taro.navigateTo({ url: '/pages/afterSale/index' })}>
                我的售后 ›
              </Text>
            </View>
            {afterSaleCases.map((c) => (
              <View
                key={c.id}
                className={styles.afterSaleCaseRow}
                onClick={() => Taro.navigateTo({ url: `/pages/afterSaleDetail/index?id=${c.id}` })}
              >
                <View className={styles.afterSaleCaseInfo}>
                  <Text className={styles.afterSaleCaseType}>
                    {AFTER_SALE_TYPE_TEXT[c.type] || c.type}{c.quantity > 0 ? ` · ${c.quantity} 件` : ''}
                  </Text>
                  <Text className={styles.afterSaleCaseReason}>{c.reason}</Text>
                </View>
                <Text className={styles.afterSaleCaseStatus}>{AFTER_SALE_STATUS_TEXT[c.status] || c.status}</Text>
              </View>
            ))}
          </View>
        )}

        {/* 订单信息 */}
        <View className={styles.section}>
          <View className={styles.sectionHeader}>
            <Text className={styles.sectionTitle}>订单信息</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>订单号</Text>
            <Text className={styles.infoValue}>{order.orderNo}</Text>
          </View>
          <View className={styles.infoRow}>
            <Text className={styles.infoLabel}>下单时间</Text>
            <Text className={styles.infoValue}>{formatDateTime(order.createdAt)}</Text>
          </View>
          {order.paidAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>支付时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.paidAt)}</Text>
            </View>
          )}
          {order.shippedAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>发货时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.shippedAt)}</Text>
            </View>
          )}
          {order.receivedAt && (
            <View className={styles.infoRow}>
              <Text className={styles.infoLabel}>收货时间</Text>
              <Text className={styles.infoValue}>{formatDateTime(order.receivedAt)}</Text>
            </View>
          )}
        </View>
      </ScrollView>

      {/* 底部操作栏 */}
      {(order.status === 0 || order.status === 1 || order.status === 2 || order.status === 3) && (
        <View className={styles.bottomBar}>
          {order.status === 0 && (
            <>
              <View className={styles.btnSecondary} onClick={handleCancel}>
                <Text className={styles.btnSecondaryText}>取消订单</Text>
              </View>
              <View className={styles.btnPrimary} onClick={handlePay}>
                <Text className={styles.btnPrimaryText}>去支付</Text>
              </View>
            </>
          )}
          {(order.status === 1) && (
            <View className={styles.btnSecondary} onClick={handleRefund}>
              <Text className={styles.btnSecondaryText}>申请退款</Text>
            </View>
          )}
          {(order.status === 1 || order.status === 2) && (
            <View className={styles.btnSecondary} onClick={openAfterSale}>
              <Text className={styles.btnSecondaryText}>申请售后</Text>
            </View>
          )}
          {order.status === 2 && (
            <View className={styles.btnPrimary} onClick={handleConfirm}>
              <Text className={styles.btnPrimaryText}>确认收货</Text>
            </View>
          )}
          {order.status === 3 && (
            <View className={styles.btnPrimary} onClick={handleRebuy}>
              <Text className={styles.btnPrimaryText}>再次购买</Text>
            </View>
          )}
        </View>
      )}
      {/* 申请退款弹窗（对齐 Web 端必填原因） */}
      {refundOpen && (
        <View className={styles.refundMask} onClick={() => !refunding && setRefundOpen(false)}>
          <View className={styles.refundModal} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.refundTitle}>申请退款</Text>
            <Text className={styles.refundHint}>说明退款原因，提交后由平台审核处理</Text>
            <Textarea
              className={styles.refundTextarea}
              value={refundReason}
              maxlength={200}
              placeholder='请填写退款原因（必填）'
              onInput={(e) => setRefundReason(e.detail.value)}
            />
            <View className={styles.refundActions}>
              <View className={styles.refundCancel} onClick={() => setRefundOpen(false)}>
                <Text className={styles.refundCancelText}>取消</Text>
              </View>
              <View className={styles.refundSubmit} onClick={refunding ? undefined : submitRefund}>
                <Text className={styles.refundSubmitText}>{refunding ? '提交中...' : '提交申请'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
      {/* 申请售后弹窗（T11：类型/商品项/数量/原因/凭证，规则对齐后端） */}
      {afterSaleOpen && (
        <View className={styles.refundMask} onClick={() => !afterSaleSubmitting && setAfterSaleOpen(false)}>
          <ScrollView scrollY className={styles.afterSaleModalScroll}>
            <View className={styles.refundModal} onClick={(e) => e.stopPropagation()}>
              <Text className={styles.refundTitle}>申请售后</Text>
              <Text className={styles.refundHint}>提交后由平台审核受理，进度可在「我的售后」查看</Text>

              <Text className={styles.afterSaleFieldLabel}>售后类型</Text>
              <View className={styles.afterSaleChips}>
                <View
                  className={`${styles.afterSaleChip} ${afterSaleType === 'REFUND_ONLY' ? styles.afterSaleChipActive : ''}`}
                  onClick={() => selectAfterSaleType('REFUND_ONLY')}
                >
                  <Text className={afterSaleType === 'REFUND_ONLY' ? styles.afterSaleChipTextActive : styles.afterSaleChipText}>仅退款</Text>
                </View>
                {order.status === 2 && (
                  <View
                    className={`${styles.afterSaleChip} ${afterSaleType === 'RETURN_REFUND' ? styles.afterSaleChipActive : ''}`}
                    onClick={() => selectAfterSaleType('RETURN_REFUND')}
                  >
                    <Text className={afterSaleType === 'RETURN_REFUND' ? styles.afterSaleChipTextActive : styles.afterSaleChipText}>退货退款</Text>
                  </View>
                )}
              </View>

              <Text className={styles.afterSaleFieldLabel}>售后商品</Text>
              <View className={styles.afterSaleChips}>
                <View
                  className={`${styles.afterSaleChip} ${afterSaleItemId === null ? styles.afterSaleChipActive : ''} ${afterSaleType === 'RETURN_REFUND' ? styles.afterSaleChipDisabled : ''}`}
                  onClick={() => afterSaleType === 'REFUND_ONLY' && selectAfterSaleItem(null)}
                >
                  <Text className={afterSaleItemId === null ? styles.afterSaleChipTextActive : styles.afterSaleChipText}>整单</Text>
                </View>
                {order.items.map((item) => (
                  <View
                    key={item.id}
                    className={`${styles.afterSaleChip} ${afterSaleItemId === item.id ? styles.afterSaleChipActive : ''}`}
                    onClick={() => selectAfterSaleItem(item.id)}
                  >
                    <Text className={afterSaleItemId === item.id ? styles.afterSaleChipTextActive : styles.afterSaleChipText}>
                      {item.productName.length > 8 ? `${item.productName.slice(0, 8)}…` : item.productName}
                    </Text>
                  </View>
                ))}
              </View>

              {afterSaleItemId != null && (
                <View className={styles.afterSaleQuantityRow}>
                  <Text className={styles.afterSaleFieldLabel}>售后数量</Text>
                  <View className={styles.quantityStepper}>
                    <View
                      className={styles.quantityBtn}
                      onClick={() => setAfterSaleQuantity((q) => Math.max(1, q - 1))}
                    >
                      <Text className={styles.quantityBtnText}>−</Text>
                    </View>
                    <Text className={styles.quantityValue}>{afterSaleQuantity}</Text>
                    <View
                      className={styles.quantityBtn}
                      onClick={() => {
                        const item = order.items.find((it) => it.id === afterSaleItemId)
                        setAfterSaleQuantity((q) => Math.min(item?.quantity ?? 1, q + 1))
                      }}
                    >
                      <Text className={styles.quantityBtnText}>＋</Text>
                    </View>
                  </View>
                </View>
              )}

              <Text className={styles.afterSaleFieldLabel}>售后原因（必填）</Text>
              <Textarea
                className={styles.refundTextarea}
                value={afterSaleReason}
                maxlength={200}
                placeholder='请描述售后原因'
                onInput={(e) => setAfterSaleReason(e.detail.value)}
              />

              <Text className={styles.afterSaleFieldLabel}>凭证图片（选填，最多 3 张）</Text>
              <View className={styles.attachmentPickerRow}>
                {afterSaleFileIds.length > 0 && (
                  <Text className={styles.attachmentCountText}>已上传 {afterSaleFileIds.length} 张</Text>
                )}
                <View
                  className={`${styles.attachmentPickerBtn} ${afterSaleUploading ? styles.attachmentPickerBtnUploading : ''}`}
                  onClick={() => !afterSaleUploading && chooseAttachment()}
                >
                  <Text className={styles.attachmentPickerText}>{afterSaleUploading ? '上传中...' : '＋ 选择图片'}</Text>
                </View>
              </View>

              <View className={styles.refundActions}>
                <View className={styles.refundCancel} onClick={() => setAfterSaleOpen(false)}>
                  <Text className={styles.refundCancelText}>取消</Text>
                </View>
                <View className={styles.refundSubmit} onClick={afterSaleSubmitting ? undefined : submitAfterSale}>
                  <Text className={styles.refundSubmitText}>{afterSaleSubmitting ? '提交中...' : '提交申请'}</Text>
                </View>
              </View>
            </View>
          </ScrollView>
        </View>
      )}
    </View>
  )
}
