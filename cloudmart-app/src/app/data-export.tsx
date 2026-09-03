import { useState, useEffect, useCallback } from 'react'
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Clipboard from 'expo-clipboard'
import { wishApi } from '@/api/wish'
import type { DataExportTask } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const STATUS_LABELS: Record<DataExportTask['status'], { label: string; color: string }> = {
  PENDING: { label: '排队中', color: WishColors.textSecondary },
  PROCESSING: { label: '生成中', color: WishColors.accentCyan },
  SUCCESS: { label: '已完成', color: '#52c41a' },
  FAILED: { label: '已失效', color: '#ff6b6b' },
}

/**
 * 数据导出（合规 34.2 / 2.13，四AB B5 APP 端）：
 * 触发导出 → 任务列表（状态轮询）→ 复制 JSON 内容到剪贴板
 * （完整文件下载建议使用 WEB 端）。
 */
export default function DataExportScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [tasks, setTasks] = useState<DataExportTask[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listDataExports()
      if (res.data?.success) setTasks(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    load()
  }, [load])

  // PENDING/PROCESSING 任务每 3s 轮询至完成
  useEffect(() => {
    const pending = tasks.some((t) => t.status === 'PENDING' || t.status === 'PROCESSING')
    if (!pending) return
    const timer = setInterval(load, 3000)
    return () => clearInterval(timer)
  }, [tasks, load])

  const handleCreate = async () => {
    setCreating(true)
    try {
      const res = await wishApi.createDataExport()
      if (res.data?.success) {
        alert('导出任务已创建，正在后台生成')
        load()
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '创建失败，请稍后重试')
    } finally {
      setCreating(false)
    }
  }

  const handleCopy = async (task: DataExportTask) => {
    try {
      const res = await wishApi.getDataExportContent(task.id)
      if (res.data?.success) {
        await Clipboard.setStringAsync(JSON.stringify(res.data.data))
        alert('JSON 已复制到剪贴板')
      }
    } catch {
      alert('内容获取失败（可能已过期）')
      load()
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>数据导出</Text>
        <TouchableOpacity onPress={load}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>刷新</Text>
        </TouchableOpacity>
      </View>

      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, paddingHorizontal: Spacing.lg, lineHeight: 18, marginBottom: Spacing.sm }}>
        合规个人数据副本（心愿/成长/互动/收藏等），7 天内有效。APP 端复制 JSON；完整文件下载请用 WEB 端。
      </Text>

      <FlatList
        data={tasks}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ padding: Spacing.lg }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 60 }} />
          ) : (
            <Text style={{ textAlign: 'center', marginTop: 60, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
              还没有导出任务
            </Text>
          )
        }
        renderItem={({ item }) => (
          <View
            style={{
              backgroundColor: WishColors.bgContainer,
              borderRadius: BorderRadius.lg,
              padding: Spacing.md,
              marginBottom: Spacing.sm,
            }}
          >
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
                任务 {String(item.id).slice(-8)}
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: STATUS_LABELS[item.status].color }}>
                {STATUS_LABELS[item.status].label}
              </Text>
            </View>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
              发起：{new Date(item.createdAt).toLocaleString('zh-CN')}
              {item.expiresAt ? ` · 有效期至 ${new Date(item.expiresAt).toLocaleDateString('zh-CN')}` : ''}
            </Text>
            {item.status === 'SUCCESS' && (
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={() => handleCopy(item)}
                style={{
                  alignSelf: 'flex-end',
                  paddingHorizontal: Spacing.md,
                  paddingVertical: 6,
                  borderRadius: BorderRadius.md,
                  backgroundColor: 'rgba(0, 212, 255, 0.12)',
                }}
              >
                <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>复制 JSON</Text>
              </TouchableOpacity>
            )}
            {item.status === 'FAILED' && (
              <Text style={{ fontSize: FontSize.xs, color: '#ff6b6b' }}>已失效，可重新发起</Text>
            )}
          </View>
        )}
      />

      <TouchableOpacity
        activeOpacity={0.85}
        disabled={creating}
        onPress={handleCreate}
        style={{
          margin: Spacing.lg,
          paddingVertical: Spacing.md,
          borderRadius: BorderRadius.lg,
          alignItems: 'center',
          backgroundColor: WishColors.accentCyan,
          opacity: creating ? 0.6 : 1,
        }}
      >
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#0b1026' }}>
          {creating ? '创建中...' : '+ 发起导出'}
        </Text>
      </TouchableOpacity>
    </View>
  )
}
