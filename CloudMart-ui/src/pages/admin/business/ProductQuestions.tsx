import { useCallback, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Input, Modal, Popconfirm, Tag } from 'antd'
import {
  adminHideProductAnswer,
  adminHideProductQuestion,
  adminListProductQuestions,
  adminRestoreProductAnswer,
  adminRestoreProductQuestion,
} from '@/api/productQa'
import type { ProductQuestionItem } from '@/api/productQa'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * PRODUCT-01：商品问答管理（商城→商品问答）。
 * 问题/答案筛选、隐藏/恢复处置（business:product:question；操作审计在代理层 @OperLog）。
 */
export default function ProductQuestions() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [hideTarget, setHideTarget] = useState<
    { kind: 'question' | 'answer'; id: number | string } | null
  >(null)
  const [hideReason, setHideReason] = useState('')

  const doHide = async () => {
    if (!hideTarget) return
    try {
      if (hideTarget.kind === 'question') {
        await adminHideProductQuestion(hideTarget.id, hideReason.trim() || undefined)
      } else {
        await adminHideProductAnswer(hideTarget.id, hideReason.trim() || undefined)
      }
      message.success('已隐藏')
      setHideTarget(null)
      setHideReason('')
      actionRef.current?.reload()
    } catch {
      message.error('操作失败')
    }
  }

  const doRestore = async (kind: 'question' | 'answer', id: number | string) => {
    try {
      if (kind === 'question') {
        await adminRestoreProductQuestion(id)
      } else {
        await adminRestoreProductAnswer(id)
      }
      message.success('已恢复')
      actionRef.current?.reload()
    } catch {
      message.error('操作失败')
    }
  }

  const columns: ProColumns<ProductQuestionItem>[] = [
    { title: '问题 ID', dataIndex: 'id', width: 90, search: false },
    {
      title: '商品 ID',
      dataIndex: 'productId',
      width: 120,
      render: (_, r) => String(r.productId),
      fieldProps: { placeholder: '按商品 ID 筛选' },
    },
    {
      title: '问题',
      dataIndex: 'question',
      ellipsis: true,
      search: false,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.question}</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
            {r.askerNickname} 提问 · {(r.createdAt ?? '').slice(0, 10)}
          </div>
        </div>
      ),
    },
    {
      title: '答案',
      dataIndex: 'answers',
      search: false,
      render: (_, r) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {(r.answers ?? []).length === 0 ? (
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>暂无回答</span>
          ) : (
            (r.answers ?? []).map((a) => (
              <div key={String(a.id)} style={{ fontSize: 13 }}>
                <span>{a.body}</span>
                <span style={{ marginLeft: 6, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                  {a.authorNickname}
                  {a.purchased && <Tag color="orange" style={{ marginLeft: 4 }}>已购</Tag>}
                </span>
              </div>
            ))
          )}
        </div>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, r) => {
        const questionHidden = r.status === 1
        return [
          questionHidden ? (
            <Button
              key="rq"
              type="link"
              size="small"
              onClick={() => void doRestore('question', r.id)}
            >
              恢复问题
            </Button>
          ) : (
            <Popconfirm
              key="hq"
              title="隐藏该问题？"
              onConfirm={() => setHideTarget({ kind: 'question', id: r.id })}
            >
              <Button type="link" size="small" danger>
                隐藏问题
              </Button>
            </Popconfirm>
          ),
          ...(r.answers ?? []).map((a) =>
            a.status === 1 ? (
              <Button
                key={`ra-${a.id}`}
                type="link"
                size="small"
                onClick={() => void doRestore('answer', a.id)}
              >
                恢复答案
              </Button>
            ) : (
              <Popconfirm
                key={`ha-${a.id}`}
                title="隐藏该答案？"
                onConfirm={() => setHideTarget({ kind: 'answer', id: a.id })}
              >
                <Button type="link" size="small" danger>
                  隐藏答案
                </Button>
              </Popconfirm>
            ),
          ),
        ]
      },
    },
  ]

  return (
    <>
      <ProTable<ProductQuestionItem>
        headerTitle="商品问答管理"
        actionRef={actionRef}
        rowKey="id"
        search={{ labelWidth: 'auto' }}
        request={async (params) => {
          return safeProTableRequest<ProductQuestionItem>(() =>
            adminListProductQuestions({
              page: params.current,
              pageSize: params.pageSize,
              productId: params.productId,
              // 状态筛选：0 正常 / 1 隐藏（不填=全部）
              status:
                params.status === 0 || params.status === 1 ? (params.status as number) : undefined,
            }),
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title="隐藏（填写处置原因，入审计）"
        open={hideTarget !== null}
        onOk={doHide}
        onCancel={() => {
          setHideTarget(null)
          setHideReason('')
        }}
        okText="确认隐藏"
        okButtonProps={{ danger: true }}
      >
        <Input.TextArea
          rows={3}
          value={hideReason}
          onChange={(e) => setHideReason(e.target.value)}
          placeholder="处置原因（如：内容不合规/广告）"
          maxLength={200}
          showCount
        />
      </Modal>
    </>
  )
}

