import { getPublishMode, PUBLISH_MODE_LABELS, type SoftwarePublishMode } from '@/utils/softwarePublishMode'
import { useCallback, useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Input, Modal, Radio, Space, Tabs, Tag, Typography } from 'antd'
import { listPublisherPermissions, togglePublisherPermission ,
  listPublisherApplications,
  decidePublisherApplication,
  type AdminPublisherApplication,
} from '@/api/admin/software';
import type { AdminPublisherPermission } from '@/api/admin/software'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * 发布资格（方案 10.8）：默认未开通；逐用户开通/关闭，原因必填（权限审计）；
 * 开通前服务端核对会员存在且可用（不可用返回 503，不创建未知用户授权）。
 * 与「付款查看授权」相互独立，不能把发布资格开关用作查看授权。
 */
export default function SoftwarePublishers() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [target, setTarget] = useState<AdminPublisherPermission | null>(null)
  const [grantOpen, setGrantOpen] = useState(false)
  /** SW12：全链路字符串 ID——大整数（如 9007199254740993）不丢精度 */
  const [grantUserId, setGrantUserId] = useState<string>('')
  /** SF03：申请队列页签 */
  const [view, setView] = useState<'grants' | 'applications'>('grants')
  const [applications, setApplications] = useState<AdminPublisherApplication[]>([])
  const [appLoading, setAppLoading] = useState(false)
  const [appStatus, setAppStatus] = useState<string | undefined>('PENDING')
  const [decisionTarget, setDecisionTarget] = useState<{
    applicationId: string
    approve: boolean
    userId: string
  } | null>(null)
  const [decisionReason, setDecisionReason] = useState('')
  const [publishMode, setPublishMode] = useState<SoftwarePublishMode>('REVIEW')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const openToggle = (permission: AdminPublisherPermission) => {
    setTarget(permission)
    setGrantOpen(false)
    setPublishMode(getPublishMode(permission))
    setReason('')
  }

  const confirmSubmit = () => {
    Modal.confirm({
      title: '变更发布资格',
      content: '确认变更该会员的发布资格？变更会写入权限审计。',
      okText: '确认变更',
      cancelText: '取消',
      onOk: () => doSubmit(),
    })
  }

  /** SF03：申请队列加载 */
  const loadApplications = useCallback(() => {
    setAppLoading(true)
    listPublisherApplications({ status: appStatus, page: 1, size: 50 })
      .then((res) => setApplications(res.data.data?.records ?? []))
      .catch(() => message.error('申请加载失败'))
      .finally(() => setAppLoading(false))
  }, [appStatus])

  useEffect(() => {
    if (view === 'applications') loadApplications()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, appStatus])

  const confirmDecision = () => {
    if (!decisionTarget) return
    if (!decisionReason.trim()) {
      message.warning('请填写决定说明（审计要求）')
      return
    }
    Modal.confirm({
      title: decisionTarget.approve ? '通过发布者申请' : '拒绝发布者申请',
      content: decisionTarget.approve
        ? '通过后该会员可发布软件，发布后需审核；可在发布权限中改为直接发布。'
        : '拒绝原因会对申请人展示（可公开）。',
      okText: '确认',
      cancelText: '取消',
      onOk: () =>
        decidePublisherApplication(
          decisionTarget.applicationId,
          { approve: decisionTarget.approve, reason: decisionReason.trim() },
        )
          .then(() => {
            message.success('已处理')
            setDecisionTarget(null)
            setDecisionReason('')
            loadApplications()
          })
          .catch(() => undefined),
    })
  }

  const doSubmit = async () => {
    const userId = grantOpen ? grantUserId.trim() : target?.userId
    if (!userId || !/^\d{1,20}$/.test(userId)) {
      message.warning('请输入合法的会员ID（纯数字，最长 20 位）')
      return
    }
    if (!reason.trim()) {
      message.warning('请填写原因（审计要求）')
      return
    }
    setSubmitting(true)
    try {
      await togglePublisherPermission(userId, { publishMode, reason: reason.trim() })
      message.success(`已设置为：${PUBLISH_MODE_LABELS[publishMode]}`)
      setTarget(null)
      setGrantOpen(false)
      setGrantUserId('')
      actionRef.current?.reload()
    } catch {
      // 会员不可用/服务降级等已由全局提示
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ProColumns<AdminPublisherPermission>[] = [
    {
      title: '会员',
      dataIndex: 'userId',
      width: 150,
      search: false,
      render: (_, record) => record.userNickname || String(record.userId),
    },
    {
      title: '发布资格',
      dataIndex: 'canPublish',
      width: 110,
      search: false,
      render: (_, record) =>
        <Tag color={getPublishMode(record) === 'DIRECT' ? 'success' : undefined}>{PUBLISH_MODE_LABELS[getPublishMode(record)]}</Tag>,
    },
    { title: '最近原因', dataIndex: 'reason', ellipsis: true, search: false },
    {
      title: '操作人',
      dataIndex: 'updatedBy',
      width: 110,
      search: false,
      render: (_, record) =>
        record.updatedByName ?? (record.updatedBy ? `管理员#${record.updatedBy}` : '-'),
    },
    { title: '更新时间', dataIndex: 'updatedAt', valueType: 'dateTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, record) => [
        <a key="toggle" onClick={() => openToggle(record)}>
          更改发布权限
        </a>,
      ],
    },
  ]

  return (
    <>
      <Tabs
        activeKey={view}
        onChange={(key) => setView(key as 'grants' | 'applications')}
        items={[
          { key: 'grants', label: '资格列表' },
          { key: 'applications', label: '申请队列（SF03）' },
        ]}
        style={{ marginBottom: 8 }}
      />
      {view === 'applications' && (
      <>
      <Space style={{ marginBottom: 12 }} wrap>
        {['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((st) => (
          <Button
            key={st}
            size="small"
            type={appStatus === st ? 'primary' : 'default'}
            onClick={() => setAppStatus(st)}
          >
            {st}
          </Button>
        ))}
        <Button size="small" onClick={() => setAppStatus(undefined)}>全部</Button>
      </Space>
      <div style={{ marginBottom: 16 }}>
        {appLoading ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : applications.length === 0 ? (
          <Typography.Text type="secondary">暂无申请</Typography.Text>
        ) : (
          applications.map((application) => (
            <div
              key={application.applicationId}
              style={{ padding: '12px 16px', borderRadius: 8, border: '1px solid #e2e8f0', marginBottom: 8 }}
            >
              <Space size={8} wrap>
                <Typography.Text strong>会员 {application.userId}</Typography.Text>
                <Tag color={application.status === 'PENDING' ? 'processing' : application.status === 'APPROVED' ? 'success' : 'default'}>
                  {application.status}
                </Tag>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {application.submittedAt.replace('T', ' ').slice(0, 16)}
                </Typography.Text>
              </Space>
              <Typography.Paragraph style={{ marginBottom: 4, marginTop: 4 }}>
                {application.intro}
              </Typography.Paragraph>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                拟投分类：{application.intendedCategories}
                {application.reason ? ` · 处理说明：${application.reason}` : ''}
              </Typography.Text>
              {application.status === 'PENDING' && (
                <div style={{ marginTop: 8 }}>
                  <Space size={8}>
                    <Button
                      size="small"
                      type="primary"
                      onClick={() => {
                        setDecisionTarget({
                          applicationId: application.applicationId,
                          approve: true,
                          userId: application.userId,
                        })
                        setDecisionReason('发布用途与来源说明符合要求')
                      }}
                    >
                      通过并授予资格
                    </Button>
                    <Button
                      size="small"
                      danger
                      onClick={() => {
                        setDecisionTarget({
                          applicationId: application.applicationId,
                          approve: false,
                          userId: application.userId,
                        })
                        setDecisionReason('')
                      }}
                    >
                      拒绝
                    </Button>
                  </Space>
                </div>
              )}
            </div>
          ))
        )}
      </div>
      </>
      )}

      {view === 'grants' && (
      <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button
          type="primary"
          onClick={() => {
            setGrantOpen(true)
            setTarget(null)
            setPublishMode('REVIEW')
            setGrantUserId('')
            setReason('')
          }}
        >
          开通资格（指定会员）
        </Button>
        <Typography.Text type="secondary">
          会员管理页也可查看并开通资格；下方列表为已有资格记录
        </Typography.Text>
      </Space>
      <ProTable<AdminPublisherPermission>
        headerTitle="发布资格"
        rowKey="userId"
        actionRef={actionRef}
        columns={columns}
        options={{ density: false }}
        search={false}
        request={(params) =>
          safeProTableRequest(() =>
            listPublisherPermissions({ page: params.current, size: params.pageSize }),
          )
        }
        pagination={{ defaultPageSize: 20 }}
      />

      <Modal
        title="设置发布权限"
        open={!!target || grantOpen}
        onCancel={() => {
          setTarget(null)
          setGrantOpen(false)
        }}
        onOk={confirmSubmit}
        confirmLoading={submitting}
        okText="确认"
        okButtonProps={{ danger: publishMode === 'DENIED' }}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          默认禁止发布。可直接发布立即上线；发布后需审核须经管理员审核后公开。禁止发布后已有内容保留。
        </Typography.Paragraph>
        {grantOpen ? (
          <>
            <Input
              placeholder="会员ID（前台 userId，纯数字字符串）"
              value={grantUserId}
              onChange={(e) => setGrantUserId(e.target.value.replace(/[^0-9]/g, ''))}
              maxLength={20}
              style={{ width: '100%', marginBottom: 8 }}
            />
            <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
              从列表复制会员列显示的 ID；会员列同时显示昵称，便于核对目标。
            </Typography.Paragraph>
          </>
        ) : (
          <Typography.Paragraph>
            会员：<Typography.Text strong>{target?.userId}</Typography.Text>
          </Typography.Paragraph>
        )}
        <Radio.Group
          value={publishMode}
          onChange={(e) => setPublishMode(e.target.value)}
          style={{ marginBottom: 12 }}
        >
          <Radio.Button value="DENIED">禁止发布</Radio.Button>
          <Radio.Button value="DIRECT">可直接发布</Radio.Button>
          <Radio.Button value="REVIEW">发布后需审核</Radio.Button>
        </Radio.Group>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="原因（1~500 字，必填，写入权限审计）"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Modal>
      </>
      )}
    </>
  )
}
