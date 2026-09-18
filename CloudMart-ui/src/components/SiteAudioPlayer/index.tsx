import { useEffect, useRef, useState } from 'react'
import { PauseCircleFilled, PlayCircleFilled } from '@ant-design/icons'
import styles from './index.module.css'

/**
 * 本站音频播放器（语音 / 音乐附件统一皮肤）。
 *
 * 自绘控制条：播放/暂停、进度拖拽、时间、倍速、音量；
 * 视频、背景音乐等其他媒体仍走原生控件。
 */

interface SiteAudioPlayerProps {
  src: string
  title?: string | null
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds)) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const PLAYBACK_RATES = [1, 1.25, 1.5, 2]

export default function SiteAudioPlayer({ src, title }: SiteAudioPlayerProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [playing, setPlaying] = useState(false)
  const [current, setCurrent] = useState(0)
  const [duration, setDuration] = useState(0)
  const [rateIndex, setRateIndex] = useState(0)
  const [volume, setVolume] = useState(1)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return
    const onTimeUpdate = () => setCurrent(audio.currentTime)
    const onLoadedMetadata = () => setDuration(audio.duration)
    const onEnded = () => setPlaying(false)
    const onError = () => setFailed(true)
    audio.addEventListener('timeupdate', onTimeUpdate)
    audio.addEventListener('loadedmetadata', onLoadedMetadata)
    audio.addEventListener('ended', onEnded)
    audio.addEventListener('error', onError)
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate)
      audio.removeEventListener('loadedmetadata', onLoadedMetadata)
      audio.removeEventListener('ended', onEnded)
      audio.removeEventListener('error', onError)
    }
  }, [src])

  const togglePlay = () => {
    const audio = audioRef.current
    if (!audio || failed) return
    if (audio.paused) {
      audio.play().catch(() => setFailed(true))
      setPlaying(true)
    } else {
      audio.pause()
      setPlaying(false)
    }
  }

  const handleSeek = (event: React.ChangeEvent<HTMLInputElement>) => {
    const audio = audioRef.current
    if (!audio) return
    audio.currentTime = Number(event.target.value)
    setCurrent(audio.currentTime)
  }

  const handleVolume = (event: React.ChangeEvent<HTMLInputElement>) => {
    const audio = audioRef.current
    if (!audio) return
    const next = Number(event.target.value)
    audio.volume = next
    setVolume(next)
  }

  const cycleRate = () => {
    const nextIndex = (rateIndex + 1) % PLAYBACK_RATES.length
    setRateIndex(nextIndex)
    if (audioRef.current) audioRef.current.playbackRate = PLAYBACK_RATES[nextIndex]
  }

  return (
    <div className={styles.player} data-media-player="">
      <audio ref={audioRef} src={src} preload="none" />
      <button
        type="button"
        className={styles.playButton}
        onClick={togglePlay}
        aria-label={playing ? '暂停' : '播放'}
      >
        {playing ? <PauseCircleFilled /> : <PlayCircleFilled />}
      </button>
      <div className={styles.body}>
        {title && <div className={styles.title}>{title}</div>}
        {failed ? (
          <span className={styles.errorText}>音频加载失败</span>
        ) : (
          <>
            <input
              type="range"
              className={styles.seekBar}
              min={0}
              max={Math.max(duration, 0.1)}
              step={0.1}
              value={current}
              onChange={handleSeek}
              aria-label="播放进度"
            />
            <div className={styles.metaRow}>
              <span>{formatTime(current)} / {formatTime(duration)}</span>
              <span className={styles.controls}>
                <button type="button" className={styles.rateButton} onClick={cycleRate}>
                  {PLAYBACK_RATES[rateIndex]}x
                </button>
                <input
                  type="range"
                  className={styles.volumeBar}
                  min={0}
                  max={1}
                  step={0.05}
                  value={volume}
                  onChange={handleVolume}
                  aria-label="音量"
                />
              </span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
