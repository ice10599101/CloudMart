import { View, Text, ScrollView } from '@tarojs/components'
import { useCallback, useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { productQuestionApi, type MyProductQuestionItem } from '@/api/product'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

/**
 * T13：我的商品提问（跨商品）——查看回答进度/回跳商品问答/删除自己的提问。
 * 服务端过滤本人已删；删除幂等；归属校验在服务端。
 */
export default function ProductQuestions() {
  const isLoggedIn = useAuthStore().isLoggedIn
  const [items, setItems] = useState<MyProductQuestionItem[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(() => {
    setLoading(true)
    productQuestionApi
      .myQuestions(1, 50)
      .then((res) => setItems(res.data?.data?.records ?? []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false))
  }, [])

  useDidShow(() => {
    if (!isLoggedIn) {
      Taro.navigateTo({ url: '/pages/login/index' })
      return
    }
    load()
  })

  const handleDelete = (item: MyProductQuestionItem) => {
    Taro.showModal({
      title: '删除提问',
      content: '删除后商品详情与该列表均不可见，确定删除？',
      success: (res) => {
        if (!res.confirm) return
        productQuestionApi
          .deleteOwn(item.id)
          .then(() => {
            setItems((prev) => prev.filter((q) => String(q.id) !== String(item.id)))
            Taro.showToast({ title: '已删除', icon: 'success' })
          })
          .catch(() => Taro.showToast({ title: '删除失败', icon: 'none' }))
      },
    })
  }

  return (
    <View className={styles.page}>
      {loading ? (
        <Text className={styles.emptyText}>加载中...</Text>
      ) : items.length === 0 ? (
        <Text className={styles.emptyText}>还没有提问过商品问题</Text>
      ) : (
        <ScrollView scrollY style={{ height: '100vh' }}>
          {items.map((item) => (
            <View key={String(item.id)} className={styles.card}>
              <Text className={styles.cardTitle}>{item.question}</Text>
              <Text className={styles.cardSummary}>
                {item.createdAt.slice(0, 10)}
                {item.productName ? ` · ${item.productName}` : ''}
              </Text>
              {item.answers.length > 0 ? (
                item.answers.map((answer) => (
                  <Text key={String(answer.id)} className={styles.cardSummary}>
                    {answer.authorNickname || '匿名'} 回答：{answer.body}
                  </Text>
                ))
              ) : (
                <Text className={styles.cardSummary}>暂无回答</Text>
              )}
              <View style={{ display: 'flex', flexDirection: 'row', marginTop: 8 }}>
                <Text
                  className={styles.cardSummary}
                  style={{ color: '#3d5afe', marginRight: 16 }}
                  onClick={() =>
                    Taro.navigateTo({
                      url: `/pages/productDetail/index?id=${item.productId}&tab=qa`,
                    })
                  }
                >
                  回跳商品问答 →
                </Text>
                <Text className={styles.cardSummary} style={{ color: '#d3392f' }} onClick={() => handleDelete(item)}>
                  删除
                </Text>
              </View>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  )
}
