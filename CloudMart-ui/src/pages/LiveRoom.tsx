import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams } from 'umi'
import { getLiveRoom, enterLiveRoom } from '@/api/live'
import type { LiveRoom } from '@/api/live'
import { useAuthStore } from '@/stores/auth'

interface DanmakuMessage {
  id: number
  username: string
  content: string
  timestamp: number
  type: 'chat' | 'system' | 'like'
}

export default function LiveRoomPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const numericRoomId = Number(roomId)
  const { user, accessToken } = useAuthStore()

  const [room, setRoom] = useState<LiveRoom | null>(null)
  const [loading, setLoading] = useState(true)
  const [messages, setMessages] = useState<DanmakuMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [likes, setLikes] = useState(0)
  const [wsConnected, setWsConnected] = useState(false)

  const wsRef = useRef<WebSocket | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const msgIdRef = useRef(0)
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectAttemptsRef = useRef(0)

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  const addSystemMessage = useCallback((content: string) => {
    msgIdRef.current += 1
    setMessages((prev) => [
      ...prev,
      { id: msgIdRef.current, username: '系统', content, timestamp: Date.now(), type: 'system' },
    ])
  }, [])

  const connectWebSocket = useCallback(() => {
    if (!accessToken) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}/ws/live/${numericRoomId}?token=${encodeURIComponent(accessToken)}`

    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      setWsConnected(true)
      reconnectAttemptsRef.current = 0
      addSystemMessage('已连接到直播间')
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data as string) as {
          type?: string
          username?: string
          content?: string
          [key: string]: unknown
        }
        if (data.type === 'like') {
          setLikes((prev) => prev + 1)
          return
        }
        if (data.type === 'system') {
          addSystemMessage(data.content ?? '')
          return
        }
        msgIdRef.current += 1
        setMessages((prev) => [
          ...prev,
          {
            id: msgIdRef.current,
            username: data.username ?? '匿名',
            content: data.content ?? '',
            timestamp: Date.now(),
            type: 'chat',
          },
        ])
      } catch {
        if ((event.data as string) === 'pong') return
      }
    }

    ws.onclose = () => {
      setWsConnected(false)
      if (reconnectAttemptsRef.current < 5) {
        const delay = 1000 * Math.pow(2, reconnectAttemptsRef.current)
        reconnectAttemptsRef.current += 1
        reconnectRef.current = setTimeout(() => connectWebSocket(), delay)
      }
    }

    ws.onerror = () => {
      setWsConnected(false)
    }
  }, [accessToken, numericRoomId, addSystemMessage])

  useEffect(() => {
    const fetchRoom = async () => {
      setLoading(true)
      try {
        const { data: res } = await getLiveRoom(numericRoomId)
        setRoom(res.data)
        try {
          await enterLiveRoom(numericRoomId)
        } catch {
          // enter room is best-effort
        }
      } finally {
        setLoading(false)
      }
    }
    fetchRoom()
  }, [numericRoomId])

  useEffect(() => {
    connectWebSocket()
    return () => {
      if (reconnectRef.current) clearTimeout(reconnectRef.current)
      if (wsRef.current) {
        wsRef.current.onclose = null
        wsRef.current.close()
        wsRef.current = null
      }
    }
  }, [connectWebSocket])

  useEffect(() => {
    scrollToBottom()
  }, [messages, scrollToBottom])

  const sendMessage = () => {
    const content = inputValue.trim()
    if (!content || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return

    wsRef.current.send(JSON.stringify({
      type: 'chat',
      username: user?.nickname ?? user?.username ?? '匿名',
      content,
    }))

    msgIdRef.current += 1
    setMessages((prev) => [
      ...prev,
      {
        id: msgIdRef.current,
        username: user?.nickname ?? user?.username ?? '我',
        content,
        timestamp: Date.now(),
        type: 'chat',
      },
    ])
    setInputValue('')
  }

  const sendLike = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return
    wsRef.current.send(JSON.stringify({ type: 'like' }))
    setLikes((prev) => prev + 1)
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh', background: 'var(--color-bg-base)' }}>
        <div style={{ width: 40, height: 40, border: '3px solid var(--color-border)', borderTopColor: 'var(--color-primary)', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
      </div>
    )
  }

  if (!room) {
    return (
      <div style={{ textAlign: 'center', padding: 100, background: 'var(--color-bg-base)', color: 'var(--color-text-tertiary)' }}>
        直播间不存在
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 64px)', background: 'var(--color-bg-base)' }}>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <div style={{
          flex: 1,
          background: 'linear-gradient(135deg, #0a0e1a 0%, var(--color-bg-footer) 50%, #0a0e1a 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          minHeight: 0,
        }}>
          <div style={{ textAlign: 'center' }}>
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.2)" strokeWidth="1.5">
              <path d="M23 7l-7 5 7 5V7z" />
              <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
            </svg>
            <div style={{ color: 'rgba(255,255,255,0.4)', marginTop: 16, fontSize: 15 }}>
              直播画面区域
            </div>
          </div>

          <div style={{ position: 'absolute', top: 16, left: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '5px 12px', borderRadius: '6px',
              background: 'rgba(255,71,87,0.9)',
            }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--color-bg-container)', animation: 'pulse 1.5s infinite' }} />
              <span style={{ color: '#fff', fontSize: 12, fontWeight: 700, letterSpacing: 1 }}>LIVE</span>
            </div>
            <span style={{ color: 'rgba(255,255,255,0.8)', fontSize: 14, fontWeight: 600 }}>{room.title}</span>
          </div>

          <div style={{ position: 'absolute', top: 16, right: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 4,
              padding: '4px 10px', borderRadius: 12,
              background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(8px)',
            }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
              <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>{room.currentViewers + likes}</span>
            </div>
            <div style={{
              padding: '4px 10px', borderRadius: 12,
              background: wsConnected ? 'rgba(46,213,115,0.2)' : 'rgba(255,71,87,0.2)',
              border: `1px solid ${wsConnected ? 'rgba(46,213,115,0.4)' : 'rgba(255,71,87,0.4)'}`,
            }}>
              <span style={{ color: wsConnected ? '#2ED573' : '#FF4757', fontSize: 11, fontWeight: 600 }}>
                {wsConnected ? '已连接' : '断开连接'}
              </span>
            </div>
          </div>
        </div>

        <div style={{
          height: 200,
          background: 'var(--color-bg-footer)',
          borderTop: '1px solid var(--color-border)',
          display: 'flex',
          flexDirection: 'column',
        }}>
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '8px 16px',
          }}>
            {messages.map((msg) => (
              <div key={msg.id} style={{ marginBottom: 4 }}>
                {msg.type === 'system' ? (
                  <span style={{ color: 'rgba(255,255,255,0.4)', fontSize: 12 }}>
                    {msg.content}
                  </span>
                ) : (
                  <span style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13 }}>
                    <span style={{
                      color: msg.username === (user?.nickname ?? user?.username) ? 'var(--color-primary)' : '#FFD700',
                      fontWeight: 600,
                      fontSize: 13,
                    }}>
                      {msg.username}：
                    </span>
                    {msg.content}
                  </span>
                )}
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          <div style={{
            padding: '8px 16px',
            borderTop: '1px solid var(--color-border)',
            display: 'flex',
            gap: 8,
            alignItems: 'center',
          }}>
            <input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') sendMessage() }}
              placeholder="说点什么..."
              style={{
                flex: 1,
                padding: '8px 14px',
                border: '1px solid var(--color-border)',
                borderRadius: '10px',
                background: 'var(--color-border)',
                color: 'var(--color-text-secondary)',
                fontSize: 13,
                outline: 'none',
              }}
              onFocus={(e) => { e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)' }}
              onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--color-border)' }}
            />
            <button
              onClick={sendMessage}
              disabled={!inputValue.trim()}
              style={{
                padding: '8px 16px',
                border: 'none',
                borderRadius: '10px',
                background: inputValue.trim() ? 'var(--color-gradient-primary)' : 'var(--color-border)',
                color: inputValue.trim() ? '#fff' : 'rgba(255,255,255,0.3)',
                fontSize: 13,
                fontWeight: 600,
                cursor: inputValue.trim() ? 'pointer' : 'not-allowed',
                transition: 'all 0.3s ease',
              }}
            >
              发送
            </button>
            <button
              onClick={sendLike}
              style={{
                padding: '8px 14px',
                border: '1px solid var(--color-border)',
                borderRadius: '10px',
                background: 'var(--color-border)',
                color: '#FF4757',
                fontSize: 16,
                cursor: 'pointer',
                transition: 'all 0.2s ease',
              }}
            >
              ❤
            </button>
          </div>
        </div>
      </div>

      <div style={{
        width: 300,
        background: 'var(--color-bg-footer)',
        borderLeft: '1px solid var(--color-border)',
        display: 'flex',
        flexDirection: 'column',
        overflowY: 'auto',
        flexShrink: 0,
      }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--color-border)' }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text-secondary)' }}>直播商品</div>
        </div>

        {room.productId ? (
          <div style={{ padding: 16 }}>
            <div style={{
              background: 'var(--color-bg-container)',
              border: '1px solid var(--color-border)',
              borderRadius: '10px',
              overflow: 'hidden',
            }}>
              <div style={{
                height: 140,
                background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.05), rgba(0,153,204,0.1))',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.3)" strokeWidth="1.5">
                  <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                </svg>
              </div>
              <div style={{ padding: 14 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                  {`商品ID-${room.productId}`}
                </div>
                <button style={{
                  width: '100%',
                  padding: '8px 0',
                  border: 'none',
                  borderRadius: '6px',
                  background: 'var(--color-gradient-primary)',
                  color: '#fff',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.25)',
                }}>
                  立即购买
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div style={{ padding: 40, textAlign: 'center' }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1.5" style={{ marginBottom: 12 }}>
              <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
              <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
            </svg>
            <div style={{ color: 'rgba(255,255,255,0.3)', fontSize: 13 }}>暂无商品</div>
          </div>
        )}

        <div style={{ padding: '16px 20px', borderTop: '1px solid var(--color-border)', marginTop: 'auto' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 8 }}>直播间信息</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', lineHeight: 1.8 }}>
            <div>主播：{room.anchorName}</div>
            <div>观看：{room.currentViewers + likes}</div>
            {room.startTime && <div>开播：{new Date(room.startTime).toLocaleString()}</div>}
          </div>
        </div>
      </div>
    </div>
  )
}
