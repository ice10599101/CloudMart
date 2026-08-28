import { useCallback, useEffect, useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Badge, Button, Input, Modal, Popconfirm, Select, Statistic, Tabs, Tag, Typography } from 'antd'
import { FileDoneOutlined, ReloadOutlined, RollbackOutlined } from '@ant-design/icons'
import {
    GRAYSCALE_FEATURE_LABELS,
    generateAiReviewSamples,
    getAiReviewStats,
    listAdminGrayscaleConfigs,
    listAiReviewSamples,
    scoreAiReviewSample,
    updateAdminGrayscaleRatio,
} from '@/api/admin/wish'
import type {
    AdminAiReviewIssueType,
    AdminAiReviewSample,
    AdminGrayscaleConfigRecord,
    AdminAiReviewStats,
} from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const { Paragraph, Text } = Typography

/**
 * 灰度控制台 + AI 质量抽检（Sprint 2.8 管理后台）：
 * 灰度比例编辑（档位吸附 + 二次确认 + 一键回滚）与
 * AI 回复抽检（生成任务/人工评分/合格率与问题分类统计）。
 */

const RATIO_OPTIONS = [0, 5, 20, 50, 100].map((v) => ({ value: v, label: `${v}%` }))

export default function GrayScaleAdmin() {
    const tableRef = useRef<ActionType>(null)
    const message = useMessage()
    const [configs, setConfigs] = useState<AdminGrayscaleConfigRecord[]>([])
    const [loadingConfigs, setLoadingConfigs] = useState(true)
    const [stats, setStats] = useState<AdminAiReviewStats | null>(null)
    const [generating, setGenerating] = useState(false)
    const [sampleSize, setSampleSize] = useState(20)

    const loadStats = useCallback(async () => {
        try {
            const res = await getAiReviewStats()
            if (res.data.success) setStats(res.data.data)
        } catch {
            // 拦截器已提示
        }
    }, [])

    const loadConfigs = useCallback(async () => {
        setLoadingConfigs(true)
        try {
            const res = await listAdminGrayscaleConfigs()
            if (res.data.success) setConfigs(res.data.data ?? [])
        } catch {
            // 拦截器已提示
        } finally {
            setLoadingConfigs(false)
        }
    }, [])

    useEffect(() => {
        loadConfigs()
        loadStats()
    }, [loadConfigs, loadStats])

    /** 灰度调整：二次确认（文档 2.8 交互验收：灰度切换有二次确认） */
    const handleUpdateRatio = (record: AdminGrayscaleConfigRecord, ratio: number) => {
        if (ratio === record.grayRatio) {
            message.info('比例未变化')
            return
        }
        const rollingBack = ratio === 0
        Modal.confirm({
            title: rollingBack ? `回滚「${GRAYSCALE_FEATURE_LABELS[record.featureKey] ?? record.featureKey}」？` : `调整灰度比例？`,
            width: 480,
            content: (
                <div>
                    <Paragraph>
                        {record.featureKey}：{record.grayRatio}% → <Text strong>{ratio}%</Text>
                        {rollingBack ? '（回滚后该功能对全部用户关闭开关）' : '（同一用户恒命中同一档，切换对命中内用户无感知）'}
                    </Paragraph>
                    <Paragraph type="secondary" style={{ fontSize: 12 }}>
                        实时生效，再次调整即可继续放量或回滚。
                    </Paragraph>
                </div>
            ),
            onOk: async () => {
                try {
                    await updateAdminGrayscaleRatio(record.featureKey, ratio)
                    message.success('已生效')
                    loadConfigs()
                } catch {
                    // 拦截器已提示
                }
            },
        })
    }

    /** 一键回滚：全部功能比例置 0（10 分钟内回滚验收的操作承载） */
    const handleRollbackAll = async () => {
        for (const record of configs) {
            if (record.grayRatio > 0) {
                await updateAdminGrayscaleRatio(record.featureKey, 0)
            }
        }
        message.success('已全部回滚')
        loadConfigs()
    }

    const markFail = async (id: number, issueType: AdminAiReviewIssueType) => {
        try {
            await scoreAiReviewSample(id, { result: 'FAIL', issueType })
            message.success('已标记不合格')
            tableRef.current?.reload()
            loadStats()
        } catch {
            // 拦截器已提示
        }
    }

    const sampleColumns: ProColumns<AdminAiReviewSample>[] = [
        {
            title: '场景',
            dataIndex: 'scene',
            width: 120,
            render: (_, r) => r.scene ?? '-',
        },
        {
            title: 'AI 回复内容',
            dataIndex: 'content',
            render: (_, r) => (
                <Paragraph ellipsis={{ rows: 2, expandable: true }} style={{ marginBottom: 0, fontSize: 12 }}>
                    {r.content}
                </Paragraph>
            ),
        },
        {
            title: '评分',
            dataIndex: 'result',
            width: 90,
            render: (_, r) =>
                r.result === 'PASS' ? (
                    <Badge status="success" text="合格" />
                ) : r.result === 'FAIL' ? (
                    <Badge status="error" text="不合格" />
                ) : (
                    <Text type="secondary">待评</Text>
                ),
        },
        {
            title: '问题分类',
            dataIndex: 'issueType',
            width: 100,
            render: (_, r) =>
                r.issueType === 'MECHANICAL' ? (
                    <Tag>机械感</Tag>
                ) : r.issueType === 'ERROR' ? (
                    <Tag color="red">错误信息</Tag>
                ) : r.issueType === 'IRRELEVANT' ? (
                    <Tag color="orange">不相关</Tag>
                ) : (
                    '-'
                ),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 180,
            render: (_, r) => [
                <Button
                    key="pass"
                    size="small"
                    type="link"
                    onClick={async () => {
                        try {
                            await scoreAiReviewSample(r.id, { result: 'PASS' })
                            tableRef.current?.reload()
                            loadStats()
                        } catch {
                            // 拦截器已提示
                        }
                    }}
                >
                    合格
                </Button>,
                <Popconfirm
                    key="failM"
                    title="标记不合格：机械感？"
                    onConfirm={async () => markFail(r.id, 'MECHANICAL')}
                >
                    <Button key="failM" size="small" type="link" danger>机械感</Button>
                </Popconfirm>,
                <Popconfirm
                    key="failE"
                    title="标记不合格：错误信息？"
                    onConfirm={async () => markFail(r.id, 'ERROR')}
                >
                    <Button key="failE" size="small" type="link" danger>错误</Button>
                </Popconfirm>,
                <Popconfirm
                    key="failI"
                    title="标记不合格：不相关？"
                    onConfirm={async () => markFail(r.id, 'IRRELEVANT')}
                >
                    <Button key="failI" size="small" type="link" danger>不相关</Button>
                </Popconfirm>,
            ],
        },
    ]

    return (
        <Tabs
            defaultActiveKey="grayscale"
            items={[
                {
                    key: 'grayscale',
                    label: '灰度控制台',
                    children: (
                        <div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
                                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                                    按用户 ID 稳定哈希分流（同一用户恒命中同一档）；回滚=比例置 0，实时生效。
                                </Typography.Paragraph>
                                <Popconfirm title="确定全部回滚吗？" description="所有功能比例置 0，对用户关闭开关（二次确认）" onConfirm={handleRollbackAll}>
                                    <Button danger icon={<RollbackOutlined />}>一键回滚</Button>
                                </Popconfirm>
                            </div>
                            {loadingConfigs ? (
                                <Text type="secondary">加载中...</Text>
                            ) : (
                                configs.map((record) => (
                                    <div
                                        key={record.featureKey}
                                        style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}
                                    >
                                        <div style={{ width: 320 }}>
                                            <Text strong>{GRAYSCALE_FEATURE_LABELS[record.featureKey] ?? record.featureKey}</Text>
                                            <div>
                                                <Text type="secondary" style={{ fontSize: 12 }}>
                                                    {record.description ?? record.featureKey}
                                                </Text>
                                            </div>
                                        </div>
                                        <Select
                                            style={{ width: 120 }}
                                            value={record.grayRatio}
                                            options={RATIO_OPTIONS}
                                            onChange={(v) => handleUpdateRatio(record, v)}
                                        />
                                        <Tag>当前 {record.grayRatio}%</Tag>
                                    </div>
                                ))
                            )}
                        </div>
                    ),
                },
                {
                    key: 'aiReview',
                    label: 'AI 质量抽检',
                    children: (
                        <div>
                            <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 16, flexWrap: 'wrap' }}>
                                <Statistic title="合格率" value={stats ? Math.round(stats.passRate * 100) : 0} suffix="%" />
                                <Statistic title="已评" value={stats?.reviewedCount ?? 0} />
                                <Statistic title="机械感" value={stats?.issueMechanical ?? 0} />
                                <Statistic title="错误信息" value={stats?.issueError ?? 0} />
                                <Statistic title="不相关" value={stats?.issueIrrelevant ?? 0} />
                                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginLeft: 'auto' }}>
                                    <Input
                                        type="number"
                                        style={{ width: 90 }}
                                        value={sampleSize}
                                        onChange={(e) => setSampleSize(Number(e.target.value) || 20)}
                                    />
                                    <Button
                                        type="primary"
                                        icon={<FileDoneOutlined />}
                                        loading={generating}
                                        onClick={async () => {
                                            setGenerating(true)
                                            try {
                                                const res = await generateAiReviewSamples({ sampleSize })
                                                message.success(`已生成 ${res.data.data ?? 0} 条待评样本`)
                                                tableRef.current?.reload()
                                                loadStats()
                                            } catch {
                                                // 拦截器已提示
                                            } finally {
                                                setGenerating(false)
                                            }
                                        }}
                                    >
                                        生成抽检任务
                                    </Button>
                                    <Button icon={<ReloadOutlined />} onClick={() => { tableRef.current?.reload(); loadStats() }}>
                                        刷新
                                    </Button>
                                </div>
                            </div>
                            <ProTable<AdminAiReviewSample>
                                headerTitle="抽检样本（人工评分：合格率目标 ≥ 90%）"
                                rowKey="id"
                                actionRef={tableRef}
                                search={false}
                                options={false}
                                columns={sampleColumns}
                                request={() => safeProTableRequest<AdminAiReviewSample>(() => listAiReviewSamples({ page: 1, size: 50 }))}
                            />
                        </div>
                    ),
                },
            ]}
        />
    )
}
