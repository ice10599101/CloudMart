import { createStore } from 'zustand'
import { useSyncExternalStore } from 'react'
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
  ensure: (userIds: Array<number | string>) => Promise<void>
  setAvatarFrame: (userId: number | string, frame: string) => void
}

const toId = (id: number | string): string => String(id)

/**
 * 全站头像装饰缓存（头像框/等级/徽章数）。
 * 装饰属非关键增强信息，接口失败静默降级，不阻塞头像渲染；契约对齐 Web 端 stores/decoration。
 */
export const decorationStore = createStore<DecorationState>((set, get) => ({
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

/** 组件级订阅 hook（zustand vanilla store 需手动接 useSyncExternalStore） */
export function useDecorationStore<T>(selector: (state: DecorationState) => T): T {
  return useSyncExternalStore(decorationStore.subscribe, () => selector(decorationStore.getState()))
}
