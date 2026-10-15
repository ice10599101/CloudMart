import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, DatePicker, Input, InputNumber, Modal, Space, Tabs, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import {
  grantSoftwareAccess,
  listAccessGrants,
  listPermissionAudits,
  revokeSoftwareAccess,
} from '@/api/admin/software'
import type { AdminAccessGrant } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '长期有效期内', color: 'success' },
  EXPIRED: { label: '已到期', color: 'warning' },
  REVOKED: { label: '已撤销', color: 'default' },
}

/**
 * 付款查看授权（方案 10.9）：管理员线下确认后人工开通/续期/撤销（长期/到期/撤销状态）；
 * 展示完整操作历史（权限审计）；与发布资格相互独立。
 * 首次创建按 unique 约束收敛，重复提交由 Idempotency-Key 吸收。
 */
export default function SoftwareAccessGrants() {
  const message = useMessage()
  const grantsRef = useRef<ActionType>(null)
  const auditsRef = useRef<ActionType>(null)

  const [softwareFilter, setSoftwareFilter] = useState('')
  const [userFilter, setUserFilter] = useState('')

  const [grantTarget, setGrantTarget] = useState<Partial<AdminAccessGrant> | null>(null)
  const [grantUserId, setGrantUserId] = useState<number | null>(null)
  const [grantSoftwareId, setGrantSoftwareId] = useState<string>('')
  const [expiresAt, setExpiresAt] = useState<dayjs.Dayjs | null>(null)
  const [confirmationNote, setConfirmationNote] = useState('')
  const [externalReference, setExternalReference] = useState('')
  const [grantReason, setGrantReason] = useState('')

  const [revokeTarget, setRevokeTarget] = useState<AdminAccessGrant | null>(null)
  const [revokeReason, setRevokeReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const openGrant = (record?: AdminAccessGrant) => {
    setGrantTarget(record ?? {})
    setGrantSoftwareId(record?.softwareId ?? softwareFilter)
    setGrantUserId(record ? Number(record.userId) : userFilter ? Number(userFilter) : null)
    setExpiresAt(record?.expiresAt ? dayjs(record.expiresAt) : null)
    setConfirmationNote('')
    setExternalReference(record?.externalReference ?? '')
    setGrantReason('')
  }

  const submitGrant = async () => {
    const softwareId = grantSoftwareId.trim()
    if (!softwareId || !grantUserId) {
      message.warning('请填写软件ID与会员ID')
      return
    }
    if (!grantReason.trim()) {
      message.warning('请填写原因（审计要求）')
      return
    }
    setSubmitting(true)
    try {
      await grantSoftwareAccess(softwareId, {
        userId: String(grantUserId),
        expiresAt: expiresAt ? expiresAt.format('YYYY-MM-DDTHH:mm:ss') : null,
        confirmationNote: confirmationNote.trim() || undefined,
        externalReference: externalReference.trim() || undefined,
        reason: grantReason.trim(),
      })
      message.success('已开通/续期（重开更新同一行并追加审计）')
      setGrantTarget(null)
      grantsRef.current?.reload()
      auditsRef.current?.reload()
    } catch {
      // 会员不可用等已由全局提示
    } finally {
      setSubmitting(false)
    }
  }

  const submitRevoke = async () => {
    if (!revokeTarget) return
    if (!revokeReason.trim()) {
      message.warning('请填写撤销原因')
      return
    }
    setSubmitting(true)
    try {
      await revokeSoftwareAccess(revokeTarget.softwareId, revokeTarget.userId, {
        reason: revokeReason.trim(),
      })
      message.success('已撤销（重复调用无副作用）')
      setRevokeTarget(null)
      grantsRef.current?.reload()
      auditsRef.current?.reload()
    } catch {
      // 业务错误已提示
    } finally {
      setSubmitting(false)
    }
  }

  const grantColumns: ProColumns<AdminAccessGrant>[] = [
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    { title: '会员ID', dataIndex: 'userId', width: 100, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      search: false,
      render: (_, record) => {
        const info = STATUS_MAP[record.status] ?? { label: record.status, color: 'default' }
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    { title: '开通时间', dataIndex: 'grantedAt', valueType: 'dateTime', width: 165, search: false },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      width: 165,
      search: false,
      render: (_, record) => record.expiresAt ?? '长期',
    },
    {
      title: '确认备注',
      dataIndex: 'confirmationNote',
      ellipsis: true,
      search: false,
      render: (_, record) => record.confirmationNote || '-',
    },
    { title: '外部核对编号', dataIndex: 'externalReference', width: 140, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => [
        <a key="extend" onClick={() => openGrant(record)}>
          续期
        </a>,
        record.status !== 'REVOKED' ? (
          <a
            key="revoke"
            style={{ color: '#d4380d' }}
            onClick={() => {
              setRevokeTarget(record)
              setRevokeReason('')
            }}
          >
            撤销
          </a>
        ) : null,
      ],
    },
  ]

  const auditColumns: ProColumns<Record<string, string>>[] = [
    { title: '审计ID', dataIndex: 'auditId', width: 90, search: false },
    { title: '操作', dataIndex: 'action', width: 150, search: false },
    { title: '目标会员', dataIndex: 'targetUserId', width: 100, search: false },
    { title: '软件ID', dataIndex: 'softwareId', width: 90, search: false },
    { title: '操作人', dataIndex: 'actorAdminId', width: 90, search: false },
    { title: '原因', dataIndex: 'reason', ellipsis: true, search: false },
    { title: '请求追踪', dataIndex: 'requestId', width: 140, ellipsis: true, search: false },
    { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 165, search: false },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input
          placeholder="软件ID（筛选/开通）"
          style={{ width: 180 }}
          value={softwareFilter}
          onChange={(e) => setSoftwareFilter(e.target.value)}
        />
        <Input
          placeholder="会员ID（筛选）"
          style={{ width: 180 }}
          value={userFilter}
          onChange={(e) => setUserFilter(e.target.value)}
        />
        <Button
          type="primary"
          onClick={() => {
            grantsRef.current?.reload()
            auditsRef.current?.reload()
          }}
        >
          查询
        </Button>
        <Button onClick={() => openGrant()}>人工开通授权</Button>
        <Typography.Text type="secondary">
          线下核对付款后开通/续期；到期由服务端按当前时间实时判定
        </Typography.Text>
      </Space>

      <Tabs
        items={[
          {
            key: 'grants',
            label: '授权列表',
            children: (
              <ProTable<AdminAccessGrant>
                headerTitle="付款查看授权"
                rowKey={(record) => `${record.softwareId}-${record.userId}`}
                actionRef={grantsRef}
                columns={grantColumns}
                options={{ density: false }}
                search={false}
                params={{ softwareId: softwareFilter, userId: userFilter }}
                request={(params) =>
                  safeProTableRequest(() =>
                    listAccessGrants({
                      softwareId: softwareFilter || undefined,
                      userId: userFilter || undefined,
                      page: params.current,
                      size: params.pageSize,
                    }),
                  )
                }
                pagination={{ defaultPageSize: 20 }}
              />
            ),
          },
          {
            key: 'audits',
            label: '操作历史（权限审计）',
            children: (
              <ProTable<Record<string, string>>
                headerTitle="权限变更审计"
                rowKey="auditId"
                actionRef={auditsRef}
                columns={auditColumns}
                options={{ density: false }}
                search={false}
                params={{ softwareId: softwareFilter, userId: userFilter }}
                request={(params) =>
                  safeProTableRequest(() =>
                    listPermissionAudits({
                      softwareId: softwareFilter || undefined,
                      userId: userFilter || undefined,
                      page: params.current,
                      size: params.pageSize,
                    }),
                  )
                }
                pagination={{ defaultPageSize: 20 }}
              />
            ),
          },
        ]}
      />

      <Modal
        title={grantTarget?.softwareId ? '续期/重开授权' : '人工开通授权'}
        open={!!grantTarget}
        onCancel={() => setGrantTarget(null)}
        onOk={submitGrant}
        confirmLoading={submitting}
        okText="确认开通/续期"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          必须明确软件与前台会员；到期时间留空表示长期有效；确认备注仅有权管理员可见。
        </Typography.Paragraph>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input
            placeholder="软件ID"
            value={grantSoftwareId}
            onChange={(e) => setGrantSoftwareId(e.target.value)}
          />
          <InputNumber
            placeholder="前台会员ID"
            value={grantUserId}
            onChange={(value) => setGrantUserId(value)}
            style={{ width: '100%' }}
            min={1}
          />
          <DatePicker
            showTime
            placeholder="到期时间（可空=长期有效）"
            value={expiresAt}
            onChange={(value) => setExpiresAt(value)}
            style={{ width: '100%' }}
          />
          <Input
            placeholder="确认备注（线下核对说明，可空）"
            maxLength={500}
            value={confirmationNote}
            onChange={(e) => setConfirmationNote(e.target.value)}
          />
          <Input
            placeholder="外部核对编号（可空）"
            maxLength={100}
            value={externalReference}
            onChange={(e) => setExternalReference(e.target.value)}
          />
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            placeholder="原因（1~500 字，必填，写入权限审计）"
            value={grantReason}
            onChange={(e) => setGrantReason(e.target.value)}
          />
        </Space>
      </Modal>

      <Modal
        title="撤销授权"
        open={!!revokeTarget}
        onCancel={() => setRevokeTarget(null)}
        onOk={submitRevoke}
        confirmLoading={submitting}
        okText="确认撤销"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          软件 {revokeTarget?.softwareId} · 会员 {revokeTarget?.userId}。
          撤销立即生效（已有授权不再可用），重复撤销幂等。
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="撤销原因（必填）"
          value={revokeReason}
          onChange={(e) => setRevokeReason(e.target.value)}
        />
      </Modal>
    </>
  )
}
