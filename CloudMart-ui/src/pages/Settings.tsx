import { useState, useEffect } from 'react'
import { Switch, Input, Button, message } from 'antd'
import { LockOutlined, MailOutlined } from '@ant-design/icons'
import { getUserProfile, changePassword } from '@/api/user'
import type { UserProfile } from '@/api/user'
import { getUserSettings, updateUserSettings } from '@/api/community'

const sectionStyle: React.CSSProperties = {
  background: 'var(--color-bg-container)',
  border: '1px solid var(--color-border)',
  borderRadius: 12,
  padding: 24,
  marginBottom: 20,
}

const sectionTitleStyle: React.CSSProperties = {
  fontSize: 17,
  fontWeight: 700,
  color: 'var(--color-text-secondary)',
  marginBottom: 20,
  paddingBottom: 12,
  borderBottom: '1px solid var(--color-border)',
}

const rowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  marginBottom: 18,
}

const labelStyle: React.CSSProperties = {
  width: 120,
  flexShrink: 0,
  fontSize: 14,
  color: 'var(--color-text-secondary)',
  fontWeight: 500,
}

const toggleRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '14px 0',
  borderBottom: '1px solid var(--color-border)',
}

export default function SettingsPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)

  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)

  const handleChangePassword = async () => {
    if (!oldPassword.trim()) { message.error('请输入当前密码'); return }
    if (newPassword.length < 6) { message.error('新密码至少6位'); return }
    if (newPassword !== confirmPassword) { message.error('两次输入的密码不一致'); return }
    setPasswordSaving(true)
    try {
      await changePassword(oldPassword, newPassword)
      message.success('密码修改成功')
      setOldPassword(''); setNewPassword(''); setConfirmPassword('')
    } catch {
      message.error('密码修改失败')
    } finally {
      setPasswordSaving(false)
    }
  }

  const [likeNotification, setLikeNotification] = useState(true)
  const [commentNotification, setCommentNotification] = useState(true)
  const [collectNotification, setCollectNotification] = useState(true)
  const [followNotification, setFollowNotification] = useState(true)
  const [systemNotification, setSystemNotification] = useState(true)

  const [allowStrangerView, setAllowStrangerView] = useState(true)
  const [allowStrangerMessage, setAllowStrangerMessage] = useState(true)
  const [showInSearch, setShowInSearch] = useState(true)

  useEffect(() => {
    const init = async () => {
      setLoading(true)
      try {
        const [profileRes, settingsRes] = await Promise.allSettled([
          getUserProfile(),
          getUserSettings(),
        ])

        if (profileRes.status === 'fulfilled' && profileRes.value.data) {
          const p = (profileRes.value.data as { data: UserProfile }).data
          setProfile(p)
        }

        if (settingsRes.status === 'fulfilled' && settingsRes.value.data) {
          const settings = (settingsRes.value.data as { data: Record<string, string> }).data
          if (settings.NOTIFICATION_LIKE !== undefined) setLikeNotification(settings.NOTIFICATION_LIKE === 'true')
          if (settings.NOTIFICATION_COMMENT !== undefined) setCommentNotification(settings.NOTIFICATION_COMMENT === 'true')
          if (settings.NOTIFICATION_FOLLOW !== undefined) setFollowNotification(settings.NOTIFICATION_FOLLOW === 'true')
          if (settings.NOTIFICATION_SYSTEM !== undefined) setSystemNotification(settings.NOTIFICATION_SYSTEM === 'true')
          if (settings.PRIVACY_ALLOW_STRANGER_MSG !== undefined) setAllowStrangerMessage(settings.PRIVACY_ALLOW_STRANGER_MSG === 'true')
          if (settings.PRIVACY_PROFILE_PUBLIC !== undefined) setAllowStrangerView(settings.PRIVACY_PROFILE_PUBLIC === 'true')
        }
      } catch {
        message.error('加载设置失败')
      } finally {
        setLoading(false)
      }
    }
    init()
  }, [])

  const handleSettingChange = async (key: string, value: boolean) => {
    try {
      await updateUserSettings({ [key]: String(value) })
      message.success('保存成功')
    } catch {
      message.error('保存失败')
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', background: 'var(--color-bg-base)' }}>
        <div style={{ width: 40, height: 40, border: '3px solid var(--color-border)', borderTopColor: 'var(--color-primary)', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)', padding: '40px 24px' }}>
      <div style={{ maxWidth: 720, margin: '0 auto' }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: 'var(--color-text-secondary)', marginBottom: 32 }}>设置</h1>

        <div style={sectionStyle}>
          <h2 style={sectionTitleStyle}>通知偏好</h2>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>点赞通知</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>有人点赞你的内容时通知</div>
            </div>
            <Switch checked={likeNotification} onChange={(v) => { setLikeNotification(v); handleSettingChange('NOTIFICATION_LIKE', v) }} />
          </div>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>评论通知</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>有人评论你的内容时通知</div>
            </div>
            <Switch checked={commentNotification} onChange={(v) => { setCommentNotification(v); handleSettingChange('NOTIFICATION_COMMENT', v) }} />
          </div>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>收藏通知</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>有人收藏你的内容时通知</div>
            </div>
            <Switch checked={collectNotification} onChange={(v) => { setCollectNotification(v); handleSettingChange('NOTIFICATION_COLLECT', v) }} />
          </div>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>关注通知</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>有人关注你时通知</div>
            </div>
            <Switch checked={followNotification} onChange={(v) => { setFollowNotification(v); handleSettingChange('NOTIFICATION_FOLLOW', v) }} />
          </div>

          <div style={{ ...toggleRowStyle, borderBottom: 'none' }}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>系统通知</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>接收系统公告和活动通知</div>
            </div>
            <Switch checked={systemNotification} onChange={(v) => { setSystemNotification(v); handleSettingChange('NOTIFICATION_SYSTEM', v) }} />
          </div>
        </div>

        <div style={sectionStyle}>
          <h2 style={sectionTitleStyle}>隐私设置</h2>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>允许陌生人查看我的主页</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>关闭后仅粉丝可查看</div>
            </div>
            <Switch checked={allowStrangerView} onChange={(v) => { setAllowStrangerView(v); handleSettingChange('PRIVACY_PROFILE_PUBLIC', v) }} />
          </div>

          <div style={toggleRowStyle}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>允许陌生人给我发消息</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>关闭后仅互关可发消息</div>
            </div>
            <Switch checked={allowStrangerMessage} onChange={(v) => { setAllowStrangerMessage(v); handleSettingChange('PRIVACY_ALLOW_STRANGER_MSG', v) }} />
          </div>

          <div style={{ ...toggleRowStyle, borderBottom: 'none' }}>
            <div>
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>在搜索结果中显示我的主页</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>关闭后他人无法通过搜索找到你</div>
            </div>
            <Switch checked={showInSearch} onChange={(v) => { setShowInSearch(v); handleSettingChange('PRIVACY_SEARCH_VISIBLE', v) }} />
          </div>
        </div>

        <div style={sectionStyle}>
          <h2 style={sectionTitleStyle}>账号安全</h2>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '14px 0',
              borderBottom: '1px solid var(--color-border)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <LockOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
              <div>
                <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>修改密码</div>
                <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>定期更换密码更安全</div>
              </div>
            </div>
          </div>
          <div style={{ padding: '16px 0 0', display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={rowStyle}>
              <span style={labelStyle}>当前密码</span>
              <Input.Password
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                placeholder="请输入当前密码"
                style={{ flex: 1 }}
              />
            </div>
            <div style={rowStyle}>
              <span style={labelStyle}>新密码</span>
              <Input.Password
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="请输入新密码（至少6位）"
                style={{ flex: 1 }}
              />
            </div>
            <div style={rowStyle}>
              <span style={labelStyle}>确认新密码</span>
              <Input.Password
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="请再次输入新密码"
                style={{ flex: 1 }}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button
                type="primary"
                onClick={handleChangePassword}
                loading={passwordSaving}
                style={{
                  background: 'var(--color-gradient-primary)',
                  border: 'none',
                  fontWeight: 600,
                  boxShadow: '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
                  borderRadius: 8,
                  minWidth: 120,
                }}
              >
                确认修改
              </Button>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '14px 0',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <MailOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />
              <div>
                <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }}>邮箱绑定</div>
                <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>
                  {profile?.email || '未绑定'}
                </div>
              </div>
            </div>
            {profile?.email ? (
              <span style={{ color: '#32CD32', fontSize: 12, fontWeight: 600 }}>已绑定</span>
            ) : (
              <span style={{ color: '#FF6B35', fontSize: 12, fontWeight: 600 }}>未绑定</span>
            )}
          </div>
        </div>
      </div>

      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  )
}
