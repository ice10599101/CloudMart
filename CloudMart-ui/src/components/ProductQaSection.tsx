import { useCallback, useEffect, useState } from 'react'
import { Button, Empty, Input, InputNumber, Pagination, Spin, Tag, message } from 'antd'
import {
  answerProductQuestion,
  askProductQuestion,
  listProductQuestions,
} from '@/api/productQa'
import type { ProductQuestionItem } from '@/api/productQa'
import type { ApiResponse } from '@/types/api'

/**
 * PRODUCT-01：商品详情问答区——提问、回答（任意登录用户）、我的提问状态；
 * 未回答的问题也可见（answers 空）；已购徽标为回答时快照（历史资格展示）。
 */
export default function ProductQaSection({ productId }: { productId: number | string }) {
  const [questions, setQuestions] = useState<ProductQuestionItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [askText, setAskText] = useState('')
  const [asking, setAsking] = useState(false)
  const [answerDraft, setAnswerDraft] = useState<Record<string, string>>({})
  const [answeringId, setAnsweringId] = useState<string | null>(null)

  const load = useCallback(
    async (p: number) => {
      setLoading(true)
      try {
        const { data: res } = await listProductQuestions(productId, p, 10)
        const response = res as ApiResponse<{ records: ProductQuestionItem[]; total: number }>
        setQuestions(response.data?.records ?? [])
        setTotal(response.data?.total ?? 0)
      } catch {
        setQuestions([])
        setTotal(0)
      } finally {
        setLoading(false)
      }
    },
    [productId],
  )

  useEffect(() => {
    void load(page)
  }, [load, page])

  const handleAsk = async () => {
    if (!askText.trim()) {
      message.warning('请输入问题')
      return
    }
    setAsking(true)
    try {
      await askProductQuestion(productId, askText.trim())
      message.success('提问成功，等待大家回答')
      setAskText('')
      setPage(1)
      await load(1)
    } catch {
      message.error('提问失败')
    } finally {
      setAsking(false)
    }
  }

  const handleAnswer = async (q: ProductQuestionItem) => {
    const draft = (answerDraft[String(q.id)] ?? '').trim()
    if (!draft) {
      message.warning('请输入回答')
      return
    }
    setAnsweringId(String(q.id))
    try {
      await answerProductQuestion(productId, q.id, draft)
      message.success('回答已提交')
      setAnswerDraft((prev) => ({ ...prev, [String(q.id)]: '' }))
      await load(page)
    } catch {
      message.error('回答失败')
    } finally {
      setAnsweringId(null)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 提问 */}
      <div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            value={askText}
            onChange={(e) => setAskText(e.target.value)}
            placeholder="输入你的问题（1-500 字）"
            maxLength={500}
            showCount
          />
          <Button type="primary" loading={asking} onClick={handleAsk}>
            提问
          </Button>
        </div>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : questions.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有问答，来提第一个问题吧" />
      ) : (
        questions.map((q) => (
          <div
            key={String(q.id)}
            style={{
              border: '1px solid var(--color-border)',
              borderRadius: 10,
              padding: 14,
              background: 'var(--color-bg-container)',
            }}
          >
            <div style={{ marginBottom: 8 }}>
              <Tag>问</Tag>
              <span style={{ fontWeight: 600 }}>{q.question}</span>
              <span style={{ marginLeft: 8, fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                {q.askerNickname} · {(q.createdAt ?? '').slice(0, 10)}
              </span>
            </div>
            {(q.answers ?? []).length === 0 ? (
              <div style={{ fontSize: 13, color: 'var(--color-text-tertiary)', marginLeft: 34 }}>
                暂无回答，等你来答
              </div>
            ) : (
              (q.answers ?? []).map((a) => (
                <div key={String(a.id)} style={{ marginLeft: 34, marginBottom: 6 }}>
                  <Tag color="green">答</Tag>
                  <span style={{ fontSize: 14 }}>{a.body}</span>
                  <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>
                    {a.authorNickname}
                    {a.purchased && (
                      <Tag color="orange" style={{ marginLeft: 6 }}>
                        已购
                      </Tag>
                    )}
                    <span style={{ marginLeft: 6 }}>{(a.createdAt ?? '').slice(0, 10)}</span>
                  </div>
                </div>
              ))
            )}
            {/* 任意登录用户可回答；同人重复提交为编辑本人答案 */}
            <div style={{ marginLeft: 34, marginTop: 8, display: 'flex', gap: 8 }}>
              <Input
                size="small"
                value={answerDraft[String(q.id)] ?? ''}
                onChange={(e) =>
                  setAnswerDraft((prev) => ({ ...prev, [String(q.id)]: e.target.value }))
                }
                placeholder="写下你的回答（1-500 字）"
                maxLength={500}
                style={{ maxWidth: 420 }}
              />
              <Button
                size="small"
                loading={answeringId === String(q.id)}
                onClick={() => void handleAnswer(q)}
              >
                回答
              </Button>
            </div>
          </div>
        ))
      )}

      {total > 10 && (
        <Pagination
          current={page}
          pageSize={10}
          total={total}
          onChange={(p) => setPage(p)}
          showSizeChanger={false}
          style={{ textAlign: 'center' }}
        />
      )}
    </div>
  )
}
