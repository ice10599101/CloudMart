import { useState, useEffect } from 'react'
import { View, Text, Input, Switch } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import { userApi } from '@/api/user'
import { communityApi } from '@/api/community'
import styles from './index.module.scss'

interface UserSettings {
  NOTIFICATION_LIKE: boolean
  NOTIFICATION_COMMENT: boolean
  NOTIFICATION_COLLECT: boolean
  NOTIFICATION_FOLLOW: boolean
  NOTIFICATION_SYSTEM: boolean
  PRIVACY_PROFILE_PUBLIC: boolean
  PRIVACY_ALLOW_STRANGER_MSG: boolean
  PRIVACY_SEARCH_VISIBLE: boolean
}

const DEFAULT_SETTINGS: UserSettings = {
  NOTIFICATION_LIKE: true,
  NOTIFICATION_COMMENT: true,
  NOTIFICATION_COLLECT: true,
  NOTIFICATION_FOLLOW: true,
  NOTIFICATION_SYSTEM: true,
  PRIVACY_PROFILE_PUBLIC: true,
  PRIVACY_ALLOW_STRANGER_MSG: true,
  PRIVACY_SEARCH_VISIBLE: true,
}

const NOTIFICATION_ITEMS = [
  { key: 'NOTIFICATION_LIKE' as const, label: '点赞通知', desc: '收到点赞时通知我' },
  { key: 'NOTIFICATION_COMMENT' as const, label: '评论通知', desc: '收到评论时通知我' },
  { key: 'NOTIFICATION_COLLECT' as const, label: '收藏通知', desc: '帖子被收藏时通知我' },
  { key: 'NOTIFICATION_FOLLOW' as const, label: '关注通知', desc: '被关注时通知我' },
  { key: 'NOTIFICATION_SYSTEM' as const, label: '系统通知', desc: '接收系统消息通知' },
]

const PRIVACY_ITEMS = [
  { key: 'PRIVACY_PROFILE_PUBLIC' as const, label: '公开主页', desc: '允许陌生人查看我的主页' },
  { key: 'PRIVACY_ALLOW_STRANGER_MSG' as const, label: '陌生人消息', desc: '允许陌生人给我发消息' },
  { key: 'PRIVACY_SEARCH_VISIBLE' as const, label: '搜索可见', desc: '在搜索结果中显示我的主页' },
]

