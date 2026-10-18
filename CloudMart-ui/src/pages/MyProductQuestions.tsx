import { useCallback, useEffect, useState } from 'react'
import { Button, Empty, List, Pagination, Spin, Tag, message } from 'antd'
import { history } from 'umi'
import { listMyProductQuestions } from '@/api/productQa'
import type { ProductQuestionItem } from '@/api/productQa'

/**
 * T13：我的商品提问（跨商品）。从个人中心进入，展示本人提问及回答进度，
 * 点击可回跳商品详情「问大家」页签继续查看/回复。
 */
export default function MyProductQuestions() {
  const [items, setItems] = useState<ProductQuestionItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const PAGE_SIZE = 10

  const load = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const res = await listMyProductQuestions(p, PAGE_SIZE)
      if (res.data.success) {
        setItems(res.data.data ?? [])
        setTotal(res.data.meta?.total ?? 0)
      }
    } catch (e) {
      message.error('加载失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(page)
  }, [page, load])

  return (
    <div style={{ maxWidth: 760, margin: '0 auto', padding: 24 }}>
      <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>我的提问</h2>
      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <Empty description="还没有提过问" />
        ) : (
          <List
            dataSource={items}
            renderItem={(item) => (
              <List.Item
                key={String(item.id)}
                actions={[
                  <Button
                    key="go"
                    type="link"
                    onClick={() => history.push(`/products/${item.productId}?tab=qa`)}
                  >
                    查看 / 回复
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={<span>{item.question}</span>}
                  description={
                    <span>
                      {item.answers?.length > 0 ? (
                        <Tag color="green">已回复 {item.answers.length} 条</Tag>
                      ) : (
                        <Tag>待回答</Tag>
                      )}
                      <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                        {item.createdAt?.slice(0, 19).replace('T', ' ')}
                      </span>
                    </span>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Spin>
      {total > PAGE_SIZE && (
        <Pagination
          style={{ marginTop: 16, textAlign: 'center' }}
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          onChange={setPage}
        />
      )}
    </div>
  )
}
