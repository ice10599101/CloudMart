import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { DataExportTask } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 数据导出（合规 34.2 / 2.13，四AB B5 移动端）：
 * 触发导出 → 任务列表（状态轮询）→ 复制 JSON 内容到剪贴板
 * （完整文件下载建议使用 WEB 端）。
 */
export default function DataExportPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [tasks, setTasks] = useState<DataExportTask[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listDataExports()
      if (res.data.success) setTasks(res.data.data ?? [])
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
      if (res.data.success) {
        Taro.showToast({ title: '导出任务已创建', icon: 'none' })
        load()
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '创建失败，请稍后重试', icon: 'none' })
    } finally {
      setCreating(false)
    }
  }

  const handleCopy = async (task: DataExportTask) => {
    try {
      const res = await wishApi.getDataExportContent(task.id)
      if (res.data.success) {
        await Taro.setClipboardData({ data: JSON.stringify(res.data.data) })
      }
    } catch {
      Taro.showToast({ title: '内容获取失败（可能已过期）', icon: 'none' })
      load()
    }
  }

  const statusLabel = (status: DataExportTask['status']): string =>
    ({ PENDING: '排队中', PROCESSING: '生成中', SUCCESS: '已完成', FAILED: '已失效' })[status]

  const statusClass = (status: DataExportTask['status']): string =>
    `status${status}`

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title='数据导出' back />
      <ScrollView className={styles.list} scrollY>
        <Text className={styles.hint}>
          依据合规要求提供个人数据副本导出（心愿/成长/互动/收藏等），生成后 7 天内有效。
          移动端复制 JSON 内容；完整文件下载请使用 WEB 端。
        </Text>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : loading ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : tasks.length === 0 ? (
          <View className={styles.empty}><Text>还没有导出任务</Text></View>
        ) : (
          tasks.map((task) => (
            <View key={task.id} className={styles.taskCard}>
              <View className={styles.taskHeader}>
                <Text className={styles.taskId}>任务 {String(task.id).slice(-8)}</Text>
                <Text className={statusClass(task.status)}>{statusLabel(task.status)}</Text>
              </View>
              <Text className={styles.taskMeta}>
                发起：{new Date(task.createdAt).toLocaleString('zh-CN')}
                {task.expiresAt ? ` · 有效期至 ${new Date(task.expiresAt).toLocaleDateString('zh-CN')}` : ''}
              </Text>
              <View>
                {task.status === 'SUCCESS' && (
                  <View className={styles.copyBtn} onClick={() => handleCopy(task)}>
                    <Text>复制 JSON</Text>
                  </View>
                )}
                {(task.status === 'PENDING' || task.status === 'PROCESSING') && (
                  <Text className={statusClass(task.status)}>生成中，稍后自动刷新…</Text>
                )}
                {task.status === 'FAILED' && (
                  <Text className={styles.taskMeta}>已失效，可重新发起</Text>
                )}
              </View>
            </View>
          ))
        )}
        <View style={{ padding: '8rpx 0 40rpx' }}>
          <View
            className={styles.actionBtn}
            onClick={creating ? undefined : handleCreate}
          >
            <Text>{creating ? '创建中...' : '+ 发起导出'}</Text>
          </View>
        </View>
      </ScrollView>
    </View>
  )
}
