import { View, Text, TextInput, TouchableOpacity, Alert } from 'react-native'
import { useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/** 发码 60 秒冷却（与服务端每邮箱冷却一致，仅 UI 层提示） */
const SEND_CODE_COOLDOWN_SECONDS = 60
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function RegisterScreen() {
  const theme = useTheme()
  const register = useAuthStore((s) => s.register)
  const sendRegisterCode = useAuthStore((s) => s.sendRegisterCode)
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [code, setCode] = useState('')
  const [sendingCode, setSendingCode] = useState(false)
  const [countdown, setCountdown] = useState(0)
  // 已发码邮箱（小写）：邮箱改动后旧验证码失效，重置倒计时引导重发
  const [sentEmail, setSentEmail] = useState('')

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const handleEmailChange = (value: string) => {
    setEmail(value)
    if (sentEmail && value.trim().toLowerCase() !== sentEmail && countdown > 0) {
      setCountdown(0)
    }
  }

  const handleSendCode = async () => {
    if (!EMAIL_PATTERN.test(email.trim())) {
      Alert.alert('提示', '请输入正确的邮箱格式')
      return
    }
    setSendingCode(true)
    try {
      const result = await sendRegisterCode(email.trim())
      if (result.sent) {
        setCountdown(SEND_CODE_COOLDOWN_SECONDS)
        setSentEmail(email.trim().toLowerCase())
        Alert.alert(
          '验证码已发送',
          result.devCode ? `开发回显验证码：${result.devCode}` : '请查收邮箱，验证码 5 分钟内有效',
        )
      } else {
        // 后端不假成功，如实展示原因（通道未配置/发送失败）
        Alert.alert('发送失败', result.message || '请稍后重试')
      }
    } catch (err: any) {
      Alert.alert('发送失败', err?.message || '请稍后重试')
    } finally {
      setSendingCode(false)
    }
  }

  const handleRegister = async () => {
    if (!nickname || !email || !password) {
      Alert.alert('提示', '请填写所有字段')
      return
    }

    // 邮箱格式校验（对齐 Web 端注册表单）
    if (!EMAIL_PATTERN.test(email.trim())) {
      Alert.alert('提示', '请输入正确的邮箱格式')
      return
    }
    if (!/^\d{6}$/.test(code)) {
      Alert.alert('提示', '请输入 6 位邮箱验证码')
      return
    }
    if (password.length < 6) {
      Alert.alert('提示', '密码至少 6 位')
      return
    }
    if (password !== confirmPassword) {
      Alert.alert('提示', '两次密码不一致')
      return
    }
    try {
      await register(nickname, email, password, code)
      Alert.alert('注册成功', '请登录', [{ text: '确定', onPress: () => router.replace('/login') }])
    } catch (err: any) {
      Alert.alert('注册失败', err?.message || '请稍后重试')
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase, padding: Spacing.xxl }}>
      <Text style={{ fontSize: FontSize.hero, fontWeight: 'bold', color: theme.text, marginBottom: Spacing.xxl }}>
        注册
      </Text>

      <TextInput
        placeholder="昵称"
        placeholderTextColor={theme.textTertiary}
        value={nickname}
        onChangeText={setNickname}
        style={{
          backgroundColor: theme.bgInput,
          color: theme.text,
          borderRadius: BorderRadius.md,
          padding: Spacing.lg,
          fontSize: FontSize.lg,
          marginBottom: Spacing.lg,
        }}
      />

      <TextInput
        placeholder="邮箱"
        placeholderTextColor={theme.textTertiary}
        value={email}
        onChangeText={handleEmailChange}
        keyboardType="email-address"
        autoCapitalize="none"
        style={{
          backgroundColor: theme.bgInput,
          color: theme.text,
          borderRadius: BorderRadius.md,
          padding: Spacing.lg,
          fontSize: FontSize.lg,
          marginBottom: Spacing.lg,
        }}
      />

      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: Spacing.lg }}>
        <TextInput
          placeholder="邮箱验证码"
          placeholderTextColor={theme.textTertiary}
          value={code}
          onChangeText={(value) => setCode(value.replace(/\D/g, ''))}
          keyboardType="number-pad"
          maxLength={6}
          style={{
            flex: 1,
            backgroundColor: theme.bgInput,
            color: theme.text,
            borderRadius: BorderRadius.md,
            padding: Spacing.lg,
            fontSize: FontSize.lg,
            marginRight: Spacing.md,
          }}
        />
        <TouchableOpacity
          onPress={handleSendCode}
          disabled={sendingCode || countdown > 0}
          style={{
            backgroundColor: countdown > 0 ? theme.bgInput : theme.primary,
            borderRadius: BorderRadius.md,
            paddingVertical: Spacing.md,
            paddingHorizontal: Spacing.md,
          }}
        >
          <Text
            style={{
              color: countdown > 0 ? theme.textTertiary : '#FFFFFF',
              fontSize: FontSize.sm,
              fontWeight: '600',
            }}
          >
            {sendingCode ? '发送中…' : countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
          </Text>
        </TouchableOpacity>
      </View>

      <TextInput
        placeholder="密码"
        placeholderTextColor={theme.textTertiary}
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        style={{
          backgroundColor: theme.bgInput,
          color: theme.text,
          borderRadius: BorderRadius.md,
          padding: Spacing.lg,
          fontSize: FontSize.lg,
          marginBottom: Spacing.lg,
        }}
      />

      <TextInput
        placeholder="确认密码"
        placeholderTextColor={theme.textTertiary}
        value={confirmPassword}
        onChangeText={setConfirmPassword}
        secureTextEntry
        style={{
          backgroundColor: theme.bgInput,
          color: theme.text,
          borderRadius: BorderRadius.md,
          padding: Spacing.lg,
          fontSize: FontSize.lg,
          marginBottom: Spacing.xl,
        }}
      />

      <TouchableOpacity
        onPress={handleRegister}
        style={{
          backgroundColor: theme.primary,
          borderRadius: BorderRadius.lg,
          paddingVertical: Spacing.lg,
          alignItems: 'center',
          marginBottom: Spacing.lg,
        }}
      >
        <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>注册</Text>
      </TouchableOpacity>

      <TouchableOpacity onPress={() => router.back()}>
        <Text style={{ color: theme.primary, fontSize: FontSize.md, textAlign: 'center' }}>
          已有账号？去登录
        </Text>
      </TouchableOpacity>
    </View>
  )
}
