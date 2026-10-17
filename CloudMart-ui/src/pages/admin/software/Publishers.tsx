import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Input, InputNumber, Modal, Radio, Space, Tag, Typography } from 'antd'
import { listPublisherPermissions, togglePublisherPermission } from '@/api/admin/software'
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
  const [grantUserId, setGrantUserId] = useState<number | null>(null)
  const [enable, setEnable] = useState(true)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const openToggle = (permission: AdminPublisherPermission) => {
    setTarget(permission)
    setGrantOpen(false)
    setEnable(!permission.canPublish)
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

  const doSubmit = async () => {
    const userId = grantOpen ? grantUserId : target?.userId
    if (!userId) {
      message.warning('请输入会员ID')
      return
    }
    if (!reason.trim()) {
      message.warning('请填写原因（审计要求）')
      return
    }
    setSubmitting(true)
    try {
      await togglePublisherPermission(String(userId), { enable, reason: reason.trim() })
      message.success(enable ? '已开通发布资格' : '已关闭发布资格')
      setTarget(null)
      setGrantOpen(false)
      setGrantUserId(null)
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
        record.canPublish ? <Tag color="success">已开通</Tag> : <Tag>未开通</Tag>,
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
          {record.canPublish ? '关闭资格' : '开通资格'}
        </a>,
      ],
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button
          type="primary"
          onClick={() => {
            setGrantOpen(true)
            setTarget(null)
            setEnable(true)
            setGrantUserId(null)
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
        title={enable ? '开通发布资格' : '关闭发布资格'}
        open={!!target || grantOpen}
        onCancel={() => {
          setTarget(null)
          setGrantOpen(false)
        }}
        onOk={confirmSubmit}
        confirmLoading={submitting}
        okText="确认"
        okButtonProps={{ danger: !enable }}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          开通后该会员可创建软件并提交审核；关闭后已发布内容保留，但不能再提交/重新上架。
        </Typography.Paragraph>
        {grantOpen ? (
          <InputNumber
            placeholder="会员ID（前台 userId）"
            value={grantUserId}
            onChange={(value) => setGrantUserId(value)}
            style={{ width: '100%', marginBottom: 12 }}
            min={1}
          />
        ) : (
          <Typography.Paragraph>
            会员：<Typography.Text strong>{target?.userId}</Typography.Text>
          </Typography.Paragraph>
        )}
        <Radio.Group
          value={enable}
          onChange={(e) => setEnable(e.target.value)}
          style={{ marginBottom: 12 }}
        >
          <Radio.Button value={true}>开通</Radio.Button>
          <Radio.Button value={false}>关闭</Radio.Button>
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
  )
}
