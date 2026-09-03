import { View, Text, TextInput, Switch, ScrollView, TouchableOpacity, Alert } from 'react-native'
import { useState, useEffect } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { userApi } from '@/api/user'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

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

function SectionTitle({ children, theme }: { children: React.ReactNode; theme: ReturnType<typeof useTheme> }) {
  return <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, fontWeight: '500', paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.xs }}>{children}</Text>
}

function SwitchRow({ label, desc, value, onValueChange, theme }: {
  label: string; desc: string; value: boolean; onValueChange: (v: boolean) => void; theme: ReturnType<typeof useTheme>
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
      <View style={{ flex: 1, marginRight: Spacing.md }}>
        <Text style={{ fontSize: FontSize.md, color: theme.text, fontWeight: '500' }}>{label}</Text>
        <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>{desc}</Text>
      </View>
      <Switch value={value} onValueChange={onValueChange} trackColor={{ false: theme.border, true: theme.primary }} thumbColor="#FFFFFF" />
    </View>
  )
}

export default function SettingsPage() {
  const theme = useTheme()
  const { user, isLoggedIn, logout } = useAuthStore()
  const { mode, toggleTheme } = useThemeStore()

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
    }
  }

  const handleChangePassword = async () => {
    if (!oldPassword.trim()) { Alert.alert('提示', '请输入当前密码'); return }
    if (!newPassword.trim()) { Alert.alert('提示', '请输入新密码'); return }
    if (newPassword.length < 6) { Alert.alert('提示', '新密码至少6位'); return }
    if (newPassword !== confirmPassword) { Alert.alert('提示', '两次密码不一致'); return }
    setChangingPassword(true)
    try {
      await userApi.changePassword({ oldPassword, newPassword })
      Alert.alert('成功', '密码修改成功')
      setOldPassword(''); setNewPassword(''); setConfirmPassword('')
    } catch {
      Alert.alert('错误', '密码修改失败')
    } finally {
      setChangingPassword(false)
    }
  }

  const handleLogout = () => {
    Alert.alert('提示', '确定要退出登录吗？', [
      { text: '取消', style: 'cancel' },
      { text: '确定', style: 'destructive', onPress: () => logout() },
    ])
  }

  /** 合规页登录守卫（数据导出/通知偏好/注销均为登录用户功能） */
  const requireLoginOr = (action: () => void) => {
    if (!isLoggedIn) {
      Alert.alert('提示', '请先登录')
      router.push('/login')
      return
    }
    action()
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ paddingBottom: Spacing.xxxl }}>
        {/* Notification Preferences */}
        <SectionTitle theme={theme}>通知偏好</SectionTitle>
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
          {NOTIFICATION_ITEMS.map((item) => (
            <SwitchRow key={item.key} label={item.label} desc={item.desc} value={settings[item.key]} onValueChange={(v) => handleSettingChange(item.key, v)} theme={theme} />
          ))}
        </View>

        {/* Privacy Settings */}
        <SectionTitle theme={theme}>隐私设置</SectionTitle>
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
          {PRIVACY_ITEMS.map((item) => (
            <SwitchRow key={item.key} label={item.label} desc={item.desc} value={settings[item.key]} onValueChange={(v) => handleSettingChange(item.key, v)} theme={theme} />
          ))}
        </View>

        {/* Account Security */}
        <SectionTitle theme={theme}>账号安全</SectionTitle>
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden', paddingHorizontal: Spacing.lg }}>
          <View style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>邮箱</Text>
            <Text style={{ fontSize: FontSize.md, color: theme.text }}>{user?.email || '未绑定'}</Text>
          </View>
          <View style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>当前密码</Text>
            <TextInput value={oldPassword} onChangeText={setOldPassword} placeholder="请输入当前密码" placeholderTextColor={theme.textTertiary} secureTextEntry style={{ fontSize: FontSize.md, color: theme.text, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: theme.bgBase }} />
          </View>
          <View style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>新密码</Text>
            <TextInput value={newPassword} onChangeText={setNewPassword} placeholder="请输入新密码（至少6位）" placeholderTextColor={theme.textTertiary} secureTextEntry style={{ fontSize: FontSize.md, color: theme.text, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: theme.bgBase }} />
          </View>
          <View style={{ paddingVertical: Spacing.md, borderBottomWidth: 1, borderBottomColor: theme.border }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.xs }}>确认密码</Text>
            <TextInput value={confirmPassword} onChangeText={setConfirmPassword} placeholder="请再次输入新密码" placeholderTextColor={theme.textTertiary} secureTextEntry style={{ fontSize: FontSize.md, color: theme.text, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: theme.bgBase }} />
          </View>
          <TouchableOpacity onPress={changingPassword ? undefined : handleChangePassword} style={{ marginVertical: Spacing.lg, height: 40, borderRadius: BorderRadius.xl, backgroundColor: theme.primary, justifyContent: 'center', alignItems: 'center', opacity: changingPassword ? 0.6 : 1 }}>
            <Text style={{ color: '#FFFFFF', fontSize: FontSize.md, fontWeight: '600' }}>{changingPassword ? '修改中...' : '修改密码'}</Text>
          </TouchableOpacity>
        </View>

        {/* Data & Account（合规 34.2/34.6，对齐 WEB UserCenter / Mobile myWishes） */}
        <SectionTitle theme={theme}>数据与账号</SectionTitle>
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
          <TouchableOpacity
            onPress={() => requireLoginOr(() => router.push('/data-export'))}
            style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xl, borderBottomWidth: 1, borderBottomColor: theme.border }}
          >
            <View style={{ flex: 1, marginRight: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.text }}>数据导出</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>下载心愿/成长/互动等个人数据副本（JSON，7 天有效）</Text>
            </View>
            <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => requireLoginOr(() => router.push('/notification-prefs'))}
            style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xl, borderBottomWidth: 1, borderBottomColor: theme.border }}
          >
            <View style={{ flex: 1, marginRight: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.text }}>心愿通知偏好</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>管理心愿宇宙的消息推送开关</Text>
            </View>
            <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => requireLoginOr(() => router.push('/account-deletion'))}
            style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xl }}
          >
            <View style={{ flex: 1, marginRight: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, color: theme.accentRed }}>注销账号</Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>申请后 30 天宽限期，期间可撤回</Text>
            </View>
            <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
          </TouchableOpacity>
        </View>

        {/* General */}
        <SectionTitle theme={theme}>通用</SectionTitle>
        <View style={{ marginHorizontal: Spacing.lg, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, overflow: 'hidden' }}>
          <TouchableOpacity onPress={toggleTheme} style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xl, borderBottomWidth: 1, borderBottomColor: theme.border }}>
            <Text style={{ fontSize: FontSize.lg, color: theme.text }}>{mode === 'ocean' ? '切换樱花主题' : '切换深海主题'}</Text>
            <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => Alert.alert('关于宝贝小答', '宝贝小答 v1.0.0\n发现好物，分享生活')} style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xl }}>
            <Text style={{ fontSize: FontSize.lg, color: theme.text }}>关于宝贝小答</Text>
            <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>›</Text>
          </TouchableOpacity>
        </View>

        {/* Logout */}
        <TouchableOpacity onPress={handleLogout} style={{ marginHorizontal: Spacing.lg, marginTop: Spacing.xl, height: 48, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.accentRed, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ color: theme.accentRed, fontSize: FontSize.lg, fontWeight: '600' }}>退出登录</Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
  )
}
