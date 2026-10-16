import { create } from 'zustand'
import { growthApi } from '@/api/growth'

export interface UserDecoration {
  /** BASE-01：用户 ID 为字符串（雪花 ID 防精度损失） */
  userId: number | string
  level: number
  levelTitle: string
  levelIcon: string
  avatarFrame: string
  badgeCount: number
  avatar?: string | null
}

interface DecorationState {
  /** BASE-01：以字符串形式的用户 ID 为键（雪花 ID 经 Number 转换会丢精度） */
  decorations: Record<string, UserDecoration>
  inFlight: Record<string, boolean>
  ensure: (userIds: (number | string)[]) => Promise<void>
  setAvatarFrame: (userId: number | string, frame: string) => void
}

const toId = (id: number | string): string => String(id)

/**
 * 全站头像装饰缓存（头像框/等级/徽章数）。
 * 装饰属非关键增强信息，接口失败静默降级；契约对齐 Web 端 stores/decoration。
 */
export const useDecorationStore = create<DecorationState>((set, get) => ({
  decorations: {},
  inFlight: {},

  ensure: async (userIds) => {
    const ids = [...new Set(userIds.map(toId))].filter((id) => /^\d+$/.test(id) && !/^0+$/.test(id))
    if (ids.length === 0) return

    const missing = ids.filter((id) => {
      const state = get()
      return !state.decorations[id] && !state.inFlight[id]
    })
    if (missing.length === 0) return

    set({ inFlight: { ...get().inFlight, ...Object.fromEntries(missing.map((id) => [id, true])) } })
    try {
      const res = await growthApi.getUserDecorations(missing)
      const remote = res.data?.data ?? {}
      const merged: Record<string, UserDecoration> = {}
      for (const [key, value] of Object.entries(remote)) {
        merged[key] = { ...value, userId: key }
      }
      set({ decorations: { ...get().decorations, ...merged } })
    } catch {
      // 静默降级
    } finally {
      const rest = { ...get().inFlight }
      missing.forEach((id) => delete rest[id])
      set({ inFlight: rest })
    }
  },

  setAvatarFrame: (userId, frame) => {
    const id = toId(userId)
    const prev = get().decorations[id]
    const next: UserDecoration = prev
      ? { ...prev, avatarFrame: frame }
      : { userId: id, level: 1, levelTitle: '', levelIcon: '', avatarFrame: frame, badgeCount: 0 }
    set({ decorations: { ...get().decorations, [id]: next } })
  },
}))

/** 头像框 key → 描边色（RN 无 conic-gradient，用代表色圆环近似 Web 渐变环） */
export const AVATAR_FRAME_COLORS: Record<string, string> = {
  gold: '#ffd700',
  purple: '#9370db',
  green: '#2ed573',
  pink: '#ff7eb3',
  rainbow: '#00d4ff',
}
