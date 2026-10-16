import { useState, useEffect, useCallback } from 'react'
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  ActivityIndicator,
  RefreshControl,
} from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { StarlightTransferLimits, StarlightTransferRecord } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { errorMessage } from '@/utils/request'
import { generateAttachmentId } from '@/utils/postAttachments'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const PAGE_SIZE = 20
const MIN_AMOUNT = 10
const MAX_AMOUNT = 100
const MAX_MESSAGE_LENGTH = 100

const DIRECTION_LABELS: Record<StarlightTransferRecord['direction'], string> = {
  SENT: '转出',
  RECEIVED: '收到',
}

const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  PENDING: '处理中',
  FAILED: '失败',
}

export default function StarlightTransferScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)

  const [limits, setLimits] = useState<StarlightTransferLimits | null>(null)
  const [records, setRecords] = useState<StarlightTransferRecord[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  const [toUserIdText, setToUserIdText] = useState('')
  const [amountText, setAmountText] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  const loadLimits = useCallback(async () => {
    try {
      const res = await wishApi.getTransferLimits()
      setLimits(res.data.data)
    } catch {
      // 额度加载失败保留上次数据，表单提交时服务端仍会权威校验
      setLimits(null)
    }
  }, [])

  const loadRecords = useCallback(
    async (pageNum: number, reset = false) => {
      if (reset) setRefreshing(true)
      else setLoadingMore(true)
      try {
        const res = await wishApi.getMyTransfers({ page: pageNum, pageSize: PAGE_SIZE })
        const data = res.data.data
        const list = data?.records ?? []
        setRecords((prev) => (reset ? list : [...prev, ...list]))
        setTotal(data?.total ?? 0)
        setPage(pageNum)
      } catch {
        if (reset) {
          setRecords([])
          setTotal(0)
        }
      } finally {
        setLoading(false)
        setRefreshing(false)
        setLoadingMore(false)
      }
    },
    [],
  )

  useEffect(() => {
    if (!isLoggedIn) return
    async function run() {
      await Promise.all([loadLimits(), loadRecords(1, true)])
    }
    void run()
  }, [isLoggedIn, loadLimits, loadRecords])

  const handleRefresh = useCallback(() => {
    void loadLimits()
    void loadRecords(1, true)
  }, [loadLimits, loadRecords])

  const hasMore = records.length < total

  const handleSubmit = useCallback(async () => {
    if (submitting) return
    const trimmedTarget = toUserIdText.trim()
    // BASE-01：用户 ID 是雪花 ID，保持字符串（正则校验，不做 Number 转换）
    const toUserId = trimmedTarget
    if (!/^\d{1,20}$/.test(trimmedTarget) || /^0+$/.test(trimmedTarget)) {
      Alert.alert('提示', '请输入正确的对方用户 ID')
      return
    }
    const trimmedAmount = amountText.trim()
    const amount = Number(trimmedAmount)
    if (!/^\d+$/.test(trimmedAmount) || !Number.isInteger(amount) || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
      Alert.alert('提示', `转赠数量需为 ${MIN_AMOUNT}-${MAX_AMOUNT} 之间的整数星光`)
      return
    }
    if (limits && amount > limits.remainingToday) {
      Alert.alert('提示', `今日剩余可转赠额度为 ${limits.remainingToday} 星光`)
      return
    }
    const trimmedMessage = message.trim()

    setSubmitting(true)
    try {
      const requestKey = generateAttachmentId().replace(/-/g, '')
      await wishApi.createTransfer({
        toUserId,
        amount,
        message: trimmedMessage || undefined,
        requestKey,
      })
      Alert.alert('转赠成功', `已向用户 ${toUserId} 转赠 ${amount} 星光`)
      setToUserIdText('')
      setAmountText('')
      setMessage('')
      await Promise.all([loadLimits(), loadRecords(1, true)])
    } catch (error) {
      Alert.alert('转赠失败', errorMessage(error, '请稍后再试'))
    } finally {
      setSubmitting(false)
    }
  }, [submitting, toUserIdText, amountText, message, limits, loadLimits, loadRecords])

  const renderHeader = () => (
    <View style={{ padding: Spacing.lg, paddingBottom: 0 }}>
      {/* 额度卡 */}
      <View
        style={{
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
          borderWidth: 1,
          borderColor: WishColors.border,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>今日转赠额度</Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', marginTop: Spacing.sm, gap: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.xxxl, fontWeight: '800', color: WishColors.accentGold }}>
            {limits ? limits.remainingToday : '--'}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: 6 }}>
            / {limits ? limits.dailyCap : '--'} 星光剩余
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.xs }}>
          今日已转赠 {limits ? limits.usedToday : '--'} · 单笔 {MIN_AMOUNT}-{MAX_AMOUNT} 星光
          {limits?.businessDate ? ` · 业务日期 ${limits.businessDate}` : ''}
        </Text>
      </View>

      {/* 转赠表单 */}
      <View
        style={{
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.lg,
          marginTop: Spacing.md,
          borderWidth: 1,
          borderColor: WishColors.border,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
          转赠星光
        </Text>

        <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: Spacing.xs }}>对方用户 ID</Text>
        <TextInput
          value={toUserIdText}
          onChangeText={setToUserIdText}
          placeholder="请输入对方用户 ID"
          placeholderTextColor={WishColors.textTertiary}
          keyboardType="numeric"
          editable={!submitting}
          style={{
            padding: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgElevated,
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.md,
          }}
        />

        <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: Spacing.xs, marginTop: Spacing.md }}>
          转赠数量（{MIN_AMOUNT}-{MAX_AMOUNT}）
        </Text>
        <TextInput
          value={amountText}
          onChangeText={setAmountText}
          placeholder={`请输入 ${MIN_AMOUNT}-${MAX_AMOUNT} 之间的整数`}
          placeholderTextColor={WishColors.textTertiary}
          keyboardType="numeric"
          editable={!submitting}
          style={{
            padding: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgElevated,
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.md,
          }}
        />

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.md, marginBottom: Spacing.xs }}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>附言（选填）</Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
            {message.length}/{MAX_MESSAGE_LENGTH}
          </Text>
        </View>
        <TextInput
          value={message}
          onChangeText={setMessage}
          placeholder="捎句话给对方吧"
          placeholderTextColor={WishColors.textTertiary}
          maxLength={MAX_MESSAGE_LENGTH}
          editable={!submitting}
          style={{
            padding: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgElevated,
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.md,
          }}
        />

        <TouchableOpacity
          activeOpacity={0.85}
          onPress={handleSubmit}
          disabled={submitting}
          style={{
            marginTop: Spacing.lg,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: submitting ? WishColors.bgElevated : WishColors.accentCyan,
            alignItems: 'center',
            flexDirection: 'row',
            justifyContent: 'center',
            gap: Spacing.sm,
          }}
        >
          {submitting ? <ActivityIndicator size="small" color={WishColors.text} /> : null}
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: submitting ? WishColors.textTertiary : '#062235' }}>
            {submitting ? '提交中...' : '确认转赠'}
          </Text>
        </TouchableOpacity>
      </View>

      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginTop: Spacing.lg, marginBottom: Spacing.sm }}>
        转赠记录
      </Text>
    </View>
  )

  const renderItem = ({ item }: { item: StarlightTransferRecord }) => {
    const isSent = item.direction === 'SENT'
    const counterpartyUserId = isSent ? item.toUserId : item.fromUserId
    return (
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.md,
          marginBottom: Spacing.sm,
          marginHorizontal: Spacing.lg,
        }}
      >
        <View style={{ flex: 1, marginRight: Spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>
              {DIRECTION_LABELS[item.direction]}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
              {STATUS_LABELS[item.status] ?? item.status}
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: 2 }} numberOfLines={1}>
            {isSent ? '对方' : '来自'}用户 {counterpartyUserId}
            {item.message ? ` · ${item.message}` : ''}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
            {new Date(item.createdAt).toLocaleString('zh-CN')}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: isSent ? '#ff6b6b' : '#52c41a' }}>
          {isSent ? '-' : '+'}{item.amount}
        </Text>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: Spacing.lg,
          paddingBottom: Spacing.sm,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>星光转赠</Text>
        <View style={{ width: 48 }} />
      </View>

      <FlatList
        data={records}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        ListHeaderComponent={renderHeader}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={WishColors.accentCyan}
          />
        }
        onEndReachedThreshold={0.2}
        onEndReached={() => {
          if (hasMore && !loadingMore && !loading) void loadRecords(page + 1, false)
        }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 60 }} />
          ) : (
            <Text style={{ textAlign: 'center', marginTop: 60, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
              暂无转赠记录
            </Text>
          )
        }
        ListFooterComponent={
          loadingMore ? <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: Spacing.md }} /> : null
        }
        contentContainerStyle={{ paddingBottom: Spacing.xxxl }}
      />
    </View>
  )
}
