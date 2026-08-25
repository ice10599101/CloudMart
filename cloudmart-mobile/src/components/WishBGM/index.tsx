import { useState, useEffect, useRef, useCallback } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { BgmSong } from '@/api/wish'
import styles from './index.module.scss'

const BGM_STORAGE_KEY = 'wish_bgm_enabled'
/** 播放列表为空/接口失败时的回退默认曲（与 mall-file OSS 配置对齐） */
const DEFAULT_BGM: BgmSong = {
    id: 0,
    title: '心愿宇宙',
    url: 'https://oss-ysf.oss-cn-guangzhou.aliyuncs.com/bgm/wish-universe-ambient.mp3',
    sort: 0,
}

/**
 * 心愿宇宙背景音乐组件（Sprint 2.3 增强版）
 * - 数据源：GET /wish/bgm/playlist（管理端上传+勾选，sort 升序）
 * - 空列表/接口失败回退内置默认曲（Fail-Open）
 * - 多首顺序循环（onEnded/onended 自动下一首）；支持上一首/下一首
 * - 小程序使用 Taro.createInnerAudioContext；H5 使用 new Audio()
 * - 首次进入默认关闭，用户点击开启后持久化偏好
 */
export default function WishBGM() {
    const [enabled, setEnabled] = useState<boolean>(() => {
        return Taro.getStorageSync(BGM_STORAGE_KEY) === 'true'
    })
    const [playlist, setPlaylist] = useState<BgmSong[]>([DEFAULT_BGM])
    const [currentIndex, setCurrentIndex] = useState(0)
    const audioCtxRef = useRef<Taro.InnerAudioContext | HTMLAudioElement | null>(null)
    const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

    // 拉取播放列表（失败回退默认曲）
    useEffect(() => {
        wishApi.getBgmPlaylist()
            .then((res) => {
                const songs = res.data.data
                if (songs && songs.length > 0) {
                    setPlaylist(songs)
                    setCurrentIndex(0)
                }
            })
            .catch(() => {
                // 接口失败保持默认曲（Fail-Open）
            })
    }, [])

    const currentSong = playlist[Math.min(currentIndex, playlist.length - 1)]

    /** 音源不可用（如 OSS 文件缺失 404）：多曲跳下一首，单曲静默关停 */
    const handleAudioError = useCallback(() => {
        if (playlist.length > 1) {
            setCurrentIndex((prev) => (prev + 1) % playlist.length)
        } else {
            stopAudio()
            setEnabled(false)
            Taro.setStorageSync(BGM_STORAGE_KEY, 'false')
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [playlist.length])

    // 播放/切歌：销毁旧上下文后按当前曲重建
    useEffect(() => {
        if (!enabled || !currentSong) {
            stopAudio()
            return
        }
        if (IS_WEAPP) {
            const ctx = Taro.createInnerAudioContext()
            ctx.src = currentSong.url
            ctx.volume = 0.3
            // 顺序循环：播完自动下一首（单曲列表即循环该曲）
            ctx.onEnded(() => {
                setCurrentIndex((prev) => (prev + 1) % playlist.length)
            })
            ctx.onError(handleAudioError)
            ctx.play()
            audioCtxRef.current = ctx
        } else {
            const audio = new Audio(currentSong.url)
            audio.volume = 0.3
            audio.onended = () => {
                setCurrentIndex((prev) => (prev + 1) % playlist.length)
            }
            audio.onerror = handleAudioError
            audio.play().catch(() => {
                // 浏览器自动播放策略拦截：保持开关，等用户交互后再播
            })
            audioCtxRef.current = audio
        }
        return stopAudio
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, currentSong?.id, playlist.length])

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

    function skip(direction: 1 | -1) {
        setCurrentIndex((prev) => (prev + direction + playlist.length) % playlist.length)
    }

    if (!currentSong) return null

    return (
        <View className={styles.bgmToggle}>
            <View className={styles.skipBtn} onClick={() => skip(-1)}>
                <Text className={styles.skipIcon}>◀</Text>
            </View>
            <View className={styles.mainBtn} onClick={toggle}>
                <Text className={enabled ? styles.iconActive : styles.icon}>♪</Text>
                <Text className={styles.label}>{enabled ? '播放中' : '播放'}</Text>
            </View>
            <View className={styles.skipBtn} onClick={() => skip(1)}>
                <Text className={styles.skipIcon}>▶</Text>
            </View>
            <Text className={styles.songTitle}>{currentSong.title}</Text>
        </View>
    )
}
