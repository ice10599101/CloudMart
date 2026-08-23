import { useCallback, useEffect, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ProColumns } from '@ant-design/pro-components'
import { Button, Card, Col, Row, Statistic, Tabs, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import {
    CAPSULE_STATUS_STAT_META,
    getAdminCapsulePushRecords,
    getAdminCapsuleStats,
} from '@/api/admin/wish'
import type { AdminCapsulePushRecord, AdminCapsuleStats } from '@/api/admin/wish'
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
                        userId: params.userId ? Number(params.userId) : undefined,
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

export default function Capsules() {
    return (
        <Tabs
            defaultActiveKey="stats"
            items={[
                { key: 'stats', label: '统计概览', children: <StatsPanel /> },
                { key: 'records', label: '推送记录', children: <PushRecordsPanel /> },
            ]}
        />
    )
}
