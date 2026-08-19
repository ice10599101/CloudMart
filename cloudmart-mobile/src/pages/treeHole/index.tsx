import { useCallback, useEffect, useRef, useState } from 'react'
import { View, Text, ScrollView, Textarea } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { AiResource } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

/** AI 数据处理协议版本（协议文本管理模块上线前为静态版本） */
const AI_CONSENT_VERSION = 'v1.0'
const DAILY_LIMIT = 10

interface ChatMessage {
  id: number | string
  role: 'USER' | 'ASSISTANT'
  content: string
  resources?: AiResource[]
  isCrisis?: boolean
}

export default function TreeHolePage() {
  const router = useRouter()
  const wishId = Number(router.params.id)
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [consentVisible, setConsentVisible] = useState(false)
  const [consentAgreeing, setConsentAgreeing] = useState(false)
  const pendingMessageRef = useRef<string | null>(null)
  const sentTodayRef = useRef(0)

  /** 加载历史对话（时间正序展示） */
  useEffect(() => {
    if (!isLoggedIn) {
      setLoadingHistory(false)
      return
    }
    const loadHistory = async () => {
      try {
        const res = await wishApi.listAiConversations({ pageSize: 50 })
        if (res.data.success && res.data.data.length > 0) {
          setMessages(
            [...res.data.data]
              .reverse()
              .map((item) => ({
                id: item.id,
                role: item.role,
                content: item.content,
                resources: item.resources,
              })),
          )
        }
      } catch {
        // 历史加载失败不阻断倾诉主流程
      } finally {
        setLoadingHistory(false)
      }
    }
    loadHistory()
  }, [isLoggedIn])

  /** 发送倾诉：未同意 AI 协议时先弹协议（本地 Modal 实现，跨 H5/小程序） */
  const handleSend = useCallback(
    async (rawMessage?: string) => {
      const text = (rawMessage ?? input).trim()
      if (!text || sending) return
      if (!isLoggedIn) {
        Taro.navigateTo({ url: '/pages/login/index' })
        return
      }

      // 同意状态前置检查，未同意弹协议（后端 403 也会兜底触发）
      try {
        const statusRes = await wishApi.getConsentStatus()
        if (statusRes.data.success && !statusRes.data.data.granted) {
          pendingMessageRef.current = text
          setConsentVisible(true)
          return
        }
      } catch {
        // 查询失败继续发送，由后端兜底
      }

      setInput('')
      setMessages((prev) => [...prev, { id: `local-${Date.now()}`, role: 'USER', content: text }])
      setSending(true)
      try {
        const res = await wishApi.sendTreeHoleMessage(wishId, text)
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
              isCrisis:
                sentimentScore !== null &&
                sentimentScore <= -0.9 &&
                resources.some((r) => r.type === 'HOTLINE'),
            },
          ])
          const remaining = DAILY_LIMIT - sentTodayRef.current
          if (remaining > 0 && remaining <= 3) {
            Taro.showToast({ title: `今天还可以倾诉 ${remaining} 次`, icon: 'none' })
          }
        } else {
          handleBusinessBody(res.data)
        }
      } catch (error) {
        // HTTP 403/429/503 在部分平台以异常抛出，解析异常体中的业务码
        handleThrownError(error, text)
      } finally {
        setSending(false)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [input, sending, isLoggedIn, wishId],
  )

  /** 业务错误（success=false 信封）：按 code 分发 */
  const handleBusinessBody = (body: { error?: { code?: string; message?: string } }) => {
    const code = body.error?.code ?? ''
    if (code === 'WISH_CONSENT_REQUIRED') {
      pendingMessageRef.current = input.trim()
      setConsentVisible(true)
      return
    }
    toastByCode(code, body.error?.message)
  }

  /** 异常体业务错误（HTTP 非 2xx 抛出）：从 error.data 提取信封 */
  const handleThrownError = (error: unknown, sentText: string) => {
    const body = (error as { data?: { error?: { code?: string; message?: string } } })?.data
    const code = body?.error?.code ?? ''
    if (code === 'WISH_CONSENT_REQUIRED') {
      pendingMessageRef.current = sentText
      setConsentVisible(true)
      return
    }
    toastByCode(code, body?.error?.message)
    // 发送失败移除本地用户消息
    setMessages((prev) => prev.filter((m) => !(String(m.id).startsWith('local-') && m.content === sentText)))
  }

  const toastByCode = (code: string, fallbackMessage?: string) => {
    const title =
      code === 'WISH_AI_RATE_LIMITED'
        ? '今日倾诉次数已达上限，明天再来好吗'
        : code === 'WISH_AI_UNAVAILABLE'
          ? '树洞守护者暂时不在，请稍后再来'
          : fallbackMessage || '发送失败，请稍后重试'
    Taro.showToast({ title, icon: 'none' })
  }

  /** 同意 AI 数据处理协议后自动发送待发消息 */
  const handleConsentAgree = async () => {
    setConsentAgreeing(true)
    try {
      const res = await wishApi.grantConsent({
        consentType: 'AI_DATA_PROCESSING',
        version: AI_CONSENT_VERSION,
        action: 'GRANT',
      })
      if (res.data.success) {
        setConsentVisible(false)
        Taro.showToast({ title: '你的倾诉会被温柔对待', icon: 'none' })
        const pending = pendingMessageRef.current
        pendingMessageRef.current = null
        if (pending) {
          setInput('')
          await handleSend(pending)
        }
      } else {
        toastByCode(res.data.error?.code ?? '', res.data.error?.message)
      }
    } catch {
      toastByCode('')
    } finally {
      setConsentAgreeing(false)
    }
  }

  /** 热线/文章资源点击 */
  const handleResourceTap = (res: AiResource) => {
    if (res.type === 'HOTLINE') {
      Taro.makePhoneCall({ phoneNumber: res.url.replace('tel:', '') }).catch(() => {
        // H5 端不支持拨号时复制号码
        Taro.setClipboardData({ data: res.url.replace('tel:', '') })
      })
    } else {
      Taro.setClipboardData({ data: res.url })
      Taro.showToast({ title: '链接已复制', icon: 'none' })
    }
  }

  const remaining = DAILY_LIMIT - sentTodayRef.current

  return (
    <View className={styles.container}>
      <CustomNavBar title="树洞" back />

      <ScrollView
        className={styles.chatArea}
        style={{ top: `${statusBarHeight + navBarHeight}px`, bottom: '120rpx' }}
        scrollY
        scrollIntoView={messages.length > 0 ? `msg-${messages[messages.length - 1].id}` : ''}
        scrollWithAnimation
      >
        {loadingHistory ? (
          <View className={styles.loadingWrap}>
            <Text className={styles.loadingText}>树洞正在苏醒...</Text>
          </View>
        ) : messages.length === 0 ? (
          <View className={styles.welcome}>
            <Text className={styles.welcomeMoon}>🌙</Text>
            <Text className={styles.welcomeTitle}>这里只属于你</Text>
            <Text className={styles.welcomeText}>
              把心事轻轻放进树洞，守护者会认真听。{'\n'}这里没有评判，只有陪伴。
            </Text>
          </View>
        ) : (
          messages.map((msg) => (
            <View key={msg.id} id={`msg-${msg.id}`}>
              {msg.role === 'USER' ? (
                <View className={styles.userRow}>
                  <View className={styles.userBubble}>
                    <Text>{msg.content}</Text>
                  </View>
                </View>
              ) : (
                <View className={styles.aiRow}>
                  <View className={styles.aiAvatar}>
                    <Text>🌙</Text>
                  </View>
                  <View className={`${styles.aiBubble} ${msg.isCrisis ? styles.aiBubbleCrisis : ''}`}>
                    <Text>{msg.content}</Text>
                    {msg.resources && msg.resources.length > 0 && (
                      <View className={styles.resources}>
                        {msg.resources.map((res) => (
                          <View
                            key={res.url}
                            className={styles.resourceCard}
                            onClick={() => handleResourceTap(res)}
                          >
                            <Text className={styles.resourceIcon}>
                              {res.type === 'HOTLINE' ? '📞' : '📖'}
                            </Text>
                            <Text className={styles.resourceTitle}>{res.title}</Text>
                            {res.type === 'HOTLINE' && <Text className={styles.resourceTag}>24小时</Text>}
                          </View>
                        ))}
                      </View>
                    )}
                  </View>
                </View>
              )}
            </View>
          ))
        )}
        {sending && (
          <View className={styles.aiRow}>
            <View className={styles.aiAvatar}>
              <Text>🌙</Text>
            </View>
            <View className={styles.aiBubble}>
              <Text className={styles.typing}>守护者正在倾听...</Text>
            </View>
          </View>
        )}
      </ScrollView>

      <View className={styles.inputArea}>
        <Textarea
          className={styles.input}
          value={input}
          onInput={(e) => setInput(e.detail.value)}
          maxlength={2000}
          placeholder="心事放在这里..."
          disabled={sending}
          autoHeight
          showConfirmBar={false}
        />
        <View
          className={`${styles.sendBtn} ${sending || !input.trim() ? styles.sendBtnDisabled : ''}`}
          onClick={() => handleSend()}
        >
          <Text>{sending ? '...' : '倾诉'}</Text>
        </View>
      </View>

      {consentVisible && (
        <View className={styles.modalMask} onClick={() => setConsentVisible(false)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>AI 数据处理协议</Text>
            <View className={styles.modalContent}>
              <Text className={styles.modalText}>
                在使用树洞 AI 陪伴前，请了解并同意：{'\n'}
                · 你的倾诉内容将在脱敏处理后发送给 AI 服务（通义千问）生成回复{'\n'}
                · 系统会自动移除消息中的手机号、邮箱、身份证号等个人信息{'\n'}
                · 对话记录仅你自己可见，可随时联系客服删除{'\n'}
                · 若你流露出伤害自己的念头，我们会优先为你提供专业心理援助渠道{'\n'}
                · 你可以随时撤回本同意
              </Text>
            </View>
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setConsentVisible(false)}>
                <Text>暂不使用</Text>
              </View>
              <View className={styles.modalOk} onClick={handleConsentAgree}>
                <Text>{consentAgreeing ? '提交中...' : '同意并继续'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}

      {remaining <= 3 && remaining > 0 && !loadingHistory && (
        <View className={styles.remainingTip}>
          <Text>今日剩余 {remaining} 次倾诉</Text>
        </View>
      )}
    </View>
  )
}
