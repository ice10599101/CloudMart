import { useState, useEffect, useCallback, useRef } from 'react'
import { createPortal } from 'react-dom'
import { history } from 'umi'
import { UserOutlined } from '@ant-design/icons'
import { Input, Select, DatePicker, Button, Modal, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import {
  updateProfile,
  listAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
} from '@/api/user'
import type { ShippingAddress, CreateAddressRequest, UpdateAddressRequest } from '@/types'
import { getWishlistList, removeWishlist } from '@/api/wishlist'
import type { WishlistItem } from '@/api/wishlist'
import { getUserProfile as getCommunityProfile, getUserPosts, getUserDrafts, getLikedPosts, getMyComments } from '@/api/community'
import type { Post, MyComment } from '@/api/community'
import {
  getUserLevel,
  getExpLogs,
  getLevelConfigs,
  getCheckInStatus,
  getContinuousDays,
} from '@/api/growth'
import type { UserLevelInfo, LevelConfig, ExpLogRecord } from '@/api/growth'
import { useAuthStore } from '@/stores/auth'
import { uploadFile } from '@/api/file'
import s from './UserCenter.module.css'

function ConfirmDialog({
  open,
  title,
  content,
  okText = '确定',
  cancelText = '取消',
  danger = false,
  onOk,
  onCancel,
}: {
  open: boolean
  title: string
  content: string
  okText?: string
  cancelText?: string
  danger?: boolean
  onOk: () => void
  onCancel: () => void
}) {
  if (!open) return null
  return createPortal(
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0, 0, 0, 0.6)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 10000,
      backdropFilter: 'blur(4px)',
    }}>
      <div style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: 16,
        padding: '28px 32px',
        width: 380,
        maxWidth: '90vw',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3), 0 0 60px rgba(var(--color-primary-rgb), 0.08)',
        textAlign: 'center',
      }}>
        <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 12 }}>{title}</div>
        <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', marginBottom: 24, lineHeight: 1.6 }}>{content}</div>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 12 }}>
          <button onClick={onCancel} style={{
            padding: '8px 24px',
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            background: 'transparent',
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}>
            {cancelText}
          </button>
          <button onClick={onOk} style={{
            padding: '8px 24px',
            border: 'none',
            borderRadius: 8,
            background: danger ? 'var(--color-accent-red)' : 'var(--color-gradient-primary)',
            color: '#fff',
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'all 0.2s',
            minWidth: 100,
            boxShadow: danger
              ? '0 4px 16px rgba(255, 71, 87, 0.3)'
              : '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
          }}>
            {okText}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

const TABS = [
  { key: 'profile', label: '基本信息', icon: '👤' },
  { key: 'posts', label: '我的帖子', icon: '📝' },
  { key: 'drafts', label: '我的草稿', icon: '📋' },
  { key: 'address', label: '收货地址', icon: '📍' },
  { key: 'wishlist', label: '我的收藏', icon: '❤️' },
  { key: 'liked', label: '我的点赞', icon: '👍' },
  { key: 'replies', label: '我的回复', icon: '💬' },
] as const

type TabKey = typeof TABS[number]['key']

interface CommunityProfileData {
  postCount: number
  followerCount: number
  followCount: number
  collectCount: number
  badges: Array<{ id: number; name: string; icon: string; description: string }>
}

const BADGE_COLORS = [
  { bg: 'rgba(var(--color-primary-rgb), 0.12)', border: 'rgba(var(--color-primary-rgb), 0.25)', text: 'var(--color-primary)', glow: 'rgba(var(--color-primary-rgb), 0.15)' },
  { bg: 'rgba(255,165,0,0.12)', border: 'rgba(255,165,0,0.25)', text: 'var(--color-accent-orange)', glow: 'rgba(255,165,0,0.15)' },
  { bg: 'rgba(var(--color-primary-rgb), 0.12)', border: 'rgba(var(--color-primary-rgb), 0.25)', text: 'var(--color-accent-purple)', glow: 'rgba(var(--color-primary-rgb), 0.15)' },
  { bg: 'rgba(50,205,50,0.12)', border: 'rgba(50,205,50,0.25)', text: 'var(--color-accent-green)', glow: 'rgba(50,205,50,0.15)' },
  { bg: 'rgba(255,71,87,0.12)', border: 'rgba(255,71,87,0.25)', text: 'var(--color-accent-red)', glow: 'rgba(255,71,87,0.15)' },
  { bg: 'rgba(255,215,0,0.12)', border: 'rgba(255,215,0,0.25)', text: 'var(--color-accent-gold)', glow: 'rgba(255,215,0,0.15)' },
]

const EXP_SOURCE_MAP: Record<string, { label: string; icon: string }> = {
  CHECK_IN: { label: '每日签到', icon: '📅' },
  POST: { label: '发布帖子', icon: '📝' },
  LIKE_RECEIVED: { label: '获得点赞', icon: '❤️' },
  COMMENT_RECEIVED: { label: '获得评论', icon: '💬' },
  FOLLOW_RECEIVED: { label: '获得关注', icon: '👥' },
  COLLECT_RECEIVED: { label: '获得收藏', icon: '⭐' },
}

const STAT_ITEMS_CONFIG = [
  { label: '帖子', icon: '📝', cssVar: '--color-primary' },
  { label: '粉丝', icon: '👥', cssVar: '--color-accent-purple' },
  { label: '关注', icon: '🔗', cssVar: '--color-accent-green' },
  { label: '收藏', icon: '⭐', cssVar: '--color-accent-gold' },
]

const CONSTELLATIONS = [
  '白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座',
  '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座',
]

const GENDER_OPTIONS = [
  { value: 'UNKNOWN', label: '未设置' },
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
]

function ProfileTab() {
  const { user } = useAuthStore()

  if (!user) return null

  const genderMap: Record<string, string> = { MALE: '男', FEMALE: '女', UNKNOWN: '未设置' }

  const rows = [
    { label: '小答号', value: user.username },
    { label: '昵称', value: user.nickname || '-' },
    { label: '邮箱', value: user.email || '-' },
    { label: '性别', value: genderMap[user.gender ?? 'UNKNOWN'] ?? '未设置' },
    { label: '生日', value: user.birthday || '-' },
    { label: '星座', value: user.constellation || '-' },
    { label: '个性签名', value: user.signature || '-' },
    { label: '职业', value: user.occupation || '-' },
    { label: '学校', value: user.school || '-' },
    { label: '所在地区', value: user.location || '-' },
    { label: '兴趣爱好', value: user.hobbies || '-' },
    { label: '注册时间', value: new Date(user.createdAt).toLocaleString() },
  ]

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', margin: 0, marginBottom: 16 }}>基本信息</h3>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {rows.map((item, index) => (
          <div key={item.label} style={{ display: 'flex', alignItems: 'center', padding: '14px 0 14px 1em', gap: 16, borderBottom: index < rows.length - 1 ? '1px solid var(--color-border)' : 'none' }}>
            <span style={{ width: 100, flexShrink: 0, color: 'var(--color-text-secondary)', fontSize: 14, fontWeight: 500 }}>{item.label}：</span>
            <span style={{ color: 'var(--color-text-secondary)', fontSize: 14, flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function EditProfileModal({ open, onClose, onToast }: { open: boolean; onClose: () => void; onToast: (msg: string, type: 'success' | 'error') => void }) {
  const { user, fetchProfile } = useAuthStore()
  const [nickname, setNickname] = useState('')
  const [signature, setSignature] = useState('')
  const [gender, setGender] = useState<string>('UNKNOWN')
  const [birthday, setBirthday] = useState<string>('')
  const [constellation, setConstellation] = useState('')
  const [occupation, setOccupation] = useState('')
  const [school, setSchool] = useState('')
  const [location, setLocation] = useState('')
  const [hobbies, setHobbies] = useState('')
  const [saving, setSaving] = useState(false)
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [confirmState, setConfirmState] = useState<{ type: 'close' | 'save'; open: boolean }>({ type: 'close', open: false })
  const avatarInputRef = useRef<HTMLInputElement>(null)

  const rowStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', marginBottom: 18 }
  const labelStyle: React.CSSProperties = { width: 100, flexShrink: 0, fontSize: 14, color: 'var(--color-text-secondary)', fontWeight: 500 }

  useEffect(() => {
    if (open && user) {
      setNickname(user.nickname || '')
      setSignature(user.signature || '')
      setGender(user.gender || 'UNKNOWN')
      setBirthday(user.birthday || '')
      setConstellation(user.constellation || '')
      setOccupation(user.occupation || '')
      setSchool(user.school || '')
      setLocation(user.location || '')
      setHobbies(user.hobbies || '')
    }
  }, [open, user])

  const handleCancel = () => {
    setConfirmState({ type: 'close', open: true })
  }

  const handleSave = () => {
    if (!nickname.trim()) {
      onToast('请输入昵称', 'error')
      return
    }
    setConfirmState({ type: 'save', open: true })
  }

  const handleConfirmOk = () => {
    if (confirmState.type === 'close') {
      setConfirmState({ type: 'close', open: false })
      onClose()
    } else {
      setConfirmState({ type: 'save', open: false })
      setSaving(true)
      updateProfile({ nickname, signature, gender, birthday, constellation, occupation, school, location, hobbies })
        .then(() => fetchProfile())
        .then(() => {
          onToast('资料更新成功', 'success')
          onClose()
        })
        .catch(() => onToast('更新失败', 'error'))
        .finally(() => setSaving(false))
    }
  }

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      onToast('头像文件不能超过5MB', 'error')
      return
    }
    if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
      onToast('仅支持 JPG/PNG/GIF/WebP 格式', 'error')
      return
    }
    setAvatarUploading(true)
    try {
      const { data: uploadRes } = await uploadFile(file)
      const avatarUrl = uploadRes.data.url
      await updateProfile({ avatar: avatarUrl })
      await fetchProfile()
      onToast('头像更新成功', 'success')
    } catch {
      onToast('头像上传失败', 'error')
    } finally {
      setAvatarUploading(false)
      if (avatarInputRef.current) avatarInputRef.current.value = ''
    }
  }

  return (
    <>
    <Modal
      open={open}
      title="编辑个人资料"
      width={640}
      onCancel={handleCancel}
      maskClosable={false}
      keyboard={false}
      footer={null}
      destroyOnClose
    >
      <div style={{ position: 'relative' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <div
          style={{ width: 72, height: 72, borderRadius: '50%', background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.2), rgba(0,153,204,0.3))', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid rgba(var(--color-primary-rgb), 0.3)', overflow: 'hidden', position: 'relative', cursor: avatarUploading ? 'wait' : 'pointer', flexShrink: 0, opacity: avatarUploading ? 0.6 : 1, transition: 'opacity 0.2s' }}
          onClick={() => !avatarUploading && avatarInputRef.current?.click()}
        >
          {user?.avatar ? (
            <img src={user.avatar!} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <span style={{ fontSize: 24, color: 'var(--color-primary)' }}>{user?.nickname?.charAt(0) || 'U'}</span>
          )}
        </div>
        <input ref={avatarInputRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" style={{ display: 'none' }} onChange={handleAvatarUpload} />
        <div>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)' }}>{nickname || user?.username}</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 4 }}>{avatarUploading ? '上传中...' : '点击头像更换'}</div>
        </div>
      </div>

      <div style={rowStyle}>
        <span style={labelStyle}>昵称</span>
        <Input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="请输入昵称" style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={{ ...labelStyle, alignSelf: 'flex-start', marginTop: 6 }}>个性签名</span>
        <Input.TextArea value={signature} onChange={(e) => setSignature(e.target.value)} placeholder="写一句话介绍自己" rows={2} style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>性别</span>
        <Select value={gender} onChange={setGender} options={GENDER_OPTIONS} style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>生日</span>
        <ConfigProvider locale={zhCN}>
          <DatePicker
            value={birthday ? dayjs(birthday) : undefined}
            onChange={(_, dateStr) => { setBirthday(typeof dateStr === 'string' ? dateStr : '') }}
            placeholder="请选择生日"
            style={{ flex: 1 }}
          />
        </ConfigProvider>
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>星座</span>
        <Select value={constellation || undefined} onChange={setConstellation} placeholder="请选择星座" options={CONSTELLATIONS.map((c) => ({ value: c, label: c }))} allowClear style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>职业</span>
        <Input value={occupation} onChange={(e) => setOccupation(e.target.value)} placeholder="例如：设计师、程序员、学生" style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>学校</span>
        <Input value={school} onChange={(e) => setSchool(e.target.value)} placeholder="例如：北京大学" style={{ flex: 1 }} />
      </div>
      <div style={rowStyle}>
        <span style={labelStyle}>所在地区</span>
        <Input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="例如：北京·朝阳区" style={{ flex: 1 }} />
      </div>
      <div style={{ ...rowStyle, marginBottom: 24 }}>
        <span style={{ ...labelStyle, alignSelf: 'flex-start', marginTop: 6 }}>兴趣爱好</span>
        <Input.TextArea value={hobbies} onChange={(e) => setHobbies(e.target.value)} placeholder="多个爱好用逗号分隔" rows={2} style={{ flex: 1 }} />
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
        <button onClick={handleCancel} style={{ padding: '8px 24px', border: '1px solid var(--color-border)', borderRadius: 8, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 14, cursor: 'pointer' }}>取消</button>
        <Button type="primary" onClick={handleSave} loading={saving} style={{ background: 'var(--color-gradient-primary)', border: 'none', fontWeight: 600, boxShadow: '0 4px 16px rgba(var(--color-primary-rgb), 0.3)', borderRadius: 8, minWidth: 100 }}>保存</Button>
      </div>
      </div>
    </Modal>

    <ConfirmDialog
      open={confirmState.open}
      title={confirmState.type === 'close' ? '确认关闭' : '确认保存'}
      content={confirmState.type === 'close' ? '您有未保存的修改，确定要关闭吗？' : '确定要保存个人资料修改吗？'}
      okText={confirmState.type === 'close' ? '确认关闭' : '确定保存'}
      cancelText={confirmState.type === 'close' ? '继续编辑' : '取消'}
      danger={confirmState.type === 'close'}
      onOk={handleConfirmOk}
      onCancel={() => setConfirmState({ ...confirmState, open: false })}
    />
    </>
  )
}

function MyPostsTab() {
  const { user } = useAuthStore()
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)

  const fetchPosts = useCallback(async () => {
    if (!user?.id) return
    setLoading(true)
    try {
      const { data: res } = await getUserPosts(user.id, 1, 50)
      setPosts(res.data ?? [])
    } catch {
      setPosts([])
    } finally {
      setLoading(false)
    }
  }, [user?.id])

  useEffect(() => { fetchPosts() }, [fetchPosts])

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
  }

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>我的帖子</h3>
      {posts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>📝</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无帖子，去社区发帖吧</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 }}>
          {posts.map((post) => (
            <div key={post.id} className={s.postCard} onClick={() => history.push(`/post/${post.id}`)}>
              <div style={{ height: 160, background: 'rgba(var(--color-primary-rgb), 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                {post.coverImage ? (
                  <img src={post.coverImage} alt={post.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.3)" strokeWidth="1.5">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                    <line x1="16" y1="13" x2="8" y2="13" />
                    <line x1="16" y1="17" x2="8" y2="17" />
                  </svg>
                )}
              </div>
              <div style={{ padding: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 8 }}>{post.title}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: 12 }}>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>❤️ {post.likeCount}</span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>💬 {post.commentCount}</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{new Date(post.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function MyDraftsTab({ onToast }: { onToast: (msg: string, type: 'success' | 'error') => void }) {
  const { user } = useAuthStore()
  const [drafts, setDrafts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)
  const [confirmState, setConfirmState] = useState<{ type: 'delete' | 'publish'; open: boolean; targetId?: number }>({ type: 'delete', open: false })

  const fetchDrafts = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getUserDrafts(1, 50)
      setDrafts(res.data ?? [])
    } catch {
      setDrafts([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchDrafts() }, [fetchDrafts])

  const handleDeleteDraft = (id: number) => {
    setConfirmState({ type: 'delete', open: true, targetId: id })
  }

  const handlePublishDraft = (id: number) => {
    setConfirmState({ type: 'publish', open: true, targetId: id })
  }

  const handleConfirmOk = async () => {
    if (!confirmState.targetId) return
    const id = confirmState.targetId
    setConfirmState({ ...confirmState, open: false })
    if (confirmState.type === 'delete') {
      try {
        const { deletePost } = await import('@/api/community')
        await deletePost(id)
        onToast('草稿已删除', 'success')
        fetchDrafts()
      } catch {
        onToast('删除失败', 'error')
      }
    } else {
      try {
        const { updatePost } = await import('@/api/community')
        await updatePost(id, { status: 1 })
        onToast('草稿已发布', 'success')
        fetchDrafts()
      } catch {
        onToast('发布失败', 'error')
      }
    }
  }

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
  }

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>我的草稿</h3>
      {drafts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>📋</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无草稿</div>
          <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12, marginTop: 6 }}>发布内容时可保存为草稿稍后编辑</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {drafts.map((draft) => (
            <div key={draft.id} className={s.addressCard} style={{ cursor: 'pointer' }} onClick={() => history.push(`/publish?edit=${draft.id}`)}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 6 }}>
                    {draft.title || '未命名草稿'}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                    {draft.summary || draft.content?.substring(0, 80) || '暂无内容'}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 6 }}>
                    最后编辑：{new Date(draft.createdAt).toLocaleString()}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexShrink: 0, marginLeft: 16 }}>
                  <button
                    onClick={(e) => { e.stopPropagation(); handlePublishDraft(draft.id) }}
                    style={{ padding: '4px 12px', border: '1px solid rgba(var(--color-primary-rgb), 0.3)', borderRadius: 6, background: 'rgba(var(--color-primary-rgb), 0.08)', color: 'var(--color-primary)', fontSize: 12, cursor: 'pointer' }}
                  >
                    发布
                  </button>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleDeleteDraft(draft.id) }}
                    style={{ padding: '4px 12px', border: '1px solid rgba(255,71,87,0.3)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 12, cursor: 'pointer' }}
                  >
                    删除
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={confirmState.open}
        title={confirmState.type === 'delete' ? '确认删除' : '确认发布'}
        content={confirmState.type === 'delete' ? '确定要删除该草稿吗？删除后无法恢复。' : '确定要发布该草稿吗？发布后将在社区公开展示。'}
        okText={confirmState.type === 'delete' ? '确认删除' : '确认发布'}
        cancelText="取消"
        danger={confirmState.type === 'delete'}
        onOk={handleConfirmOk}
        onCancel={() => setConfirmState({ ...confirmState, open: false })}
      />
    </div>
  )
}

