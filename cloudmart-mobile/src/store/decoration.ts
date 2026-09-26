import { createStore } from 'zustand'
import { useSyncExternalStore } from 'react'
import { growthApi } from '@/api/growth'

export interface UserDecoration {
  userId: number
  level: number
  levelTitle: string
  levelIcon: string
  avatarFrame: string
  badgeCount: number
  avatar?: string | null
}

interface DecorationState {
  decorations: Record<number, UserDecoration>
  inFlight: Record<number, boolean>
  ensure: (userIds: Array<number | string>) => Promise<void>
  setAvatarFrame: (userId: number, frame: string) => void
}

const toNumber = (id: number | string): number => Number(id)

/**
 * 全站头像装饰缓存（头像框/等级/徽章数）。
 * 装饰属非关键增强信息，接口失败静默降级，不阻塞头像渲染；契约对齐 Web 端 stores/decoration。
 */
export const decorationStore = createStore<DecorationState>((set, get) => ({
  decorations: {},
  inFlight: {},

  ensure: async (userIds) => {
    const ids = userIds.map(toNumber).filter((n) => Number.isFinite(n) && n > 0)
    if (ids.length === 0) return

    const missing = [...new Set(ids)].filter((id) => {
      const state = get()
      return !state.decorations[id] && !state.inFlight[id]
    })
    if (missing.length === 0) return

    set({ inFlight: { ...get().inFlight, ...Object.fromEntries(missing.map((id) => [id, true])) } })
    try {
      const res = await growthApi.getUserDecorations(missing)
      const remote = res.data?.data ?? {}
      const merged: Record<number, UserDecoration> = {}
      for (const [key, value] of Object.entries(remote)) {
        const id = Number(key)
        if (Number.isFinite(id)) merged[id] = { ...value, userId: id }
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
    const prev = get().decorations[userId]
    const next: UserDecoration = prev
      ? { ...prev, avatarFrame: frame }
      : { userId, level: 1, levelTitle: '', levelIcon: '', avatarFrame: frame, badgeCount: 0 }
    set({ decorations: { ...get().decorations, [userId]: next } })
  },
}))

/** 组件级订阅 hook（zustand vanilla store 需手动接 useSyncExternalStore） */
export function useDecorationStore<T>(selector: (state: DecorationState) => T): T {
  return useSyncExternalStore(decorationStore.subscribe, () => selector(decorationStore.getState()))
}
