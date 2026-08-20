import { useState, useEffect, useRef } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import styles from './index.module.scss'

const BGM_STORAGE_KEY = 'wish_bgm_enabled'
const BGM_AUDIO_URL = 'https://cloudmart-oss.oss-cn-hangzhou.aliyuncs.com/bgm/wish-universe-ambient.mp3'

/**
 * 心愿宇宙背景音乐组件
 * - 小程序使用 Taro.createInnerAudioContext
 * - H5 使用 new Audio()
 * - 首次进入默认关闭，用户点击开启后持久化偏好
 * - 自动遵守浏览器自动播放策略（需用户手势）
 */
export default function WishBGM() {
  const [enabled, setEnabled] = useState<boolean>(() => {
    return Taro.getStorageSync(BGM_STORAGE_KEY) === 'true'
  })
  const audioCtxRef = useRef<Taro.InnerAudioContext | HTMLAudioElement | null>(null)
  const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

  useEffect(() => {
    if (!enabled) {
      stopAudio()
      return
    }

    startAudio()
    return stopAudio
  }, [enabled])

  function startAudio() {
    if (IS_WEAPP) {
      const ctx = Taro.createInnerAudioContext()
      ctx.src = BGM_AUDIO_URL
      ctx.loop = true
      ctx.volume = 0.3
      ctx.play()
      audioCtxRef.current = ctx
    } else {
      const audio = new Audio(BGM_AUDIO_URL)
      audio.loop = true
      audio.volume = 0.3
      audio.play().catch(() => {
        setEnabled(false)
        Taro.setStorageSync(BGM_STORAGE_KEY, 'false')
      })
      audioCtxRef.current = audio
    }
  }

  function stopAudio() {
    const ctx = audioCtxRef.current
    if (!ctx) return
    if (IS_WEAPP && 'stop' in ctx) {
      ;(ctx as Taro.InnerAudioContext).stop()
      ;(ctx as Taro.InnerAudioContext).destroy()
    } else if (ctx instanceof HTMLAudioElement) {
      ctx.pause()
    }
    audioCtxRef.current = null
  }

  function toggle() {
    const next = !enabled
    setEnabled(next)
    Taro.setStorageSync(BGM_STORAGE_KEY, String(next))
  }

  return (
    <View className={styles.bgmToggle} onClick={toggle}>
      <Text className={enabled ? styles.iconActive : styles.icon}>{enabled ? '♪' : '♪'}</Text>
      <Text className={styles.label}>{enabled ? '播放中' : '播放'}</Text>
    </View>
  )
}
