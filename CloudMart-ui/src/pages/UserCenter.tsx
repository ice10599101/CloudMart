import RichText, { richTextToPlainText } from '@/components/RichText'
import { stripHtml, formatDateTime } from '@/utils/format'
import { useState, useEffect, useCallback, useRef } from 'react'
import { createPortal } from 'react-dom'
import { history } from 'umi'
import { Input, Select, DatePicker, Button, Modal, ConfigProvider, Empty, Spin, Pagination } from 'antd'
import { StarOutlined, TrophyOutlined, BookOutlined, GiftOutlined } from '@ant-design/icons'
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
import { getUserCollections, type CollectionPostItem } from '@/api/community'
import { listWishCollections, listMyCollectedDriftBottles, type WishCollectionItem, type DriftBottleItem } from '@/api/wish'
import type { WishlistItem } from '@/api/wishlist'
import { getUserProfile as getCommunityProfile, getUserPosts, getUserDrafts, getLikedPosts, getMyComments, getMyBrowseHistory, type Post, type BrowseHistoryItem } from '@/api/community'
import {
  getUserLevel,
  getExpLogs,
  getLevelConfigs,
  setAvatarFrame as setAvatarFrameApi,
} from '@/api/growth'
import type { UserLevelInfo, LevelConfig, ExpLogRecord } from '@/api/growth'
import { getSigninCalendar } from '@/api/wish'
import type { MyComment } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import { useDecorationStore } from '@/stores/decoration'
import DecoratedAvatar from '@/components/DecoratedAvatar'
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
          <button type="button" onClick={onCancel} style={{
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
          <button type="button" onClick={onOk} style={{
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
  { key: 'wishPosts', label: '心愿帖子', icon: '🌟' },
  { key: 'drafts', label: '我的草稿', icon: '📋' },
  { key: 'address', label: '收货地址', icon: '📍' },
  { key: 'wishlist', label: '我的收藏', icon: '❤️' },
  { key: 'history', label: '浏览足迹', icon: '👣' },
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

/** 权益名称 → 功能明细。level_configs.benefits 只存标签，具体内容由前端语义化展开 */
const BENEFIT_DETAILS: Record<string, string> = {
  基础功能: '发帖、评论、点赞、收藏、关注、私信、每日签到领星光',
  自定义头像框: '解锁头像框装扮，主页与评论区展示个性化头像边框',
  专属标签: '昵称旁展示当前等级专属标签，身份一目了然',
  优先推荐: '发布的帖子在首页推荐流中获得更高曝光权重',
  官方活动优先: '官方活动报名通道优先开放，名额优先分配',
  全部功能: '解锁社区全部功能，无任何限制',
  本站贵宾标识: '获得本站贵宾标识，头像与昵称旁展示贵宾皇冠',
  官方认证: '可申请官方认证标识，认证后展示认证徽章',
  活动特权: '专享活动通道与稀有装扮特权',
}

/** 权益 → 所需等级（与 level_configs.benefits 口径一致） */
const BENEFIT_MIN_LEVEL: Record<string, number> = {
  基础功能: 1,
  自定义头像框: 2,
  专属标签: 3,
  优先推荐: 4,
  官方活动优先: 5,
  本站贵宾标识: 6,
  官方认证: 7,
  活动特权: 8,
  全部功能: 9,
}

/**
 * 权益 → 功能入口。每项权益在站内都有一个可触达的功能/展示页。
 * path 为 '#' 开头表示锚点动作（滚动到主页顶部身份标识区），否则为路由跳转。
 * 「自定义头像框」的入口是面板内的头像框选择器，不在此表登记。
 */
const BENEFIT_ENTRIES: Record<string, { label: string; path: string }> = {
  基础功能: { label: '去发帖', path: '/publish' },
  专属标签: { label: '查看等级标签', path: '#top' },
  优先推荐: { label: '查看首页推荐', path: '/' },
  官方活动优先: { label: '查看官方活动', path: '/wish/activities' },
  本站贵宾标识: { label: '查看贵宾标识', path: '#top' },
  官方认证: { label: '查看认证徽章', path: '#top' },
  活动特权: { label: '查看专属活动', path: '/wish/activities' },
  全部功能: { label: '去逛逛', path: '/' },
}

/** 权益固定顺序（面板按此排列） */
const BENEFIT_ORDER = [
  '基础功能',
  '自定义头像框',
  '专属标签',
  '优先推荐',
  '官方活动优先',
  '本站贵宾标识',
  '官方认证',
  '活动特权',
  '全部功能',
]

const EXP_SOURCE_MAP: Record<string, { label: string; icon: string }> = {
  CHECK_IN: { label: '每日签到', icon: '📅' },
  POST: { label: '发布帖子', icon: '📝' },
  COMMENT: { label: '发表评论', icon: '💬' },
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

/** 根据生日（YYYY-MM-DD）计算星座 */
function getConstellationFromBirthday(dateStr: string): string {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length < 3) return ''
  const month = Number(parts[1])
  const day = Number(parts[2])
  if (!month || !day) return ''
  const md = month * 100 + day
  if (md >= 1222 || md <= 119) return '摩羯座'
  if (md <= 218) return '水瓶座'
  if (md <= 320) return '双鱼座'
  if (md <= 419) return '白羊座'
  if (md <= 520) return '金牛座'
  if (md <= 621) return '双子座'
  if (md <= 722) return '巨蟹座'
  if (md <= 822) return '狮子座'
  if (md <= 922) return '处女座'
  if (md <= 1023) return '天秤座'
  if (md <= 1122) return '天蝎座'
  return '射手座'
}

/** 头像框方案（权益：自定义头像框，Lv2+ 解锁）——选中项持久化并应用于顶部头像 */
const AVATAR_FRAMES = [
  { key: 'none', label: '默认', ring: 'none' },
  { key: 'gold', label: '金环', ring: 'conic-gradient(from 0deg, #ffd700, #ff6b35, #ffd700)' },
  { key: 'purple', label: '紫晕', ring: 'conic-gradient(from 0deg, #9370db, #00d4ff, #9370db)' },
  { key: 'green', label: '翠光', ring: 'conic-gradient(from 0deg, #2ed573, #ffd700, #2ed573)' },
  { key: 'pink', label: '樱粉', ring: 'conic-gradient(from 0deg, #ff7eb3, #ff5a8a, #ff7eb3)' },
  { key: 'rainbow', label: '彩虹', ring: 'conic-gradient(from 0deg, #ff6b6b, #ffd700, #2ed573, #00d4ff, #9370db, #ff6b6b)' },
] as const

const AVATAR_FRAME_KEY = 'avatar_frame'
const readAvatarFrame = () => {
  try { return localStorage.getItem(AVATAR_FRAME_KEY) ?? 'none' } catch { return 'none' }
}

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
        <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
          <Button size="small" icon={<StarOutlined />} onClick={() => history.push('/wish/starlight-log')}>
            星光流水
          </Button>
          <Button size="small" icon={<GiftOutlined />} onClick={() => history.push('/gift/my')}>
            我的礼物
          </Button>
          <Button size="small" icon={<TrophyOutlined />} onClick={() => history.push('/wish/badges')}>
            心愿殿堂
          </Button>
          <Button size="small" icon={<BookOutlined />} onClick={() => history.push('/wish/collections')}>
            心愿收藏
          </Button>
        </div>
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
      mask={{ closable: false }}
      keyboard={false}
      footer={null}
      destroyOnClose
    >
      <div style={{ position: 'relative' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <DecoratedAvatar
          userId={user?.id}
          src={user?.avatar}
          size={72}
          fallback={<span style={{ fontSize: 24, color: 'var(--color-primary)' }}>{user?.nickname?.charAt(0) || 'U'}</span>}
          onClick={() => !avatarUploading && avatarInputRef.current?.click()}
          style={{ cursor: avatarUploading ? 'wait' : 'pointer', opacity: avatarUploading ? 0.6 : 1, transition: 'opacity 0.2s' }}
        />
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
            onChange={(_, dateStr) => {
              const birthdayValue = typeof dateStr === 'string' ? dateStr : ''
              setBirthday(birthdayValue)
              setConstellation(getConstellationFromBirthday(birthdayValue))
            }}
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
        <button type="button" onClick={handleCancel} style={{ padding: '8px 24px', border: '1px solid var(--color-border)', borderRadius: 8, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 14, cursor: 'pointer' }}>取消</button>
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

/** 心愿帖判定：心愿宇宙同步帖的标签含"心愿"（如 ✨ 心愿完成） */
function isWishPost(post: Post): boolean {
  return !!post.tags?.some((t) => (t.name ?? '').includes('心愿'))
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
      const list = res.data ?? []
      // 最新发布的帖子排在最前（后端返回顺序不保证）
      list.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
      // 我的帖子面板只展示普通社区帖，心愿帖在"心愿帖子"面板
      setPosts(list.filter((p) => !isWishPost(p)))
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
          {posts.map((post) => {
            const preview = stripHtml(post.content).slice(0, 80)
            return (
            <div key={post.id} className={s.postCard} onClick={() => history.push(`/post/${post.id}`)}>
              {post.coverImage && (
                <div style={{ height: 110, background: 'rgba(var(--color-primary-rgb), 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                  <img src={post.coverImage} alt={post.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </div>
              )}
              <div style={{ padding: 12 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 6 }}>{post.title}</div>
                {preview && (
                  <RichText content={post.content} clamp={2} variant="preview" style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 8 }} />
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: 12 }}>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>❤️ {post.likeCount}</span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>💬 {post.commentCount}</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{new Date(post.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function WishPostsTab() {
  const { user } = useAuthStore()
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)

  const fetchPosts = useCallback(async () => {
    if (!user?.id) return
    setLoading(true)
    try {
      const { data: res } = await getUserPosts(user.id, 1, 50)
      const list = res.data ?? []
      list.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
      setPosts(list.filter((p) => isWishPost(p)))
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
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>心愿帖子</h3>
      {posts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>🌟</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>还没有心愿帖子</div>
          <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12, marginTop: 6 }}>完成心愿并发布还愿故事后，会同步到这里</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 }}>
          {posts.map((post) => {
            const preview = stripHtml(post.content).slice(0, 80)
            return (
            <div key={post.id} className={s.postCard} onClick={() => history.push(`/post/${post.id}`)}>
              {post.coverImage && (
                <div style={{ height: 110, background: 'rgba(var(--color-primary-rgb), 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                  <img src={post.coverImage} alt={post.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </div>
              )}
              <div style={{ padding: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                  <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 6, background: 'rgba(var(--color-accent-purple-rgb, 156, 108, 255), 0.15)', color: 'var(--color-accent-purple, #9c6cff)', fontWeight: 600 }}>🌟 心愿宇宙</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{post.title}</span>
                </div>
                {preview && (
                  <RichText content={post.content} clamp={2} variant="preview" style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 8 }} />
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: 12 }}>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>❤️ {post.likeCount}</span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>💬 {post.commentCount}</span>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{new Date(post.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function MyDraftsTab({ onToast }: { onToast: (msg: string, type: 'success' | 'error') => void }) {
  const [drafts, setDrafts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)
  const [confirmState, setConfirmState] = useState<{ type: 'delete' | 'publish'; open: boolean; targetId?: number }>({ type: 'delete', open: false })

  const fetchDrafts = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getUserDrafts(1, 50)
      const list = res.data ?? []
      list.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
      setDrafts(list)
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
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 }}>
          {drafts.map((draft) => (
            <div key={draft.id} className={s.postCard} onClick={() => history.push(`/publish?edit=${draft.id}`)}>
              <div style={{ padding: 12 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 6 }}>
                  {draft.title || '未命名草稿'}
                </div>
                <RichText
                  content={draft.content}
                  clamp={2}
                  variant="preview"
                  style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 8 }}
                />
                {!draft.content?.trim() && (
                  <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginBottom: 8 }}>暂无内容</div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                    {new Date(draft.createdAt).toLocaleDateString()}
                  </span>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handlePublishDraft(draft.id) }}
                      style={{ padding: '2px 10px', border: '1px solid rgba(var(--color-primary-rgb), 0.3)', borderRadius: 6, background: 'rgba(var(--color-primary-rgb), 0.08)', color: 'var(--color-primary)', fontSize: 12, cursor: 'pointer' }}
                    >
                      发布
                    </button>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handleDeleteDraft(draft.id) }}
                      style={{ padding: '2px 10px', border: '1px solid rgba(255,71,87,0.3)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 12, cursor: 'pointer' }}
                    >
                      删除
                    </button>
                  </div>
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

type BrowseKind = 'wish' | 'post' | 'product'

/** 浏览足迹类型元信息：标签、图标与主题色 */
const BROWSE_KIND_META: Record<BrowseKind, { label: string; emoji: string; color: string; bg: string }> = {
  wish: { label: '心愿', emoji: '🌟', color: 'var(--color-accent-purple)', bg: 'rgba(156, 108, 255, 0.14)' },
  post: { label: '帖子', emoji: '📝', color: 'var(--color-primary)', bg: 'rgba(var(--color-primary-rgb), 0.12)' },
  product: { label: '商品', emoji: '🛍️', color: 'var(--color-accent-orange)', bg: 'rgba(255, 165, 0, 0.14)' },
}

/** 足迹 targetType → 展示类型 */
function browseKindOf(targetType: BrowseHistoryItem['targetType']): BrowseKind {
  if (targetType === 'PRODUCT') return 'product'
  if (targetType === 'POST') return 'post'
  return 'wish'
}

/** 足迹 → 详情页路由（跳转到具体浏览过的对象） */
function browseDetailRoute(item: BrowseHistoryItem): string {
  const kind = browseKindOf(item.targetType)
  if (kind === 'product') return `/products/${item.targetId}`
  if (kind === 'post') return `/post/${item.targetId}`
  return `/wish/${item.targetId}`
}

/** 浏览足迹页签：调用真实足迹接口分页展示，点击跳转对应详情页 */
function BrowseHistoryTab() {
  const [items, setItems] = useState<BrowseHistoryItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const PAGE_SIZE = 20

  const load = useCallback(async (targetPage: number) => {
    setLoading(true)
    try {
      const { data: res } = await getMyBrowseHistory(targetPage, PAGE_SIZE)
      setItems(res.data ?? [])
      setTotal(res.meta?.total ?? 0)
    } catch {
      // 未登录或加载失败展示空态；错误提示由拦截器统一处理
      setItems([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(1)
  }, [load])

  return (
    <div>
      <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 16 }}>
        浏览足迹
        <span style={{ fontSize: 12, fontWeight: 400, color: 'var(--color-text-tertiary)', marginLeft: 8 }}>
          共 {total} 条
        </span>
      </h3>
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
          <Spin size="small" />
        </div>
      ) : items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有浏览足迹，去逛逛吧" />
      ) : (
        <>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {items.map((item) => {
              const meta = BROWSE_KIND_META[browseKindOf(item.targetType)]
              return (
                <div key={item.id} className={s.addressCard} style={{ cursor: 'pointer' }} onClick={() => history.push(browseDetailRoute(item))}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ fontSize: 18, flexShrink: 0 }}>{meta.emoji}</span>
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        fontSize: 13,
                        color: 'var(--color-text-secondary)',
                        fontWeight: 500,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {item.title || `${meta.label}详情`}
                    </span>
                    <span style={{ padding: '2px 8px', borderRadius: 6, fontSize: 11, background: meta.bg, color: meta.color, flexShrink: 0 }}>
                      {meta.label}
                    </span>
                    <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)', flexShrink: 0, width: 64, textAlign: 'right' }}>
                      {formatDateTime(item.viewedAt)}
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
          {total > PAGE_SIZE && (
            <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={total}
                showSizeChanger={false}
                size="small"
                onChange={(targetPage) => {
                  setPage(targetPage)
                  void load(targetPage)
                }}
              />
            </div>
          )}
        </>
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
        <button type="button" className={s.primaryBtn} onClick={openAddModal} style={{ padding: '8px 20px', fontSize: 13, borderRadius: 6, boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.25)' }}>
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
                  <button type="button" onClick={() => openEditModal(addr)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 12, cursor: 'pointer' }}>编辑</button>
                  <button type="button" onClick={() => handleDeleteClick(addr.id)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 12, cursor: 'pointer' }}>删除</button>
                  {!addr.isDefault && (
                    <button type="button" onClick={() => handleSetDefault(addr.id)} style={{ padding: '4px 12px', border: '1px solid var(--color-border)', borderRadius: 6, background: 'transparent', color: 'var(--color-primary)', fontSize: 12, cursor: 'pointer' }}>设为默认</button>
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
        onCancel={() => setConfirmState({ type: 'close', open: true })}
        mask={{ closable: false }}
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
          <button type="button" onClick={() => setConfirmState({ type: 'close', open: true })} style={{ padding: '10px 24px', border: '1px solid var(--color-border)', borderRadius: 10, background: 'transparent', color: 'var(--color-text-secondary)', fontSize: 14, cursor: 'pointer' }}>取消</button>
          <button type="button" className={s.primaryBtn} onClick={handleSave} disabled={saving} style={{ padding: '10px 24px', fontSize: 14, borderRadius: 10 }}>{saving ? '保存中...' : '保存'}</button>
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
  const { user } = useAuthStore()
  const [category, setCategory] = useState<'products' | 'posts' | 'wishes' | 'bottles'>('products')
  // 商品收藏
  const [items, setItems] = useState<WishlistItem[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [removeTargetId, setRemoveTargetId] = useState<number | null>(null)
  // 帖子收藏 / 心愿收藏 / 漂流瓶收藏（懒加载）
  const [postCollects, setPostCollects] = useState<CollectionPostItem[] | null>(null)
  const [wishCollects, setWishCollects] = useState<WishCollectionItem[] | null>(null)
  const [bottleCollects, setBottleCollects] = useState<DriftBottleItem[] | null>(null)
  const [loading, setLoading] = useState(false)

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

  // 帖子收藏：切到帖子分类时懒加载
  useEffect(() => {
    if (category !== 'posts' || postCollects || !user?.id) return
    getUserCollections(user.id, 1, 50)
      .then((res) => setPostCollects(res.data.data ?? []))
      .catch(() => setPostCollects([]))
  }, [category, postCollects, user?.id])

  // 心愿收藏：切到心愿分类时懒加载
  useEffect(() => {
    if (category !== 'wishes' || wishCollects) return
    listWishCollections(undefined, 50)
      .then((res) => setWishCollects(res.data.data ?? []))
      .catch(() => setWishCollects([]))
  }, [category, wishCollects])

  // 漂流瓶收藏：切到漂流瓶分类时懒加载
  useEffect(() => {
    if (category !== 'bottles' || bottleCollects) return
    listMyCollectedDriftBottles()
      .then((res) => setBottleCollects(res.data.data ?? []))
      .catch(() => setBottleCollects([]))
  }, [category, bottleCollects])

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

  const categories: Array<{ key: 'products' | 'posts' | 'wishes' | 'bottles'; label: string }> = [
    { key: 'products', label: '🛍️ 商品' },
    { key: 'posts', label: '📝 帖子' },
    { key: 'wishes', label: '🌟 心愿' },
    { key: 'bottles', label: '🍾 漂流瓶' },
  ]

  return (
    <div>
      {/* 收藏分类切换 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {categories.map((cat) => {
          const active = category === cat.key
          return (
            <button
              key={cat.key}
              type="button"
              onClick={() => setCategory(cat.key)}
              style={{
                padding: '6px 16px',
                borderRadius: 8,
                border: active ? '1px solid rgba(var(--color-primary-rgb), 0.4)' : '1px solid var(--color-border)',
                background: active ? 'rgba(var(--color-primary-rgb), 0.1)' : 'transparent',
                color: active ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                fontSize: 13,
                fontWeight: active ? 600 : 400,
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
            >
              {cat.label}
            </button>
          )
        })}
      </div>

      {/* 商品收藏 */}
      {category === 'products' && (
        loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
        ) : items.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>🛍️</div>
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
                    <button type="button" onClick={(e) => { e.stopPropagation(); handleRemoveClick(item.productId) }} style={{ padding: '3px 8px', border: '1px solid rgba(255,71,87,0.3)', borderRadius: 6, background: 'transparent', color: 'var(--color-accent-red)', fontSize: 11, cursor: 'pointer' }}>取消</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      )}

      {/* 帖子收藏 */}
      {category === 'posts' && (
        postCollects === null ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
        ) : postCollects.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>📝</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无收藏的帖子</div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 20 }}>
            {postCollects.map((post) => (
              <div
                key={post.id}
                className={s.wishlistCard}
                onClick={() => history.push(`/post/${post.id}`)}
                style={{ display: 'flex', gap: 14, alignItems: 'center' }}
              >
                {post.coverImage && (
                  <img src={post.coverImage} alt={post.title} style={{ width: 96, height: 64, borderRadius: 8, objectFit: 'cover', flexShrink: 0 }} />
                )}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 4 }}>{post.title}</div>
                  <div style={{ display: 'flex', gap: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                    <span>❤️ {post.likeCount}</span>
                    <span>💬 {post.commentCount}</span>
                    <span>{'collectedAt' in post && post.collectedAt ? new Date(String(post.collectedAt)).toLocaleDateString() : ''}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      )}

      {/* 心愿收藏 */}
      {category === 'wishes' && (
        wishCollects === null ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
        ) : wishCollects.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>🌟</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>暂无收藏的心愿</div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 20 }}>
            {wishCollects.map((item) => (
              <div
                key={item.collectionId}
                className={s.wishlistCard}
                onClick={() => history.push(`/wish/${item.wishId}`)}
                style={{ display: 'flex', gap: 14, alignItems: 'center' }}
              >
                <div style={{ fontSize: 26, flexShrink: 0 }}>🌟</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 4 }}>{item.title}</div>
                  <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                    收藏于 {new Date(item.collectedAt).toLocaleDateString('zh-CN')}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      )}

      {/* 漂流瓶收藏：捞起时收藏的瓶子，点击进入漂流瓶页查看 */}
      {category === 'bottles' && (
        bottleCollects === null ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}><div className={s.spinner} /></div>
        ) : bottleCollects.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.3 }}>🍾</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14 }}>还没有收藏的漂流瓶，去海上捞一个吧</div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 20 }}>
            {bottleCollects.map((bottle) => {
              const excerpt = bottle.wishTitle ?? richTextToPlainText(bottle.content).slice(0, 60)
              return (
                <div
                  key={bottle.bottleId}
                  className={s.wishlistCard}
                  onClick={() => history.push('/wish/drift-bottle')}
                  style={{ display: 'flex', gap: 14, alignItems: 'center' }}
                >
                  <div style={{ fontSize: 26, flexShrink: 0 }}>🍾</div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 4 }}>
                      {excerpt || '一只空瓶子'}
                    </div>
                    <div style={{ display: 'flex', gap: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                      {bottle.wishId && <span>关联心愿</span>}
                      <span>💬 {bottle.commentCount}</span>
                      <span>捞于 {bottle.pickedAt ? new Date(bottle.pickedAt).toLocaleDateString('zh-CN') : '-'}</span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )
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
  // 最近动态：社区行为流（发帖/评论）
  const [activities, setActivities] = useState<Array<{ key: string; type: 'post' | 'comment'; postId: number; title: string; preview?: string; createdAt: string }> | null>(null)
  const [expLogs, setExpLogs] = useState<ExpLogRecord[]>([])
  const [expLogsOpen, setExpLogsOpen] = useState(false)
  // 当前权益面板：手风琴展开的权益名
  const [expandedBenefit, setExpandedBenefit] = useState<string | null>(null)
  // 等级（权益判定基准；levelInfo 加载完成为准，未加载用缓存）
  const userLevel = levelInfo?.level ?? Number(localStorage.getItem('user_level') ?? 0)
  // 自定义头像框（Lv2+ 权益）：未达标时锁定为默认
  const [avatarFrame, setAvatarFrame] = useState(readAvatarFrame())
  const frameUnlocked = userLevel >= 2
  const applyAvatarFrame = async (key: string) => {
    if (!frameUnlocked) return
    try {
      if (user?.id) {
        await setAvatarFrameApi(key)
        useDecorationStore.getState().setAvatarFrame(user.id, key)
      }
      localStorage.setItem(AVATAR_FRAME_KEY, key)
      window.dispatchEvent(new CustomEvent('avatar-frame-changed', { detail: key }))
      setAvatarFrame(key)
    } catch {
      setToast({ message: '头像框保存失败，请稍后重试', type: 'error' })
    }
  }
  const activeFrame = AVATAR_FRAMES.find((f) => f.key === avatarFrame) ?? AVATAR_FRAMES[0]
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
    const fetchRecentActivities = async () => {
      try {
        // 聚合最新帖子与评论，按时间倒序组成社区行为流
        const [postsRes, commentsRes] = await Promise.all([
          getUserPosts(user!.id, 1, 3),
          getMyComments(1, 3),
        ])
        const postActs = (postsRes.data.data ?? []).map((p) => ({
          key: `post-${p.id}`,
          type: 'post' as const,
          postId: p.id,
          title: p.title,
          createdAt: p.createdAt,
        }))
        const commentActs = (commentsRes.data.data ?? []).map((c: MyComment) => ({
          key: `comment-${c.id}`,
          type: 'comment' as const,
          postId: c.postId,
          title: c.postTitle,
          preview: stripHtml(c.content).slice(0, 40),
          createdAt: c.createdAt,
        }))
        setActivities([...postActs, ...commentActs]
          .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
          .slice(0, 6))
        // 经验变动折叠小节数据
        try { const logRes = await getExpLogs(1, 8); if (logRes.data) setExpLogs(logRes.data.data ?? []) } catch { setExpLogs([]) }
      } catch {
        setActivities([])
      }
    }
    // 连续签到徽章数据来自心愿每日签到（用户实际使用的签到入口），
    // 社区成长签到是独立数据源，无 UI 入口，不反映真实签到行为
    const fetchSigninOverview = async () => {
      try {
        const now = new Date()
        const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
        const todayStr = `${month}-${String(now.getDate()).padStart(2, '0')}`
        const { data: res } = await getSigninCalendar(month)
        if (res.data) {
          setContinuousDays(res.data.consecutiveDays)
          setCheckedInToday(res.data.signedDates.includes(todayStr))
        }
      } catch {
        setCheckedInToday(false)
        setContinuousDays(0)
      }
    }
    fetchCommunityProfile(); fetchLevelInfo(); fetchLevelConfigs(); fetchRecentActivities(); fetchSigninOverview()
  }, [user?.id])

  // 等级持久化：Home（优先推荐标记）/Activities（官方活动优先横幅）读取（必须在 hooks 区）
  useEffect(() => {
    if (levelInfo?.level) {
      try { localStorage.setItem('user_level', String(levelInfo.level)) } catch { /* ignore */ }
    }
  }, [levelInfo?.level])

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', background: 'var(--color-bg-base)' }}><div className={s.spinner} style={{ width: 40, height: 40 }} /></div>
  }

  const statsItems = [
    { label: '帖子', value: communityProfile.postCount, config: STAT_ITEMS_CONFIG[0], onClick: () => { setActiveTab('posts') } },
    { label: '粉丝', value: communityProfile.followerCount, config: STAT_ITEMS_CONFIG[1], onClick: () => { history.push(`/user/${user?.id}/following?tab=followers`) } },
    { label: '关注', value: communityProfile.followCount, config: STAT_ITEMS_CONFIG[2], onClick: () => { history.push(`/user/${user?.id}/following?tab=following`) } },
    { label: '收藏', value: communityProfile.collectCount, config: STAT_ITEMS_CONFIG[3], onClick: () => { setActiveTab('wishlist') } },
  ]

  const nextLevelConfig = levelConfigs.find((c) => c.level === (levelInfo?.level ?? 0) + 1)
  // 全量权益列表：固定顺序 + 每项所需等级 + 拥有状态
  const allBenefits = BENEFIT_ORDER.map((name) => ({
    name,
    minLevel: BENEFIT_MIN_LEVEL[name] ?? 1,
    owned: userLevel >= (BENEFIT_MIN_LEVEL[name] ?? 1),
  }))

  return (
    <div className={s.userCenter}>
      {toast && createPortal(
        <div className={`${s.toast} ${toast.type === 'success' ? s.toastSuccess : s.toastError}`}>
          {toast.message}
        </div>,
        document.body,
      )}

      <EditProfileModal open={editModalOpen} onClose={() => setEditModalOpen(false)} onToast={(msg, type) => setToast({ message: msg, type })} />

      <div className={s.hero}>
        <div className={s.heroGlowPrimary} />
        <div className={s.heroGlowPurple} />
        <div className={s.heroLine} />

        <div style={{ maxWidth: 1100, margin: '0 auto', position: 'relative' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 28 }}>
            <DecoratedAvatar
              userId={user?.id}
              src={user?.avatar}
              size={100}
              fallback={
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                </svg>
              }
            />

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
                {userLevel >= 7 && (
                    <span style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      padding: '4px 12px',
                      borderRadius: 8,
                      background: 'linear-gradient(135deg, #ffd700, #ff6b35)',
                      color: '#fff',
                      fontSize: 12,
                      fontWeight: 700,
                    }} title="Lv7 官方认证">🏅 官方认证</span>
                )}
              </h1>

              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 12 }}>
                {user?.email && (
                  <span style={{ color: 'var(--color-text-secondary)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" /></svg>
                    {user.email}
                  </span>
                )}
                <span
                  onClick={() => history.push('/wish/signin')}
                  style={{
                    padding: '3px 10px',
                    borderRadius: 6,
                    fontSize: 12,
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    cursor: 'pointer',
                    background: checkedInToday ? 'rgba(50, 205, 50, 0.1)' : 'rgba(255, 165, 0, 0.1)',
                    border: `1px solid ${checkedInToday ? 'rgba(50, 205, 50, 0.2)' : 'rgba(255, 165, 0, 0.2)'}`,
                    color: checkedInToday ? 'var(--color-accent-green)' : 'var(--color-accent-orange)',
                  }}>
                  {checkedInToday ? `✅ 已签 · 连续${continuousDays}天` : `🔥 连续${continuousDays}天`}
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
              type="button"
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
                  cursor: !!stat.onClick ? 'pointer' : 'default',
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
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {allBenefits.map((benefit) => {
                    const isExpanded = expandedBenefit === benefit.name
                    const detail = BENEFIT_DETAILS[benefit.name] ?? '提升等级即可享受该权益'
                    return (
                        <div key={benefit.name}>
                          <button
                            type="button"
                            onClick={() => setExpandedBenefit(isExpanded ? null : benefit.name)}
                            aria-expanded={isExpanded}
                            style={{
                              width: '100%',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              padding: '8px 6px',
                              border: 'none',
                              background: isExpanded ? 'rgba(var(--color-primary-rgb), 0.08)' : 'transparent',
                              borderRadius: 8,
                              cursor: 'pointer',
                              transition: 'background 0.2s',
                              textAlign: 'left',
                            }}
                          >
                            <span style={{ color: benefit.owned ? 'var(--color-accent-gold)' : 'var(--color-text-tertiary)', fontSize: 12 }}>
                              {benefit.owned ? '✦' : '🔒'}
                            </span>
                            <span style={{
                              color: benefit.owned ? 'var(--color-text-secondary)' : 'var(--color-text-tertiary)',
                              fontSize: 13,
                              flex: 1,
                            }}>{benefit.name}</span>
                            <span style={{
                              fontSize: 10,
                              padding: '1px 6px',
                              borderRadius: 6,
                              background: benefit.owned ? 'rgba(50, 205, 50, 0.15)' : 'rgba(255, 255, 255, 0.08)',
                              color: benefit.owned ? 'var(--color-accent-green)' : 'var(--color-text-tertiary)',
                              flexShrink: 0,
                            }}>
                              {benefit.owned ? '已拥有' : `Lv${benefit.minLevel} 解锁`}
                            </span>
                            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 11, transform: isExpanded ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }}>▸</span>
                          </button>
                          {isExpanded && (
                            <div style={{
                              margin: '4px 6px 6px 26px',
                              padding: '8px 10px',
                              borderRadius: 8,
                              background: 'rgba(var(--color-primary-rgb), 0.05)',
                              border: '1px solid rgba(var(--color-primary-rgb), 0.12)',
                              color: 'var(--color-text-tertiary)',
                              fontSize: 12,
                              lineHeight: 1.7,
                            }}>
                              {detail}
                              {!benefit.owned && (
                                <span style={{ color: 'var(--color-accent-gold)' }}>（需 Lv{benefit.minLevel}，当前 Lv{userLevel}）</span>
                              )}
                              {benefit.name === '自定义头像框' && benefit.owned && (
                                <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                                  {AVATAR_FRAMES.map((f) => {
                                    const active = avatarFrame === f.key
                                    return (
                                        <button
                                          key={f.key}
                                          type="button"
                                          title={f.label}
                                          aria-label={`头像框：${f.label}`}
                                          onClick={() => applyAvatarFrame(f.key)}
                                          style={{
                                            width: 34,
                                            height: 34,
                                            borderRadius: '50%',
                                            padding: 0,
                                            border: active ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                                            background: f.ring === 'none'
                                                ? 'var(--color-bg-input)'
                                                : f.ring,
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                          }}
                                        >
                                          {f.ring === 'none' && (
                                              <span style={{ fontSize: 10, color: 'var(--color-text-tertiary)' }}>无</span>
                                          )}
                                        </button>
                                    )
                                  })}
                                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 11 }}>
                                    当前：{activeFrame.label}（顶栏头像同步生效）
                                  </span>
                                </div>
                              )}
                              {benefit.owned && benefit.name !== '自定义头像框' && BENEFIT_ENTRIES[benefit.name] && (
                                <div style={{ marginTop: 8 }}>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      const path = BENEFIT_ENTRIES[benefit.name].path
                                      if (path.startsWith('#')) window.scrollTo({ top: 0, behavior: 'smooth' })
                                      else history.push(path)
                                    }}
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      gap: 4,
                                      padding: '4px 12px',
                                      border: '1px solid rgba(var(--color-primary-rgb), 0.35)',
                                      borderRadius: 12,
                                      background: 'rgba(var(--color-primary-rgb), 0.1)',
                                      color: 'var(--color-primary)',
                                      fontSize: 12,
                                      fontWeight: 600,
                                      cursor: 'pointer',
                                      transition: 'all 0.2s',
                                    }}
                                  >
                                    {BENEFIT_ENTRIES[benefit.name].label} →
                                  </button>
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                    )
                  })}
                  <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, textAlign: 'center', padding: '4px 0 2px' }}>
                    点击权益查看功能详情 · 已拥有 {allBenefits.filter((b) => b.owned).length}/{allBenefits.length}
                  </div>
                </div>
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
                    type="button"
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
              {activeTab === 'wishPosts' && <WishPostsTab />}
              {activeTab === 'drafts' && <MyDraftsTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'address' && <AddressTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'wishlist' && <WishlistTab onToast={(msg, type) => setToast({ message: msg, type })} />}
              {activeTab === 'history' && <BrowseHistoryTab />}
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
              <div className={s.panelBody} style={{ padding: '8px 12px' }}>
                {!activities ? (
                  <div style={{ textAlign: 'center', padding: '16px 0' }}><div className={s.spinner} style={{ width: 28, height: 28 }} /></div>
                ) : activities.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '16px 0' }}><div style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>暂无社区动态</div></div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {activities.map((act, index) => (
                      <div
                        key={act.key}
                        onClick={() => history.push(`/post/${act.postId}`)}
                        className={`${s.expLogRow} ${index < activities.length - 1 ? s.expLogDivider : ''}`}
                        style={{ cursor: 'pointer' }}
                        title="查看详情"
                      >
                        <span style={{ fontSize: 16 }}>{act.type === 'post' ? '📝' : '💬'}</span>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {act.type === 'post' ? '发布了帖子' : '评论了'}《{act.title}》
                          </div>
                          {act.preview && (
                            <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{act.preview}</div>
                          )}
                          <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 2 }}>{new Date(act.createdAt).toLocaleDateString()}</div>
                        </div>
                        <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)', flexShrink: 0 }}>→</span>
                      </div>
                    ))}
                  </div>
                )}

                <details
                  onToggle={(e) => setExpLogsOpen((e.target as HTMLDetailsElement).open)}
                  style={{ marginTop: 10, borderTop: '1px dashed var(--color-border)', paddingTop: 8 }}
                >
                  <summary style={{ cursor: 'pointer', fontSize: 12, color: 'var(--color-text-tertiary)', userSelect: 'none' }}>
                    {expLogsOpen ? '收起经验变动' : '经验变动'}
                  </summary>
                  <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column' }}>
                    {expLogs.length === 0 ? (
                      <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', textAlign: 'center', padding: '8px 0' }}>暂无经验变动</div>
                    ) : (
                      expLogs.map((log) => (
                        <div key={log.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, padding: '4px 0', fontSize: 12 }}>
                          <span style={{ color: 'var(--color-text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0 }}>
                            {EXP_SOURCE_MAP[log.source]?.label ?? log.description ?? log.source}
                          </span>
                          <span style={{ fontWeight: 600, color: log.expChange > 0 ? 'var(--color-accent-green)' : 'var(--color-accent-red)', flexShrink: 0 }}>
                            {log.expChange > 0 ? '+' : ''}{log.expChange} 经验
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </details>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
