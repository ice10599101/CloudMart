import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { orderApi } from '@/api/order'
import { shippingApi, type OrderShipping, type OrderTracking } from '@/api/shipping'
import { productApi, type OwnReview } from '@/api/product'
import {
  applyAfterSale,
  listOrderAfterSales,
  AFTER_SALE_STATUS_TEXT,
  AFTER_SALE_TYPE_TEXT,
  type AfterSaleCase,
} from '@/api/afterSale'
import { fileApi } from '@/api/file'
import { errorMessage } from '@/utils/request'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { OrderStatus, OrderAction } from '@/types'
import styles from './index.module.scss'

/** T05：WMS 物流状态语义（与后端 PENDING/SHIPPED/DELIVERED 对齐） */
const SHIPPING_STATUS_TEXT: Record<string, string> = {
  PENDING: '待出库',
  SHIPPED: '已出库',
  DELIVERED: '已签收',
}

interface OrderItem {
  id: number
  productId: number
  /** R08 评价提交需与订单行一致的 skuId（后端 OrderItemVO 实际返回） */
  skuId: number
  productName: string
  productImage: string
  skuName: string
  price: number
  quantity: number
}

interface OrderDetail {
  id: number
  orderNo: string
  /** T07：语义状态 + 服务端 allowedActions */
  status: OrderStatus
  allowedActions?: OrderAction[]
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

const STATUS_CONFIG: Record<string, { icon: string; text: string }> = {
  PENDING_PAYMENT: { icon: '💰', text: '待付款' },
  PAID: { icon: '📦', text: '已付款' },
  SHIPPED: { icon: '🚚', text: '已发货' },
  COMPLETED: { icon: '✅', text: '已完成' },
  CANCELLED: { icon: '❌', text: '已取消' },
  REFUNDING: { icon: '↩️', text: '退款中' },
  REFUNDED: { icon: '💸', text: '已退款' },
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
  // T05：物流事实（待出库/已发货/轨迹）——数据来自 WMS
  const [shipping, setShipping] = useState<OrderShipping | null>(null)
  const [trackings, setTrackings] = useState<OrderTracking[]>([])
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

  // R08：商品评价（仅已完成订单；按订单行查本人评价，决定“评价/查看评价”入口与弹层形态）
  const [reviewMap, setReviewMap] = useState<Record<number, OwnReview | null>>({})
  const [reviewOpen, setReviewOpen] = useState(false)
  const [reviewItem, setReviewItem] = useState<OrderItem | null>(null)
  const [reviewLoading, setReviewLoading] = useState(false)
  const [existingReview, setExistingReview] = useState<OwnReview | null>(null)
  const [reviewRating, setReviewRating] = useState(5)
  const [reviewContent, setReviewContent] = useState('')
  const [reviewSubmitting, setReviewSubmitting] = useState(false)

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
      const detail = res.data?.data as unknown as OrderDetail
      setOrder(detail)
      // T05：物流信息随订单加载；未建包裹（404）按“待出库”展示
      if (detail && ['PAID', 'SHIPPED', 'COMPLETED'].includes(detail.status)) {
        loadShipping(detail.id)
      }
      // R08：已完成订单预取各订单行评价，用于“评价/查看评价”入口；失败 fail-open，默认显示“评价”
      if (detail?.status === 'COMPLETED' && detail.items?.length > 0) {
        loadOwnReviews(detail.items)
      }
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  /** T05：物流信息（WMS 权威事实）；未建包裹/查询失败按“待出库”处理，不阻塞详情 */
  const loadShipping = async (orderId: number | string) => {
    try {
      const res = await shippingApi.getByOrder(orderId)
      const shippingData = (res.data?.data as unknown as OrderShipping) ?? null
      setShipping(shippingData)
      if (shippingData) {
        const trackRes = await shippingApi.getTrackings(orderId)
        setTrackings((trackRes.data?.data as unknown as OrderTracking[]) ?? [])
      } else {
        setTrackings([])
      }
    } catch {
      setShipping(null)
      setTrackings([])
    }
  }

  /** R08：并发拉取已完成订单各订单行的本人评价（单项失败按未评价处理，不阻塞页面） */  const loadOwnReviews = async (items: OrderItem[]) => {
    const entries = await Promise.all(
      items.map(async (item) => {
        try {
          const res = await productApi.getOwnReviewByOrderItem(item.id)
          return [item.id, res.data.data ?? null] as const
        } catch {
          return [item.id, null] as const
        }
      }),
    )
    setReviewMap(Object.fromEntries(entries))
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
          } catch (err) {
            Taro.showToast({ title: errorMessage(err, '取消失败'), icon: 'none' })
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
          } catch (err) {
            Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' })
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
      Taro.showToast({ title: errorMessage(err, '退款申请失败'), icon: 'none' })
    } finally {
      setRefunding(false)
    }
  }

