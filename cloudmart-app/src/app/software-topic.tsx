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
import { softwareApi } from '@/api/software'
import type { SoftwareCard } from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

/**
 * 专题详情（SW11-3 App 端）：广场专题入口 → 详情 → 软件。
 * 清单由后端按实时可见性过滤（草稿/封禁不泄露）；后台下线后详情 404 → 明确提示。
 */
export default function SoftwareTopicScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const { slug } = useLocalSearchParams<{ slug?: string }>()
  const [topic, setTopic] = useState<import('@/api/software').SoftwareTopicDetail | null>(null)
  const [missing, setMissing] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (!slug) return
    setLoading(true)
    try {
      const res = await softwareApi.topic(slug)
      setTopic(res.data.data)
    } catch {
      // 下线/不存在：展示明确原因，不泄露未发布条目
      setMissing(true)
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void load()
  }, [load])

  const renderItem = ({ item }: { item: SoftwareCard }) => (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/software-detail?id=${item.id}`)}
      style={{
        flexDirection: 'row',
        gap: Spacing.sm,
        marginHorizontal: Spacing.md,
        marginTop: Spacing.sm,
        padding: Spacing.md,
        borderRadius: BorderRadius.md,
        backgroundColor: theme.bgContainer,
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>
          {item.name}
        </Text>
        {!!item.summary && (
          <Text numberOfLines={2} style={{ marginTop: 4, fontSize: FontSize.sm, color: theme.textSecondary }}>
            {item.summary}
          </Text>
        )}
      </View>
    </TouchableOpacity>
  )

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top + Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingHorizontal: Spacing.md }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: theme.text }}>‹</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, flex: 1 }} numberOfLines={1}>
          {topic?.title ?? '专题'}
        </Text>
      </View>

      {loading ? (
        <ActivityIndicator style={{ marginTop: 48 }} />
      ) : missing || !topic ? (
        <Text style={{ textAlign: 'center', marginTop: 48, color: theme.textSecondary, paddingHorizontal: Spacing.lg }}>
          专题不存在或已下线
        </Text>
      ) : (
        <ScrollView showsVerticalScrollIndicator={false}>
          {!!topic.summary && (
            <Text style={{ paddingHorizontal: Spacing.md, marginTop: Spacing.sm, fontSize: FontSize.sm, color: theme.textSecondary }}>
              {topic.summary}
            </Text>
          )}
          {/* SF05：专题介绍（后台维护的运营文案） */}
          {!!topic.description && (
            <Text style={{ paddingHorizontal: Spacing.md, marginTop: Spacing.sm, fontSize: FontSize.sm, lineHeight: 20, color: theme.text }}>
              {topic.description}
            </Text>
          )}
          {/* UX02：工具箱三要素（能帮你做什么/适合谁/准备条件） */}
          {(topic.problem || topic.audience || topic.prerequisites) ? (
            <View
              style={{
                marginHorizontal: Spacing.md,
                marginTop: Spacing.sm,
                padding: Spacing.md,
                borderRadius: BorderRadius.md,
                backgroundColor: theme.bgContainer,
              }}
            >
              {topic.problem ? (
                <Text style={{ fontSize: FontSize.sm, color: theme.text }}>
                  能帮你做什么：{topic.problem}
                </Text>
              ) : null}
              {topic.audience ? (
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4 }}>
                  适合谁：{topic.audience}
                </Text>
              ) : null}
              {topic.prerequisites ? (
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4 }}>
                  准备条件：{topic.prerequisites}
                </Text>
              ) : null}
            </View>
          ) : null}
          {/* UX02：按分组展示（用途说明/可选替代；稳定 groupKey） */}
          {(topic.groups ?? []).length > 0
            ? (topic.groups ?? []).map((group) => (
                <View key={group.groupKey} style={{ paddingHorizontal: Spacing.md, marginTop: Spacing.md }}>
                  <Text style={{ fontSize: FontSize.sm, fontWeight: '700', color: theme.text }}>
                    {group.groupKey}
                  </Text>
                  {group.items.map((item) => (
                    <TouchableOpacity
                      key={`${group.groupKey}-${item.softwareId}`}
                      onPress={() => router.push(`/software-detail?id=${item.softwareId}`)}
                      style={{ marginTop: Spacing.sm }}
                    >
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text, flex: 1 }}>
                          {item.name}
                          {item.optional ? '（可选替代）' : ''}
                        </Text>
                      </View>
                      {item.roleNote ? (
                        <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary, marginTop: 2 }}>
                          {item.roleNote}
                        </Text>
                      ) : null}
                    </TouchableOpacity>
                  ))}
                </View>
              ))
            : null}
          <FlatList
            data={topic.items}
            keyExtractor={(item) => item.id}
            renderItem={renderItem}
            ListEmptyComponent={
              <Text style={{ textAlign: 'center', color: theme.textTertiary, marginTop: Spacing.xl }}>
                该专题暂无上架软件
              </Text>
            }
            scrollEnabled={false}
            contentContainerStyle={{ paddingBottom: 48 }}
          />
        </ScrollView>
      )}
    </View>
  )
}
