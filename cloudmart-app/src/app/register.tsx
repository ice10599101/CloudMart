import { View, Text, TextInput, TouchableOpacity, Alert } from 'react-native'
import { useState } from 'react'
import { router } from 'expo-router'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

export default function RegisterScreen() {
  const theme = useTheme()
  const register = useAuthStore((s) => s.register)
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const handleRegister = async () => {
    if (!nickname || !email || !password) {
      Alert.alert('提示', '请填写所有字段')
      return
    }
    if (password !== confirmPassword) {
      Alert.alert('提示', '两次密码不一致')
      return
    }
    try {
      await register(nickname, email, password)
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
        onChangeText={setEmail}
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