export default function SettingsPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { user, logout } = useAuthStore()
  const { mode, toggleTheme } = useThemeStore()
  useAuthGuard()

  const [settings, setSettings] = useState<UserSettings>(DEFAULT_SETTINGS)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [changingPassword, setChangingPassword] = useState(false)

  useEffect(() => {
    loadSettings()
  }, [])

  const loadSettings = async () => {
    try {
      const res = await communityApi.getSettings()
      const data = res.data?.data
      if (data) {
        setSettings({
          NOTIFICATION_LIKE: data.NOTIFICATION_LIKE !== 'false',
          NOTIFICATION_COMMENT: data.NOTIFICATION_COMMENT !== 'false',
          NOTIFICATION_COLLECT: data.NOTIFICATION_COLLECT !== 'false',
          NOTIFICATION_FOLLOW: data.NOTIFICATION_FOLLOW !== 'false',
          NOTIFICATION_SYSTEM: data.NOTIFICATION_SYSTEM !== 'false',
          PRIVACY_PROFILE_PUBLIC: data.PRIVACY_PROFILE_PUBLIC !== 'false',
          PRIVACY_ALLOW_STRANGER_MSG: data.PRIVACY_ALLOW_STRANGER_MSG !== 'false',
          PRIVACY_SEARCH_VISIBLE: data.PRIVACY_SEARCH_VISIBLE !== 'false',
        })
      }
    } catch {
      // Use defaults
    }
  }

  const handleSettingChange = async (key: keyof UserSettings, value: boolean) => {
    const newSettings = { ...settings, [key]: value }
    setSettings(newSettings)
    try {
      await communityApi.updateSettings({ [key]: String(value) })
    } catch {
      setSettings(settings)
      Taro.showToast({ title: '设置失败', icon: 'none' })
    }
  }

  const handleChangePassword = async () => {
    if (!oldPassword.trim()) {
      Taro.showToast({ title: '请输入当前密码', icon: 'none' })
      return
    }
    if (!newPassword.trim()) {
      Taro.showToast({ title: '请输入新密码', icon: 'none' })
      return
    }
    if (newPassword.length < 6) {
      Taro.showToast({ title: '新密码至少6位', icon: 'none' })
      return
    }
    if (newPassword !== confirmPassword) {
      Taro.showToast({ title: '两次密码不一致', icon: 'none' })
      return
    }
    setChangingPassword(true)
    try {
      await userApi.changePassword({ oldPassword, newPassword })
      Taro.showToast({ title: '密码修改成功', icon: 'success' })
      setOldPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch {
      Taro.showToast({ title: '密码修改失败', icon: 'none' })
    } finally {
      setChangingPassword(false)
    }
  }

  const handleLogout = () => {
    Taro.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          logout()
          Taro.redirectTo({ url: '/pages/login/index' })
        }
      },
    })
  }

  const handleClearCache = async () => {
    try {
      const res = await Taro.getStorageInfo()
      const size = (res as any).currentSize || 0
      Taro.clearStorageSync()
      // Re-save essential data
      const token = Taro.getStorageSync('access_token')
      if (token) Taro.setStorageSync('access_token', token)
      Taro.showToast({ title: `已清除 ${size}KB 缓存`, icon: 'none' })
    } catch {
      Taro.showToast({ title: '清除失败', icon: 'none' })
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {/* Notification Preferences */}
      <View className={styles.sectionTitle}>通知偏好</View>
      <View className={styles.section}>
        {NOTIFICATION_ITEMS.map((item) => (
          <View key={item.key} className={styles.switchItem}>
            <View className={styles.switchInfo}>
              <Text className={styles.switchLabel}>{item.label}</Text>
              <Text className={styles.switchDesc}>{item.desc}</Text>
            </View>
            <Switch
              checked={settings[item.key]}
              onChange={(e) => handleSettingChange(item.key, e.detail.value)}
              color='var(--color-primary)'
            />
          </View>
        ))}
      </View>

      {/* Privacy Settings */}
      <View className={styles.sectionTitle}>隐私设置</View>
      <View className={styles.section}>
        {PRIVACY_ITEMS.map((item) => (
          <View key={item.key} className={styles.switchItem}>
            <View className={styles.switchInfo}>
              <Text className={styles.switchLabel}>{item.label}</Text>
              <Text className={styles.switchDesc}>{item.desc}</Text>
            </View>
            <Switch
              checked={settings[item.key]}
              onChange={(e) => handleSettingChange(item.key, e.detail.value)}
              color='var(--color-primary)'
            />
          </View>
        ))}
      </View>

      {/* Account Security */}
      <View className={styles.sectionTitle}>账号安全</View>
      <View className={styles.section}>
        <View className={styles.fieldItem}>
          <Text className={styles.fieldLabel}>邮箱</Text>
          <Text className={styles.fieldValue}>{user?.email || '未绑定'}</Text>
        </View>
        <View className={styles.fieldItem}>
          <Text className={styles.fieldLabel}>当前密码</Text>
          <Input
            className={styles.fieldInput}
            type={'password' as never}
            value={oldPassword}
            onInput={(e) => setOldPassword(e.detail.value)}
            placeholder='请输入当前密码'
            placeholderClass={styles.placeholder}
          />
        </View>
        <View className={styles.fieldItem}>
          <Text className={styles.fieldLabel}>新密码</Text>
          <Input
            className={styles.fieldInput}
            type={'password' as never}
            value={newPassword}
            onInput={(e) => setNewPassword(e.detail.value)}
            placeholder='请输入新密码（至少6位）'
            placeholderClass={styles.placeholder}
          />
        </View>
        <View className={styles.fieldItem}>
          <Text className={styles.fieldLabel}>确认密码</Text>
          <Input
            className={styles.fieldInput}
            type={'password' as never}
            value={confirmPassword}
            onInput={(e) => setConfirmPassword(e.detail.value)}
            placeholder='请再次输入新密码'
            placeholderClass={styles.placeholder}
          />
        </View>
        <View className={styles.changePwdBtn} onClick={changingPassword ? undefined : handleChangePassword}>
          <Text className={styles.changePwdText}>{changingPassword ? '修改中...' : '修改密码'}</Text>
        </View>
      </View>

      {/* General */}
      <View className={styles.sectionTitle}>通用</View>
      <View className={styles.section}>
        <View className={styles.item} onClick={handleClearCache}>
          <Text className={styles.itemText}>清除缓存</Text>
          <Text className={styles.arrow}>›</Text>
        </View>
        <View className={styles.item} onClick={toggleTheme}>
          <Text className={styles.itemText}>{mode === 'ocean' ? '切换樱花主题' : '切换深海主题'}</Text>
          <Text className={styles.arrow}>›</Text>
        </View>
        <View className={styles.item} onClick={() => Taro.showModal({ title: '关于宝贝小答', content: '宝贝小答 v1.0.0\n发现好物，分享生活', showCancel: false })}>
          <Text className={styles.itemText}>关于宝贝小答</Text>
          <Text className={styles.arrow}>›</Text>
        </View>
      </View>

      {/* Logout */}
      <View className={styles.logoutBtn} onClick={handleLogout}>
        <Text className={styles.logoutText}>退出登录</Text>
      </View>
    </View>
  )
}
