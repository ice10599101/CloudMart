import { useCallback, useEffect, useState } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { softwarePublishApi, type SoftwareMineItem } from '@/api/software'
import { errorMessage } from '@/utils/request'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: '已发布',
  UNLISTED: '已下架',
  BANNED: '已封禁',
  DRAFT: '草稿',
  PENDING: '审核中',
  REJECTED: '已退回',
}

const STATUS_CLASS: Record<string, string> = {
  PUBLISHED: styles.badgePublished,
  UNLISTED: styles.badgeUnlisted,
  BANNED: styles.badgeBanned,
  DRAFT: styles.badgeDraft,
  PENDING: styles.badgePending,
  REJECTED: styles.badgeRejected,
}

export default function SoftwareMinePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const router = useRouter()
  const [items, setItems] = useState<SoftwareMineItem[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await softwarePublishApi.mine(1, 50)
      setItems(res.data.data?.records ?? [])
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    // 新建/编辑返回后刷新
    const flag = router.params.refreshed
    load()
    void flag
  }, [isLoggedIn, router.params.refreshed, load])

  const gate = async (fn: () => Promise<unknown>, doneTip: string) => {
    setBusy(doneTip)
    try {
      await fn()
      Taro.showToast({ title: doneTip, icon: 'success' })
      await load()
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' })
    } finally {
      setBusy(null)
    }
  }

  const openEditor = (softwareId: string) => {
    Taro.navigateTo({ url: `/pages/softwareEdit/index?id=${softwareId}&refreshed=1` })
  }

  const createNew = async () => {
    try {
      const created = await softwarePublishApi.create()
      Taro.redirectTo({ url: `/pages/softwareEdit/index?id=${created.data.data.softwareId}&refreshed=1` })
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '创建失败'), icon: 'none' })
    }
  }

  const statusActions = (it: SoftwareMineItem) => {
    const act: Array<{ label: string; run: () => void; danger?: boolean }> = []
    const lock = it.lockVersion
    const id = it.softwareId
    if (it.status === 'DRAFT' || it.status === 'REJECTED') {
      act.push({ label: '继续编辑', run: () => openEditor(id) })
    }
    if (it.status === 'PUBLISHED' || it.status === 'UNLISTED') {
      act.push({ label: '编辑新版', run: () => openEditor(id) })
    }
    if (it.status === 'UNLISTED') {
      act.push({
        label: '重新上架',
        run: () => gate(() => softwarePublishApi.relist(id, lock), '已重新上架'),
      })
    }
    if (it.status === 'PUBLISHED') {
      act.push({
        label: '下架',
        run: () => gate(() => softwarePublishApi.unlist(id, lock), '已下架'),
        danger: true,
      })
    }
    act.push({
      label: '删除',
      run: () =>
        Taro.showModal({
          title: '删除软件',
          content: '删除后不可恢复，确定继续？',
          success: (r) => {
            if (r.confirm) gate(() => softwarePublishApi.deleteSoftware(id, lock), '已删除')
          },
        }),
      danger: true,
    })
    return act
  }

  return (
    <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
      <CustomNavBar title='我的软件' back />
      <ScrollView scrollY className={styles.scroll}>
        <View className={styles.headerRow}>
          <Text className={styles.title}>我发布的软件</Text>
          <View
            className={`${styles.newBtn} ${busy ? styles.btnDisabled : ''}`}
            onClick={() => {
              if (busy) return
              void createNew()
            }}
          >
            <Text className={styles.newBtnText}>＋ 新建软件</Text>
          </View>
        </View>

        {loading && <Text className={styles.hint}>加载中…</Text>}
        {!loading && items.length === 0 && (
          <Text className={styles.hint}>还没有发布过软件，点右上角「新建软件」开始</Text>
        )}

        {items.map((it) => (
          <View key={it.softwareId} className={styles.card}>
            <View className={styles.cardHead}>
              <Text className={styles.cardName}>{it.unavailable ? '（内容已不可见）' : it.name}</Text>
              <Text className={`${styles.badge} ${STATUS_CLASS[it.status] ?? styles.badgeDraft}`}>
                {STATUS_LABEL[it.status] ?? it.status}
              </Text>
            </View>
            {!it.unavailable && !!it.summary && <Text className={styles.cardSummary}>{it.summary}</Text>}
            <View className={styles.cardFoot}>
              <Text className={styles.meta}>收藏 {it.favoriteCount}</Text>
              <Text className={styles.meta}>{(it.updatedAt || '').replace('T', ' ').slice(0, 16)}</Text>
            </View>
            {it.status === 'REJECTED' && <Text className={styles.rejectTip}>被退回：修改后可再次提交</Text>}
            <View className={styles.actionRow}>
              {statusActions(it).map((a) => (
                <View
                  key={a.label}
                  className={`${styles.actionBtn} ${a.danger ? styles.actionDanger : ''} ${busy ? styles.btnDisabled : ''}`}
                  onClick={() => {
                    if (busy) return
                    a.run()
                  }}
                >
                  <Text className={styles.actionText}>{a.label}</Text>
                </View>
              ))}
            </View>
          </View>
        ))}
        <View style={{ height: '60rpx' }} />
      </ScrollView>
    </View>
  )
}
