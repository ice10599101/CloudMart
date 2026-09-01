import { useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Badge, Button, Input, Popconfirm, Tabs, Tag, Typography } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import {
    listAdminMatchConfigs,
    listAdminMatchGroups,
    updateAdminMatchConfig,
    forceDissolveMatchGroup,
} from '@/api/admin/wish'
import type { AdminMatchConfigRecord, AdminMatchGroupRow } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const { Text } = Typography

/**
 * 同愿匹配管理（Sprint 2.6 管理后台）：
 * 小组管理（查看/解散异常小组/活跃度监控）+ 匹配算法配置
 * （关键词/城市/活跃度权重 + 相似度阈值 + 提醒/建组限频，实时生效）。
 */

export default function MatchAdmin() {
    const tableRef = useRef<ActionType>(null)
    const message = useMessage()
    const [configs, setConfigs] = useState<AdminMatchConfigRecord[]>([])
    const [configDrafts, setConfigDrafts] = useState<Record<string, string>>({})
    const [savingKey, setSavingKey] = useState<string | null>(null)

    const loadConfigs = async () => {
        try {
            const res = await listAdminMatchConfigs()
            const items = res.data.data ?? []
            setConfigs(items)
            setConfigDrafts(Object.fromEntries(items.map((c) => [c.configKey, c.configValue])))
        } catch {
            // 拦截器已提示
        }
    }

    useEffect(() => {
        loadConfigs()
    }, [])

    const handleSaveConfig = async (record: AdminMatchConfigRecord) => {
        const value = (configDrafts[record.configKey] ?? '').trim()
        if (!value) {
            message.warning('配置值不能为空')
            return
        }
        if (value === record.configValue) {
            message.info('配置值未变化')
            return
        }
        setSavingKey(record.configKey)
        try {
            await updateAdminMatchConfig(record.configKey, value)
            message.success('已保存并实时生效')
            loadConfigs()
        } catch {
            // 拦截器已提示
        } finally {
            setSavingKey(null)
        }
    }

    const columns: ProColumns<AdminMatchGroupRow>[] = [
        {
            title: '主题',
            dataIndex: 'keyword',
            width: 160,
            render: (_, r) => <Text strong>「{r.keyword}」</Text>,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => (
                <Badge
                    status={r.status === 'OPEN' ? 'success' : r.status === 'FULL' ? 'warning' : 'default'}
                    text={r.status === 'OPEN' ? '招募中' : r.status === 'FULL' ? '已满员' : '已解散'}
                />
            ),
        },
        { title: '人数', width: 90, render: (_, r) => `${r.memberCount}/${r.maxMembers}` },
        { title: '组长', dataIndex: 'leaderNickname', width: 120 },
        {
            title: '最近活跃',
            dataIndex: 'lastActiveAt',
            width: 150,
            render: (_, r) =>
                r.lastActiveAt ? (
                    <Text style={{ fontSize: 12 }}>{new Date(r.lastActiveAt).toLocaleString('zh-CN')}</Text>
                ) : (
                    <Tag>无活跃记录</Tag>
                ),
        },
        { title: '同城码', dataIndex: 'cityCode', width: 90, render: (_, r) => r.cityCode ?? '-' },
        {
            title: '建组时间',
            dataIndex: 'createdAt',
            width: 150,
            render: (_, r) => <Text style={{ fontSize: 12 }}>{new Date(r.createdAt).toLocaleString('zh-CN')}</Text>,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 110,
            render: (_, r) =>
                r.status !== 'CLOSED' && [
                    <Popconfirm
                        key="dissolve"
                        title={`确定解散小组「${r.keyword}」吗？`}
                        description="解散后所有成员都会收到通知（二次确认）"
                        onConfirm={async () => {
                            try {
                                await forceDissolveMatchGroup(r.groupId)
                                message.success('已解散')
                                tableRef.current?.reload()
                            } catch {
                                // 拦截器已提示
                            }
                        }}
                    >
                        <Button key="dissolveBtn" size="small" type="link" danger icon={<DeleteOutlined />}>
                            解散
                        </Button>
                    </Popconfirm>,
                ],
        },
    ]

    return (
        <Tabs
            defaultActiveKey="groups"
            items={[
                {
                    key: 'groups',
                    label: '小组管理',
                    children: (
                        <ProTable<AdminMatchGroupRow>
                            headerTitle="同愿小组（含已解散，活跃度监控口径：最近活跃 = 组内成员 last_active_at 最大值）"
                            rowKey="groupId"
                            actionRef={tableRef}
                            search={false}
                            options={false}
                            columns={columns}
                            request={(params) =>
                                safeProTableRequest<AdminMatchGroupRow>(() =>
                                    listAdminMatchGroups({
                                        status: (params as { status?: string }).status,
                                    }),
                                )
                            }
                        />
                    ),
                },
                {
                    key: 'configs',
                    label: '匹配算法配置',
                    children: (
                        <div>
                            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                                权重调整后匹配排序实时变化、不改代码（文档 2.6 验收）。三项权重建议和为 1，
                                超和时服务端按比例归一；相似度阈值以下的组不进推荐（精确关键词命中不受阈值限制）。
                            </Typography.Paragraph>
                            {configs.map((record) => (
                                <div
                                    key={record.configKey}
                                    style={{
                                        display: 'flex',
                                        gap: 12,
                                        alignItems: 'center',
                                        marginBottom: 12,
                                        flexWrap: 'wrap',
                                    }}
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
            ]}
        />
    )
}
