import { View, Text, TextInput, TouchableOpacity, Alert } from 'react-native'
import { useState } from 'react'
import { router } from 'expo-router'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

export default function LoginScreen() {
  const theme = useTheme()
  const login = useAuthStore((s) => s.login)
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')

  const handleLogin = async () => {
    if (!account || !password) {
      Alert.alert('提示', '请输入账号和密码')
      return
    }
    try {
      await login(account, password)
      router.replace('/')
    } catch (err: any) {
      Alert.alert('登录失败', err?.message || '请检查账号密码')
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase, padding: Spacing.xxl }}>
      <Text style={{ fontSize: FontSize.hero, fontWeight: 'bold', color: theme.text, marginBottom: Spacing.xxl }}>
        登录
      </Text>

      <TextInput
        placeholder="手机号/邮箱/小答号"
        placeholderTextColor={theme.textTertiary}
        value={account}
        onChangeText={setAccount}
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
          marginBottom: Spacing.xl,
        }}
      />

      <TouchableOpacity
        onPress={handleLogin}
        style={{
          backgroundColor: theme.primary,
          borderRadius: BorderRadius.lg,
          paddingVertical: Spacing.lg,
          alignItems: 'center',
          marginBottom: Spacing.lg,
        }}
      >
        <Text style={{ color: '#FFFFFF', fontSize: FontSize.lg, fontWeight: '600' }}>登录</Text>
      </TouchableOpacity>

      <TouchableOpacity onPress={() => router.push('/register')}>
        <Text style={{ color: theme.primary, fontSize: FontSize.md, textAlign: 'center' }}>
          没有账号？去注册
        </Text>
      </TouchableOpacity>
    </View>
  )
}
