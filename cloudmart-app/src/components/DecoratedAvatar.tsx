import { View, Text, Image } from 'react-native'
import { useDecoration } from '@/hooks/useDecoration'
import { AVATAR_FRAME_COLORS } from '@/store/decoration'

interface DecoratedAvatarProps {
  /** 头像 URL（空则渲染首字母占位） */
  src?: string | null
  /** 用户 ID（头像框数据源；本人优先读本地选择） */
  userId?: number | string | null
  /** 尺寸 px（直径） */
  size?: number
  /** 无头像时的占位字符 */
  fallbackText?: string
}

/**
 * 带装饰头像（对齐 Web 端 DecoratedAvatar）：
 * 头像框圆环（RN 无 conic-gradient，用方案代表色描边近似）。
 * 全站头像处复用：帖子卡片/详情作者/评论/会话列表/主页/通知。
 */
export default function DecoratedAvatar({ src, userId, size = 40, fallbackText }: DecoratedAvatarProps) {
  const decoration = useDecoration(userId)
  const frameKey = decoration?.avatarFrame ?? 'none'
  const ringColor = AVATAR_FRAME_COLORS[frameKey]
  const padding = Math.max(2, Math.round(size * 0.07))
  const innerSize = size - padding * 2

  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        padding,
        borderWidth: ringColor ? padding : 0,
        borderColor: ringColor ?? 'transparent',
        justifyContent: 'center',
        alignItems: 'center',
      }}
    >
      {src ? (
        <Image source={{ uri: src }} style={{ width: innerSize, height: innerSize, borderRadius: innerSize / 2 }} />
      ) : (
        <View
          style={{
            width: innerSize,
            height: innerSize,
            borderRadius: innerSize / 2,
            backgroundColor: 'rgba(148,163,184,0.25)',
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: Math.round(innerSize * 0.42), color: '#94a3b8' }}>{fallbackText || '?'}</Text>
        </View>
      )}
    </View>
  )
}
