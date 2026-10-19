import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  FlatList,
  ScrollView,
  Text,
  TouchableOpacity,
  View,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import {
  listMyUpdates,
  markUpdatesRead,
  type SoftwareUpdateItem,
} from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

/** UX05：订阅更新流（App 端）——突出"这次改了什么"；已读幂等；下架标注不可用 */
export default function SoftwareUpdatesScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const [items, setItems] = useState<SoftwareUpdateItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async (append: boolean, cur: string | null) => {
    setLoading(true)
    try {
      const res = await listMyUpdates(append ? cur : null, 20)
      const feed = res.data.data ?? { items: [], hasMore: false, nextCursor: null }
      setItems((prev) => (append ? [...prev, ...feed.items] : feed.items))
      setCursor(feed.nextCursor)
      setHasMore(feed.hasMore)
    } catch {
      // 失败保留现有内容；下拉重试
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(false, null)
  }, [load])

  const read = async (eventId: string) => {
    try {
      await markUpdatesRead([eventId])
      setItems((prev) =>
        prev.map((it) => (it.eventId === eventId ? { ...it, read: true } : it)),
      )
    } catch {
      // 已读失败不阻塞浏览
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top + Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingHorizontal: Spacing.md }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: theme.text }}>‹</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, flex: 1 }}>
          订阅更新
        </Text>
      </View>
      {loading && items.length === 0 ? (
        <ActivityIndicator style={{ marginTop: 48 }} />
      ) : (
        <ScrollView showsVerticalScrollIndicator={false}>
          {items.length === 0 ? (
            <Text style={{ textAlign: 'center', marginTop: 48, color: theme.textSecondary }}>
              暂无更新；订阅的软件发布新版本后会出现在这里
            </Text>
          ) : (
            items.map((it) => (
              <View
                key={it.eventId}
                style={{
                  marginHorizontal: Spacing.md,
                  marginTop: Spacing.sm,
                  padding: Spacing.md,
                  borderRadius: BorderRadius.md,
                  backgroundColor: theme.bgContainer,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                  {!it.read ? (
                    <View style={{ paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, backgroundColor: '#FFF1F0' }}>
                      <Text style={{ fontSize: 11, color: '#FF4D4F' }}>未读</Text>
                    </View>
                  ) : null}
                  <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, flex: 1 }}>
                    {it.softwareName || `软件 ${it.softwareId}`}
                    {it.versionName ? ` · ${it.versionName}` : ''}
                  </Text>
                </View>
                {it.changelog ? (
                  <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4, lineHeight: 20 }}>
                    {it.changelog}
                  </Text>
                ) : null}
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
                    {it.publishedAt ? `发布于 ${it.publishedAt.slice(0, 16).replace('T', ' ')}` : ''}
                  </Text>
                  <View style={{ flexDirection: 'row', gap: Spacing.md }}>
                    {it.softwareVisible ? (
                      <TouchableOpacity onPress={() => router.push(`/software-detail?id=${it.softwareId}`)}>
                        <Text style={{ fontSize: FontSize.sm, color: '#1677FF' }}>查看软件</Text>
                      </TouchableOpacity>
                    ) : (
                      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>当前不可用</Text>
                    )}
                    {!it.read ? (
                      <TouchableOpacity onPress={() => void read(it.eventId)}>
                        <Text style={{ fontSize: FontSize.sm, color: '#1677FF' }}>标记已读</Text>
                      </TouchableOpacity>
                    ) : null}
                  </View>
                </View>
              </View>
            ))
          )}
          {hasMore ? (
            <TouchableOpacity
              onPress={() => void load(true, cursor)}
              style={{ alignItems: 'center', padding: Spacing.lg }}
            >
              <Text style={{ color: '#1677FF', fontSize: FontSize.sm }}>加载更多</Text>
            </TouchableOpacity>
          ) : null}
          {loading && items.length > 0 ? <ActivityIndicator style={{ margin: Spacing.md }} /> : null}
        </ScrollView>
      )}
    </View>
  )
}
