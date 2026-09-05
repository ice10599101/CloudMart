import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Input, Modal, Spin, Tag } from 'antd'
import { ArrowLeftOutlined, SendOutlined, PhoneOutlined, ReadOutlined } from '@ant-design/icons'
import { history, useParams } from 'umi'
import {
  getConsentStatus,
  grantConsent,
  listAiConversations,
  sendTreeHoleMessage,
} from '@/api/wish'
import type { AiConversationItem, AiResource } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import styles from './TreeHole.module.css'

/** AI 回复打字机组件（Sprint 2.3 验收：AI 回复有打字机效果） */
function TypewriterText({ text }: { text: string }) {
  const [displayed, setDisplayed] = useState('')
  const idxRef = useRef(0)

  useEffect(() => {
    setDisplayed('')
    idxRef.current = 0
    const timer = setInterval(() => {
      idxRef.current += 2
      if (idxRef.current >= text.length) {
        setDisplayed(text)
        clearInterval(timer)
      } else {
        setDisplayed(text.slice(0, idxRef.current))
      }
    }, 25)
    return () => clearInterval(timer)
  }, [text])

  return <>{displayed}</>
}

/** AI 数据处理协议版本（协议文本管理模块上线前为静态版本） */
const AI_CONSENT_VERSION = 'v1.0'
/** 剩余次数提示阈值 */
const DAILY_LIMIT = 10

interface ChatMessage {
  id: number | string
  role: 'USER' | 'ASSISTANT'
  content: string
  resources?: AiResource[]
  /** 是否为危机拦截回复（特殊样式 + 热线卡片） */
  isCrisis?: boolean
}

