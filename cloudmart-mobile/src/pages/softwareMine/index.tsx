import { useCallback, useEffect, useState } from 'react'
import { View, Text, ScrollView, Switch } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { softwareApi, softwarePublishApi, type SoftwareMineItem , type SoftwarePublisherApplication } from '@/api/software'
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
  WITHDRAWN: '已撤回',
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

const MINE_TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'published', label: '已发布' },
  { key: 'draft', label: '草稿' },
  { key: 'pending', label: '审核中' },
  { key: 'rejected', label: '已退回' },
  { key: 'favorites', label: '收藏' },
  { key: 'subscriptions', label: '订阅' },
]
type TabKey = 'published' | 'draft' | 'pending' | 'rejected'
  | 'favorites' | 'subscriptions'

const PAGE_SIZE = 20

export default function SoftwareMinePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const router = useRouter()
  const [tab, setTab] = useState<TabKey>('published')
  const [items, setItems] = useState<SoftwareMineItem[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  // SF03：发布资格申请状态卡
  const [application, setApplication] = useState<SoftwarePublisherApplication | null>(null)
  /** SW11：更新提醒总开关（与订阅关系相互独立；退订≠关闭提醒） */
  const [updateNotify, setUpdateNotify] = useState(true)

  const isOwnTab = tab === 'published' || tab === 'draft' || tab === 'pending'
    || tab === 'rejected'

  /** SW11：分页加载——首页覆盖，加载更多追加；切页签重置游标 */
  const load = useCallback(async (nextTab: TabKey, nextPage: number) => {
    if (nextPage === 1) setLoading(true)
    else setLoadingMore(true)
    try {
      const res = await softwarePublishApi.mine(nextPage, PAGE_SIZE, nextTab)
      const records = res.data.data?.records ?? []
      const total = res.data.data?.total ?? 0
      setItems((prev) => nextPage === 1 ? records : [...prev, ...records.filter(
        (row) => !prev.some((p) => p.softwareId === row.softwareId),
      )])
      setHasMore(records.length > 0 && (nextPage - 1) * PAGE_SIZE + records.length < total)
      setPage(nextPage)
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '加载失败'), icon: 'none' })
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    softwareApi.preferences()
      .then((res) => setUpdateNotify(res.data.data?.updateNotifications ?? true))
      .catch(() => undefined)
    // 新建/编辑返回后刷新
    const flag = router.params.refreshed
    void load(tab, 1)
    void flag
  }, [isLoggedIn, router.params.refreshed, load, tab])

  // SF03：加载我的发布资格申请
  useEffect(() => {
    softwareApi.myPublisherApplication()
      .then((res) =>
        setApplication(
          (res.data as { data?: SoftwarePublisherApplication | null })?.data ?? null,
        ),
      )
      .catch(() => undefined)
  }, [])
  const handleToggleNotify = async (enabled: boolean) => {
    const prev = updateNotify
    setUpdateNotify(enabled)
    try {
      await softwareApi.updatePreferences(enabled)
      Taro.showToast({ title: enabled ? '已开启更新提醒' : '已关闭更新提醒', icon: 'success' })
    } catch {
      setUpdateNotify(prev)
    }
  }

  const gate = async (fn: () => Promise<unknown>, doneTip: string) => {
    setBusy(doneTip)
    try {
      await fn()
      Taro.showToast({ title: doneTip, icon: 'success' })
      await load(tab, 1)
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

  /** SW11：收藏/订阅页签的行操作（详情 + 取消收藏/退订，取消后刷新） */
  const collectionActions = (it: SoftwareMineItem) => {
    const act: Array<{ label: string; run: () => void; danger?: boolean }> = []
    const id = it.softwareId
    act.push({
      label: '查看详情',
      run: () => Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${id}` }),
    })
    if (tab === 'favorites') {
      act.push({
        label: '取消收藏',
        run: () => gate(() => softwareApi.unfavorite(id), '已取消收藏'),
        danger: true,
      })
    }
    if (tab === 'subscriptions') {
      act.push({
        label: '退订',
        run: () => gate(() => softwareApi.unsubscribe(id), '已退订'),
        danger: true,
      })
    }
    return act
  }

  const statusActions = (it: SoftwareMineItem) => {
    const act: Array<{ label: string; run: () => void; danger?: boolean }> = []
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
        run: () => gate(() => softwarePublishApi.relist(id), '已重新上架'),
      })
    }
    if (it.status === 'PUBLISHED') {
      act.push({
        label: '下架',
        run: () => gate(() => softwarePublishApi.unlist(id), '已下架'),
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
            if (r.confirm) gate(() => softwarePublishApi.deleteSoftware(id), '已删除')
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
          <Text className={styles.title}>我的软件</Text>
          {tab === 'published' && (
            <View
              className={`${styles.newBtn} ${busy ? styles.btnDisabled : ''}`}
              onClick={() => {
                if (busy) return
                void createNew()
              }}
            >
              <Text className={styles.newBtnText}>＋ 新建软件</Text>
            </View>
          )}
        </View>

        {/* SW11：更新提醒开关（退订与关闭语义不同） */}
        <View className={styles.notifyRow}>
          <Text className={styles.notifyLabel}>软件更新提醒</Text>
          <Switch
            checked={updateNotify}
            onChange={(event) => void handleToggleNotify(event.detail.value)}
          />
        </View>

        {/* SF03：发布资格申请状态卡 */}
        <View className={styles.card}>
          {application ? (
            <>
              <Text className={styles.cardTitle}>
                发布资格申请：{
                  application.status === 'PENDING' ? '审核中'
                  : application.status === 'APPROVED' ? '已通过'
                  : application.status === 'REJECTED' ? '未通过'
                  : '已撤回'
                }
              </Text>
              {application.reviewNote ? (
                <Text className={styles.cardSummary}>审核意见：{application.reviewNote}</Text>
              ) : null}
              {application.status === 'PENDING' ? (
                <Text
                  className={styles.cardSummary}
                  style={{ color: '#d3392f' }}
                  onClick={() => {
                    void (async () => {
                      try {
                        await softwareApi.cancelPublisherApplication(application.id)
                        setApplication(null)
                      } catch {
                        Taro.showToast({ title: '撤回失败', icon: 'none' })
                      }
                    })()
                  }}
                >
                  撤回申请
                </Text>
              ) : null}
              {application.status === 'REJECTED' ? (
                <Text
                  className={styles.cardSummary}
                  style={{ color: '#3d5afe' }}
                  onClick={() => {
                    void (async () => {
                      try {
                        const res = await softwareApi.submitPublisherApplication({
                          motivation: '重新申请发布资格',
                        })
                        setApplication(
                          (res.data as { data?: SoftwarePublisherApplication | null })?.data ?? null,
                        )
                      } catch {
                        Taro.showToast({ title: '提交失败', icon: 'none' })
                      }
                    })()
                  }}
                >
                  重新申请
                </Text>
              ) : null}
            </>
          ) : (
            <>
              <Text className={styles.cardSummary}>
                想发布软件？提交发布资格申请，审核通过后开放发布。
              </Text>
              <Text
                className={styles.cardSummary}
                style={{ color: '#3d5afe' }}
                onClick={() => {
                  void (async () => {
                    try {
                      const res = await softwareApi.submitPublisherApplication({
                        motivation: '申请发布资格（小程序端提交）',
                      })
                      setApplication(
                        (res.data as { data?: SoftwarePublisherApplication | null })?.data ?? null,
                      )
                    } catch {
                      Taro.showToast({ title: '提交失败（可能已有申请）', icon: 'none' })
                    }
                  })()
                }}
              >
                提交发布资格申请 →
              </Text>
            </>
          )}
        </View>

        {/* SW11：页签——投稿/收藏/订阅 + 翻页加载更多 */}
        <ScrollView scrollX className={styles.tabBar} enhanced showScrollbar={false}>
                {/* UX05/UX06：更新流与需求入口 */}
      <View style={{ display: 'flex', flexDirection: 'row', gap: 8, padding: '0 24rpx 8rpx' }}>
        <View
          className={styles.card}
          style={{ flex: 1, padding: '12rpx 0', textAlign: 'center' }}
          onClick={() => Taro.navigateTo({ url: '/pages/softwareUpdates/index' })}
        >
          <Text className={styles.cardName}>订阅更新</Text>
        </View>
        <View
          className={styles.card}
          style={{ flex: 1, padding: '12rpx 0', textAlign: 'center' }}
          onClick={() => Taro.navigateTo({ url: '/pages/softwareRequests/index' })}
        >
          <Text className={styles.cardName}>找软件需求</Text>
        </View>
      </View>

<View className={styles.tabRow}>
            {MINE_TABS.map((entry) => (
              <View
                key={entry.key}
                className={`${styles.tabItem} ${entry.key === tab ? styles.tabActive : ''}`}
                onClick={() => setTab(entry.key)}
              >
                <Text className={entry.key === tab ? styles.tabActiveText : styles.tabText}>
                  {entry.label}
                </Text>
              </View>
            ))}
          </View>
        </ScrollView>

        {loading && <Text className={styles.hint}>加载中…</Text>}
        {!loading && items.length === 0 && (
          <Text className={styles.hint}>
            {isOwnTab ? '这里还没有内容，点右上角「新建软件」开始' : '暂无内容'}
          </Text>
        )}

        {items.map((it) => (
          <View key={it.softwareId} className={styles.card}>
            <View className={styles.cardHead}>
              <Text className={styles.cardName}>{it.unavailable ? '（内容已不可见）' : it.name}</Text>
              {isOwnTab && (
                <Text className={`${styles.badge} ${STATUS_CLASS[it.status] ?? styles.badgeDraft}`}>
                  {STATUS_LABEL[it.status] ?? it.status}
                </Text>
              )}
            </View>
            {!it.unavailable && !!it.summary && <Text className={styles.cardSummary}>{it.summary}</Text>}
            <View className={styles.cardFoot}>
              <Text className={styles.meta}>收藏 {it.favoriteCount}</Text>
              <Text className={styles.meta}>{(it.updatedAt || '').replace('T', ' ').slice(0, 16)}</Text>
            </View>
            {it.status === 'REJECTED' && <Text className={styles.rejectTip}>被退回：修改后可再次提交</Text>}
            <View className={styles.actionRow}>
              {(isOwnTab ? statusActions(it) : collectionActions(it)).map((a) => (
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
        {!loading && hasMore && (
          <View
            className={styles.loadMoreBtn}
            onClick={() => {
              if (!loadingMore) void load(tab, page + 1)
            }}
          >
            <Text className={styles.loadMoreText}>{loadingMore ? '加载中…' : '加载更多'}</Text>
          </View>
        )}
        <View style={{ height: '60rpx' }} />
      </ScrollView>
    </View>
  )
}
