import { useState, useEffect, useCallback } from 'react'
import { Alert, Button, Card, Empty, Input, List, Space, Spin, Statistic } from 'antd'
import { ArrowLeftOutlined, CopyOutlined, TeamOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { message } from '@/utils/appMessage'
import { bindInvite, getMyInvite, type MyInviteInfo } from '@/api/user'
import { getMyResourceLogs, type ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'

const LOG_PAGE_SIZE = 20

/** execCommand 兜底复制：非安全上下文（无 navigator.clipboard）时保证可复制 */
function fallbackCopy(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

/**
 * R19 邀请有礼：我的邀请码与复制、绑定他人邀请码（仅一次）、
 * 邀请奖励星光流水（source=INVITE_REWARD，cursor 分页）。
 */
export default function InviteCenter() {
  const { user, userLoading } = useAuthStore()
  const [invite, setInvite] = useState<MyInviteInfo | null>(null)
  const [loadingInvite, setLoadingInvite] = useState(true)
  const [bindCode, setBindCode] = useState('')
  const [binding, setBinding] = useState(false)
  const [bound, setBound] = useState(false)
  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loadingLogs, setLoadingLogs] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const loadInvite = useCallback(async () => {
    setLoadingInvite(true)
    try {
      const res = await getMyInvite()
      if (res.data.success) setInvite(res.data.data)
    } catch {
      // 错误已由 request 拦截器统一提示
    } finally {
      setLoadingInvite(false)
    }
  }, [])

  const loadLogs = useCallback(async (nextCursor?: string | null, reset = false) => {
    if (reset) setLoadingLogs(true)
    else setLoadingMore(true)
    try {
      const res = await getMyResourceLogs({
        source: 'INVITE_REWARD',
        cursor: nextCursor ?? undefined,
        pageSize: LOG_PAGE_SIZE,
      })
      if (res.data.success) {
        const list = res.data.data ?? []
        setLogs((prev) => (reset ? list : [...prev, ...list]))
        setCursor(list.length > 0 ? String(list[list.length - 1].id) : null)
        setHasMore(list.length >= LOG_PAGE_SIZE)
      }
    } catch {
      // 错误已由 request 拦截器统一提示
    } finally {
      setLoadingLogs(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/invite')
      return
    }
    if (user) {
      void loadInvite()
      void loadLogs(null, true)
    }
  }, [user, userLoading, loadInvite, loadLogs])

  const handleCopy = async () => {
    if (!invite?.code) return
    try {
      await navigator.clipboard.writeText(invite.code)
      message.success('邀请码已复制')
    } catch {
      if (fallbackCopy(invite.code)) {
        message.success('邀请码已复制')
      } else {
        message.error('复制失败，请手动选择邀请码复制')
      }
    }
  }

  const handleBind = async () => {
    const code = bindCode.trim()
    if (!code) {
      message.warning('请输入邀请码')
      return
    }
    setBinding(true)
    try {
      const res = await bindInvite(code)
      if (res.data.success) {
        message.success('邀请码绑定成功')
        setBound(true)
        setBindCode('')
        void loadInvite()
        void loadLogs(null, true)
      }
    } catch {
      // 已绑定/邀请码无效等错误由拦截器统一提示，绑定区保持可见
    } finally {
      setBinding(false)
    }
  }

  return (
    <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/profile')} style={{ marginBottom: 12 }}>
        个人中心
      </Button>

      <Card title="我的邀请码" loading={loadingInvite} style={{ marginBottom: 16 }}>
        {invite && (
          <Space size="large" wrap align="center">
            <span style={{ fontSize: 32, fontWeight: 700, letterSpacing: 4, color: 'var(--color-primary)' }}>
              {invite.code}
            </span>
            <Button type="primary" icon={<CopyOutlined />} onClick={() => { void handleCopy() }}>
              复制邀请码
            </Button>
            <Statistic title="已邀请人数" value={invite.invitedCount} prefix={<TeamOutlined />} />
            <Statistic title="每邀请一人奖励" value={invite.rewardPerInvite} suffix="星光" />
          </Space>
        )}
      </Card>

      {!bound && (
        <Card title="绑定邀请码" style={{ marginBottom: 16 }}>
          <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
            <Input
              value={bindCode}
              maxLength={32}
              placeholder="填写好友的邀请码（仅可绑定一次）"
              onChange={(e) => setBindCode(e.target.value)}
              onPressEnter={() => { void handleBind() }}
            />
            <Button type="primary" loading={binding} onClick={() => { void handleBind() }}>
              绑定
            </Button>
          </Space.Compact>
        </Card>
      )}
      {bound && (
        <Alert type="success" showIcon message="邀请码绑定成功" style={{ marginBottom: 16 }} />
      )}

      <Card title="邀请奖励记录">
        {loadingLogs ? (
          <div style={{ textAlign: 'center', padding: '24px 0' }}><Spin /></div>
        ) : logs.length === 0 ? (
          <Empty description="暂无邀请奖励记录" />
        ) : (
          <>
            <List<ResourceLogItem>
              dataSource={logs}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={item.reason || '邀请奖励'}
                    description={new Date(item.createdAt).toLocaleString('zh-CN')}
                  />
                  <span style={{ color: '#52c41a', fontWeight: 600 }}>+{item.amount} 星光</span>
                </List.Item>
              )}
            />
            {hasMore && (
              <div style={{ textAlign: 'center', marginTop: 16 }}>
                <Button loading={loadingMore} onClick={() => loadLogs(cursor, false)}>加载更多</Button>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
