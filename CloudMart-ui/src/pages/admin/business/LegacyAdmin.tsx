import { useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Badge, Button, Input, Popconfirm, Statistic, Tabs, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import {
    getAdminLegacyStats,
    listAdminContentFlowLogs,
    listAdminLeaderboardConfigs,
    retryAdminContentFlow,
    updateAdminLeaderboardConfig,
} from '@/api/admin/wish'
import type {
    AdminContentFlowLog,
    AdminContentFlowStatus,
    AdminLeaderboardConfigRecord,
    AdminLegacyStats,
} from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const { Text } = Typography

/**
 * 传承 + 排行榜管理（Sprint 2.7 管理后台）：
 * 排行榜配置（刷新周期/Top N/同分处理/封禁过滤，实时生效）+
 * 内容流转日志（查看/重试，community 不可用补偿入口）+ 传承统计。
 */

const FLOW_STATUS_LABELS: Record<AdminContentFlowStatus, { text: string; badge: 'success' | 'error' | 'default' }> = {
    SUCCESS: { text: '已生成', badge: 'success' },
    FAILED: { text: '失败', badge: 'error' },
    HIDDEN: { text: '已隐藏', badge: 'default' },
}

export default function LegacyAdmin() {
    const tableRef = useRef<ActionType>(null)
    const message = useMessage()
    const [stats, setStats] = useState<AdminLegacyStats | null>(null)
    const [configs, setConfigs] = useState<AdminLeaderboardConfigRecord[]>([])
    const [configDrafts, setConfigDrafts] = useState<Record<string, string>>({})
    const [savingKey, setSavingKey] = useState<string | null>(null)

    const loadStats = async () => {
        try {
            const res = await getAdminLegacyStats()
            if (res.data.success) setStats(res.data.data)
        } catch {
            // 拦截器已提示
        }
    }

    const loadConfigs = async () => {
        try {
            const res = await listAdminLeaderboardConfigs()
            const items = res.data.data ?? []
            setConfigs(items)
            setConfigDrafts(Object.fromEntries(items.map((c) => [c.configKey, c.configValue])))
        } catch {
            // 拦截器已提示
        }
    }

    useEffect(() => {
        loadStats()
        loadConfigs()
    }, [])

    const handleSaveConfig = async (record: AdminLeaderboardConfigRecord) => {
        const value = (configDrafts[record.configKey] ?? '').trim()
        if (!value || value === record.configValue) {
            message.info('配置值未变化')
            return
        }
        setSavingKey(record.configKey)
        try {
            await updateAdminLeaderboardConfig(record.configKey, value)
            message.success('已保存并实时生效')
            loadConfigs()
        } catch {
            // 拦截器已提示
        } finally {
            setSavingKey(null)
        }
    }

    const columns: ProColumns<AdminContentFlowLog>[] = [
        { title: '心愿 ID', dataIndex: 'wishId', width: 160 },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => {
                const meta = FLOW_STATUS_LABELS[r.status]
                return <Badge status={meta.badge} text={meta.text} />
            },
        },
        {
            title: 'community 帖子',
            dataIndex: 'postId',
            width: 150,
            render: (_, r) => (r.postId ? <Tag color="gold">{r.postId}</Tag> : <Text type="secondary">-</Text>),
        },
        { title: '重试次数', dataIndex: 'retryCount', width: 90 },
        {
            title: '失败原因',
            dataIndex: 'errorMsg',
            ellipsis: true,
            render: (_, r) => r.errorMsg ?? '-',
        },
        {
            title: '更新时间',
            dataIndex: 'updatedAt',
            width: 160,
            render: (_, r) => <Text style={{ fontSize: 12 }}>{new Date(r.updatedAt).toLocaleString('zh-CN')}</Text>,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 100,
            render: (_, r) =>
                r.status === 'FAILED' && [
                    <Popconfirm
                        key="retry"
                        title="立即重试该流转？"
                        onConfirm={async () => {
                            try {
                                await retryAdminContentFlow(r.id)
                                message.success('已触发重试')
                                tableRef.current?.reload()
                            } catch {
                                // 拦截器已提示
                            }
                        }}
                    >
                        <Button key="retryBtn" size="small" type="link" icon={<ReloadOutlined />}>
                            重试
                        </Button>
                    </Popconfirm>,
                ],
        },
    ]

    return (
        <Tabs
            defaultActiveKey="leaderboard"
            items={[
                {
                    key: 'leaderboard',
                    label: '排行榜配置',
                    children: (
                        <div>
                            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                                数据源固定：热门=wish.light_count / 温暖=wish.bless_count / 坚持=累计打卡天数 /
                                星火=帮助次数。配置保存实时生效（下次刷新任务按新值执行）。
                            </Typography.Paragraph>
                            {configs.map((record) => (
                                <div
                                    key={record.configKey}
                                    style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}
                                >
                                    <div style={{ width: 260 }}>
                                        <Text strong>{record.configKey}</Text>
                                        <div>
                                            <Text type="secondary" style={{ fontSize: 12 }}>
                                                {record.description}
                                            </Text>
                                        </div>
                                    </div>
                                    <Input
                                        style={{ width: 180 }}
                                        value={configDrafts[record.configKey] ?? record.configValue}
                                        onChange={(e) =>
                                            setConfigDrafts((prev) => ({ ...prev, [record.configKey]: e.target.value }))
                                        }
                                    />
                                    <Button
                                        type="primary"
                                        ghost
                                        loading={savingKey === record.configKey}
                                        onClick={() => handleSaveConfig(record)}
                                    >
                                        保存
                                    </Button>
                                    <Tag>当前值：{record.configValue}</Tag>
                                </div>
                            ))}
                            {configs.length === 0 && <Button onClick={loadConfigs}>加载配置</Button>}
                        </div>
                    ),
                },
                {
                    key: 'flows',
                    label: '内容流转日志',
                    children: (
                        <ProTable<AdminContentFlowLog>
                            headerTitle="还愿 → community 帖子流转记录（FAILED 可重试；HIDDEN 为故事撤回后的状态同步）"
                            rowKey="id"
                            actionRef={tableRef}
                            search={false}
                            options={false}
                            columns={columns}
                            request={() => safeProTableRequest<AdminContentFlowLog>(() => listAdminContentFlowLogs({ page: 1, size: 50 }))}
                        />
                    ),
                },
                {
                    key: 'stats',
                    label: '传承统计',
                    children: stats ? (
                        <div>
                            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', marginBottom: 24 }}>
                                <Statistic title="传承发起次数" value={stats.inheritCount} />
                                <Statistic title="覆盖同求用户" value={stats.totalTargets} />
                                <Statistic title="成功推送" value={stats.totalPushed} />
                                <Statistic
                                    title="推送成功率"
                                    value={Math.round(stats.pushedRate * 100)}
                                    suffix="%"
                                />
                                <Statistic title="流转成功" value={stats.flowSuccess} />
                                <Statistic title="流转失败" value={stats.flowFailed} />
                                <Statistic title="帖子已隐藏" value={stats.flowHidden} />
                            </div>
                            <Typography.Paragraph type="secondary">
                                触达率以推送成功率计（sum(pushed)/sum(target)）；实际查看率与二次传播量需
                                community 阅读互动埋点，契约偏差已留档（见进度文件四T）。
                            </Typography.Paragraph>
                        </div>
                    ) : (
                        <Button onClick={loadStats}>加载统计</Button>
                    ),
                },
            ]}
        />
    )
}
