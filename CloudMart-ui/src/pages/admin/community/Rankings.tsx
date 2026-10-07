import { useEffect, useRef, useState } from 'react'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { ProTable } from '@ant-design/pro-components'
import { Modal, Table, Tag, Typography } from 'antd'
import {
  getCurrentRankings,
  getRankingSeason,
  listRankingSeasons,
  updateRankingSeasonStatus,
} from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * T23/P0-4：排行榜赛季页。
 * 当期榜单概览 + 赛季列表（状态筛选）+ 赛季榜单详情 + 赛季启停（0 进行中/1 已归档）。
 * 启停走 community:ranking:manage（后端强制）；已结算赛季规则不可原地修改由社区服务约束。
 */

interface RankingSeason {
  id: number
  name: string
  seasonKey: string
  startDate: string
  endDate: string
  status: number
  createdAt: string
}

interface RankingItem {
  userId: number
  expValue: number
  rankNo: number
}

const SEASON_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '进行中', color: 'processing' },
  1: { label: '已归档', color: 'default' },
}

export default function Rankings() {
  const message = useMessage()
  const { confirmSubmit } = useModalConfirm()
  const actionRef = useRef<ActionType>(null)
  const [detailSeason, setDetailSeason] = useState<RankingSeason | null>(null)
  const [detailItems, setDetailItems] = useState<RankingItem[]>([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentItems, setCurrentItems] = useState<RankingItem[]>([])
  const [currentLoading, setCurrentLoading] = useState(false)

  useEffect(() => {
    setCurrentLoading(true)
    getCurrentRankings()
      .then(({ data: res }) => setCurrentItems((res?.data as unknown as RankingItem[]) ?? []))
      .catch(() => message.error('当期榜单加载失败'))
      .finally(() => setCurrentLoading(false))
  }, [])

  const openDetail = async (season: RankingSeason) => {
    setDetailSeason(season)
    setDetailLoading(true)
    try {
      const { data: res } = await getRankingSeason(season.id)
      setDetailItems((res?.data as unknown as RankingItem[]) ?? [])
    } catch {
      message.error('赛季详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const toggleStatus = (season: RankingSeason, status: number) =>
    confirmSubmit(async () => {
      await updateRankingSeasonStatus(season.id, status)
      message.success(status === 1 ? '赛季已归档' : '赛季已重新开启')
      actionRef.current?.reload()
    })

  const seasonColumns: ProColumns<RankingSeason>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '赛季名称', dataIndex: 'name', width: 180, search: false },
    { title: '赛季 Key', dataIndex: 'seasonKey', width: 140, search: false },
    { title: '开始日期', dataIndex: 'startDate', width: 120, search: false },
    { title: '结束日期', dataIndex: 'endDate', width: 120, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '进行中', value: 0 },
          { label: '已归档', value: 1 },
        ],
      },
      render: (_, record) => {
        const info = SEASON_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => [
        <a key="detail" onClick={() => openDetail(record)}>
          榜单详情
        </a>,
        record.status === 0 ? (
          <a key="archive" onClick={() => toggleStatus(record, 1)}>
            归档
          </a>
        ) : (
          <a key="reopen" onClick={() => toggleStatus(record, 0)}>
            重新开启
          </a>
        ),
      ],
    },
  ]

  const itemColumns = [
    { title: '名次', dataIndex: 'rankNo', width: 80 },
    { title: '用户 ID', dataIndex: 'userId', width: 140 },
    { title: '经验值', dataIndex: 'expValue', width: 140 },
  ]

  return (
    <>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
        当月经验榜（Redis 实时）与历史赛季归档；榜单结算由调度自动归档，也可手动启停赛季。已结算赛季的规则不可原地修改。
      </Typography.Paragraph>

      <Typography.Title level={5}>当期榜单</Typography.Title>
      <Table
        size="small"
        rowKey={(r) => `${r.rankNo}-${r.userId}`}
        columns={itemColumns}
        dataSource={currentItems}
        loading={currentLoading}
        pagination={false}
        style={{ marginBottom: 24 }}
      />

      <Typography.Title level={5}>赛季列表</Typography.Title>
      <ProTable<RankingSeason>
        rowKey="id"
        actionRef={actionRef}
        columns={seasonColumns}
        cardBordered
        search={{ labelWidth: 'auto' }}
        pagination={{ pageSize: 20 }}
        request={async (params) =>
          safeProTableRequest<RankingSeason>(() =>
            listRankingSeasons({
              page: params.current ?? 1,
              pageSize: params.pageSize ?? 20,
              status: typeof params.status === 'number' ? params.status : undefined,
            }),
          )
        }
      />

      <Modal
        open={detailSeason != null}
        title={`赛季榜单详情：${detailSeason?.name ?? ''}`}
        footer={null}
        onCancel={() => setDetailSeason(null)}
        width={640}
      >
        <Table
          size="small"
          rowKey={(r) => `${r.rankNo}-${r.userId}`}
          columns={itemColumns}
          dataSource={detailItems}
          loading={detailLoading}
          pagination={false}
        />
      </Modal>
    </>
  )
}
