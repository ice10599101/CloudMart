import { useState } from 'react'
import { View, Text, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

export default function LoginPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuthStore()

  const handleLogin = async () => {
    if (!account.trim() || !password.trim()) {
      Taro.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    setLoading(true)
    try {
      await login(account, password)
      Taro.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/home/index' })
      }, 1500)
    } catch (err: any) {
      Taro.showToast({ title: err?.response?.data?.error?.message || '登录失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = () => {
    Taro.navigateTo({ url: '/pages/register/index' })
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.header}>
        <Text className={styles.logo}>宝贝小答</Text>
        <Text className={styles.slogan}>发现好物，分享生活</Text>
      </View>

      <View className={styles.form}>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>账号</Text>
          <Input
            className={styles.input}
            placeholder='请输入账号'
            value={account}
            onInput={(e) => setAccount(e.detail.value)}
          />
        </View>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>密码</Text>
          <Input
            className={styles.input}
            type={'password' as never}
            placeholder='请输入密码'
            value={password}
            onInput={(e) => setPassword(e.detail.value)}
          />
        </View>

        <View className={styles.loginBtn} onClick={loading ? undefined : handleLogin}>
          <Text className={styles.loginBtnText}>{loading ? '登录中...' : '登录'}</Text>
        </View>

        <View className={styles.registerRow}>
          <Text className={styles.registerText} onClick={handleRegister}>没有账号？立即注册</Text>
        </View>
      </View>
    </View>
  )
}