  // ==================== T11/P0-2：申请售后 ====================

  /** 打开弹窗并重置表单；已发货默认退货退款，待发货仅退款（未发货无货可退） */
  const openAfterSale = () => {
    setAfterSaleType(order?.status === 'SHIPPED' ? 'RETURN_REFUND' : 'REFUND_ONLY')
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
        } catch (err) {
          Taro.showToast({ title: errorMessage(err, '凭证上传失败'), icon: 'none' })
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
      Taro.showToast({ title: errorMessage(err, '售后申请失败'), icon: 'none' })
    } finally {
      setAfterSaleSubmitting(false)
    }
  }

  // ==================== R08：商品评价 ====================

  /** 点击入口：先查该订单行是否已有评价，决定弹层为只读态还是发表态 */
  const openReview = async (item: OrderItem) => {
    setReviewItem(item)
    setExistingReview(null)
    setReviewRating(5)
    setReviewContent('')
    setReviewOpen(true)
    setReviewLoading(true)
    try {
      const res = await productApi.getOwnReviewByOrderItem(item.id)
      setExistingReview(res.data.data ?? null)
      setReviewMap((prev) => ({ ...prev, [item.id]: res.data.data ?? null }))
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '评价查询失败'), icon: 'none' })
      setReviewOpen(false)
      setReviewItem(null)
    } finally {
      setReviewLoading(false)
    }
  }

  const closeReview = () => {
    if (reviewSubmitting) return
    setReviewOpen(false)
    setReviewItem(null)
  }

  const submitReview = async () => {
    if (!order || !reviewItem) return
    if (!reviewContent.trim()) {
      Taro.showToast({ title: '请填写评价内容', icon: 'none' })
      return
    }
    setReviewSubmitting(true)
    try {
      const res = await productApi.createReview({
        orderItemId: reviewItem.id,
        orderId: order.id,
        productId: reviewItem.productId,
        skuId: reviewItem.skuId,
        rating: reviewRating,
        content: reviewContent.trim(),
      })
      const created = res.data.data
      setReviewMap((prev) => ({
        ...prev,
        [reviewItem.id]: created
          ? {
              id: created.id,
              rating: created.rating,
              content: created.content,
              images: created.images ?? null,
              status: 1,
              hideReason: null,
              createdAt: created.createdAt,
            }
          : (prev[reviewItem.id] ?? null),
      }))
      Taro.showToast({ title: '评价成功', icon: 'success' })
      setReviewOpen(false)
      setReviewItem(null)
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '评价提交失败'), icon: 'none' })
    } finally {
      setReviewSubmitting(false)
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
        {order.status !== 'CANCELLED' && (
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

        {/* T05：物流信息（WMS 权威事实） */}
        {['PAID', 'SHIPPED', 'COMPLETED'].includes(order.status) && (
          <View className={styles.section}>
            <View className={styles.sectionHeader}>
              <Text className={styles.sectionTitle}>物流信息</Text>
            </View>
            {shipping ? (
              <View>
                <View style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12rpx' }}>
                  <Text style={{ fontSize: '26rpx', color: '#8B9DC3' }}>承运商</Text>
                  <Text style={{ fontSize: '26rpx' }}>{shipping.carrier || '-'}</Text>
                </View>
                <View style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12rpx' }}>
                  <Text style={{ fontSize: '26rpx', color: '#8B9DC3' }}>运单号</Text>
                  <Text style={{ fontSize: '26rpx' }}>{shipping.trackingNo || '-'}</Text>
                </View>
                <View style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12rpx' }}>
                  <Text style={{ fontSize: '26rpx', color: '#8B9DC3' }}>状态</Text>
                  <Text style={{ fontSize: '26rpx' }}>
                    {SHIPPING_STATUS_TEXT[shipping.status] ?? shipping.status}
                  </Text>
                </View>
                {trackings.map((tracking) => (
                  <View key={String(tracking.id)} style={{ display: 'flex', marginTop: '12rpx' }}>
                    <Text style={{ width: '220rpx', fontSize: '22rpx', color: '#8B9DC3' }}>
                      {tracking.happenedAt || ''}
                    </Text>
                    <Text style={{ flex: 1, fontSize: '24rpx', color: '#C8D3E8' }}>
                      {[tracking.location, tracking.description].filter(Boolean).join(' · ') || '-'}
                    </Text>
                  </View>
                ))}
              </View>
            ) : (
              <Text style={{ fontSize: '26rpx', color: '#8B9DC3' }}>待出库（商家尚未创建包裹）</Text>
            )}
          </View>
        )}

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
                  <View className={styles.itemBottomRight}>
                    <Text className={styles.itemQuantity}>x{item.quantity}</Text>
                    {order.status === 'COMPLETED' && (
                      <View className={styles.itemReviewBtn} onClick={() => openReview(item)}>
                        <Text className={styles.itemReviewText}>{reviewMap[item.id] ? '查看评价' : '评价'}</Text>
                      </View>
                    )}
                  </View>
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
      {['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED'].includes(order.status) && (
        <View className={styles.bottomBar}>
          {(order.allowedActions ?? []).includes('CANCEL') && (
            <>
              <View className={styles.btnSecondary} onClick={handleCancel}>
                <Text className={styles.btnSecondaryText}>取消订单</Text>
              </View>
              <View className={styles.btnPrimary} onClick={handlePay}>
                <Text className={styles.btnPrimaryText}>去支付</Text>
              </View>
            </>
          )}
          {order.status === 'PAID' && (
            <View className={styles.btnSecondary} onClick={handleRefund}>
              <Text className={styles.btnSecondaryText}>申请退款</Text>
            </View>
          )}
          {(order.status === 'PAID' || order.status === 'SHIPPED') && (
            <View className={styles.btnSecondary} onClick={openAfterSale}>
              <Text className={styles.btnSecondaryText}>申请售后</Text>
            </View>
          )}
          {(order.allowedActions ?? []).includes('CONFIRM_RECEIPT') && (
            <View className={styles.btnPrimary} onClick={handleConfirm}>
              <Text className={styles.btnPrimaryText}>确认收货</Text>
            </View>
          )}
          {order.status === 'COMPLETED' && (
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
                {(order.allowedActions ?? []).includes('CONFIRM_RECEIPT') && (
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
      {/* R08：评价弹层——loading / 只读查看（含下架理由）/ 发表 三态 */}
      {reviewOpen && reviewItem && (
        <View className={styles.refundMask} onClick={closeReview}>
          <View className={styles.refundModal} onClick={(e) => e.stopPropagation()}>
            {reviewLoading ? (
              <>
                <Text className={styles.refundTitle}>评价加载中</Text>
                <Text className={styles.refundHint}>正在获取评价信息…</Text>
              </>
            ) : existingReview ? (
              <>
                <Text className={styles.refundTitle}>我的评价</Text>
                <Text className={styles.refundHint}>{reviewItem.productName}</Text>
                <View className={styles.reviewStars}>
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Text
                      key={star}
                      className={star <= existingReview.rating ? styles.reviewStarActive : styles.reviewStar}
                    >
                      {star <= existingReview.rating ? '★' : '☆'}
                    </Text>
                  ))}
                  <Text className={styles.reviewRatingLabel}>评分：{existingReview.rating}星</Text>
                </View>
                <Text className={styles.reviewContentText}>{existingReview.content}</Text>
                {existingReview.status === 0 && (
                  <Text className={styles.reviewHideReason}>已被下架：{existingReview.hideReason || '暂无理由'}</Text>
                )}
                <Text className={styles.reviewTimeText}>{formatDateTime(existingReview.createdAt)}</Text>
                <View className={styles.refundActions}>
                  <View className={styles.refundSubmit} onClick={closeReview}>
                    <Text className={styles.refundSubmitText}>关闭</Text>
                  </View>
                </View>
              </>
            ) : (
              <>
                <Text className={styles.refundTitle}>评价商品</Text>
                <Text className={styles.refundHint}>{reviewItem.productName}</Text>
                <View className={styles.reviewStars}>
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Text
                      key={star}
                      className={star <= reviewRating ? styles.reviewStarActive : styles.reviewStar}
                      onClick={() => setReviewRating(star)}
                    >
                      {star <= reviewRating ? '★' : '☆'}
                    </Text>
                  ))}
                  <Text className={styles.reviewRatingLabel}>评分：{reviewRating}星</Text>
                </View>
                <Textarea
                  className={styles.refundTextarea}
                  value={reviewContent}
                  maxlength={500}
                  placeholder='分享你的使用感受吧（必填）'
                  onInput={(e) => setReviewContent(e.detail.value)}
                />
                <View className={styles.refundActions}>
                  <View className={styles.refundCancel} onClick={closeReview}>
                    <Text className={styles.refundCancelText}>取消</Text>
                  </View>
                  <View className={styles.refundSubmit} onClick={reviewSubmitting ? undefined : submitReview}>
                    <Text className={styles.refundSubmitText}>{reviewSubmitting ? '提交中...' : '提交评价'}</Text>
                  </View>
                </View>
              </>
            )}
          </View>
        </View>
      )}
    </View>
  )
}
