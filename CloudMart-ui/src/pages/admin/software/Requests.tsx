import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import {
  auditAdminRequest,
  listAdminRequests,
  markRequestMatched,
  type AdminSoftwareRequest,
} from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

const AUDIT_LABEL: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审核', color: 'warning' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
}

const STATUS_LABEL: Record<string, { label: string; color: string }> = {
  OPEN: { label: '寻找中', color: 'processing' },
  ANSWERED: { label: '已找到', color: 'success' },
  CLOSED: { label: '已关闭', color: 'default' },
}

/**
 * 软件需求管理（UX06）：审核（拒绝必须原因）+ 标记"已找到"（目标必须当前可见）。
 * 已找到的软件下架后 matchedVisible=false 前台可解释，不给有效获取承诺。
 */
export default function SoftwareRequestsAdmin() {
  const message = useMessage()
  const [rows, setRows] = useState<AdminSoftwareRequest[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [auditFilter, setAuditFilter] = useState<string | null>('PENDING')
  const [loading, setLoading] = useState(false)
  const [matching, setMatching] = useState<AdminSoftwareRequest | null>(null)
  const [matchForm] = Form.useForm()

  const load = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const res = await listAdminRequests(auditFilter, null, p, 12)
      if (res.data.success) {
        setRows(res.data.data?.records ?? [])
        setTotal(res.data.data?.total ?? 0)
        setPage(p)
      }
    } finally {
      setLoading(false)
    }
  }, [auditFilter])

  useEffect(() => {
    void load(1)
  }, [load])

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 140 }}
          value={auditFilter ?? 'ALL'}
          onChange={(v) => setAuditFilter(v === 'ALL' ? null : v)}
          options={[
            { value: 'ALL', label: '全部审核态' },
            { value: 'PENDING', label: '待审核' },
            { value: 'APPROVED', label: '已通过' },
            { value: 'REJECTED', label: '已拒绝' },
          ]}
        />
        <Button onClick={() => load(page)}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 12, onChange: (p) => load(p) }}
        columns={[
          { title: '标题', dataIndex: 'title', width: 180 },
          { title: '用途', dataIndex: 'usageDesc', ellipsis: true },
          {
            title: '来源关键词',
            dataIndex: 'sourceKeyword',
            width: 120,
            render: (v: string | null) => v || '-',
          },
          {
            title: '审核',
            dataIndex: 'auditStatus',
            width: 90,
            render: (v: string) => <Tag color={AUDIT_LABEL[v]?.color}>{AUDIT_LABEL[v]?.label}</Tag>,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (v: string) => <Tag color={STATUS_LABEL[v]?.color}>{STATUS_LABEL[v]?.label}</Tag>,
          },
          {
            title: '已找到',
            width: 140,
            render: (_: unknown, row: AdminSoftwareRequest) =>
              row.matchedSoftwareId ? (
                <span>
                  #{row.matchedSoftwareId}
                  {row.matchedVisible === false ? (
                    <Tag color="warning" style={{ marginLeft: 4 }}>
                      已下架
                    </Tag>
                  ) : null}
                </span>
              ) : (
                '-'
              ),
          },
          {
            title: '操作',
            width: 240,
            render: (_: unknown, row: AdminSoftwareRequest) => (
              <Space size="small" wrap>
                {row.auditStatus === 'PENDING' ? (
                  <>
                    <Button
                      size="small"
                      type="primary"
                      onClick={() => auditAdminRequest(row.id, true).then(() => load(page))}
                    >
                      通过
                    </Button>
                    <Popconfirm
                      title="拒绝该需求？"
                      description="将标记为已拒绝（原因默认：重复/无效需求）"
                      onConfirm={() =>
                        auditAdminRequest(row.id, false, '重复/无效需求').then(() => load(page))
                      }
                    >
                      <Button size="small" danger>
                        拒绝
                      </Button>
                    </Popconfirm>
                  </>
                ) : null}
                {row.auditStatus === 'APPROVED' && row.status === 'OPEN' ? (
                  <Button size="small" onClick={() => setMatching(row)}>
                    标记已找到
                  </Button>
                ) : null}
              </Space>
            ),
          },
        ] as never}
      />

      <Modal
        title={`标记已找到（需求 #${matching?.id ?? ''}）`}
        open={!!matching}
        onCancel={() => setMatching(null)}
        onOk={async () => {
          const values = await matchForm.validateFields()
          if (!matching) return
          const res = await markRequestMatched(matching.id, values.softwareId, values.reason)
          if (res.data.success) {
            message.success('已标记')
            setMatching(null)
            matchForm.resetFields()
            await load(page)
          }
        }}
        okText="确认"
        destroyOnHidden
      >
        <Form form={matchForm} layout="vertical">
          <Form.Item
            name="softwareId"
            label="已找到软件 ID"
            rules={[{ required: true }]}
            extra="必须是当前可见软件；下架后前台将标注不可用并解释"
          >
            <Input />
          </Form.Item>
          <Form.Item name="reason" label="理由（≤200 字）" rules={[{ max: 200 }]}>
            <Input.TextArea rows={2} placeholder="如：支持批量重命名且有预览撤销，免费" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
