import { useEffect } from 'react'
import { useDecorationStore, type UserDecoration } from '@/store/decoration'
import { storage } from '@/utils/storage'
import { useAuthStore } from '@/store/auth'

/**
 * 头像装饰 hook（对齐 Web 端 useDecoration）：
 * 自动把 userId 加入批量补齐队列；本人头像框优先读本地（选择后即时生效）。
 */
export function useDecoration(userId: number | string | null | undefined): UserDecoration | undefined {
  // BASE-01：装饰缓存按字符串 ID 索引（雪花 ID 不能经 Number 转换）
  const decorationId = userId != null ? String(userId) : ''
  const decoration = useDecorationStore((state) => (decorationId ? state.decorations[decorationId] : undefined))
  const { user } = useAuthStore()

  useEffect(() => {
    if (!decorationId || !/^\d+$/.test(decorationId) || /^0+$/.test(decorationId)) return
    void useDecorationStore.getState().ensure([decorationId])
  }, [decorationId])

  if (user && decorationId && decorationId === String(user.id)) {
    void storage.getItem('avatar_frame').then((localFrame) => {
      if (localFrame && decoration && decoration.avatarFrame !== localFrame) {
        useDecorationStore.getState().setAvatarFrame(decorationId, localFrame)
      }
    })
    const localFrame = decoration?.avatarFrame ?? 'none'
    if (decoration) return decoration
    return { userId: decorationId, level: 0, levelTitle: '', levelIcon: '', avatarFrame: localFrame, badgeCount: 0 }
  }
  return decoration
}
