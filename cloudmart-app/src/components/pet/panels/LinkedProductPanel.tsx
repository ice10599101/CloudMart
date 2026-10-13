// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Text, TextInput, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { errorMessage } from '@/utils/request'
import { petApi, type PetLinkedProductOperation, type PetLinkedProductEntitlement } from '@/api/pet'
import { ChipButton, newR19RequestKey } from '../shared'

const LINKED_OP_STATUS_LABEL: Record<string, string> = {
  VERIFYING: '核验中',
  PENDING: '处理中',
  DELIVERED: '已发放',
  REJECTED: '已拒绝',
}

const LINKED_REJECT_REASON_LABEL: Record<string, string> = {
  PET_CODE_NOT_FOUND: '兑换码不存在，请核对后重试',
  PET_CODE_DISABLED: '兑换码已停用',
  PET_CODE_NOT_EXCHANGED: '请先在商城完成兑换码兑换，再来核销',
  PET_CODE_NOT_OWNED: '兑换码不属于当前账号',
}

const LINKED_ENTITLEMENT_STATUS_LABEL: Record<string, string> = {
  AVAILABLE: '可用',
  CONSUMED: '已使用',
  EXPIRED: '已过期',
}

/** R19 实物商品双倍喂食：权益份数展示 + 兑换码核销（处理中可按操作 ID 查询恢复） */
export function LinkedProductPanel({ redeemEnabled }: { redeemEnabled: boolean }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [entitlement, setEntitlement] = useState<PetLinkedProductEntitlement | null>(null)
  const [entitlementError, setEntitlementError] = useState(false)
  const [code, setCode] = useState('')
  const [redeeming, setRedeeming] = useState(false)
  const [querying, setQuerying] = useState(false)
  const [operation, setOperation] = useState<PetLinkedProductOperation | null>(null)
  // 同一核销意图的 requestKey：结果未知时保留，原样重试由服务端按原操作收敛（不重复发放）
  const requestKeyRef = useRef<string | null>(null)

  const refreshEntitlement = useCallback(async () => {
    try {
      const { data: res } = await petApi.getLinkedProductEntitlement()
      setEntitlement(res.data)
      setEntitlementError(false)
    } catch {
      // 展示型数据：标记内联错误态供重试，不阻断核销操作
      setEntitlementError(true)
    }
  }, [])

  // 挂载加载：setState 发生在 await 之后（重试/核销后走 refreshEntitlement）
  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const { data: res } = await petApi.getLinkedProductEntitlement()
        if (!cancelled) {
          setEntitlement(res.data)
          setEntitlementError(false)
        }
      } catch {
        // 展示型数据：标记内联错误态供重试，不阻断核销操作
        if (!cancelled) setEntitlementError(true)
      }
    })()
    return () => { cancelled = true }
  }, [])

  const handleDelivered = useCallback(async () => {
    requestKeyRef.current = null
    setCode('')
    Alert.alert('核销成功', '双倍喂食权益已到账，下一次喂食效果翻倍')
    await refreshEntitlement()
  }, [refreshEntitlement])

  const redeem = async () => {
    const trimmed = code.trim()
    if (redeeming) return
    if (!trimmed) {
      Alert.alert('请输入兑换码', '兑换码印在实物零食随附卡片上')
      return
    }
    setRedeeming(true)
    try {
      if (!requestKeyRef.current) requestKeyRef.current = newR19RequestKey()
      const { data: res } = await petApi.redeemLinkedProduct({
        redemptionCode: trimmed,
        requestKey: requestKeyRef.current ?? undefined,
      })
      const view = res.data
      setOperation(view)
      if (view.status === 'DELIVERED') {
        await handleDelivered()
      } else if (view.status === 'REJECTED') {
        // 终态拒绝：释放本次意图键
        requestKeyRef.current = null
        Alert.alert('核销未通过', LINKED_REJECT_REASON_LABEL[view.rejectReasonCode] ?? '兑换码无效或已核销')
      } else {
        // VERIFYING/PENDING：coupon 跨服务核验中，凭操作 ID 查询恢复
        Alert.alert('核销处理中', '权益正在核验，可稍后在下方查询处理结果')
      }
    } catch (error) {
      // 网络结果未知：保留兑换码与 requestKey，用户原样重试，服务端同键返回原操作
      Alert.alert('核销失败', errorMessage(error, '网络不稳定，可稍后原样重试，不会重复核销'))
    } finally {
      setRedeeming(false)
    }
  }

  const queryOperation = async () => {
    if (!operation || querying) return
    setQuerying(true)
    try {
      const { data: res } = await petApi.getLinkedProductOperation(operation.operationId)
      const view = res.data
      setOperation(view)
      if (view.status === 'DELIVERED') {
        await handleDelivered()
      } else if (view.status === 'REJECTED') {
        requestKeyRef.current = null
        Alert.alert('核销未通过', LINKED_REJECT_REASON_LABEL[view.rejectReasonCode] ?? '兑换码无效或已核销')
      } else {
        Alert.alert('仍在处理中', '权益核验尚未完成，请稍后再查询')
      }
    } catch (error) {
      Alert.alert('查询失败', errorMessage(error, '请稍后再试'))
    } finally {
      setQuerying(false)
    }
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4, marginBottom: Spacing.sm,
  } as const
  const processing = operation?.status === 'VERIFYING' || operation?.status === 'PENDING'

  return (
    <View>
      <View style={cardStyle}>
        <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🎁 双倍喂食权益</Text>
        {entitlementError ? (
          <>
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>权益状态暂时加载失败</Text>
            <ChipButton label="重新加载" onPress={() => void refreshEntitlement()} />
          </>
        ) : (
          <>
            <Text style={{ color: colors.primary, fontSize: FontSize.lg, fontWeight: '700' }}>
              {`可用 ${entitlement?.usableCount ?? 0} 次`}
            </Text>
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
              {(entitlement?.usableCount ?? 0) > 0
                ? '权益已就绪，下一次喂食效果翻倍（喂食时自动占用一条）'
                : '暂无可用权益，购买实物零食随单获得兑换码'}
            </Text>
            {entitlement?.items?.map((item) => (
              <Text key={String(item.entitlementId)} style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`· ${item.itemCode} · ${LINKED_ENTITLEMENT_STATUS_LABEL[item.status] ?? item.status}${item.expiresAt ? ` · 失效 ${item.expiresAt.slice(0, 16).replace('T', ' ')}` : ''}`}
              </Text>
            ))}
          </>
        )}
      </View>

      {redeemEnabled ? (
        <View style={cardStyle}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>兑换码核销</Text>
          <TextInput
            value={code}
            onChangeText={setCode}
            maxLength={64}
            placeholder="输入实物零食随附的兑换码"
            placeholderTextColor={colors.textTertiary}
            autoCapitalize="none"
            autoCorrect={false}
            style={{
              paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
              borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgContainer,
            }}
          />
          <ChipButton label={redeeming ? '核销中…' : '核销兑换码'} primary disabled={redeeming} onPress={() => void redeem()} />
          {operation && (
            <View style={{ gap: 4, marginTop: Spacing.xs }}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`最近操作：${LINKED_OP_STATUS_LABEL[operation.status] ?? operation.status}（${operation.operationId}）`}
              </Text>
              {processing && (
                <ChipButton label={querying ? '查询中…' : '查询处理结果'} primary disabled={querying} onPress={() => void queryOperation()} />
              )}
            </View>
          )}
        </View>
      ) : (
        <View style={cardStyle}>
          <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>实物商品兑换暂未开放</Text>
        </View>
      )}
    </View>
  )
}

