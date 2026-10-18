import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  FlatList,
  Text,
  TouchableOpacity,
  View,
  Alert,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { softwarePublishApi } from '@/api/software'
import type { SoftwareMineItem } from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: '已发布',
  UNLISTED: '已下架',
  BANNED: '已封禁',
  DRAFT: '草稿',
  PENDING: '审核中',
  WITHDRAWN: '已撤回',
  REJECTED: '已退回',
}

const STATUS_COLOR: Record<string, string> = {
  PUBLISHED: '#1d9a51',
  UNLISTED: '#c07b12',
  BANNED: '#d3392f',
  DRAFT: '#5b6478',
  PENDING: '#2f6fd3',
  REJECTED: '#d3392f',
}

export default function SoftwareMineScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const params = useLocalSearchParams<{ refreshed?: string }>()
  const [items, setItems] = useState<SoftwareMineItem[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await softwarePublishApi.mine(1, 50)
      setItems(res.data.data?.records ?? [])
    } catch {
      Alert.alert('提示', '加载失败，请稍后再试')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, params.refreshed])

  const gate = async (fn: () => Promise<unknown>, doneTip: string) => {
    setBusy(true)
    try {
      await fn()
      Alert.alert('提示', doneTip)
      await load()
    } catch {
      Alert.alert('提示', '操作失败，请稍后再试')
    } finally {
      setBusy(false)
    }
  }

  const confirmDelete = (it: SoftwareMineItem) => {
    Alert.alert('删除软件', '删除后不可恢复，确定继续？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: () => {
          void gate(() => softwarePublishApi.deleteSoftware(it.softwareId), '已删除')
        },
      },
    ])
  }

  const renderItem = ({ item }: { item: SoftwareMineItem }) => (
    <View
      style={{
        backgroundColor: theme.bgElevated ?? '#fff',
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.xs }}>
        <Text
          numberOfLines={1}
          style={{ flex: 1, fontSize: FontSize.md, fontWeight: '600', color: theme.text }}
        >
          {item.unavailable ? '（内容已不可见）' : item.name}
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: STATUS_COLOR[item.status] ?? '#5b6478' }}>
          {STATUS_LABEL[item.status] ?? item.status}
        </Text>
      </View>
      {!item.unavailable && !!item.summary && (
        <Text numberOfLines={1} style={{ marginTop: 6, fontSize: FontSize.sm, color: theme.textSecondary }}>
          {item.summary}
        </Text>
      )}
      <Text style={{ marginTop: 6, fontSize: FontSize.xs, color: theme.textSecondary }}>
        收藏 {item.favoriteCount} · {(item.updatedAt || '').replace('T', ' ').slice(0, 16)}
      </Text>
      {item.status === 'REJECTED' && (
        <Text style={{ marginTop: 4, fontSize: FontSize.xs, color: '#d3392f' }}>被退回：修改后可再次提交</Text>
      )}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.sm, paddingTop: Spacing.sm, borderTopWidth: 1, borderTopColor: theme.border }}>
        {(item.status === 'DRAFT' || item.status === 'REJECTED' || item.status === 'PUBLISHED' || item.status === 'UNLISTED') && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => router.push(`/software-edit?id=${item.softwareId}`)}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#eef2ff' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#3d5afe' }}>
              {item.status === 'PUBLISHED' ? '编辑新版' : '继续编辑'}
            </Text>
          </TouchableOpacity>
        )}
        {item.status === 'UNLISTED' && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => void gate(() => softwarePublishApi.relist(item.softwareId), '已重新上架')}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#eef2ff' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#3d5afe' }}>重新上架</Text>
          </TouchableOpacity>
        )}
        {item.status === 'PUBLISHED' && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => void gate(() => softwarePublishApi.unlist(item.softwareId), '已下架')}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#fdecec' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>下架</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity
          disabled={busy}
          onPress={() => confirmDelete(item)}
          style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#fdecec' }}
        >
          <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>删除</Text>
        </TouchableOpacity>
      </View>
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top + Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: theme.text }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>我的软件</Text>
        <TouchableOpacity
          disabled={busy}
          onPress={() => {
            void (async () => {
              setBusy(true)
              try {
                const created = await softwarePublishApi.create()
                router.push(`/software-edit?id=${created.data.data.softwareId}`)
              } catch {
                Alert.alert('提示', '创建失败，请稍后再试')
              } finally {
                setBusy(false)
              }
            })()
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: '#3d5afe', fontWeight: '600' }}>＋ 新建</Text>
        </TouchableOpacity>
      </View>
      {loading ? (
        <ActivityIndicator style={{ marginTop: 48 }} />
      ) : items.length === 0 ? (
        <Text style={{ textAlign: 'center', marginTop: 48, color: theme.textSecondary }}>
          还没有发布过软件，点右上角「＋ 新建」开始
        </Text>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.softwareId}
          renderItem={renderItem}
          contentContainerStyle={{ padding: Spacing.md, paddingBottom: 48 }}
        />
      )}
    </View>
  )
}
