/**
 * 头像框装扮统一定义（与后端 user_levels.avatar_frame 合法 key 对应）。
 * 供全站头像装饰组件、个人中心选择器、顶栏头像共用，避免多副本分叉。
 */

export interface AvatarFrameDef {
  key: string
  label: string
  ring: string
}

/** 头像框方案（权益：自定义头像框，Lv2+ 解锁）——选中项由后端 user_levels.avatar_frame 持久化 */
export const AVATAR_FRAMES: AvatarFrameDef[] = [
  { key: 'none', label: '默认', ring: 'none' },
  { key: 'gold', label: '金环', ring: 'conic-gradient(from 0deg, #ffd700, #ff6b35, #ffd700)' },
  { key: 'purple', label: '紫晕', ring: 'conic-gradient(from 0deg, #9370db, #00d4ff, #9370db)' },
  { key: 'green', label: '翠光', ring: 'conic-gradient(from 0deg, #2ed573, #ffd700, #2ed573)' },
  { key: 'pink', label: '樱粉', ring: 'conic-gradient(from 0deg, #ff7eb3, #ff5a8a, #ff7eb3)' },
  { key: 'rainbow', label: '彩虹', ring: 'conic-gradient(from 0deg, #ff6b6b, #ffd700, #2ed573, #00d4ff, #9370db, #ff6b6b)' },
]

/** key → ring 渐变（不含 none），供组件快速取色 */
export const AVATAR_FRAME_RINGS: Record<string, string> = Object.fromEntries(
  AVATAR_FRAMES.filter((f) => f.ring !== 'none').map((f) => [f.key, f.ring]),
)

export type AvatarFrameKey = (typeof AVATAR_FRAMES)[number]['key']

export function isAvatarFrameKey(value: unknown): value is AvatarFrameKey {
  return typeof value === 'string' && AVATAR_FRAMES.some((f) => f.key === value)
}