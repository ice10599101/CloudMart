import { useEffect, type CSSProperties, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'
import { Avatar } from 'antd'
import { useDecorationStore } from '@/stores/decoration'
import { AVATAR_FRAME_RINGS } from '@/utils/avatarFrame'

/** 订阅某个用户的头像装饰信息（头像框/等级/徽章数），按需批量拉取并缓存 */
export function useDecoration(userId: number | string | null | undefined) {
  const decoration = useDecorationStore((s) =>
    userId === null || userId === undefined ? undefined : s.decorations[Number(userId)],
  )
  const ensure = useDecorationStore((s) => s.ensure)

  useEffect(() => {
    if (userId !== null && userId !== undefined) void ensure([userId])
  }, [userId, ensure])

  return decoration
}

interface DecoratedAvatarProps {
  userId: number | string | null | undefined
  src?: string | null
  size?: number
  /** 无图片时的占位内容（如昵称首字） */
  fallback?: ReactNode
  style?: CSSProperties
  className?: string
  title?: string
  onClick?: (e: ReactMouseEvent<HTMLDivElement>) => void
}

/**
 * 带头像装饰的 Avatar：头像框（conic-gradient 圆环）、等级标签（LV pill）、
 * 贵宾标识（👑，Lv6+）、徽章数量角标。供全站展示头像处复用。
 */
export default function DecoratedAvatar({
  userId,
  src,
  size = 36,
  fallback,
  style,
  className,
  title,
  onClick,
}: DecoratedAvatarProps) {
  const decoration = useDecoration(userId)

  const ring =
    decoration && decoration.avatarFrame !== 'none'
      ? AVATAR_FRAME_RINGS[decoration.avatarFrame]
      : undefined
  const hasFrame = !!ring
  const framePadding = hasFrame ? Math.max(2, Math.round(size * 0.09)) : 0
  const innerSize = size - framePadding * 2

  const level = decoration?.level ?? 0
  const badgeCount = decoration?.badgeCount ?? 0

  return (
    <div
      role="img"
      onClick={onClick}
      title={title}
      className={className}
      style={{
        position: 'relative',
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: '50%',
        boxSizing: 'border-box',
        ...(hasFrame ? { padding: framePadding, background: ring } : {}),
        ...style,
      }}
    >
      <Avatar
        size={innerSize}
        src={src || decoration?.avatar || undefined}
        style={{ display: 'block', backgroundColor: 'var(--color-gradient-primary)' }}
      >
        {fallback}
      </Avatar>

      {size >= 28 && level >= 2 && (
        <span
          style={{
            position: 'absolute',
            bottom: -4,
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '0 4px',
            borderRadius: 7,
            background: 'linear-gradient(135deg, var(--color-accent-gold), var(--color-accent-gold-dark))',
            color: 'var(--color-bg-base)',
            fontSize: Math.min(12, Math.max(8, Math.round(size * 0.16))),
            lineHeight: 1.3,
            fontWeight: 800,
            whiteSpace: 'nowrap',
            boxShadow: '0 1px 3px rgba(255, 215, 0, 0.4)',
          }}
        >
          {decoration?.levelIcon ? `${decoration.levelIcon} ` : ''}LV{level}
        </span>
      )}

      {level >= 6 && (
        <span
          style={{
            position: 'absolute',
            top: -2,
            right: -2,
            fontSize: Math.min(18, Math.max(12, Math.round(size * 0.28))),
            lineHeight: 1,
            filter: 'drop-shadow(0 1px 2px rgba(255, 215, 0, 0.6))',
          }}
          title="Lv6 贵宾标识"
        >
          👑
        </span>
      )}

      {size >= 32 && badgeCount > 0 && (
        <span
          title={`${badgeCount} 枚徽章`}
          style={{
            position: 'absolute',
            top: -3,
            left: -3,
            minWidth: Math.min(18, Math.max(14, Math.round(size * 0.3))),
            height: Math.min(18, Math.max(14, Math.round(size * 0.3))),
            padding: '0 3px',
            borderRadius: 999,
            background: 'var(--color-accent-purple)',
            color: '#fff',
            fontSize: Math.min(13, Math.max(8, Math.round(size * 0.16))),
            lineHeight: 1,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            boxShadow: '0 1px 3px rgba(0, 0, 0, 0.25)',
          }}
        >
          {badgeCount > 99 ? '99+' : badgeCount}
        </span>
      )}
    </div>
  )
}