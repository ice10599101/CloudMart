import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, Segmented, Space, Spin, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import {
  getPetWallet,
  listPetWalletTransactions,
  type PetWalletTransactionVO,
  type PetWalletVO,
} from '@/api/pet'

const PAGE_SIZE = 20

const DIRECTION_FILTERS = [
  { label: '全部', value: '' },
  { label: '收入', value: 'EARN' },
  { label: '支出', value: 'SPEND' },
  { label: '退款', value: 'REFUND' },
] as const

const BIZ_TYPE_LABELS: Record<string, string> = {
  PURCHASE: '购买',
  ACTIVITY_REWARD: '活动奖励',
  CLAIM_WORK: '打工工资',
  CLAIM_STUDY: '读书奖励',
  BATTLE_REWARD: '对战奖励',
  BOTTLE_REWARD: '捞瓶奖励',
  QUEST_CLAIM: '任务奖励',
  QUEST_CHEST: '宝箱奖励',
  CAREER_CLAIM: '职业工资',
  CAREER_PROMOTE: '晋升',
  EVENT_CLAIM: '活动奖励',
  EVENT_ALT: '活动替代奖励',
  COOP_REWARD_ALT: '合作奖励',
  ONBOARDING_GIFT: '新手礼物',
  EVOLVE: '进化',
  FURNITURE_BUY: '家具购买',
  REFUND: '退款',
  ADJUSTMENT: '调账',
}

function bizLabel(bizType: string): string {
  return BIZ_TYPE_LABELS[bizType] ?? bizType
}

function directionTag(direction: PetWalletTransactionVO['direction']) {
  if (direction === 'EARN') return <Tag color="green">收入</Tag>
  if (direction === 'SPEND') return <Tag color="orange">支出</Tag>
  if (direction === 'REFUND') return <Tag color="blue">退款</Tag>
  return <Tag>调整</Tag>
}

/**
 * 宠物币钱包（W03/§7.8）：余额、收支明细、方向筛选、游标加载更多。
 * 查看流水无副作用（不触发领奖）；余额不可用时显示"暂不可用"而非 0；
 * 冻结状态提示原因与可用操作（仍可读账与退款）。
 */
export default function PetWallet() {
  const [wallet, setWallet] = useState<PetWalletVO | null>(null)
  const [walletError, setWalletError] = useState(false)
  const [transactions, setTransactions] = useState<PetWalletTransactionVO[]>([])
  const [filter, setFilter] = useState<string>('')
  const [cursor, setCursor] = useState<number | string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [listError, setListError] = useState(false)

  const loadWallet = useCallback(async () => {
    setWalletError(false)
    try {
      const { data: res } = await getPetWallet()
      if (res.success && res.data) {
        setWallet(res.data)
      } else {
        setWalletError(true)
      }
    } catch {
      setWalletError(true)
    }
  }, [])

  const loadTransactions = useCallback(
    async (reset: boolean) => {
      if (reset) {
        setLoading(true)
        setCursor(null)
      } else {
        setLoadingMore(true)
      }
      setListError(false)
      try {
        const { data: res } = await listPetWalletTransactions({
          size: PAGE_SIZE,
          direction: filter || undefined,
          cursor: reset ? undefined : (cursor ?? undefined),
        })
        if (res.success && res.data) {
          const page = res.data
          setTransactions((prev) => (reset ? page : [...prev, ...page]))
          const nextCursor = (res.meta as { nextCursor?: string | null } | undefined)?.nextCursor
          setCursor(nextCursor ?? null)
          setHasMore(Boolean(nextCursor))
        } else {
          setListError(true)
        }
      } catch {
        setListError(true)
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [filter, cursor],
  )

  useEffect(() => {
    void loadWallet()
  }, [loadWallet])

  useEffect(() => {
    void loadTransactions(true)
    // filter 变化时重置列表；cursor 由本回调管理
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter])

  const frozen = wallet?.status === 'FROZEN'

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 24 }}>
      <Card style={{ marginBottom: 16 }}>
        {walletError ? (
          <Space direction="vertical" style={{ width: '100%' }} align="center">
            <Typography.Text type="secondary">钱包暂不可用，稍后再试</Typography.Text>
            <Button icon={<ReloadOutlined />} onClick={() => void loadWallet()}>
              重试
            </Button>
          </Space>
        ) : !wallet ? (
          <Spin />
        ) : (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Typography.Text type="secondary">宠物币余额（{wallet.currency}）</Typography.Text>
            <Space align="baseline" size={12}>
              <Typography.Title level={2} style={{ margin: 0 }}>
                {wallet.balance}
              </Typography.Title>
              {frozen ? (
                <Tag color="red">已冻结：暂不能消费，仍可查看与退款，请联系客服</Tag>
              ) : (
                <Tag color="gold">正常</Tag>
              )}
            </Space>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              宠物币与社区星光相互独立；历史社区星光请前往心愿宇宙的星光记录查看。
            </Typography.Text>
          </Space>
        )}
      </Card>

      <Card
        title="收支明细"
        extra={
          <Segmented
            size="small"
            options={DIRECTION_FILTERS.map((f) => ({ label: f.label, value: f.value }))}
            value={filter}
            onChange={(value) => setFilter(String(value))}
          />
        }
      >
        {listError ? (
          <Empty description="明细加载失败">
            <Button onClick={() => void loadTransactions(true)}>重试</Button>
          </Empty>
        ) : loading ? (
          <div style={{ textAlign: 'center', padding: 32 }}>
            <Spin />
          </div>
        ) : transactions.length === 0 ? (
          <Empty description="还没有收支记录" />
        ) : (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {transactions.map((tx) => (
              <div
                key={tx.transactionId}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
              >
                <Space direction="vertical" size={0}>
                  <Space size={8}>
                    {directionTag(tx.direction)}
                    <Typography.Text>{bizLabel(tx.bizType)}</Typography.Text>
                  </Space>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {new Date(tx.occurredAt).toLocaleString()}
                  </Typography.Text>
                </Space>
                <Typography.Text
                  strong
                  type={tx.direction === 'SPEND' ? 'danger' : 'success'}
                  style={{ fontSize: 16 }}
                >
                  {tx.direction === 'SPEND' ? '-' : '+'}
                  {tx.amount}
                </Typography.Text>
              </div>
            ))}
            {hasMore && (
              <Button
                block
                loading={loadingMore}
                onClick={() => {
                  void loadTransactions(false)
                }}
              >
                加载更多
              </Button>
            )}
            {!hasMore && transactions.length > 0 && (
              <Typography.Text type="secondary" style={{ textAlign: 'center', fontSize: 12 }}>
                没有更多了
              </Typography.Text>
            )}
          </Space>
        )}
      </Card>
    </div>
  )
}