export default function TreeHole() {
  const params = useParams<{ id: string }>()
  const wishId = params.id
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [consentOpen, setConsentOpen] = useState(false)
  const [consentAgreeing, setConsentAgreeing] = useState(false)
  /** 同意弹窗确认后待发送的消息 */
  const pendingMessageRef = useRef<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const sentTodayRef = useRef(0)

  /** 加载历史对话（当前心愿关联的树洞会话，按时间正序展示） */
  useEffect(() => {
    if (!user) {
      setLoadingHistory(false)
      return
    }
    const loadHistory = async () => {
      try {
        const res = await listAiConversations({ pageSize: 50 })
        if (res.data.success && res.data.data.length > 0) {
          const historyMessages: ChatMessage[] = res.data.data
            .filter((item: AiConversationItem) => item.id.toString().length > 0)
            .reverse()
            .map((item: AiConversationItem) => ({
              id: item.id,
              role: item.role,
              content: item.content,
              resources: item.resources,
            }))
          setMessages(historyMessages)
        }
      } catch {
        // 历史加载失败不阻断倾诉主流程
      } finally {
        setLoadingHistory(false)
      }
    }
    loadHistory()
  }, [user])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  /** 发送倾诉消息；未同意 AI 协议时先弹协议，同意后自动发送 */
  const handleSend = useCallback(
    async (rawMessage?: string) => {
      const text = (rawMessage ?? input).trim()
      if (!text || sending) return
      if (!user && !userLoading) {
        history.push('/login')
        return
      }

      // 同意状态前置检查（本地缓存 + 接口确认），未同意则弹协议
      try {
        const statusRes = await getConsentStatus()
        if (statusRes.data.success && !statusRes.data.data.granted) {
          pendingMessageRef.current = text
          setConsentOpen(true)
          return
        }
      } catch {
        // 状态查询失败时继续发送，由后端 403 兜底触发协议弹窗
      }

      setInput('')
      setMessages((prev) => [...prev, { id: `local-${Date.now()}`, role: 'USER', content: text }])
      setSending(true)
      try {
        const res = await sendTreeHoleMessage(wishId, { message: text })
        if (res.data.success) {
          const { reply, sentimentScore, resources } = res.data.data
          sentTodayRef.current += 1
          setMessages((prev) => [
            ...prev,
            {
              id: `reply-${Date.now()}`,
              role: 'ASSISTANT',
              content: reply,
              resources,
              // 情感 <= -0.9 且带热线资源 → 危机拦截场景
              isCrisis: sentimentScore !== null && sentimentScore <= -0.9 && resources.some((r) => r.type === 'HOTLINE'),
            },
          ])
          const remaining = DAILY_LIMIT - sentTodayRef.current
          if (remaining > 0 && remaining <= 3) {
            message.info(`今天还可以倾诉 ${remaining} 次`)
          }
        }
      } catch (err) {
        const code = (err as { code?: string })?.code
        if (code === 'WISH_CONSENT_REQUIRED') {
          pendingMessageRef.current = text
          setConsentOpen(true)
        } else if (code === 'WISH_AI_RATE_LIMITED') {
          message.warning((err as Error)?.message || '今日倾诉次数已达上限，明天再来好吗')
        } else if (code === 'WISH_AI_UNAVAILABLE') {
          message.warning('树洞守护者暂时不在，请稍后再来')
        }
        // 发送失败移除本地用户消息，避免误导已送达；文本放回输入框便于重试
        setMessages((prev) => prev.filter((m) => m.content !== text || m.role !== 'USER' || !String(m.id).startsWith('local-')))
        setInput((cur) => (cur.trim() ? cur : text))
      } finally {
        setSending(false)
      }
    },
    [input, sending, user, wishId, message],
  )

  /** 同意 AI 数据处理协议后自动发送待发消息 */
  const handleConsentAgree = async () => {
    setConsentAgreeing(true)
    try {
      const res = await grantConsent({
        consentType: 'AI_DATA_PROCESSING',
        version: AI_CONSENT_VERSION,
        action: 'GRANT',
      })
      if (res.data.success) {
        setConsentOpen(false)
        message.success('感谢信任，你的倾诉会被温柔对待')
        const pending = pendingMessageRef.current
        pendingMessageRef.current = null
        if (pending) {
          setInput('')
          await handleSend(pending)
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setConsentAgreeing(false)
    }
  }

  const remaining = DAILY_LIMIT - sentTodayRef.current

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => history.back()}
          className={styles.backBtn}
        >
          返回
        </Button>
        <div className={styles.headerTitle}>
          <span className={styles.moon}>🌙</span>
          <span>树洞</span>
        </div>
        <span className={styles.remaining}>{remaining > 0 ? `今日剩余 ${remaining} 次` : ''}</span>
      </div>

      <div className={styles.chatArea}>
        {loadingHistory ? (
          <div className={styles.loadingWrap}>
            <Spin tip="树洞正在苏醒..." />
          </div>
        ) : (
          <>
            {messages.length === 0 && (
              <div className={styles.welcome}>
                <div className={styles.welcomeMoon}>🌙</div>
                <p className={styles.welcomeTitle}>这里只属于你</p>
                <p className={styles.welcomeText}>
                  把心事轻轻放进树洞，守护者会认真听。
                  <br />
                  这里没有评判，只有陪伴。
                </p>
              </div>
            )}
            {messages.map((msg, mIdx) =>
              msg.role === 'USER' ? (
                <div key={msg.id} className={styles.userRow}>
                  <div className={styles.userBubble}>{msg.content}</div>
                </div>
              ) : (
                <div key={msg.id} className={styles.aiRow}>
                  <div className={styles.aiAvatar}>🌙</div>
                  <div className={`${styles.aiBubble} ${msg.isCrisis ? styles.aiBubbleCrisis : ''}`}>
                    {mIdx === messages.length - 1 && !loadingHistory ? <TypewriterText text={msg.content} /> : msg.content}
                    {msg.resources && msg.resources.length > 0 && (
                      <div className={styles.resources}>
                        {msg.resources.map((res) => (
                          <a
                            key={res.url}
                            className={styles.resourceCard}
                            href={res.url}
                            target={res.type === 'HOTLINE' ? undefined : '_blank'}
                            rel="noreferrer"
                          >
                            {res.type === 'HOTLINE' ? <PhoneOutlined /> : <ReadOutlined />}
                            <span className={styles.resourceTitle}>{res.title}</span>
                            {res.type === 'HOTLINE' && <Tag color="red">24小时</Tag>}
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ),
            )}
            {sending && (
              <div className={styles.aiRow}>
                <div className={styles.aiAvatar}>🌙</div>
                <div className={styles.aiBubble}>
                  <span className={styles.typing}>守护者正在倾听...</span>
                </div>
              </div>
            )}
          </>
        )}
        <div ref={bottomRef} />
      </div>

      <div className={styles.inputArea}>
        <Input.TextArea
          className={styles.input}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              handleSend()
            }
          }}
          placeholder="心事放在这里，回车发送..."
          maxLength={2000}
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={sending}
        />
        <Button
          type="primary"
          className={styles.sendBtn}
          icon={<SendOutlined />}
          loading={sending}
          onClick={() => handleSend()}
        >
          倾诉
        </Button>
      </div>

      <Modal
        open={consentOpen}
        title="AI 数据处理协议"
        onOk={handleConsentAgree}
        onCancel={() => {
          setConsentOpen(false)
          pendingMessageRef.current = null
        }}
        confirmLoading={consentAgreeing}
        okText="同意并继续"
        cancelText="暂不使用"
      >
        <div className={styles.consentBody}>
          <p>在使用树洞 AI 陪伴前，请了解并同意：</p>
          <ul>
            <li>你的倾诉内容将在<span className={styles.emphasis}>脱敏处理后</span>发送给 AI 服务（通义千问）生成回复</li>
            <li>系统会自动移除消息中的手机号、邮箱、身份证号等个人信息</li>
            <li>对话记录仅你自己可见，可随时联系客服删除</li>
            <li>若你流露出伤害自己的念头，我们会优先为你提供专业心理援助渠道</li>
            <li>你可以随时在设置中撤回本同意</li>
          </ul>
        </div>
      </Modal>
      <WishBGM />
    </div>
  )
}
