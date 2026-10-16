import { create } from 'zustand'
import { getUserDecorations, type UserDecoration } from '@/api/growth'

interface DecorationState {
  /** BASE-01：以字符串形式的用户 ID 为键（雪花 ID 经 Number 转换会丢精度） */
  decorations: Record<string, UserDecoration>
  /** 正在补齐中的用户，避免同一批并发重复请求 */
  inFlight: Record<string, boolean>
  ensure: (userIds: Array<number | string>) => Promise<void>
  setAvatarFrame: (userId: number | string, frame: string) => void
}

const toId = (id: number | string): string => String(id)

/**
 * 全站头像装饰（头像框/等级/徽章数）缓存。
 * 装饰属非关键增强信息，接口失败静默降级，不阻塞头像渲染。
 */
export const useDecorationStore = create<DecorationState>((set, get) => ({
  decorations: {},
  inFlight: {},

  ensure: async (userIds) => {
    const ids = [...new Set(userIds.map(toId))].filter((id) => /^\d+$/.test(id))
    if (ids.length === 0) return

    const missing = ids.filter((id) => {
      const state = get()
      return !state.decorations[id] && !state.inFlight[id]
    })
    if (missing.length === 0) return

    set({ inFlight: { ...get().inFlight, ...Object.fromEntries(missing.map((id) => [id, true])) } })
    try {
      const { data: resp } = await getUserDecorations(missing)
      const remote = resp.data ?? {}
      const merged: Record<string, UserDecoration> = {}
      for (const [key, value] of Object.entries(remote)) {
        merged[key] = { ...value, userId: key }
      }
      set({ decorations: { ...get().decorations, ...merged } })
    } catch {
      // 静默降级：装饰拉取失败不影响头像本身
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
