import { useState } from 'react'
import { View, Text, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useAuthStore } from '@/store/auth'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

export default function RegisterPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const { register } = useAuthStore()

  const handleRegister = async () => {
    if (!nickname.trim() || !email.trim() || !password.trim()) {
      Taro.showToast({ title: '请填写完整信息', icon: 'none' })
      return
    }
    if (password !== confirmPassword) {
      Taro.showToast({ title: '两次密码不一致', icon: 'none' })
      return
    }
    setLoading(true)
    try {
      await register(nickname, email, password)
      Taro.showToast({ title: '注册成功', icon: 'success' })
      setTimeout(() => {
        Taro.navigateBack()
      }, 1500)
    } catch (err: any) {
      Taro.showToast({ title: err?.response?.data?.error?.message || '注册失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.header}>
        <Text className={styles.title}>创建账号</Text>
      </View>

      <View className={styles.form}>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>昵称</Text>
          <Input className={styles.input} placeholder='请输入昵称' value={nickname} onInput={(e) => setNickname(e.detail.value)} />
        </View>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>邮箱</Text>
          <Input className={styles.input} placeholder='请输入邮箱' type='text' value={email} onInput={(e) => setEmail(e.detail.value)} />
        </View>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>密码</Text>
          <Input className={styles.input} placeholder='请输入密码' password value={password} onInput={(e) => setPassword(e.detail.value)} />
        </View>
        <View className={styles.inputGroup}>
          <Text className={styles.label}>确认密码</Text>
          <Input className={styles.input} placeholder='请再次输入密码' password value={confirmPassword} onInput={(e) => setConfirmPassword(e.detail.value)} />
        </View>

        <View className={styles.registerBtn} onClick={loading ? undefined : handleRegister}>
          <Text className={styles.registerBtnText}>{loading ? '注册中...' : '注册'}</Text>
        </View>
      </View>
    </View>
  )
}
