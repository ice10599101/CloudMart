import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  FlatList,
  Switch,
  Text,
  TouchableOpacity,
  View,
  Alert,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { softwareApi, softwarePublishApi, getMyPublisherApplication,
  submitPublisherApplication, cancelPublisherApplication,
  type SoftwarePublisherApplication } from '@/api/software'
import type { SoftwareMineItem } from '@/api/software'
import { useAuthStore } from '@/store/auth'
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

const MINE_TABS: { key: TabKey; label: string }[] = [
  { key: 'published', label: '已发布' },
  { key: 'draft', label: '草稿' },
  { key: 'pending', label: '审核中' },
  { key: 'rejected', label: '已退回' },
  { key: 'favorites', label: '收藏' },
  { key: 'subscriptions', label: '订阅' },
]
type TabKey =
  | 'published'
  | 'draft'
  | 'pending'
  | 'rejected'
  | 'favorites'
  | 'subscriptions'

const PAGE_SIZE = 20

export default function SoftwareMineScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const params = useLocalSearchParams<{ refreshed?: string }>()
  const [tab, setTab] = useState<TabKey>('published')
  const [items, setItems] = useState<SoftwareMineItem[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [busy, setBusy] = useState(false)
  // SF03：发布资格申请状态卡
  const [application, setApplication] = useState<SoftwarePublisherApplication | null>(null)
  /** SW11：更新提醒总开关（与订阅关系相互独立；退订≠关闭提醒） */
  const [updateNotify, setUpdateNotify] = useState(true)

  useEffect(() => {
    softwareApi.preferences()
      .then((res) => setUpdateNotify(res.data.data?.updateNotifications ?? true))
      .catch(() => undefined)
  }, [])

  const handleToggleNotify = async (enabled: boolean) => {
    const prev = updateNotify
    setUpdateNotify(enabled)
    try {
      await softwareApi.updatePreferences(enabled)
      Alert.alert('提示', enabled ? '已开启软件更新提醒' : '已关闭软件更新提醒')
    } catch {
      setUpdateNotify(prev)
    }
  }

  const isOwnTab = tab === 'published' || tab === 'draft' || tab === 'pending'
    || tab === 'rejected'

  /** SW11：分页加载——首页覆盖，加载更多追加；切换页签重置游标 */
  const load = useCallback(async (nextTab: TabKey, nextPage: number) => {
    if (nextPage === 1) setLoading(true)
    else setLoadingMore(true)
    try {
      const res = await softwarePublishApi.mine(nextPage, PAGE_SIZE, nextTab)
      const records = res.data.data?.records ?? []
      const total = res.data.data?.total ?? 0
      setItems((prev) =>
        nextPage === 1 ? records : [...prev, ...records.filter(
          (row) => !prev.some((p) => p.softwareId === row.softwareId),
        )],
      )
      setHasMore(records.length > 0 && (nextPage - 1) * PAGE_SIZE + records.length < total)
      setPage(nextPage)
    } catch {
      Alert.alert('提示', '加载失败，请稍后再试')
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    void load(tab, 1)
  }, [load, tab, params.refreshed])


  // SF03：加载我的发布资格申请（状态卡）
  useEffect(() => {
    getMyPublisherApplication()
      .then((res) => setApplication(res.data?.data ?? null))
      .catch(() => undefined);
  }, []);  const gate = async (fn: () => Promise<unknown>, doneTip: string) => {
    setBusy(true)
    try {
      await fn()
      Alert.alert('提示', doneTip)
      await load(tab, 1)
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
        {!isOwnTab && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => router.push(`/software-detail?id=${item.softwareId}`)}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#eef2ff' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#3d5afe' }}>查看详情</Text>
          </TouchableOpacity>
        )}
        {!isOwnTab && tab === 'favorites' && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => void gate(() => softwareApi.unfavorite(item.softwareId), '已取消收藏')}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#fdecec' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>取消收藏</Text>
          </TouchableOpacity>
        )}
        {!isOwnTab && tab === 'subscriptions' && (
          <TouchableOpacity
            disabled={busy}
            onPress={() => void gate(() => softwareApi.unsubscribe(item.softwareId), '已退订')}
            style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#fdecec' }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>退订</Text>
          </TouchableOpacity>
        )}
        {isOwnTab && (item.status === 'DRAFT' || item.status === 'REJECTED' || item.status === 'PUBLISHED' || item.status === 'UNLISTED') && (
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
        {tab === 'published' && (
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
        )}
      </View>
      {/* SF03：发布资格申请状态卡（PENDING 可撤回；REJECTED 显示原因可重申） */}
      {application ? (
        <View
          style={{
            marginHorizontal: Spacing.md,
            marginBottom: Spacing.sm,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            borderWidth: 1,
            borderColor: theme.border ?? '#e2e8f0',
          }}
        >
          <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>
            发布资格申请：{
              application.status === 'PENDING' ? '审核中'
              : application.status === 'APPROVED' ? '已通过'
              : application.status === 'REJECTED' ? '未通过'
              : '已撤回'
            }
          </Text>
          {application.reviewNote ? (
            <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary, marginTop: 2 }}>
              审核意见：{application.reviewNote}
            </Text>
          ) : null}
          {application.status === 'PENDING' || application.status === 'REJECTED' ? (
            <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.sm }}>
              {application.status === 'PENDING' ? (
                <TouchableOpacity
                  onPress={() => {
                    void (async () => {
                      try {
                        await cancelPublisherApplication(application.id)
                        setApplication(null)
                      } catch {
                        Alert.alert('提示', '撤回失败，请稍后再试')
                      }
                    })()
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: '#d3392f' }}>撤回申请</Text>
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  onPress={() => {
                    void (async () => {
                      try {
                        const res = await submitPublisherApplication({
                          motivation: '重新申请发布资格',
                        })
                        setApplication(res.data?.data ?? null)
                      } catch {
                        Alert.alert('提示', '提交失败，请稍后再试')
                      }
                    })()
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: '#3d5afe' }}>重新申请</Text>
                </TouchableOpacity>
              )}
            </View>
          ) : null}
        </View>
      ) : (
        <View
          style={{
            marginHorizontal: Spacing.md,
            marginBottom: Spacing.sm,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            borderWidth: 1,
            borderColor: theme.border ?? '#e2e8f0',
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
            想发布软件？提交发布资格申请，审核通过后开放发布。
          </Text>
          <TouchableOpacity
            onPress={() => {
              void (async () => {
                try {
                  const res = await submitPublisherApplication({
                    motivation: '申请发布资格（App 端提交）',
                  })
                  setApplication(res.data?.data ?? null)
                } catch {
                  Alert.alert('提示', '提交失败（可能已有申请或已具资格）')
                }
              })()
            }}
          >
            <Text style={{ fontSize: FontSize.sm, color: '#3d5afe', marginTop: 4 }}>
              提交发布资格申请 →
            </Text>
          </TouchableOpacity>
        </View>
      )}

      {/* SW11：更新提醒开关（我的软件设置；退订与关闭语义不同） */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: 6,
        }}
      >
        <Text style={{ fontSize: FontSize.sm, color: theme.text }}>软件更新提醒</Text>
        <Switch value={updateNotify} onValueChange={(v) => void handleToggleNotify(v)} />
      </View>

      {/* UX05/UX06：更新流与需求入口（独立页面，不挤占页签） */}
      <View style={{ flexDirection: 'row', paddingHorizontal: Spacing.md, paddingTop: Spacing.xs, gap: Spacing.sm }}>
        <TouchableOpacity
          onPress={() => router.push('/software-updates')}
          style={{
            flex: 1,
            padding: Spacing.sm,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            borderWidth: 1,
            borderColor: theme.border ?? '#e2e8f0',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: theme.text }}>订阅更新</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => router.push('/software-requests')}
          style={{
            flex: 1,
            padding: Spacing.sm,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            borderWidth: 1,
            borderColor: theme.border ?? '#e2e8f0',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: theme.text }}>找软件需求</Text>
        </TouchableOpacity>
      </View>

      {/* SW11：页签——投稿/收藏/订阅 + 翻页加载更多 */}
      <View style={{ flexDirection: 'row', paddingHorizontal: Spacing.md, paddingBottom: Spacing.xs, gap: Spacing.xs }}>
        {MINE_TABS.map((entry) => {
          const active = entry.key === tab
          return (
            <TouchableOpacity
              key={entry.key}
              onPress={() => setTab(entry.key)}
              style={{
                paddingHorizontal: Spacing.sm,
                paddingVertical: 6,
                borderRadius: BorderRadius.full,
                backgroundColor: active ? '#3d5afe' : (theme.bgElevated ?? '#fff'),
                borderWidth: 1,
                borderColor: active ? '#3d5afe' : (theme.border ?? '#e2e8f0'),
              }}
            >
              <Text style={{ fontSize: FontSize.xs, color: active ? '#fff' : theme.text }}>
                {entry.label}
              </Text>
            </TouchableOpacity>
          )
        })}
      </View>
      {loading ? (
        <ActivityIndicator style={{ marginTop: 48 }} />
      ) : items.length === 0 ? (
        <Text style={{ textAlign: 'center', marginTop: 48, color: theme.textSecondary }}>
          {isOwnTab ? '这里还没有内容，点右上角「＋ 新建」开始' : '暂无内容'}
        </Text>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.softwareId}
          renderItem={renderItem}
          contentContainerStyle={{ padding: Spacing.md, paddingBottom: 48 }}
          onEndReachedThreshold={0.4}
          onEndReached={() => {
            if (!loadingMore && hasMore) void load(tab, page + 1)
          }}
          ListFooterComponent={
            loadingMore ? <ActivityIndicator style={{ marginVertical: 12 }} /> : null
          }
        />
      )}
    </View>
  )
}
