import { useEffect } from 'react'
import { decorationStore, useDecorationStore, type UserDecoration } from '@/store/decoration'
import Taro from '@tarojs/taro'
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
    // 本人：本地选择即时生效，同时仍补齐等级/徽章信息
    void decorationStore.getState().ensure([decorationId])
  }, [decorationId])

  if (user && decorationId && decorationId === String(user.id)) {
    const localFrame = (Taro.getStorageSync('avatar_frame') as string) || 'none'
    if (decoration) return { ...decoration, avatarFrame: localFrame }
    return { userId: decorationId, level: 0, levelTitle: '', levelIcon: '', avatarFrame: localFrame, badgeCount: 0 }
  }
  return decoration
}
