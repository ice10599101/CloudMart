import { View, Text, TouchableOpacity, Modal } from 'react-native'
import { useMemo } from 'react'
import * as Clipboard from 'expo-clipboard'
import * as Sharing from 'expo-sharing'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

/**
 * 心愿分享卡片（Sprint 1.5，四AC R5 APP 端）。
 *
 * 方案说明：WEB/Mobile 端用 Canvas 导出 PNG；APP 端为免引入原生截图依赖
 * （react-native-view-shot 不支持 Expo Go），采用 RN View 渲染同款星空视觉
 * + expo-clipboard 一键复制分享文案。星点位置用与 WEB 相同的确定性算法，
 * 同一心愿在任一端视觉一致。
 */

/** 字符串确定性哈希（djb2，与 WEB/Mobile 端一致） */
function hashString(input: string): number {
  let hash = 5381
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash + input.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

/** mulberry32 伪随机数生成器 [0,1)，确定性可复现 */
function seededRandom(seed: number): () => number {
  let state = seed | 0
  return () => {
    state = (state + 0x6d2b79f5) | 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

interface StarDot {
  // RN 的 DimensionValue 只接受 `${number}%` 形式的百分比字符串
  left: `${number}%`
  top: `${number}%`
  size: number
  gold: boolean
  opacity: number
}

export interface WishShareCardProps {
  visible: boolean
  onClose: () => void
  title: string
  author: string
  dateText: string
  fruitLabel: string
  fruitColor: string
}

export default function WishShareCard({ visible, onClose, title, author, dateText, fruitLabel, fruitColor }: WishShareCardProps) {
  // 星点（确定性：同标题同星空，与 WEB Canvas 版视觉规则一致）
  const stars = useMemo<StarDot[]>(() => {
    const rand = seededRandom(hashString(title || 'wish'))
    return Array.from({ length: 60 }, () => ({
      left: `${rand() * 100}%`,
      top: `${rand() * 86}%`,
      size: 1 + Math.round(rand() * 2.5),
      gold: rand() > 0.82,
      opacity: 0.35 + rand() * 0.65,
    }))
  }, [title])

  const handleCopy = async () => {
    await Clipboard.setStringAsync(
      `✦ 心愿宇宙 ✦\n「${title}」\n许愿人：${author} · 许愿于 ${dateText}\n—— 愿望终会实现 ——`,
    )
  }
  const shareText = `✦ 心愿宇宙 ✦
「${title}」
许愿人：${author} · 许愿于 ${dateText}
—— 愿望终会实现 ——`

  /** 系统分享面板（Sprint 1.5 验收：APP 走系统分享面板） */
  const handleSystemShare = async () => {
    try {
      const available = await Sharing.isAvailableAsync()
      if (!available) {
        await Clipboard.setStringAsync(shareText)
        return
      }
      await Sharing.shareAsync(`data:text/plain;charset=utf-8,${encodeURIComponent(shareText)}`, {
        mimeType: 'text/plain',
        dialogTitle: '分享心愿',
      })
    } catch {
      // 用户取消或不可用：静默
    }
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'center', padding: Spacing.lg }}>
        <TouchableOpacity activeOpacity={1} style={{ position: 'absolute', inset: 0 }} onPress={onClose} />

        {/* 星空卡片 */}
        <View
          style={{
            borderRadius: BorderRadius.xl,
            borderWidth: 1,
            borderColor: 'rgba(255,255,255,0.18)',
            backgroundColor: '#1b2447',
            padding: Spacing.xl,
            overflow: 'hidden',
          }}
        >
          {/* 星点层 */}
          {stars.map((star, i) => (
            <View
              key={i}
              style={{
                position: 'absolute',
                left: star.left,
                top: star.top,
                width: star.size,
                height: star.size,
                borderRadius: star.size / 2,
                backgroundColor: star.gold ? '#ffd97a' : '#ffffff',
                opacity: star.opacity,
              }}
            />
          ))}

          <Text style={{ textAlign: 'center', fontSize: FontSize.sm, color: 'rgba(255,255,255,0.55)', marginBottom: Spacing.xl }}>
            ✦ 心 愿 宇 宙 ✦
          </Text>

          <Text style={{ textAlign: 'center', fontSize: 24, fontWeight: '800', color: '#ffffff', lineHeight: 36, minHeight: 108 }}>
            {title}
          </Text>

          {/* 星光分隔 */}
          <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 40, marginVertical: Spacing.lg }}>
            <View style={{ width: 3, height: 3, borderRadius: 1.5, backgroundColor: 'rgba(255,217,122,0.9)' }} />
            <View style={{ width: 4, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,217,122,0.9)', marginTop: 4 }} />
            <View style={{ width: 3, height: 3, borderRadius: 1.5, backgroundColor: 'rgba(255,217,122,0.9)' }} />
          </View>

          {/* 果实标签（胶囊） */}
          <View style={{ alignSelf: 'center', paddingHorizontal: Spacing.xl, paddingVertical: 6, borderRadius: 999, borderWidth: 1, borderColor: fruitColor, backgroundColor: `${fruitColor}33` }}>
            <Text style={{ fontSize: FontSize.sm, color: '#ffffff' }}>{fruitLabel}</Text>
          </View>

          <Text style={{ textAlign: 'center', marginTop: Spacing.xl, fontSize: FontSize.md, color: 'rgba(255,255,255,0.85)' }}>
            许愿人：{author}
          </Text>
          <Text style={{ textAlign: 'center', marginTop: Spacing.xs, fontSize: FontSize.xs, color: 'rgba(255,255,255,0.45)' }}>
            许愿于 {dateText}
          </Text>
          <Text style={{ textAlign: 'center', marginTop: Spacing.xs, fontSize: FontSize.xs, color: 'rgba(255,255,255,0.45)' }}>
            —— 愿望终会实现 ——
          </Text>
        </View>

        {/* 操作 */}
        <TouchableOpacity
          activeOpacity={0.85}
          onPress={handleCopy}
          style={{
            marginTop: Spacing.lg,
            paddingVertical: Spacing.md,
            borderRadius: 28,
            alignItems: 'center',
            backgroundColor: 'rgba(233, 69, 96, 0.25)',
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>复制分享文案</Text>
        </TouchableOpacity>

        <TouchableOpacity
          activeOpacity={0.85}
          onPress={handleSystemShare}
          style={{
            marginTop: Spacing.lg,
            paddingVertical: Spacing.md,
            borderRadius: 28,
            alignItems: 'center',
            backgroundColor: 'rgba(233, 69, 96, 0.25)',
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>复制分享文案</Text>
        </TouchableOpacity>
        <TouchableOpacity activeOpacity={0.85} onPress={onClose} style={{ alignItems: 'center', paddingVertical: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>关闭</Text>
        </TouchableOpacity>
      </View>
    </Modal>
  )
}
