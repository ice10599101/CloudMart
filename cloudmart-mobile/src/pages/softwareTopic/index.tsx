import { useCallback, useEffect, useState } from 'react'
import { Image, Text, View } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { softwareApi, type SoftwareTopicDetail } from '@/api/software'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

function iconUrl(assetId: string | null): string | undefined {
  return assetId ? `/files/${assetId}` : undefined
}

/**
 * 专题详情（SW11-3 移动端）：广场专题入口 → 详情 → 软件。
 * 清单由后端按实时可见性过滤（草稿/封禁不泄露）；后台下线后详情报错 → 明确提示。
 */
export default function SoftwareTopicPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const router = useRouter()
  const slug = router.params.slug
  const [topic, setTopic] = useState<SoftwareTopicDetail | null>(null)
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

  return (
    <View className={styles.page} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title={topic?.title ?? '专题'} back />
      {loading && <Text className={styles.emptyText}>加载中…</Text>}
      {!loading && (missing || !topic) ? (
        <Text className={styles.emptyText}>专题不存在或已下线</Text>
      ) : null}
      {!loading && topic ? (
        <View>
          {topic.summary ? <Text className={styles.summary}>{topic.summary}</Text> : null}
          {/* UX02：工具箱三要素（能帮你做什么/适合谁/准备条件） */}
          {(topic.problem || topic.audience || topic.prerequisites) ? (
            <View className={styles.card}>
              {topic.problem ? <Text className={styles.cardSummary}>能帮你做什么：{topic.problem}</Text> : null}
              {topic.audience ? <Text className={styles.cardSummary}>适合谁：{topic.audience}</Text> : null}
              {topic.prerequisites ? <Text className={styles.cardSummary}>准备条件：{topic.prerequisites}</Text> : null}
            </View>
          ) : null}
          {/* UX02：按分组展示（用途说明/可选替代；稳定 groupKey） */}
          {(topic.groups ?? []).length > 0
            ? (topic.groups ?? []).map((group) => (
                <View key={group.groupKey} className={styles.card}>
                  <Text className={styles.cardTitle}>{group.groupKey}</Text>
                  {group.items.map((item) => (
                    <View
                      key={`${group.groupKey}-${item.softwareId}`}
                      style={{ marginTop: 8 }}
                      onClick={() =>
                        Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${item.softwareId}` })
                      }
                    >
                      <Text className={styles.cardSummary}>
                        {item.name}
                        {item.optional ? '（可选替代）' : ''}
                        {item.roleNote ? `——${item.roleNote}` : ''}
                      </Text>
                    </View>
                  ))}
                </View>
              ))
            : null}
          {topic.items.length === 0 ? (
            <Text className={styles.emptyText}>该专题暂无上架软件</Text>
          ) : (
            topic.items.map((item) => (
              <View
                key={item.id}
                className={styles.card}
                onClick={() =>
                  Taro.navigateTo({ url: `/pages/softwareDetail/index?id=${item.id}` })
                }
              >
                <Image
                  className={styles.cardIcon}
                  src={iconUrl(item.iconAssetId) ?? '/assets/default-avatar.png'}
                  mode='aspectFill'
                />
                <View className={styles.cardBody}>
                  <Text className={styles.cardTitle}>{item.name}</Text>
                  <Text className={styles.cardSummary}>{item.summary}</Text>
                </View>
              </View>
            ))
          )}
        </View>
      ) : null}
    </View>
  )
}
