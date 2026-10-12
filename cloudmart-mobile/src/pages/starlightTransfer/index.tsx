import { useCallback, useEffect, useState } from 'react'
import { View, Text, ScrollView, Input, Textarea, Button } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { transferApi } from '@/api/wish'
import type { StarlightTransferItem, StarlightTransferLimits } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { errorMessage } from '@/utils/request'
import { generateAttachmentId } from '@/utils/postAttachments'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20
const MIN_AMOUNT = 10
const MAX_AMOUNT = 100
const MAX_MESSAGE_LENGTH = 100

const DIRECTION_META: Record<StarlightTransferItem['direction'], { label: string; color: string }> = {
  SENT: { label: '转出', color: '#ff6b6b' },
  RECEIVED: { label: '转入', color: '#52c41a' },
}

/**
 * R19 星光转赠：当日额度展示 + 转赠表单（10-100 整数、附言 ≤100、requestKey 幂等）
 * + 转赠记录 page/pageSize 分页（触底加载更多）。
 */
export default function StarlightTransferPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [limits, setLimits] = useState<StarlightTransferLimits | null>(null)
  const [records, setRecords] = useState<StarlightTransferItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const [toUserIdInput, setToUserIdInput] = useState('')
  const [amountInput, setAmountInput] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const hasMore = records.length < total

  const fetchPage = useCallback(
    async (targetPage: number, append: boolean) => {
      try {
        const res = await transferApi.getMyTransfers(targetPage, PAGE_SIZE)
        const pageData = res.data.data
        setRecords((prev) => (append ? [...prev, ...pageData.records] : pageData.records))
        setTotal(pageData.total)
        setPage(targetPage)
      } catch {
        // 记录加载失败保持已有列表，静默处理
      }
    },
    [],
  )

  const loadInitial = useCallback(async () => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const [limitsRes] = await Promise.all([
        transferApi.getTransferLimits().catch(() => null),
        fetchPage(1, false),
      ])
      if (limitsRes) setLimits(limitsRes.data.data)
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn, fetchPage])

  useEffect(() => {
    loadInitial()
  }, [loadInitial])

  const refreshLimits = async () => {
    try {
      const res = await transferApi.getTransferLimits()
      setLimits(res.data.data)
    } catch {
      // 额度刷新失败不阻断主流程
    }
  }

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore || loading) return
    setLoadingMore(true)
    try {
      await fetchPage(page + 1, true)
    } finally {
      setLoadingMore(false)
    }
  }

  const handleSubmit = async () => {
    if (submitting) return
    const toUserId = Number(toUserIdInput.trim())
    const amount = Number(amountInput.trim())
    const trimmedMessage = message.trim()

    if (!toUserIdInput.trim() || !Number.isInteger(toUserId) || toUserId <= 0) {
      Taro.showToast({ title: '请输入正确的对方用户ID', icon: 'none' })
      return
    }
    if (!amountInput.trim() || !Number.isInteger(amount) || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
      Taro.showToast({ title: `转赠数量须为 ${MIN_AMOUNT}-${MAX_AMOUNT} 的整数`, icon: 'none' })
      return
    }
    if (trimmedMessage.length > MAX_MESSAGE_LENGTH) {
      Taro.showToast({ title: `附言不能超过 ${MAX_MESSAGE_LENGTH} 字`, icon: 'none' })
      return
    }
    if (limits && amount > limits.remainingToday) {
      Taro.showToast({ title: '超出今日剩余转赠额度', icon: 'none' })
      return
    }

    setSubmitting(true)
    try {
      // 幂等键沿用项目惯例：generateAttachmentId() 去横线
      const requestKey = generateAttachmentId().replace(/-/g, '')
      const res = await transferApi.createTransfer({
        toUserId,
        amount,
        message: trimmedMessage || undefined,
        requestKey,
      })
      Taro.showToast({ title: '转赠成功', icon: 'success' })
      setToUserIdInput('')
      setAmountInput('')
      setMessage('')
      const created = res.data.data
      if (typeof created.remainingToday === 'number' && limits) {
        setLimits({ ...limits, usedToday: limits.dailyCap - created.remainingToday, remainingToday: created.remainingToday })
      } else {
        refreshLimits()
      }
      await fetchPage(1, false)
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '转赠失败'), icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title='星光转赠' back />
      <ScrollView className={styles.scroll} scrollY onScrollToLower={handleLoadMore}>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : (
          <>
            <View className={styles.limitCard}>
              <View className={styles.limitRow}>
                <View className={styles.limitItem}>
                  <Text className={styles.limitValue}>{limits?.usedToday ?? '-'}</Text>
                  <Text className={styles.limitLabel}>今日已用</Text>
                </View>
                <View className={styles.limitItem}>
                  <Text className={styles.limitValue}>{limits?.dailyCap ?? '-'}</Text>
                  <Text className={styles.limitLabel}>每日上限</Text>
                </View>
                <View className={styles.limitItem}>
                  <Text className={styles.limitValueHighlight}>{limits?.remainingToday ?? '-'}</Text>
                  <Text className={styles.limitLabel}>今日剩余</Text>
                </View>
              </View>
              {limits && <Text className={styles.businessDate}>业务日期 {limits.businessDate}</Text>}
            </View>

            <View className={styles.formCard}>
              <View className={styles.field}>
                <Text className={styles.fieldLabel}>对方用户ID</Text>
                <Input
                  className={styles.input}
                  type='number'
                  placeholder='请输入对方用户ID'
                  placeholderClass={styles.placeholder}
                  value={toUserIdInput}
                  onInput={(e) => setToUserIdInput(e.detail.value)}
                />
              </View>
              <View className={styles.field}>
                <Text className={styles.fieldLabel}>转赠数量</Text>
                <Input
                  className={styles.input}
                  type='number'
                  placeholder={`单次 ${MIN_AMOUNT}-${MAX_AMOUNT} 的整数星光`}
                  placeholderClass={styles.placeholder}
                  value={amountInput}
                  onInput={(e) => setAmountInput(e.detail.value)}
                />
              </View>
              <View className={styles.field}>
                <Text className={styles.fieldLabel}>附言</Text>
                <Textarea
                  className={styles.textarea}
                  placeholder='捎句话给对方（选填，100字以内）'
                  placeholderClass={styles.placeholder}
                  maxlength={MAX_MESSAGE_LENGTH}
                  value={message}
                  onInput={(e) => setMessage(e.detail.value)}
                />
                <Text className={styles.messageCount}>{message.length}/{MAX_MESSAGE_LENGTH}</Text>
              </View>
              <Button
                className={styles.submitBtn}
                loading={submitting}
                disabled={submitting}
                onClick={handleSubmit}
              >
                {submitting ? '提交中...' : '确认转赠'}
              </Button>
            </View>

            <Text className={styles.sectionTitle}>转赠记录</Text>
            {loading ? (
              <View className={styles.empty}><Text>加载中...</Text></View>
            ) : records.length === 0 ? (
              <View className={styles.empty}><Text>暂无转赠记录</Text></View>
            ) : (
              records.map((record) => {
                const meta = DIRECTION_META[record.direction]
                const counterpartId = record.direction === 'SENT' ? record.toUserId : record.fromUserId
                return (
                  <View key={String(record.id)} className={styles.recordCard}>
                    <View className={styles.recordLeft}>
                      <View className={styles.recordHead}>
                        <Text className={styles.directionTag} style={{ color: meta.color, borderColor: meta.color }}>
                          {meta.label}
                        </Text>
                        <Text className={styles.counterpart}>对方用户ID：{counterpartId}</Text>
                      </View>
                      {record.message && <Text className={styles.recordMessage}>{record.message}</Text>}
                      <Text className={styles.recordTime}>
                        {new Date(record.createdAt).toLocaleString('zh-CN')}
                      </Text>
                    </View>
                    <Text className={styles.recordAmount} style={{ color: meta.color }}>
                      {record.direction === 'SENT' ? '-' : '+'}{record.amount}
                    </Text>
                  </View>
                )
              })
            )}
            {loadingMore && <View className={styles.empty}><Text>加载更多...</Text></View>}
            {!loading && !loadingMore && records.length > 0 && (
              <View className={styles.listFooter}>
                <Text>{hasMore ? '上拉加载更多' : '没有更多了'}</Text>
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  )
}
