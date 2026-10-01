import { useState, useEffect } from 'react'
import { View, Text, TextInput, TouchableOpacity } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

/** 发码 60 秒冷却（与服务端每用户冷却一致，仅 UI 层提示） */

/**
 * 全账号注销（W03：mall-user 唯一权威，APP 端）：
 * 申请注销（30 天宽限期）→ 撤回。统一编排不再走短信验证码链路。
 */
export default function AccountDeletionScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(false)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const handleApply = async () => {
    setBusy(true)
    try {
      const res = await wishApi.applyAccountDeletion(reason.trim() || undefined)
      if (res.data?.success) {
        setPending(true)
        alert('注销申请已提交，30 天宽限期内可在本页撤回')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '申请失败')
    } finally {
      setBusy(false)
    }
  }

  const handleCancel = async () => {
    setBusy(true)
    try {
      const res = await wishApi.cancelAccountDeletion()
      if (res.data?.success) {
        setPending(false)
        alert('已撤回注销申请')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '撤回失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>注销账号</Text>
        <View style={{ width: 48 }} />
      </View>

      <View style={{ padding: Spacing.lg }}>
        <View
          style={{
            borderWidth: 1,
            borderColor: 'rgba(255, 77, 79, 0.5)',
            borderRadius: BorderRadius.lg,
            padding: Spacing.md,
            marginBottom: Spacing.lg,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: '#ff4d4f', marginBottom: 4 }}>
            ⚠ 危险操作
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, lineHeight: 18 }}>
            申请后进入 30 天宽限期，期间可随时撤回；到期将清除心愿、成长记录等个人数据且不可恢复。
          </Text>
        </View>

        {pending ? (
          <View>
            <Text style={{ fontSize: FontSize.sm, color: '#ffd700', marginBottom: Spacing.lg }}>
              注销申请处理中，宽限期内可在下方撤回。
            </Text>
            <TouchableOpacity
              activeOpacity={0.85}
              disabled={busy}
              onPress={handleCancel}
              style={{
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                alignItems: 'center',
                backgroundColor: 'rgba(255,255,255,0.08)',
              }}
            >
              <Text style={{ fontSize: FontSize.md, color: WishColors.text }}>撤回注销申请</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View>
            <TextInput
              value={reason}
              onChangeText={setReason}
              maxLength={500}
              multiline
              placeholder="注销原因（可选）"
              placeholderTextColor={WishColors.textSecondary}
              style={{
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.md,
                padding: Spacing.md,
                marginBottom: Spacing.lg,
                fontSize: FontSize.sm,
                color: WishColors.text,
                minHeight: 70,
                textAlignVertical: 'top',
              }}
            />
            <TouchableOpacity
              activeOpacity={0.85}
              disabled={busy}
              onPress={handleApply}
              style={{
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                alignItems: 'center',
                backgroundColor: '#ff4d4f',
                opacity: busy ? 0.5 : 1,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#ffffff' }}>
                {busy ? '提交中...' : '确认申请注销'}
              </Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    </View>
  )
}
