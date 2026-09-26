import { useEffect } from 'react'
import { useDecorationStore, type UserDecoration } from '@/store/decoration'
import { storage } from '@/utils/storage'
import { useAuthStore } from '@/store/auth'

/**
 * 头像装饰 hook（对齐 Web 端 useDecoration）：
 * 自动把 userId 加入批量补齐队列；本人头像框优先读本地（选择后即时生效）。
 */
export function useDecoration(userId: number | string | null | undefined): UserDecoration | undefined {
  const numericId = userId != null ? Number(userId) : NaN
  const decoration = useDecorationStore((state) => (Number.isFinite(numericId) ? state.decorations[numericId] : undefined))
  const { user } = useAuthStore()

  useEffect(() => {
    if (!Number.isFinite(numericId) || numericId <= 0) return
    void useDecorationStore.getState().ensure([numericId])
  }, [numericId])

  if (user && numericId === Number(user.id)) {
    void storage.getItem('avatar_frame').then((localFrame) => {
      if (localFrame && decoration && decoration.avatarFrame !== localFrame) {
        useDecorationStore.getState().setAvatarFrame(numericId, localFrame)
      }
    })
    const localFrame = decoration?.avatarFrame ?? 'none'
    if (decoration) return decoration
    return { userId: numericId, level: 0, levelTitle: '', levelIcon: '', avatarFrame: localFrame, badgeCount: 0 }
  }
  return decoration
}
