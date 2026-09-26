/**
 * 头像框装扮统一定义（与后端 user_levels.avatar_frame 合法 key 对应；契约对齐 Web 端 utils/avatarFrame.ts）。
 * ring 为 conic-gradient（H5/新版基础库 WXSS 支持），solid 为渐变不支持时的兜底描边色。
 */

export interface AvatarFrameDef {
  key: string
  label: string
  ring: string
  solid: string
}

export const AVATAR_FRAMES: AvatarFrameDef[] = [
  { key: 'none', label: '默认', ring: 'none', solid: 'transparent' },
  { key: 'gold', label: '金环', ring: 'conic-gradient(from 0deg, #ffd700, #ff6b35, #ffd700)', solid: '#ffd700' },
  { key: 'purple', label: '紫晕', ring: 'conic-gradient(from 0deg, #9370db, #00d4ff, #9370db)', solid: '#9370db' },
  { key: 'green', label: '翠光', ring: 'conic-gradient(from 0deg, #2ed573, #ffd700, #2ed573)', solid: '#2ed573' },
  { key: 'pink', label: '樱粉', ring: 'conic-gradient(from 0deg, #ff7eb3, #ff5a8a, #ff7eb3)', solid: '#ff7eb3' },
  { key: 'rainbow', label: '彩虹', ring: 'conic-gradient(from 0deg, #ff6b6b, #ffd700, #2ed573, #00d4ff, #9370db, #ff6b6b)', solid: '#00d4ff' },
]

/** key → 渐变（不含 none），供组件快速取色 */
export const AVATAR_FRAME_RINGS: Record<string, string> = Object.fromEntries(
  AVATAR_FRAMES.filter((f) => f.ring !== 'none').map((f) => [f.key, f.ring]),
)

/** key → 纯色兜底（渐变不可用时描边） */
export const AVATAR_FRAME_SOLIDS: Record<string, string> = Object.fromEntries(
  AVATAR_FRAMES.filter((f) => f.solid !== 'transparent').map((f) => [f.key, f.solid]),
)
