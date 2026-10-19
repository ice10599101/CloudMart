import { View, Text, ScrollView, TouchableOpacity, TextInput, Alert, ActivityIndicator } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { productQuestionApi, type MyProductQuestionItem } from '@/api/product'
import { errorMessage } from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/**
 * T13：我的商品提问（跨商品）——查看回答进度/回跳商品问答/删除自己的提问。
 * 服务端过滤本人已删；删除幂等；归属校验在服务端（他人问题 404 同语义）。
 */
export default function ProductQuestionsScreen() {
  const theme = useTheme()
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const params = useLocalSearchParams<{ from?: string }>()
  const [items, setItems] = useState<MyProductQuestionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await productQuestionApi.myQuestions(1, 50)
      setItems(res.data.data?.records ?? [])
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    void load()
  }, [isLoggedIn, load])

  const handleDelete = (item: MyProductQuestionItem) => {
    Alert.alert('删除提问', '删除后商品详情与该列表均不可见，确定删除？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            setDeletingId(String(item.id))
            try {
              await productQuestionApi.deleteOwn(item.id)
              setItems((prev) => prev.filter((q) => String(q.id) !== String(item.id)))
            } catch (error) {
              Alert.alert('提示', errorMessage(error, '删除失败，请稍后重试'))
            } finally {
              setDeletingId(null)
            }
          })()
        },
      },
    ])
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>←</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text }}>我的提问</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + Spacing.xl }}>
        {loading ? (
          <ActivityIndicator style={{ marginTop: 48 }} />
        ) : items.length === 0 ? (
          <Text style={{ textAlign: 'center', marginTop: 64, color: theme.textTertiary, fontSize: FontSize.sm }}>
            还没有提问过商品问题
          </Text>
        ) : (
          items.map((item) => (
            <View
              key={String(item.id)}
              style={{
                padding: Spacing.md,
                borderRadius: BorderRadius.md,
                backgroundColor: theme.bgContainer,
                marginBottom: Spacing.sm,
              }}
            >
              <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>
                {item.question}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
                {item.createdAt.slice(0, 10)}
                {item.productName ? ` · ${item.productName}` : ''}
              </Text>
              {item.answers.length > 0 ? (
                item.answers.map((answer) => (
                  <View
                    key={String(answer.id)}
                    style={{
                      marginTop: Spacing.xs,
                      padding: Spacing.sm,
                      borderRadius: BorderRadius.sm,
                      backgroundColor: theme.bgInput,
                    }}
                  >
                    <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>
                      {answer.authorNickname || '匿名'} 回答：{answer.body}
                    </Text>
                  </View>
                ))
              ) : (
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.xs }}>
                  暂无回答
                </Text>
              )}
              <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.sm }}>
                <TouchableOpacity onPress={() => router.push(`/product/${item.productId}?tab=qa`)}>
                  <Text style={{ fontSize: FontSize.xs, color: theme.primary }}>回跳商品问答 →</Text>
                </TouchableOpacity>
                <TouchableOpacity onPress={() => handleDelete(item)} disabled={deletingId === String(item.id)}>
                  <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>
                    {deletingId === String(item.id) ? '删除中...' : '删除'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </View>
  )
}
