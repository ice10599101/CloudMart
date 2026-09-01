import { Modal, Input, Button, message, Tooltip } from 'antd'
import { CopyOutlined, WechatOutlined, ShareAltOutlined, CloseOutlined } from '@ant-design/icons'
import { sharePost } from '@/api/community'

interface ShareModalProps {
  visible: boolean
  onClose: () => void
  postTitle: string
  postId: number
}

const CHANNEL_MAP: Record<string, string> = {
  wechat: 'WECHAT',
  weibo: 'WEIBO',
  qq: 'QQ',
  douban: 'DOUBAN',
  link: 'LINK',
}

const SHARE_CHANNELS = [
  {
    key: 'wechat',
    label: '微信',
    icon: <WechatOutlined style={{ fontSize: 24 }} />,
    color: '#07C160',
    bg: 'rgba(7, 193, 96, 0.12)',
    border: 'rgba(7, 193, 96, 0.25)',
    tooltip: '请复制链接发送到微信',
    buildUrl: () => null,
  },
  {
    key: 'weibo',
    label: '微博',
    icon: <ShareAltOutlined style={{ fontSize: 24 }} />,
    color: '#E6162D',
    bg: 'rgba(230, 22, 45, 0.12)',
    border: 'rgba(230, 22, 45, 0.25)',
    buildUrl: (title: string, url: string) =>
      `https://service.weibo.com/share/share.php?title=${encodeURIComponent(title)}&url=${encodeURIComponent(url)}`,
  },
  {
    key: 'qq',
    label: 'QQ',
    icon: <ShareAltOutlined style={{ fontSize: 24 }} />,
    color: '#12B7F5',
    bg: 'rgba(18, 183, 245, 0.12)',
    border: 'rgba(18, 183, 245, 0.25)',
    buildUrl: (title: string, url: string) =>
      `https://connect.qq.com/widget/shareqq/index.html?title=${encodeURIComponent(title)}&url=${encodeURIComponent(url)}`,
  },
  {
    key: 'douban',
    label: '豆瓣',
    icon: <ShareAltOutlined style={{ fontSize: 24 }} />,
    color: '#00B51D',
    bg: 'rgba(0, 181, 29, 0.12)',
    border: 'rgba(0, 181, 29, 0.25)',
    buildUrl: (title: string, url: string) =>
      `https://www.douban.com/share/service?name=${encodeURIComponent(title)}&href=${encodeURIComponent(url)}`,
  },
]

export default function ShareModal({ visible, onClose, postTitle, postId }: ShareModalProps) {
  const postUrl = `${window.location.origin}/post/${postId}`

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(postUrl)
      sharePost(postId, 'LINK').catch(() => {})
      message.success('链接已复制到剪贴板')
    } catch {
      message.error('复制失败，请手动复制')
    }
  }

  const handleChannelClick = async (channel: (typeof SHARE_CHANNELS)[number]) => {
    const backendChannel = CHANNEL_MAP[channel.key] || 'LINK'
    sharePost(postId, backendChannel).catch(() => {})
    const url = channel.buildUrl(postTitle, postUrl)
    if (url) {
      window.open(url, '_blank', 'width=600,height=500')
    }
  }

  return (
    <Modal
      open={visible}
      onCancel={onClose}
      footer={null}
      width={440}
      centered
      closable={false}
      styles={{
        body: {
          background: 'var(--color-bg-container)',
          border: '1px solid var(--color-border)',
          borderRadius: 16,
          padding: 0,
          overflow: 'hidden',
        },
        mask: { background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' },
      }}
    >
      <div style={{ position: 'relative', padding: '28px 28px 24px' }}>
        <button
          type="button"
          onClick={onClose}
          style={{
            position: 'absolute',
            top: 16,
            right: 16,
            background: 'var(--color-border)',
            border: 'none',
            borderRadius: '50%',
            width: 32,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            color: 'var(--color-text-secondary)',
            fontSize: 14,
            zIndex: 1,
            transition: 'all 0.2s ease',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255,255,255,0.12)'
            e.currentTarget.style.color = '#FFFFFF'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'var(--color-border)'
            e.currentTarget.style.color = 'var(--color-text-secondary)'
          }}
        >
          <CloseOutlined />
        </button>

        <h2
          style={{
            fontSize: 20,
            fontWeight: 700,
            color: 'var(--color-text-secondary)',
            margin: '0 0 24px',
            textAlign: 'center',
          }}
        >
          分享
        </h2>

        <div style={{ marginBottom: 24 }}>
          <div
            style={{
              fontSize: 13,
              color: 'var(--color-text-secondary)',
              marginBottom: 10,
              fontWeight: 500,
            }}
          >
            分享链接
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Input
              value={postUrl}
              readOnly
              style={{
                flex: 1,
                background: 'var(--color-bg-input)',
                border: '1px solid var(--color-border)',
                borderRadius: 10,
                color: '#C8D6E5',
                fontSize: 13,
                padding: '8px 14px',
              }}
            />
            <Button
              icon={<CopyOutlined />}
              onClick={handleCopyLink}
              style={{
                background: 'var(--color-gradient-primary)',
                border: 'none',
                borderRadius: 10,
                color: 'var(--color-bg-base)',
                fontWeight: 600,
                boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.12)',
              }}
            >
              复制链接
            </Button>
          </div>
        </div>

        <div style={{ marginBottom: 24 }}>
          <div
            style={{
              fontSize: 13,
              color: 'var(--color-text-secondary)',
              marginBottom: 14,
              fontWeight: 500,
            }}
          >
            快捷分享
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 20 }}>
            {SHARE_CHANNELS.map((channel) => {
              const button = (
                <button
                  key={channel.key}
                  type="button"
                  onClick={() => handleChannelClick(channel)}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 8,
                    border: 'none',
                    background: 'transparent',
                    cursor: 'pointer',
                    padding: 0,
                    transition: 'transform 0.2s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)'
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)'
                  }}
                >
                  <div
                    style={{
                      width: 52,
                      height: 52,
                      borderRadius: 14,
                      background: channel.bg,
                      border: `1px solid ${channel.border}`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: channel.color,
                      transition: 'all 0.2s ease',
                    }}
                  >
                    {channel.icon}
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>{channel.label}</span>
                </button>
              )

              if (channel.key === 'wechat') {
                return (
                  <Tooltip key={channel.key} title={channel.tooltip} placement="top">
                    {button}
                  </Tooltip>
                )
              }

              return button
            })}
          </div>
        </div>

        <div>
          <div
            style={{
              fontSize: 13,
              color: 'var(--color-text-secondary)',
              marginBottom: 14,
              fontWeight: 500,
            }}
          >
            扫码分享
          </div>
          <div
            style={{
              width: 140,
              height: 140,
              margin: '0 auto',
              borderRadius: 12,
              background: 'var(--color-bg-input)',
              border: '1px solid var(--color-border)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
            }}
          >
            <ShareAltOutlined style={{ fontSize: 32, color: 'var(--color-text-tertiary)' }} />
            <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>扫码分享</span>
          </div>
        </div>
      </div>
    </Modal>
  )
}