function MyLikedTab() {
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)

  const fetchLiked = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getLikedPosts(1, 50)
      setPosts(res.data ?? [])
    } catch {
      setPosts([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchLiked() }, [fetchLiked])

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
  }

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>我的点赞</h3>
      {posts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>👍</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无点赞</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 }}>
          {posts.map((post) => (
            <div key={post.id} className={s.postCard} onClick={() => history.push(`/post/${post.id}`)}>
              <div style={{ height: 160, background: 'rgba(var(--color-primary-rgb), 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                {post.coverImage ? (
                  <img src={post.coverImage} alt={post.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ fontSize: 32, opacity: 0.3 }}>👍</span>
                )}
              </div>
              <div style={{ padding: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 8 }}>{post.title}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: 12 }}>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>❤️ {post.likeCount}</span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>💬 {post.commentCount}</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{new Date(post.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function MyRepliesTab() {
  const [comments, setComments] = useState<MyComment[]>([])
  const [loading, setLoading] = useState(false)

  const fetchComments = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getMyComments(1, 50)
      setComments(res.data ?? [])
    } catch {
      setComments([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchComments() }, [fetchComments])

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
  }

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>我的回复</h3>
      {comments.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>💬</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无回复</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {comments.map((comment) => (
            <div key={comment.id} className={s.addressCard} style={{ cursor: 'pointer' }} onClick={() => history.push(`/post/${comment.postId}`)}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, color: 'var(--color-text-secondary)', marginBottom: 6, lineHeight: 1.5 }}>
                    {comment.content}
                  </div>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                    <span style={{ fontSize: 12, color: 'var(--color-primary)', cursor: 'pointer' }}>
                      原帖：{comment.postTitle || `帖子#${comment.postId}`}
                    </span>
                    {comment.replyToNickname && (
                      <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                        回复 @{comment.replyToNickname}
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 6 }}>
                    {new Date(comment.createdAt).toLocaleString()}
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0, marginLeft: 16 }}>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>❤️ {comment.likeCount}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function AddressTab({ onToast }: { onToast: (msg: string, type: 'success' | 'error') => void }) {
  const [addresses, setAddresses] = useState<ShippingAddress[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingAddress, setEditingAddress] = useState<ShippingAddress | null>(null)
  const [form, setForm] = useState({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: false })
  const [saving, setSaving] = useState(false)
  const [confirmState, setConfirmState] = useState<{ type: 'delete' | 'close'; open: boolean; targetId?: number }>({ type: 'close', open: false })

  const fetchAddresses = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listAddresses()
      setAddresses(res.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAddresses() }, [fetchAddresses])

  const openAddModal = () => {
    setEditingAddress(null)
    setForm({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: false })
    setModalOpen(true)
  }

  const openEditModal = (addr: ShippingAddress) => {
    setEditingAddress(addr)
    setForm({ receiverName: addr.receiverName, receiverPhone: addr.receiverPhone, province: addr.province, city: addr.city, district: addr.district, detailAddress: addr.detailAddress, isDefault: addr.isDefault })
    setModalOpen(true)
  }

  const handleSave = async () => {
    if (!form.receiverName.trim() || !form.receiverPhone.trim() || !form.province.trim() || !form.city.trim() || !form.district.trim() || !form.detailAddress.trim()) {
      onToast('请填写完整地址信息', 'error')
      return
    }
    setSaving(true)
    try {
      if (editingAddress) {
        await updateAddress(editingAddress.id, form as UpdateAddressRequest)
        onToast('更新成功', 'success')
      } else {
        await createAddress(form as CreateAddressRequest)
        onToast('添加成功', 'success')
      }
      setModalOpen(false)
      fetchAddresses()
    } catch {
      onToast('保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteClick = (id: number) => {
    setConfirmState({ type: 'delete', open: true, targetId: id })
  }

  const handleConfirmOk = () => {
    if (confirmState.type === 'delete' && confirmState.targetId) {
      const id = confirmState.targetId
      setConfirmState({ type: 'delete', open: false })
      deleteAddress(id)
        .then(() => { onToast('删除成功', 'success'); fetchAddresses() })
        .catch(() => onToast('删除失败', 'error'))
    } else if (confirmState.type === 'close') {
      setConfirmState({ type: 'close', open: false })
      setModalOpen(false)
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await setDefaultAddress(id)
      onToast('已设为默认地址', 'success')
      fetchAddresses()
    } catch {
      onToast('设置失败', 'error')
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', margin: 0 }}>收货地址</h3>
        <button className={s.primaryBtn} onClick={openAddModal} style={{ padding: '8px 20px', fontSize: 13, borderRadius: 6, boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.25)' }}>
          + 新增地址
        </button>
      </div>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
      ) : addresses.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>📍</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无收货地址</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {addresses.map((addr) => (
            <div key={addr.id} className={s.addressCard}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)' }}>{addr.receiverName}</span>
                    <span style={{ fontSize: 14, color: 'var(--color-text-secondary)' }}>{addr.receiverPhone}</span>
                    {addr.isDefault && <span style={{ padding: '2px 8px', borderRadius: 6, background: 'rgba(var(--color-primary-rgb), 0.1)', color: 'var(--color-primary)', fontSize: 11, fontWeight: 600 }}>默认</span>}
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>{addr.province}{addr.city}{addr.district} {addr.detailAddress}</div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={() => openEditModal(addr)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 12, cursor: 'pointer' }}>编辑</button>
                  <button onClick={() => handleDeleteClick(addr.id)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 12, cursor: 'pointer' }}>删除</button>
                  {!addr.isDefault && (
                    <button onClick={() => handleSetDefault(addr.id)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-primary)', fontSize: 12, cursor: 'pointer' }}>设为默认</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editingAddress ? '编辑地址' : '新增地址'}
        width={520}
        onCancel={() => setConfirmState({ type: 'close', open: false })}
        maskClosable={false}
        footer={null}
        destroyOnClose
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div><label className={s.inputLabel}>收货人</label><input className={s.inputField} value={form.receiverName} onChange={(e) => setForm({ ...form, receiverName: e.target.value })} placeholder="请输入收货人" /></div>
          <div><label className={s.inputLabel}>手机号</label><input className={s.inputField} value={form.receiverPhone} onChange={(e) => setForm({ ...form, receiverPhone: e.target.value })} placeholder="请输入手机号" /></div>
          <div><label className={s.inputLabel}>省份</label><input className={s.inputField} value={form.province} onChange={(e) => setForm({ ...form, province: e.target.value })} placeholder="请输入省份" /></div>
          <div><label className={s.inputLabel}>城市</label><input className={s.inputField} value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} placeholder="请输入城市" /></div>
          <div><label className={s.inputLabel}>区/县</label><input className={s.inputField} value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} placeholder="请输入区/县" /></div>
          <div style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 4 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', color: 'var(--color-text-secondary)', fontSize: 13 }}>
              <input type="checkbox" checked={form.isDefault} onChange={(e) => setForm({ ...form, isDefault: e.target.checked })} style={{ accentColor: 'var(--color-primary)' }} />
              设为默认地址
            </label>
          </div>
        </div>
        <div style={{ marginTop: 16 }}>
          <label className={s.inputLabel}>详细地址</label>
          <textarea className={s.inputField} value={form.detailAddress} onChange={(e) => setForm({ ...form, detailAddress: e.target.value })} placeholder="请输入详细地址" rows={2} style={{ resize: 'vertical' }} />
        </div>
        <div style={{ display: 'flex', gap: 12, marginTop: 24, justifyContent: 'flex-end' }}>
          <button onClick={() => setConfirmState({ type: 'close', open: false })} style={{ padding: '10px 24px', border: '1px solid var(--color-border)', borderRadius: 10, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 14, cursor: 'pointer' }}>取消</button>
          <button className={s.primaryBtn} onClick={handleSave} disabled={saving} style={{ padding: '10px 24px', fontSize: 14, borderRadius: 10 }}>{saving ? '保存中...' : '保存'}</button>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmState.open}
        title={confirmState.type === 'delete' ? '确认删除' : '确认关闭'}
        content={confirmState.type === 'delete' ? '确定要删除该收货地址吗？' : '关闭后未保存的内容将丢失，确认关闭吗？'}
        okText={confirmState.type === 'delete' ? '确认删除' : '确认关闭'}
        cancelText={confirmState.type === 'close' ? '继续编辑' : '取消'}
        danger={confirmState.type === 'delete'}
        onOk={handleConfirmOk}
        onCancel={() => setConfirmState({ ...confirmState, open: false })}
      />
    </div>
  )
}

function WishlistTab({ onToast }: { onToast: (msg: string, type: 'success' | 'error') => void }) {
  const [items, setItems] = useState<WishlistItem[]>([])
  const [loading, setLoading] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [removeTargetId, setRemoveTargetId] = useState<number | null>(null)

  const fetchWishlist = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getWishlistList(1, 20)
      setItems(res.data ?? [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchWishlist() }, [fetchWishlist])

  const handleRemoveClick = (productId: number) => {
    setRemoveTargetId(productId)
    setConfirmOpen(true)
  }

  const handleConfirmOk = () => {
    if (!removeTargetId) return
    const id = removeTargetId
    setConfirmOpen(false)
    setRemoveTargetId(null)
    removeWishlist(id)
      .then(() => { onToast('已取消收藏', 'success'); fetchWishlist() })
      .catch(() => onToast('操作失败', 'error'))
  }

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
  }

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>我的收藏</h3>
      {items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>❤️</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无收藏商品</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 20 }}>
          {items.map((item) => (
            <div key={item.id} className={s.wishlistCard} onClick={() => history.push(`/products/${item.productId}`)}>
              <div style={{ height: 160, background: 'rgba(var(--color-primary-rgb), 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                {item.mainImage ? (
                  <img src={item.mainImage} alt={item.productName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.3)" strokeWidth="1.5">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2" /><circle cx="8.5" cy="8.5" r="1.5" /><polyline points="21 15 16 10 5 21" />
                  </svg>
                )}
              </div>
              <div style={{ padding: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 6 }}>{item.productName}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 16, fontWeight: 700, color: 'var(--color-primary)' }}>¥{item.minPrice.toFixed(2)}</span>
                  <button onClick={(e) => { e.stopPropagation(); handleRemoveClick(item.productId) }} style={{ padding: '3px 8px', border: '1px solid rgba(255,71,87,0.3)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 11, cursor: 'pointer' }}>取消</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="确认取消收藏"
        content="确定要取消收藏该商品吗？"
        okText="确认取消"
        cancelText="再想想"
        danger
        onOk={handleConfirmOk}
        onCancel={() => { setConfirmOpen(false); setRemoveTargetId(null) }}
      />
    </div>
  )
}

export default function UserCenterPage() {
  const { user, fetchProfile } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<TabKey>('profile')
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)
  const [communityProfile, setCommunityProfile] = useState<CommunityProfileData>({ postCount: 0, followerCount: 0, followCount: 0, collectCount: 0, badges: [] })
  const [levelInfo, setLevelInfo] = useState<UserLevelInfo | null>(null)
  const [levelConfigs, setLevelConfigs] = useState<LevelConfig[]>([])
  const [expLogs, setExpLogs] = useState<ExpLogRecord[]>([])
  const [checkedInToday, setCheckedInToday] = useState(false)
  const [continuousDays, setContinuousDays] = useState(0)
  const [editModalOpen, setEditModalOpen] = useState(false)

  useEffect(() => {
    if (toast) { const timer = setTimeout(() => setToast(null), 3000); return () => clearTimeout(timer) }
  }, [toast])

  useEffect(() => {
    const init = async () => { setLoading(true); try { await fetchProfile() } finally { setLoading(false) } }
    init()
  }, [fetchProfile])

  useEffect(() => {
    if (!user?.id) return
    const fetchCommunityProfile = async () => {
      try {
        const { data: res } = await getCommunityProfile(user.id)
        if (res.data) setCommunityProfile({ postCount: res.data.postCount ?? 0, followerCount: res.data.followerCount ?? 0, followCount: res.data.followCount ?? 0, collectCount: res.data.collectCount ?? 0, badges: res.data.badges ?? [] })
      } catch { setCommunityProfile({ postCount: 0, followerCount: 0, followCount: 0, collectCount: 0, badges: [] }) }
    }
    const fetchLevelInfo = async () => { try { const { data: res } = await getUserLevel(); if (res.data) setLevelInfo(res.data) } catch { setLevelInfo(null) } }
    const fetchLevelConfigs = async () => { try { const { data: res } = await getLevelConfigs(); if (res.data) setLevelConfigs(res.data) } catch { setLevelConfigs([]) } }
    const fetchExpLogs = async () => { try { const { data: res } = await getExpLogs(1, 8); if (res.data) setExpLogs(res.data) } catch { setExpLogs([]) } }
    const fetchCheckInStatus = async () => { try { const { data: res } = await getCheckInStatus(); if (res.data != null) setCheckedInToday(res.data) } catch { setCheckedInToday(false) } }
    const fetchContinuousDays = async () => { try { const { data: res } = await getContinuousDays(); if (res.data != null) setContinuousDays(res.data) } catch { setContinuousDays(0) } }
    fetchCommunityProfile(); fetchLevelInfo(); fetchLevelConfigs(); fetchExpLogs(); fetchCheckInStatus(); fetchContinuousDays()
  }, [user?.id])

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', background: 'var(--color-bg-base)' }}><div className={s.spinner} style={{ width: 40, height: 40 }} /></div>
  }

  const statsItems = [
    { label: '帖子', value: communityProfile.postCount, config: STAT_ITEMS_CONFIG[0], onClick: () => { setActiveTab('posts') } },
    { label: '粉丝', value: communityProfile.followerCount, config: STAT_ITEMS_CONFIG[1], onClick: undefined },
    { label: '关注', value: communityProfile.followCount, config: STAT_ITEMS_CONFIG[2], onClick: undefined },
    { label: '收藏', value: communityProfile.collectCount, config: STAT_ITEMS_CONFIG[3], onClick: undefined },
  ]

  const nextLevelConfig = levelConfigs.find((c) => c.level === (levelInfo?.level ?? 0) + 1)
  const currentLevelConfig = levelConfigs.find((c) => c.level === levelInfo?.level)
  const currentBenefits = currentLevelConfig?.benefits ? (() => { try { return JSON.parse(currentLevelConfig.benefits) as string[] } catch { return [] } })() : []

  return (
    <div className={s.userCenter}>
      {toast && (
        <div className={`${s.toast} ${toast.type === 'success' ? s.toastSuccess : s.toastError}`}>
          {toast.message}
        </div>
      )}

      <EditProfileModal open={editModalOpen} onClose={() => setEditModalOpen(false)} onToast={(msg, type) => setToast({ message: msg, type })} />

      <div className={s.hero}>
        <div className={s.heroGlowPrimary} />
        <div className={s.heroGlowPurple} />
        <div className={s.heroLine} />

        <div style={{ maxWidth: 1100, margin: '0 auto', position: 'relative' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 28 }}>
            <div style={{ position: 'relative', width: 100, height: 100, flexShrink: 0 }}>
              <div className={s.avatarRing} />
              <div className={s.avatarInner}>
                {user?.avatar ? (
                  <img src={user.avatar} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                  </svg>
                )}
              </div>
              {levelInfo && <div style={{
                position: 'absolute',
                bottom: -4,
                left: '50%',
                transform: 'translateX(-50%)',
                padding: '2px 10px',
                borderRadius: 10,
                background: 'linear-gradient(135deg, var(--color-accent-gold), var(--color-accent-gold-dark))',
                color: 'var(--color-bg-base)',
                fontSize: 11,
                fontWeight: 800,
                whiteSpace: 'nowrap',
                boxShadow: '0 2px 8px rgba(255, 215, 0, 0.4)',
                letterSpacing: '0.5px',
              }}>{levelInfo.levelIcon || '⭐'} LV{levelInfo.level}</div>}
            </div>

            <div style={{ flex: 1, paddingTop: 4 }}>
              <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--color-text-secondary)', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 12 }}>
                {user?.nickname || user?.username || '用户'}
                {levelInfo && <span style={{
                  padding: '4px 14px',
                  borderRadius: 8,
                  background: 'rgba(var(--color-primary-rgb), 0.1)',
                  border: '1px solid rgba(var(--color-primary-rgb), 0.2)',
                  color: 'var(--color-primary)',
                  fontSize: 13,
                  fontWeight: 600,
                }}>{levelInfo.levelTitle}</span>}
              </h1>

              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 12 }}>
                {user?.email && (
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" /></svg>
                    {user.email}
                  </span>
                )}
                <span style={{
                  padding: '3px 10px',
                  borderRadius: 6,
                  fontSize: 12,
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  background: checkedInToday ? 'rgba(50, 205, 50, 0.1)' : 'rgba(255, 165, 0, 0.1)',
                  border: `1px solid ${checkedInToday ? 'rgba(50, 205, 50, 0.2)' : 'rgba(255, 165, 0, 0.2)'}`,
                  color: checkedInToday ? 'var(--color-accent-green)' : 'var(--color-accent-orange)',
                }}>
                  {checkedInToday ? '✅ 今日已签到' : `🔥 连续${continuousDays}天`}
                </span>
              </div>

              {levelInfo && (
                <div style={{ maxWidth: 360 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>
                      距离 {nextLevelConfig?.title || '下一等级'} 还需 <span style={{ color: 'var(--color-accent-gold)', fontWeight: 600 }}>{nextLevelConfig ? nextLevelConfig.minExp - (levelInfo.totalExp ?? 0) : 0}</span> 经验
                    </span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                      {levelInfo.totalExp ?? 0} / {levelInfo.nextLevelExp || '∞'} EXP
                    </span>
                  </div>
                  <div className={s.expBarBg}>
                    <div className={s.expBarFill} style={{ width: `${Math.min((levelInfo.expProgress ?? 0) * 100, 100)}%` }}>
                      <div className={s.expBarShine} />
                    </div>
                  </div>
                </div>
              )}
            </div>
            <button
              onClick={() => setEditModalOpen(true)}
              style={{
                flexShrink: 0,
                marginTop: 8,
                padding: '8px 20px',
                border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
                borderRadius: 8,
                background: 'transparent',
                color: 'var(--color-primary)',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.25s ease',
              }}
            >
              ✏️ 编辑资料
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginTop: 32 }}>
            {statsItems.map((stat) => (
              <div
                key={stat.label}
                onClick={stat.onClick}
                className={s.statCard}
                style={{
                  background: `linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))`,
                  cursor: stat.onClick ? 'pointer' : 'default',
                  borderRadius: 20,
                  textAlign: 'center',
                }}
              >
                <div style={{ fontSize: 24, marginBottom: 8 }}>{stat.config.icon}</div>
                <span style={{ fontSize: 22, fontWeight: 700, color: `var(${stat.config.cssVar})`, lineHeight: 1 }}>{stat.value}</span>
                <span style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 6 }}>{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 1200, margin: '0 auto', padding: '32px 32px 80px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr 280px', gap: 20, alignItems: 'start' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
              <div className={s.panelHeader} style={{ background: 'rgba(var(--color-primary-rgb), 0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 20px', borderBottom: '1px solid var(--color-border)' }}>
                <span className={s.panelIcon}>🏆</span>
                <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>成就徽章</span>
              </div>
              <div className={s.panelBody}>
                {communityProfile.badges.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '20px 0' }}>
                    <div style={{ fontSize: 32, marginBottom: 8, opacity: 0.5 }}>🏅</div>
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>暂无成就徽章</div>
                    <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, marginTop: 4 }}>积极参与社区互动来获取徽章</div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    {communityProfile.badges.map((badge, index) => {
                      const colorSet = BADGE_COLORS[index % BADGE_COLORS.length]
                      return (
                        <div key={badge.id} title={badge.description} className={s.badgeItem}
                          style={{ background: colorSet.bg, border: `1px solid ${colorSet.border}`, color: colorSet.text, boxShadow: `0 2px 8px ${colorSet.glow}` }}>
                          <span style={{ fontSize: 16 }}>{badge.icon}</span>{badge.name}
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            </div>

            <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
              <div className={s.panelHeader} style={{ background: 'rgba(var(--color-primary-rgb), 0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 20px', borderBottom: '1px solid var(--color-border)' }}>
                <span className={s.panelIcon}>🎁</span>
                <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>当前权益</span>
              </div>
              <div className={s.panelBody}>
                {currentBenefits.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '16px 0' }}>
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>暂无专属权益</div>
                    <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, marginTop: 4 }}>提升等级解锁更多权益</div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                    {currentBenefits.map((benefit, index) => (
                      <div key={index} className={s.benefitRow}>
                        <span style={{ color: 'var(--color-accent-gold)', fontSize: 12 }}>✦</span>
                        <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>{benefit}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
            <div style={{ display: 'flex', gap: 6, padding: '12px 16px', background: 'rgba(var(--color-primary-rgb), 0.08)', borderBottom: '1px solid var(--color-border)', flexWrap: 'wrap' }}>
              {TABS.map((tab) => {
                const isActive = activeTab === tab.key
                return (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '8px 18px',
                      border: 'none',
                      borderRadius: 8,
                      background: isActive ? 'var(--color-gradient-primary)' : 'transparent',
                      color: isActive ? 'var(--color-bg-base)' : 'var(--color-text-secondary)',
                      fontSize: 14,
                      fontWeight: isActive ? 600 : 500,
                      cursor: 'pointer',
                      transition: 'all 0.25s ease',
                      whiteSpace: 'nowrap',
                      boxShadow: isActive ? '0 2px 12px rgba(var(--color-primary-rgb), 0.3)' : 'none',
                    }}
                  >
                    <span style={{ fontSize: 15, lineHeight: 1 }}>{tab.icon}</span>
                    <span>{tab.label}</span>
                  </button>
                )
              })}
            </div>
            <div className={s.tabContent}>
              {activeTab === 'profile' && <ProfileTab />}
              {activeTab === 'posts' && <MyPostsTab />}
              {activeTab === 'drafts' && <MyDraftsTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'address' && <AddressTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'wishlist' && <WishlistTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'liked' && <MyLikedTab />}
              {activeTab === 'replies' && <MyRepliesTab />}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, position: 'sticky', top: 80 }}>
            <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
              <div className={s.panelHeader} style={{ background: 'rgba(var(--color-primary-rgb), 0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 20px', borderBottom: '1px solid var(--color-border)' }}>
                <span className={s.panelIcon}>📊</span>
                <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>等级体系</span>
              </div>
              <div className={s.panelBody}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {levelConfigs.sort((a, b) => a.level - b.level).map((config) => {
                    const isCurrentLevel = config.level === levelInfo?.level
                    const isUnlocked = config.level <= (levelInfo?.level ?? 0)
                    return (
                      <div key={config.id} className={`${s.levelRow} ${isCurrentLevel ? s.levelRowCurrent : isUnlocked ? s.levelRowUnlocked : s.levelRowLocked}`} style={{ paddingLeft: '1em' }}>
                        <span style={{ fontSize: 20, opacity: isUnlocked ? 1 : 0.5 }}>{config.icon}</span>
                        <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
                          <div style={{ fontSize: 13, fontWeight: isCurrentLevel ? 700 : 500, color: isCurrentLevel ? 'var(--color-primary)' : isUnlocked ? 'var(--color-text-secondary)' : 'var(--color-text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            Lv.{config.level} {config.title}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 2 }}>{config.minExp} EXP</div>
                        </div>
                        {isCurrentLevel && <span style={{ padding: '2px 8px', borderRadius: 6, background: 'rgba(var(--color-primary-rgb), 0.15)', color: 'var(--color-primary)', fontSize: 10, fontWeight: 700 }}>当前</span>}
                        {isUnlocked && !isCurrentLevel && <span style={{ color: 'var(--color-accent-green)', fontSize: 14 }}>✓</span>}
                        {!isUnlocked && (
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary)" strokeWidth="2" style={{ opacity: 0.5 }}>
                            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
                          </svg>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>

            <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
              <div className={s.panelHeader} style={{ background: 'rgba(var(--color-primary-rgb), 0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 20px', borderBottom: '1px solid var(--color-border)' }}>
                <span className={s.panelIcon}>⚡</span>
                <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>最近动态</span>
              </div>
              <div className={s.panelBody}>
                {expLogs.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '16px 0' }}><div style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>暂无经验记录</div></div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 0, alignItems: 'center' }}>
                    {expLogs.map((log, index) => {
                      const sourceInfo = EXP_SOURCE_MAP[log.source] || { label: log.source, icon: '📋' }
                      return (
                        <div key={log.id} className={`${s.expLogRow} ${index < expLogs.length - 1 ? s.expLogDivider : ''}`}>
                          <span style={{ fontSize: 16 }}>{sourceInfo.icon}</span>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{log.description || sourceInfo.label}</div>
                            <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 2 }}>{new Date(log.createdAt).toLocaleDateString()}</div>
                          </div>
                          <span style={{ fontSize: 13, fontWeight: 700, color: log.expChange > 0 ? 'var(--color-accent-green)' : 'var(--color-accent-red)', flexShrink: 0 }}>
                            {log.expChange > 0 ? '+' : ''}{log.expChange}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
