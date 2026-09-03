import { useState, useEffect } from 'react'
import { View, Text, TextInput, TouchableOpacity, Alert } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

/**
 * 账号注销（合规 34.2 / API 2.13，四AB A1 APP 端）：
 * 发送验证码 → 申请注销（30 天宽限期）→ 撤回。
 */
export default function AccountDeletionScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [code, setCode] = useState('')
  const [reason, setReason] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  const handleSend = async () => {
    setBusy(true)
    try {
      const res = await wishApi.sendDeletionCode()
      if (res.data?.success) {
        setCodeSent(true)
        const devCode = res.data.data?.devCode
        alert(devCode ? `验证码已发送（开发回显：${devCode}）` : '验证码已发送，请查收短信/邮件')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '验证码发送失败')
    } finally {
      setBusy(false)
    }
  }

  const handleApply = async () => {
    if (!/^\d{6}$/.test(code)) {
      alert('请输入 6 位验证码')
      return
    }
    setBusy(true)
    try {
      const res = await wishApi.applyAccountDeletion(code, reason.trim() || undefined)
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
            <TouchableOpacity
              activeOpacity={0.85}
              disabled={busy || codeSent}
              onPress={handleSend}
              style={{
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                alignItems: 'center',
                backgroundColor: 'rgba(0, 212, 255, 0.12)',
                marginBottom: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>
                {codeSent ? '验证码已发送（5 分钟有效）' : '1. 发送注销验证码'}
              </Text>
            </TouchableOpacity>
            <TextInput
              value={code}
              onChangeText={(v) => setCode(v.replace(/[^0-9]/g, ''))}
              maxLength={6}
              keyboardType="number-pad"
              placeholder="2. 输入 6 位验证码"
              placeholderTextColor={WishColors.textSecondary}
              style={{
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.md,
                padding: Spacing.md,
                marginBottom: Spacing.sm,
                fontSize: FontSize.sm,
                color: WishColors.text,
              }}
            />
            <TextInput
              value={reason}
              onChangeText={setReason}
              maxLength={500}
              multiline
              placeholder="3. 注销原因（可选）"
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
              disabled={busy || !codeSent}
              onPress={handleApply}
              style={{
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                alignItems: 'center',
                backgroundColor: '#ff4d4f',
                opacity: busy || !codeSent ? 0.5 : 1,
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
