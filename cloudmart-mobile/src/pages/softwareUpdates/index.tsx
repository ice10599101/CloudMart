import { useCallback, useEffect, useState } from 'react'
import { ScrollView, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { listMyUpdates, markUpdatesRead, type SoftwareUpdateItem } from '@/api/software'
import styles from './index.module.scss'

/** UX05：订阅更新流（小程序端）——突出"这次改了什么"；已读幂等；下架标注不可用 */
export default function SoftwareUpdates() {
  const [items, setItems] = useState<SoftwareUpdateItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async (append: boolean, cur: string | null) => {
    setLoading(true)
    try {
      const res = await listMyUpdates(append ? cur : null, 20)
      const feed = res.data?.data ?? { items: [], hasMore: false, nextCursor: null }
      setItems((prev) => (append ? [...prev, ...feed.items] : feed.items))
      setCursor(feed.nextCursor)
      setHasMore(feed.hasMore)
    } catch {
      Taro.showToast({ title: '加载失败，请稍后重试', icon: 'none' })
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
    <View className={styles.page}>
      {loading && items.length === 0 ? (
        <Text className={styles.emptyText}>加载中…</Text>
      ) : (
        <ScrollView scrollY style={{ height: '100vh' }}>
          {items.length === 0 ? (
            <Text className={styles.emptyText}>暂无更新；订阅的软件发布新版本后会出现在这里</Text>
          ) : (
            items.map((it) => (
              <View key={it.eventId} className={styles.card}>
                <View style={{ display: 'flex', flexDirection: 'row', alignItems: 'center' }}>
                  {!it.read && (
                    <Text style={{ fontSize: 11, color: '#FF4D4F', backgroundColor: '#FFF1F0', padding: '0 6px', borderRadius: 4 }}>
                      未读
                    </Text>
                  )}
                  <Text className={styles.title}>
                    {it.softwareName || `软件 ${it.softwareId}`}
                    {it.versionName ? ` · ${it.versionName}` : ''}
                  </Text>
                </View>
                {it.changelog ? <Text className={styles.desc}>{it.changelog}</Text> : null}
                <View style={{ display: 'flex', flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 }}>
                  <Text style={{ fontSize: 11, color: '#999' }}>
                    {it.publishedAt ? `发布于 ${it.publishedAt.slice(0, 16).replace('T', ' ')}` : ''}
                  </Text>
                  <View style={{ display: 'flex', flexDirection: 'row', gap: 16 }}>
                    {it.softwareVisible ? (
                      <Text
                        style={{ fontSize: 12, color: '#1677FF' }}
                        onClick={() =>
                          Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${it.softwareId}` })
                        }
                      >
                        查看软件
                      </Text>
                    ) : (
                      <Text style={{ fontSize: 12, color: '#999' }}>当前不可用</Text>
                    )}
                    {!it.read && (
                      <Text style={{ fontSize: 12, color: '#1677FF' }} onClick={() => void read(it.eventId)}>
                        标记已读
                      </Text>
                    )}
                  </View>
                </View>
              </View>
            ))
          )}
          {hasMore && (
            <Text
              className={styles.emptyText}
              style={{ color: '#1677FF' }}
              onClick={() => void load(true, cursor)}
            >
              加载更多
            </Text>
          )}
        </ScrollView>
      )}
    </View>
  )
}
