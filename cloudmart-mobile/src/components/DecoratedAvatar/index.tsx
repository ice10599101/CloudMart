import { View, Image, Text } from '@tarojs/components'
import { useDecoration } from '@/hooks/useDecoration'
import { AVATAR_FRAME_RINGS, AVATAR_FRAME_SOLIDS } from '@/utils/avatarFrame'

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
 * 头像框 conic-gradient 圆环（weapp 不支持渐变时降级纯色描边）。
 * 全站头像处复用：帖子卡片/详情作者/评论/会话列表/主页/通知。
 */
export default function DecoratedAvatar({ src, userId, size = 40, fallbackText }: DecoratedAvatarProps) {
  const decoration = useDecoration(userId)
  const frameKey = decoration?.avatarFrame ?? 'none'
  const ring = AVATAR_FRAME_RINGS[frameKey]
  const solid = AVATAR_FRAME_SOLIDS[frameKey]
  const padding = Math.max(2, Math.round(size * 0.07))

  const outerStyle: Record<string, string> = {
    width: `${size}px`,
    height: `${size}px`,
    borderRadius: '50%',
    padding: `${padding}px`,
    boxSizing: 'border-box',
  }
  if (ring) {
    outerStyle.background = ring
  } else if (solid && solid !== 'transparent') {
    outerStyle.background = solid
  }

  const innerSize = size - padding * 2

  return (
    <View style={outerStyle}>
      <View
        style={{
          width: `${innerSize}px`,
          height: `${innerSize}px`,
          borderRadius: '50%',
          overflow: 'hidden',
          backgroundColor: 'var(--color-border, #e5e7eb)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {src ? (
          <Image
            src={src}
            mode='aspectFill'
            style={{ width: '100%', height: '100%' }}
          />
        ) : (
          <Text style={{ fontSize: `${Math.round(innerSize * 0.42)}px`, color: '#94a3b8' }}>
            {fallbackText || '?'}
          </Text>
        )}
      </View>
    </View>
  )
}
