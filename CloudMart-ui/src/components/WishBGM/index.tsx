import { useState, useEffect, useRef, useCallback } from 'react'
import { SoundOutlined, MutedOutlined } from '@ant-design/icons'

const BGM_STORAGE_KEY = 'wish-bgm-enabled'
const BGM_AUDIO_URL = 'https://cloudmart-oss.oss-cn-hangzhou.aliyuncs.com/bgm/wish-universe-ambient.mp3'

export default function WishBGM() {
  const [enabled, setEnabled] = useState(false)
  const [ready, setReady] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    const stored = localStorage.getItem(BGM_STORAGE_KEY)
    if (stored === 'true') {
      setEnabled(true)
    }
    setReady(true)
  }, [])

  useEffect(() => {
    if (!enabled) {
      if (audioRef.current) {
        audioRef.current.pause()
      }
      return
    }

    if (!audioRef.current) {
      const audio = new Audio(BGM_AUDIO_URL)
      audio.loop = true
      audio.volume = 0.3
      // 音源不可用（如 OSS 文件未上传 404）时静默回退，避免控制台报错
      audio.onerror = () => {
        setEnabled(false)
        localStorage.setItem(BGM_STORAGE_KEY, 'false')
      }
      audioRef.current = audio
    }

    const audio = audioRef.current
    audio.play().catch(() => {
      setEnabled(false)
      localStorage.setItem(BGM_STORAGE_KEY, 'false')
    })

    return () => {
      audio.pause()
    }
  }, [enabled])

  const toggle = useCallback(() => {
    setEnabled(prev => {
      const next = !prev
      localStorage.setItem(BGM_STORAGE_KEY, String(next))
      return next
    })
  }, [])

  if (!ready) return null

  return (
    <button
      onClick={toggle}
      aria-label={enabled ? '关闭背景音乐' : '开启背景音乐'}
      title={enabled ? '关闭背景音乐' : '开启背景音乐'}
      style={{
        position: 'fixed',
        bottom: 24,
        right: 24,
        zIndex: 999,
        width: 44,
        height: 44,
        borderRadius: '50%',
        border: '1px solid var(--color-border)',
        background: 'var(--color-bg-container)',
        color: enabled ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 18,
        boxShadow: 'var(--shadow-card)',
        transition: 'all 0.3s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-primary)'
        e.currentTarget.style.boxShadow = 'var(--shadow-glow)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = 'var(--shadow-card)'
      }}
    >
      {enabled ? <SoundOutlined /> : <MutedOutlined />}
    </button>
  )
}
