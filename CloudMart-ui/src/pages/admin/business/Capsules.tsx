import { useCallback, useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Card, Col, Input, Modal, Row, Statistic, Tabs, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import {
    CAPSULE_STATUS_STAT_META,
    decideAdminCapsuleWall,
    getAdminCapsulePushRecords,
    getAdminCapsuleStats,
    getAdminCapsuleWallApproved,
    getAdminCapsuleWallPending,
    takeDownAdminCapsuleWall,
} from '@/api/admin/wish'
import type {
    AdminCapsulePushRecord,
    AdminCapsuleStats,
    AdminCapsuleWallRecord,
} from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * 时间胶囊统计（文档 Sprint 2.4 管理后台）。
 *
 * 统计概览：总量/四状态计数/今日创建（mall-admin → mall-wish /admin/capsules/stats）
 * 推送记录：type=CAPSULE_AVAILABLE 的胶囊到期推送（mall-admin → mall-notification）
 */

/** 统计概览卡片 */
function StatsPanel() {
    const message = useMessage()
    const [loading, setLoading] = useState(true)
    const [stats, setStats] = useState<AdminCapsuleStats | null>(null)

    const loadStats = useCallback(async () => {
        setLoading(true)
        try {
            const res = await getAdminCapsuleStats()
            if (res.data.success) {
                setStats(res.data.data)
            } else {
                message.error(res.data.error?.message ?? '统计加载失败')
            }
        } catch {
            // 错误已由 request 拦截器处理
        } finally {
            setLoading(false)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        loadStats()
    }, [loadStats])

    return (
        <>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
                <Button icon={<ReloadOutlined />} onClick={loadStats} loading={loading}>
                    刷新
                </Button>
            </div>
            <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} lg={6}>
                    <Card loading={loading}>
                        <Statistic title="胶囊总量" value={stats?.total ?? 0} />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card loading={loading}>
                        <Statistic title="今日创建" value={stats?.todayCreated ?? 0} />
                    </Card>
                </Col>
                {CAPSULE_STATUS_STAT_META.map(({ key, label, emoji }) => (
                    <Col xs={24} sm={12} lg={6} key={key}>
                        <Card loading={loading}>
                            <Statistic
                                title={
                                    <span>
                    {emoji} {label}
                  </span>
                                }
                                value={stats?.[key] ?? 0}
                            />
                        </Card>
                    </Col>
                ))}
            </Row>
            <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
                到期扫描由 XXL-Job capsuleOpenScanHandler 每 10 分钟执行（SEALED → AVAILABLE）；到期推送记录见「推送记录」页签。
            </Typography.Paragraph>
        </>
    )
}

/** 推送记录表（ProTable 搜索：用户ID/类型） */
function PushRecordsPanel() {
    const columns: ProColumns<AdminCapsulePushRecord>[] = [
        { title: 'ID', dataIndex: 'id', width: 150, search: false, ellipsis: true },
        { title: '用户ID', dataIndex: 'userId', width: 90 },
        { title: '用户名', dataIndex: 'username', width: 120, search: false },
        {
            title: '类型',
            dataIndex: 'type',
            width: 170,
            ellipsis: true,
            valueEnum: { CAPSULE_AVAILABLE: { text: '胶囊到期推送' } },
            render: (_, record) =>
                record.type === 'CAPSULE_AVAILABLE' ? (
                    <Tag color="cyan">胶囊到期推送</Tag>
                ) : (
                    <Tag color="default">{record.type}</Tag>
                ),
        },
        { title: '标题', dataIndex: 'title', width: 200, search: false, ellipsis: true },
        { title: '内容', dataIndex: 'content', width: 280, search: false, ellipsis: true },
        {
            title: '已读',
            dataIndex: 'isRead',
            width: 80,
            search: false,
            render: (_, record) => (
                <Tag color={record.isRead === 1 ? 'green' : 'default'}>{record.isRead === 1 ? '已读' : '未读'}</Tag>
            ),
        },
        { title: '发送时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    ]

    return (
        <ProTable<AdminCapsulePushRecord>
            headerTitle="胶囊到期推送记录"
            rowKey="id"
            scroll={{ x: 1250 }}
            request={async (params) => {
                return safeProTableRequest<AdminCapsulePushRecord>(() =>
                    getAdminCapsulePushRecords({
                        userId: params.userId || undefined,
                        type: params.type,
                        page: params.current,
                        pageSize: params.pageSize,
                    })
                )
            }}
            columns={columns}
            pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
    )
}

/** R17 理由输入 Modal（驳回/下架共用；理由必填 ≤200 字） */
function WallReasonModal({
    open,
    title,
    okText,
    confirmLoading,
    onCancel,
    onOk,
}: {
    open: boolean
    title: string
    okText: string
    confirmLoading: boolean
    onCancel: () => void
    onOk: (reason: string) => void
}) {
    const [reason, setReason] = useState('')
    const [error, setError] = useState<string | null>(null)

    const submit = () => {
        const trimmed = reason.trim()
        if (!trimmed) {
            setError('请填写理由（将随通知告知作者）')
            return
        }
        if (trimmed.length > 200) {
            setError('理由不能超过 200 字')
            return
        }
        onOk(trimmed)
    }

    return (
        <Modal
            open={open}
            title={title}
            okText={okText}
            cancelText="取消"
            confirmLoading={confirmLoading}
            onCancel={onCancel}
            onOk={submit}
            destroyOnClose
            afterOpenChange={(visible) => {
                if (visible) {
                    setReason('')
                    setError(null)
                }
            }}
        >
            <Input.TextArea
                rows={4}
                maxLength={220}
                showCount
                value={reason}
                placeholder="请填写理由（必填，≤200 字，将随站内通知告知作者）"
                onChange={(e) => {
                    setReason(e.target.value)
                    if (error) setError(null)
                }}
            />
            {error && <Typography.Text type="danger" style={{ fontSize: 12 }}>{error}</Typography.Text>}
        </Modal>
    )
}

/** R17 待审核队列：通过/驳回（驳回理由必填），并发冲突由后端 409 拦截器统一提示 */
function WallPendingPanel() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const [rejectTarget, setRejectTarget] = useState<AdminCapsuleWallRecord | null>(null)
    const [submitting, setSubmitting] = useState(false)

    const approve = (record: AdminCapsuleWallRecord) => {
        Modal.confirm({
            title: '确认通过该胶囊上墙？',
            content: `《${record.title}》将立即在公共胶囊墙匿名展出，作者会收到通知。`,
            okText: '确认通过',
            cancelText: '取消',
            onOk: async () => {
                setSubmitting(true)
                try {
                    const res = await decideAdminCapsuleWall(record.capsuleId, { approve: true })
                    if (res.data.success) {
                        message.success('已通过上墙')
                        actionRef.current?.reload()
                    } else {
                        message.error(res.data.error?.message ?? '操作失败')
                        throw new Error('business failure')
                    }
                } finally {
                    setSubmitting(false)
                }
            },
        })
    }

    const reject = async (reason: string) => {
        if (!rejectTarget) return
        setSubmitting(true)
        try {
            const res = await decideAdminCapsuleWall(rejectTarget.capsuleId, { approve: false, reason })
            if (res.data.success) {
                message.success('已驳回并通知作者')
                setRejectTarget(null)
                actionRef.current?.reload()
            } else {
                message.error(res.data.error?.message ?? '操作失败')
            }
        } catch {
            // 错误由拦截器处理
        } finally {
            setSubmitting(false)
        }
    }

    const columns: ProColumns<AdminCapsuleWallRecord>[] = [
        { title: '胶囊 ID', dataIndex: 'capsuleId', width: 150, search: false, copyable: true },
        { title: '作者 ID', dataIndex: 'userId', width: 110, search: false },
        { title: '标题', dataIndex: 'title', width: 180, search: false, ellipsis: true },
        {
            title: '正文',
            dataIndex: 'content',
            search: false,
            ellipsis: true,
            render: (_, record) => (
                <Typography.Paragraph style={{ margin: 0, maxWidth: 360 }} ellipsis={{ rows: 2, tooltip: record.content }}>
                    {record.content}
                </Typography.Paragraph>
            ),
        },
        { title: '开启时间', dataIndex: 'openedAt', width: 170, valueType: 'dateTime', search: false },
        { title: '申请时间', dataIndex: 'wallAppliedAt', width: 170, valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            width: 160,
            fixed: 'right',
            render: (_, record) => [
                <Button
                    key="approve"
                    type="link"
                    size="small"
                    loading={submitting}
                    onClick={() => approve(record)}
                >
                    通过
                </Button>,
                <Button key="reject" type="link" size="small" danger onClick={() => setRejectTarget(record)}>
                    驳回
                </Button>,
            ],
        },
    ]

    return (
        <>
            <ProTable<AdminCapsuleWallRecord>
                actionRef={actionRef}
                headerTitle="公共胶囊墙待审核"
                rowKey="capsuleId"
                scroll={{ x: 1200 }}
                request={async (params) =>
                    safeProTableRequest<AdminCapsuleWallRecord>(() =>
                        getAdminCapsuleWallPending({ page: params.current, pageSize: params.pageSize })
                    )
                }
                columns={columns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
            <WallReasonModal
                open={rejectTarget !== null}
                title={rejectTarget ? `驳回《${rejectTarget.title}》` : '驳回胶囊'}
                okText="确认驳回"
                confirmLoading={submitting}
                onCancel={() => setRejectTarget(null)}
                onOk={reject}
            />
        </>
    )
}

/** R17 已上墙管理：管理员下架（理由必填，下架后公开墙即刻不可见） */
function WallApprovedPanel() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const [target, setTarget] = useState<AdminCapsuleWallRecord | null>(null)
    const [submitting, setSubmitting] = useState(false)

    const takeDown = async (reason: string) => {
        if (!target) return
        setSubmitting(true)
        try {
            const res = await takeDownAdminCapsuleWall(target.capsuleId, reason)
            if (res.data.success) {
                message.success('已下架并通知作者')
                setTarget(null)
                actionRef.current?.reload()
            } else {
                message.error(res.data.error?.message ?? '操作失败')
            }
        } catch {
            // 作者抢先撤下等 409 由拦截器提示
        } finally {
            setSubmitting(false)
        }
    }

    const columns: ProColumns<AdminCapsuleWallRecord>[] = [
        { title: '胶囊 ID', dataIndex: 'capsuleId', width: 150, search: false, copyable: true },
        { title: '作者 ID', dataIndex: 'userId', width: 110, search: false },
        { title: '标题', dataIndex: 'title', width: 180, search: false, ellipsis: true },
        {
            title: '正文',
            dataIndex: 'content',
            search: false,
            ellipsis: true,
            render: (_, record) => (
                <Typography.Paragraph style={{ margin: 0, maxWidth: 320 }} ellipsis={{ rows: 2, tooltip: record.content }}>
                    {record.content}
                </Typography.Paragraph>
            ),
        },
        { title: '上墙时间', dataIndex: 'wallDecidedAt', width: 170, valueType: 'dateTime', search: false },
        {
            title: '状态',
            dataIndex: 'wallStatus',
            width: 100,
            search: false,
            render: () => <Tag color="green">已上墙</Tag>,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 110,
            fixed: 'right',
            render: (_, record) => [
                <Button key="takeDown" type="link" size="small" danger onClick={() => setTarget(record)}>
                    下架
                </Button>,
            ],
        },
    ]

    return (
        <>
            <ProTable<AdminCapsuleWallRecord>
                actionRef={actionRef}
                headerTitle="已上墙胶囊（下架管理）"
                rowKey="capsuleId"
                scroll={{ x: 1150 }}
                request={async (params) =>
                    safeProTableRequest<AdminCapsuleWallRecord>(() =>
                        getAdminCapsuleWallApproved({ page: params.current, pageSize: params.pageSize })
                    )
                }
                columns={columns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
            />
            <WallReasonModal
                open={target !== null}
                title={target ? `下架《${target.title}》` : '下架胶囊'}
                okText="确认下架"
                confirmLoading={submitting}
                onCancel={() => setTarget(null)}
                onOk={takeDown}
            />
        </>
    )
}

export default function Capsules() {
    return (
        <Tabs
            defaultActiveKey="stats"
            items={[
                { key: 'stats', label: '统计概览', children: <StatsPanel /> },
                { key: 'wallPending', label: '公共墙待审核', children: <WallPendingPanel /> },
                { key: 'wallApproved', label: '已上墙管理', children: <WallApprovedPanel /> },
                { key: 'records', label: '推送记录', children: <PushRecordsPanel /> },
            ]}
        />
    )
}
