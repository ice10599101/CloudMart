import { useEffect, useState } from 'react'
import { View, Text, TouchableOpacity } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { useAudioPlayer, useAudioPlayerStatus, setAudioModeAsync } from 'expo-audio'
import { wishApi } from '@/api/wish'
import type { BgmSong } from '@/api/wish'
import { storage } from '@/utils/storage'
import { Spacing, FontSize } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const BGM_STORAGE_KEY = 'wish_bgm_enabled'
/** 播放列表为空/接口失败时的回退默认曲（与 mall-file OSS 配置对齐） */
const DEFAULT_BGM: BgmSong = {
    id: 0,
    title: '心愿宇宙',
    url: 'https://oss-ysf.oss-cn-guangzhou.aliyuncs.com/bgm/wish-universe-ambient.mp3',
    sort: 0,
}

/**
 * 心愿宇宙背景音乐组件（APP 端，expo-audio）
 * - 数据源：GET /wish/bgm/playlist（管理端上传+勾选，sort 升序）
 * - 空列表/接口失败回退内置默认曲（Fail-Open）
 * - 多首顺序循环（didJustFinish 自动下一首）；支持上一首/下一首
 * - 首次进入默认关闭，用户点击开启后持久化偏好
 */
export default function WishBGM() {
    const insets = useSafeAreaInsets()
    const [enabled, setEnabled] = useState(false)
    const [playlist, setPlaylist] = useState<BgmSong[]>([DEFAULT_BGM])
    const [currentIndex, setCurrentIndex] = useState(0)
    const currentSong = playlist[Math.min(currentIndex, playlist.length - 1)]

    // useAudioPlayer 在 source 变化时内部自动 replace 音源
    const player = useAudioPlayer(currentSong?.url ?? null)
    const status = useAudioPlayerStatus(player)

    // 初始化：读取偏好 + iOS 静音模式可播 + 拉取播放列表（失败回退默认曲）
    useEffect(() => {
        storage.getItem(BGM_STORAGE_KEY).then((v) => setEnabled(v === 'true'))
        // BGM 场景需要锁定静音键后仍可播放；失败不阻断（如 web 平台）
        setAudioModeAsync({ playsInSilentMode: true }).catch(() => {})
        wishApi
            .getBgmPlaylist()
            .then((res) => {
                const songs = res.data?.data
                if (songs && songs.length > 0) {
                    setPlaylist(songs)
                    setCurrentIndex(0)
                }
            })
            .catch(() => {
                // 接口失败保持默认曲（Fail-Open）
            })
    }, [])

    // 播完自动下一首（顺序循环，单曲列表即循环该曲）
    useEffect(() => {
        if (status.didJustFinish) {
            setCurrentIndex((prev) => (prev + 1) % playlist.length)
        }
    }, [status.didJustFinish, playlist.length])

    // 音源不可用（如 OSS 文件缺失 404）：多曲跳下一首，单曲静默关停
    useEffect(() => {
        if (status.error) {
            if (playlist.length > 1) {
                setCurrentIndex((prev) => (prev + 1) % playlist.length)
            } else {
                setEnabled(false)
                storage.setItem(BGM_STORAGE_KEY, 'false')
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [status.error, playlist.length])

    // 开关/切歌后驱动播放器（replace 后需重新 play）
    useEffect(() => {
        if (enabled) {
            player.volume = 0.3
            player.play()
        } else {
            player.pause()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, currentSong?.id])

    function toggle() {
        const next = !enabled
        setEnabled(next)
        storage.setItem(BGM_STORAGE_KEY, String(next))
    }

    function skip(direction: 1 | -1) {
        setCurrentIndex((prev) => (prev + direction + playlist.length) % playlist.length)
    }

    return (
        <View
            style={{
                position: 'absolute',
                left: Spacing.md,
                bottom: insets.bottom + 24,
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 8,
                paddingHorizontal: Spacing.md,
                borderRadius: 24,
                backgroundColor: 'rgba(15, 52, 96, 0.85)',
                borderWidth: 1,
                borderColor: 'rgba(0, 212, 255, 0.3)',
                shadowColor: '#000',
                shadowOffset: { width: 0, height: 4 },
                shadowOpacity: 0.3,
                shadowRadius: 8,
                elevation: 6,
                maxWidth: '80%',
            }}
        >
            <TouchableOpacity onPress={() => skip(-1)} hitSlop={8}>
                <Text style={{ color: WishColors.textTertiary, fontSize: 12 }}>◀</Text>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={toggle}
                style={{ flexDirection: 'row', alignItems: 'center', marginHorizontal: Spacing.sm }}
                hitSlop={4}
            >
                <Text style={{ color: enabled ? WishColors.accentCyan : WishColors.textTertiary, fontSize: 14 }}>♪</Text>
                <Text style={{ color: WishColors.textSecondary, fontSize: FontSize.xs, marginLeft: 4 }}>
                    {enabled ? '播放中' : '播放'}
                </Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => skip(1)} hitSlop={8}>
                <Text style={{ color: WishColors.textTertiary, fontSize: 12 }}>▶</Text>
            </TouchableOpacity>
            <Text
                style={{ color: WishColors.textTertiary, fontSize: FontSize.xs, marginLeft: Spacing.sm, maxWidth: 140 }}
                numberOfLines={1}
            >
                {currentSong?.title}
            </Text>
        </View>
    )
}
