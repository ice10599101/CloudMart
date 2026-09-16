import { useEffect, useRef, useState } from 'react'
import { View, Text, Image, TouchableOpacity } from 'react-native'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { WishInteractionItem } from '@/types'

/**
 * 心愿祝福墙（Sprint 1.2 补充）：展示该心愿收到的全部 BLESS 互动。
 *
 * 祝福者与被祝福者都能看到；cursor 分页 + 点击加载更多；
 * 祝福发送成功后由父组件递增 refreshTick 触发重载首屏。
 * 图片模式 APP 端以纯文本呈现（APP 端祝福输入不支持图片）。
 */

const BLESS_PAGE_SIZE = 10

interface WishBlessListProps {
  wishId: number | string
  blessCount: number
  /** 祝福发送成功后的刷新信号（父组件递增） */
  refreshTick?: number
}

/** 时间展示与评论组件一致口径 */
function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function WishBlessList({ wishId, blessCount, refreshTick = 0 }: WishBlessListProps) {
  const [items, setItems] = useState<WishInteractionItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  /** 防重复加载（refreshTick 重载/连点竞态） */
  const requestSeq = useRef(0)

  const loadFirstPage = async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await wishApi.listInteractions(wishId, {
        type: 'BLESS',
        pageSize: BLESS_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data?.success) {
        setItems(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 未登录/无权限时列表留空不打扰
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }

  useEffect(() => {
    loadFirstPage()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wishId, refreshTick])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await wishApi.listInteractions(wishId, {
        type: 'BLESS',
        cursor,
        pageSize: BLESS_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data?.success) {
        setItems((prev) => {
          const seen = new Set(prev.map((i) => i.id))
          return [...prev, ...res.data.data.filter((i) => !seen.has(i.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误静默，保持现状
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }

  return (
    <View
      style={{
        marginTop: Spacing.md,
        padding: Spacing.lg,
        borderRadius: BorderRadius.lg,
        backgroundColor: WishColors.bgContainer,
        borderWidth: 1,
        borderColor: WishColors.border,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <Text style={{ fontSize: 16 }}>🌟</Text>
        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.xs }}>
          祝福
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: Spacing.sm }}>
          {blessCount}
        </Text>
      </View>

      {loading ? (
        <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, textAlign: 'center', marginTop: Spacing.lg }}>
          加载中...
        </Text>
      ) : items.length === 0 ? (
        <View style={{ alignItems: 'center', marginTop: Spacing.lg }}>
          <Text style={{ fontSize: 20 }}>🎁</Text>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, marginTop: Spacing.xs }}>
            还没有祝福，来送出第一份祝福吧
          </Text>
        </View>
      ) : (
        <View style={{ marginTop: Spacing.md, gap: Spacing.md }}>
          {items.map((blessing) => (
            <View key={blessing.id} style={{ flexDirection: 'row' }}>
              {blessing.avatar ? (
                <Image
                  source={{ uri: blessing.avatar }}
                  style={{ width: 36, height: 36, borderRadius: 18 }}
                />
              ) : (
                <View
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 18,
                    backgroundColor: 'rgba(255,255,255,0.1)',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ fontSize: 14, color: '#ffb454' }}>★</Text>
                </View>
              )}
              <View style={{ flex: 1, marginLeft: Spacing.sm }}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>
                    {blessing.nickname}
                  </Text>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: 'auto' }}>
                    {formatTime(blessing.createdAt)}
                  </Text>
                </View>
                {/* content 后端已 XSS 转义，可直接渲染 */}
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, lineHeight: 20, marginTop: 2 }}>
                  {blessing.content ?? ''}
                </Text>
              </View>
            </View>
          ))}
          {hasMore && (
            <TouchableOpacity
              onPress={() => !loadingMore && handleLoadMore()}
              activeOpacity={0.7}
              accessibilityLabel="加载更多祝福"
              style={{ alignItems: 'center', paddingVertical: Spacing.sm }}
            >
              <Text style={{ fontSize: FontSize.xs, color: WishColors.primary }}>
                {loadingMore ? '加载中...' : '加载更多祝福'}
              </Text>
            </TouchableOpacity>
          )}
          {!hasMore && items.length > 0 && (
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, textAlign: 'center', paddingVertical: Spacing.sm }}>
              已经到底啦~
            </Text>
          )}
        </View>
      )}
    </View>
  )
}