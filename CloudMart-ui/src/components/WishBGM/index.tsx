import { useState, useEffect, useRef, useCallback } from 'react'
import { SoundOutlined, MutedOutlined, StepBackwardOutlined, StepForwardOutlined } from '@ant-design/icons'
import { getBgmPlaylist } from '@/api/wish'
import type { BgmSong } from '@/api/wish'

const BGM_STORAGE_KEY = 'wish-bgm-enabled'
/** 播放列表为空/接口失败时的回退默认曲（与 mall-file OSS 配置对齐） */
const DEFAULT_BGM: BgmSong = {
  id: 0,
  title: '心愿宇宙',
  url: 'https://oss-ysf.oss-cn-guangzhou.aliyuncs.com/bgm/wish-universe-ambient.mp3',
  sort: 0,
}

/**
 * 心愿宇宙背景音乐播放器（Sprint 2.3 增强版）。
 *
 * 数据源：GET /wish/bgm/playlist（管理端上传+勾选的歌曲，sort 升序）；
 * 空列表/接口失败回退内置默认曲（Fail-Open，不报错）。
 * 播放语义：多首顺序循环（onended 自动下一首）；增强 UI 含当前曲名
 * 与上一首/下一首切换；开关状态 localStorage 持久化。
 */
export default function WishBGM() {
  const [enabled, setEnabled] = useState(false)
  const [ready, setReady] = useState(false)
  const [playlist, setPlaylist] = useState<BgmSong[]>([DEFAULT_BGM])
  const [currentIndex, setCurrentIndex] = useState(0)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  // 拉取播放列表（失败回退默认曲）
  useEffect(() => {
    let cancelled = false
    getBgmPlaylist()
      .then((response) => {
        if (cancelled) return
        const songs = response.data?.data
        if (songs && songs.length > 0) {
          setPlaylist(songs)
          setCurrentIndex(0)
        }
      })
      .catch(() => {
        // 接口失败保持默认曲（Fail-Open）
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const stored = localStorage.getItem(BGM_STORAGE_KEY)
    if (stored === 'true') {
      setEnabled(true)
    }
    setReady(true)
  }, [])

  const currentSong = playlist[Math.min(currentIndex, playlist.length - 1)]

  // 播放/切歌：重建 audio 指向当前曲（换 src 需 load）
  useEffect(() => {
    if (!enabled || !currentSong) {
      audioRef.current?.pause()
      return
    }

    const audio = new Audio(currentSong.url)
    audio.volume = 0.3
    // 顺序循环：播完自动下一首（单曲列表即循环该曲）
    audio.onended = () => {
      setCurrentIndex((prev) => (prev + 1) % playlist.length)
    }
    // 音源不可用（OSS 文件缺失 404）：跳下一首；全不可用静默关停
    audio.onerror = () => {
      if (playlist.length > 1) {
        setCurrentIndex((prev) => (prev + 1) % playlist.length)
      } else {
        setEnabled(false)
        localStorage.setItem(BGM_STORAGE_KEY, 'false')
      }
    }
    audioRef.current = audio
    audio.play().catch(() => {
      // 浏览器自动播放策略拦截：保持开关开启，等用户交互后再播
    })

    return () => {
      audio.pause()
      audio.onended = null
      audio.onerror = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, currentSong?.id, playlist.length])

  const toggle = useCallback(() => {
    setEnabled((prev) => {
      const next = !prev
      localStorage.setItem(BGM_STORAGE_KEY, String(next))
      return next
    })
  }, [])

  const skip = useCallback(
    (direction: 1 | -1) => {
      setCurrentIndex((prev) => (prev + direction + playlist.length) % playlist.length)
    },
    [playlist.length]
  )

  if (!ready || !currentSong) return null

  const controlButtonStyle: React.CSSProperties = {
    background: 'transparent',
    border: 'none',
    color: enabled ? 'var(--color-primary)' : 'var(--color-text-tertiary)',
    cursor: 'pointer',
    fontSize: 13,
    padding: '2px 4px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  }

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 24,
        right: 24,
        zIndex: 999,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 12px',
        borderRadius: 22,
        border: '1px solid var(--color-border)',
        background: 'var(--color-bg-container)',
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
      <button
        type="button"
        onClick={() => skip(-1)}
        aria-label="上一首"
        title="上一首"
        style={controlButtonStyle}
      >
        <StepBackwardOutlined />
      </button>
      <button
        type="button"
        onClick={toggle}
        aria-label={enabled ? '关闭背景音乐' : '开启背景音乐'}
        title={enabled ? '关闭背景音乐' : '开启背景音乐'}
        style={{ ...controlButtonStyle, fontSize: 18 }}
      >
        {enabled ? <SoundOutlined /> : <MutedOutlined />}
      </button>
      <button
        type="button"
        onClick={() => skip(1)}
        aria-label="下一首"
        title="下一首"
        style={controlButtonStyle}
      >
        <StepForwardOutlined />
      </button>
      <span
        style={{
          maxWidth: 120,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          fontSize: 12,
          color: enabled ? 'var(--color-text)' : 'var(--color-text-tertiary)',
        }}
        title={currentSong.title}
      >
        {currentSong.title}
      </span>
    </div>
  )
}
