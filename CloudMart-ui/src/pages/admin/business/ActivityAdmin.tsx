import { useRef, useState } from 'react'
import {
    ProTable,
    ModalForm,
    ProFormText,
    ProFormDigit,
    ProFormSelect,
    ProFormTextArea,
    ProFormDateTimePicker,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Modal, Popconfirm, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import { PlusOutlined, TrophyOutlined, FileTextOutlined } from '@ant-design/icons'
import {
    listAdminActivities,
    createAdminActivity,
    updateAdminActivity,
    transitionAdminActivity,
    deleteAdminActivity,
    issueAdminActivityRewards,
    listAdminActivityRewardLogs,
    ADMIN_ACTIVITY_TYPE_MAP,
    ADMIN_ACTIVITY_STATUS_MAP,
    ADMIN_ACTIVITY_CONDITION_TYPE_MAP,
} from '@/api/admin/wish'
import type {
    AdminActivityRecord,
    AdminActivityStatus,
    AdminActivityType,
    AdminActivityConditionType,
    AdminActivityRewardLog,
} from '@/api/admin/wish'
import dayjs, { type Dayjs } from 'dayjs'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 社区活动管理面板（文档 Sprint 3.5 管理后台：活动 CRUD + 状态机控制 +
 * 奖励发放 + 日志审计）。
 *
 * condition/reward 采用结构化表单（参照树环境 visual 模式），
 * 提交时序列化为 JSON 字符串，服务端 ActivityConditionParser 结构校验兜底。
 * 状态机：DRAFT --start--> ACTIVE --end--> ENDED --archive--> ARCHIVED。
 */

const { Text, Paragraph } = Typography

interface ActivityFormValues {
    type: AdminActivityType
    title: string
    description?: string
    cityCode?: string
    validFrom?: Dayjs | string
    validTo?: Dayjs | string
    conditionType?: AdminActivityConditionType
    conditionThreshold?: number
    rewardStarlight?: number
    rewardBadgeCode?: string
}

/** 解析 conditionJson（后端结构：{type, threshold?}；空 = 参与即达标） */
function parseCondition(json?: string | null): { type?: AdminActivityConditionType; threshold?: number } {
    if (!json) return {}
    try {
        const node = JSON.parse(json)
        return { type: node.type, threshold: node.threshold }
    } catch {
        return {}
    }
}

/** 解析 rewardJson（后端结构：{starlight?, badgeCode?}） */
function parseReward(json?: string | null): { starlight?: number; badgeCode?: string } {
    if (!json) return {}
    try {
        const node = JSON.parse(json)
        return { starlight: node.starlight, badgeCode: node.badgeCode }
    } catch {
        return {}
    }
}

function serializeCondition(values: ActivityFormValues): string | null {
    if (!values.conditionType) return null
    const condition: Record<string, unknown> = { type: values.conditionType }
    if (values.conditionThreshold !== undefined && values.conditionThreshold !== null) {
        condition.threshold = values.conditionThreshold
    }
    return JSON.stringify(condition)
}

function serializeReward(values: ActivityFormValues): string | null {
    const starlight = values.rewardStarlight ?? 0
    const badgeCode = values.rewardBadgeCode?.trim()
    if (starlight <= 0 && !badgeCode) return null
    const reward: Record<string, unknown> = {}
    if (starlight > 0) reward.starlight = starlight
    if (badgeCode) reward.badgeCode = badgeCode
    return JSON.stringify(reward)
}

function formatTime(value?: string | null): string {
    if (!value) return '-'
    return value.replace('T', ' ').slice(0, 16)
}

function renderConditionSummary(record: AdminActivityRecord) {
    const { type, threshold } = parseCondition(record.conditionJson)
    if (!type) return <Text type="secondary">无条件（参与即达标）</Text>
    const label = ADMIN_ACTIVITY_CONDITION_TYPE_MAP[type] ?? type
    if (type === 'MEMBER_FULFILLED') return <Text>{label}</Text>
    return <Text>{label}≥{threshold ?? 0}</Text>
}

function renderRewardSummary(record: AdminActivityRecord) {
    const { starlight, badgeCode } = parseReward(record.rewardJson)
    const parts: string[] = []
    if (starlight && starlight > 0) parts.push(`星光×${starlight}`)
    if (badgeCode) parts.push(`徽章 ${badgeCode}`)
    return parts.length > 0 ? <Text>{parts.join(' + ')}</Text> : <Text type="secondary">-</Text>
}

/** 状态机允许的下一动作：DRAFT→start / ACTIVE→end / ENDED→archive */
const TRANSITION_ACTION: Partial<Record<AdminActivityStatus, { action: 'start' | 'end' | 'archive'; label: string; confirm: string }>> = {
    DRAFT: { action: 'start', label: '启动', confirm: '确认启动活动？活动将进入进行中并对用户可见' },
    ACTIVE: { action: 'end', label: '结束', confirm: '确认结束活动？进行中将切换为已结束' },
    ENDED: { action: 'archive', label: '归档', confirm: '确认归档？归档后入口消失，详情页仍可访问' },
}

export default function ActivityAdmin() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState<AdminActivityRecord | null>(null)
    const [statusFilter, setStatusFilter] = useState<AdminActivityStatus | undefined>()
    const [typeFilter, setTypeFilter] = useState<AdminActivityType | undefined>()
    const [logsRecord, setLogsRecord] = useState<AdminActivityRecord | null>(null)
    const [rewardLogs, setRewardLogs] = useState<AdminActivityRewardLog[]>([])
    const [logsLoading, setLogsLoading] = useState(false)
    const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

    const reload = () => actionRef.current?.reload()

    const handleSubmit = async (values: ActivityFormValues) => {
        return confirmSubmit(async () => {
            const payload: Partial<AdminActivityRecord> = {
                type: values.type,
                title: values.title.trim(),
                description: values.description?.trim() || null,
                cityCode: values.cityCode?.trim() || null,
                validFrom: values.validFrom ? dayjs(values.validFrom).format('YYYY-MM-DDTHH:mm:ss') : null,
                validTo: values.validTo ? dayjs(values.validTo).format('YYYY-MM-DDTHH:mm:ss') : null,
                conditionJson: serializeCondition(values),
                rewardJson: serializeReward(values),
            }
            if (editingRecord) {
                await updateAdminActivity(editingRecord.id, payload)
                message.success('活动已更新')
            } else {
                const res = await createAdminActivity(payload)
                message.success(`活动已创建（# ${res.data.data?.id ?? ''}），初始为筹备中`)
            }
            reload()
        })
    }

    const handleTransition = async (record: AdminActivityRecord, action: 'start' | 'end' | 'archive') => {
        await transitionAdminActivity(record.id, action)
        message.success(`活动「${record.title}」状态已更新`)
        reload()
    }

    const handleDelete = async (record: AdminActivityRecord) => {
        await deleteAdminActivity(record.id)
        message.success('活动已删除')
        reload()
    }

    const handleIssueRewards = (record: AdminActivityRecord) => {
        Modal.confirm({
            title: '发放活动奖励',
            content: `对「${record.title}」的全部参与/组队用户按奖励配置发放。uk 幂等，重复发放自动跳过，确认执行？`,
            okText: '确认发放',
            cancelText: '取消',
            onOk: async () => {
                const res = await issueAdminActivityRewards(record.id)
                const stats = res.data.data
                if (stats) {
                    Modal.success({
                        title: '奖励发放完成',
                        content: (
                            <Space size="large" style={{ marginTop: 16 }}>
                                <Statistic title="符合条件" value={stats.eligible} />
                                <Statistic title="星光发放" value={stats.starlightIssued} />
                                <Statistic title="徽章发放" value={stats.badgeIssued} />
                                <Statistic title="幂等跳过" value={stats.skipped} />
                            </Space>
                        ),
                    })
                } else {
                    message.success('奖励发放完成')
                }
                reload()
            },
        })
    }

    const openLogs = async (record: AdminActivityRecord) => {
        setLogsRecord(record)
        setRewardLogs([])
        setLogsLoading(true)
        try {
            const res = await listAdminActivityRewardLogs(record.id)
            setRewardLogs(res.data.data ?? [])
        } finally {
            setLogsLoading(false)
        }
    }

    const columns: ProColumns<AdminActivityRecord>[] = [
        { title: 'ID', dataIndex: 'id', width: 130, search: false, ellipsis: true },
        {
            title: '类型',
            dataIndex: 'type',
            width: 100,
            valueType: 'select',
            fieldProps: {
                options: Object.entries(ADMIN_ACTIVITY_TYPE_MAP).map(([value, m]) => ({ value, label: m.label })),
            },
            render: (_, record) => {
                const m = ADMIN_ACTIVITY_TYPE_MAP[record.type]
                return m ? <Tag color={m.color}>{m.label}</Tag> : record.type
            },
        },
        {
            title: '标题',
            dataIndex: 'title',
            width: 180,
            search: false,
            ellipsis: true,
            render: (_, record) => (
                <Space direction="vertical" size={0}>
                    <Text strong>{record.title}</Text>
                    {record.cityCode && <Text type="secondary" style={{ fontSize: 12 }}>城市 {record.cityCode}</Text>}
                </Space>
            ),
        },
        {
            title: '起止时间',
            width: 230,
            search: false,
            render: (_, record) => (
                <Text type="secondary" style={{ fontSize: 12 }}>
                    {formatTime(record.validFrom)}<br />~ {formatTime(record.validTo)}
                </Text>
            ),
        },
        { title: '达成条件', width: 160, search: false, render: (_, record) => renderConditionSummary(record) },
        { title: '奖励', width: 150, search: false, render: (_, record) => renderRewardSummary(record) },
        { title: '进度', dataIndex: 'progressCounter', width: 80, search: false },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            valueType: 'select',
            fieldProps: {
                options: Object.entries(ADMIN_ACTIVITY_STATUS_MAP).map(([value, m]) => ({ value, label: m.label })),
            },
            render: (_, record) => {
                const m = ADMIN_ACTIVITY_STATUS_MAP[record.status]
                return m ? <Tag color={m.color}>{m.label}</Tag> : record.status
            },
        },
        {
            title: '操作',
            valueType: 'option',
            width: 230,
            fixed: 'right',
            render: (_, record) => {
                const transition = TRANSITION_ACTION[record.status]
                return [
                    record.status === 'DRAFT' || record.status === 'ACTIVE' ? (
                        <Button
                            key="edit"
                            type="link"
                            size="small"
                            onClick={() => {
                                setEditingRecord(record)
                                setModalVisible(true)
                            }}
                        >
                            编辑
                        </Button>
                    ) : null,
                    transition ? (
                        <Popconfirm key="transition" title={transition.confirm} onConfirm={() => handleTransition(record, transition.action)}>
                            <Button type="link" size="small">{transition.label}</Button>
                        </Popconfirm>
                    ) : null,
                    record.status === 'ACTIVE' || record.status === 'ENDED' ? (
                        <Button key="rewards" type="link" size="small" icon={<TrophyOutlined />}
                            onClick={() => handleIssueRewards(record)}>
                            发奖励
                        </Button>
                    ) : null,
                    <Button key="logs" type="link" size="small" icon={<FileTextOutlined />}
                        onClick={() => openLogs(record)}>
                        日志
                    </Button>,
                    record.status === 'DRAFT' ? (
                        <Popconfirm key="delete" title="确认删除？仅筹备中的活动可删除" onConfirm={() => handleDelete(record)}>
                            <Button type="link" size="small" danger>删除</Button>
                        </Popconfirm>
                    ) : null,
                ].filter(Boolean)
            },
        },
    ]

    const editing = editingRecord !== null
    const parsedCondition = editing ? parseCondition(editingRecord.conditionJson) : {}
    const parsedReward = editing ? parseReward(editingRecord.rewardJson) : {}

    return (
        <>
            <ProTable<AdminActivityRecord>
                headerTitle="社区活动"
                actionRef={actionRef}
                rowKey="id"
                search={false}
                request={async () => {
                    // 后端 list 无 total 元数据，活动量级小，一次拉取后前端分页
                    return safeProTableRequest<AdminActivityRecord>(() =>
                        listAdminActivities({ status: statusFilter, type: typeFilter, page: 1, size: 500 }),
                    )
                }}
                params={{ status: statusFilter, type: typeFilter }}
                toolBarRender={() => [
                    <Select<AdminActivityStatus>
                        key="status"
                        allowClear
                        placeholder="全部状态"
                        style={{ width: 130 }}
                        value={statusFilter}
                        options={Object.entries(ADMIN_ACTIVITY_STATUS_MAP).map(([value, m]) => ({ value, label: m.label }))}
                        onChange={(v) => { setStatusFilter(v); reload() }}
                    />,
                    <Select<AdminActivityType>
                        key="type"
                        allowClear
                        placeholder="全部类型"
                        style={{ width: 130 }}
                        value={typeFilter}
                        options={Object.entries(ADMIN_ACTIVITY_TYPE_MAP).map(([value, m]) => ({ value, label: m.label }))}
                        onChange={(v) => { setTypeFilter(v); reload() }}
                    />,
                    <Button
                        key="create"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => {
                            setEditingRecord(null)
                            setModalVisible(true)
                        }}
                    >
                        新增活动
                    </Button>,
                ]}
                columns={columns}
                pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 个活动` }}
            />

            <ModalForm<ActivityFormValues>
                title={editing ? `编辑活动：${editingRecord.title}` : '新增活动'}
                open={modalVisible}
                onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
                onFinish={handleSubmit}
                modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
                initialValues={
                    editing
                        ? {
                            type: editingRecord.type,
                            title: editingRecord.title,
                            description: editingRecord.description ?? undefined,
                            cityCode: editingRecord.cityCode ?? undefined,
                            validFrom: editingRecord.validFrom ? dayjs(editingRecord.validFrom) : undefined,
                            validTo: editingRecord.validTo ? dayjs(editingRecord.validTo) : undefined,
                            conditionType: parsedCondition.type,
                            conditionThreshold: parsedCondition.threshold,
                            rewardStarlight: parsedReward.starlight,
                            rewardBadgeCode: parsedReward.badgeCode,
                        }
                        : { type: 'WORLD_EVENT' }
                }
            >
                <ProFormSelect<AdminActivityType>
                    name="type"
                    label="活动类型"
                    rules={[{ required: true, message: '请选择活动类型' }]}
                    options={Object.entries(ADMIN_ACTIVITY_TYPE_MAP).map(([value, m]) => ({ value, label: m.label }))}
                />
                <ProFormText
                    name="title"
                    label="活动标题"
                    rules={[
                        { required: true, message: '请输入活动标题' },
                        { max: 64, message: '标题最长 64 字' },
                    ]}
                />
                <ProFormTextArea
                    name="description"
                    label="活动描述"
                    fieldProps={{ rows: 3, maxLength: 500, showCount: true }}
                />
                <ProFormText
                    name="cityCode"
                    label="城市编码（城市活动选填）"
                    placeholder="如 440100"
                    rules={[{ max: 16, message: '城市编码最长 16 字符' }]}
                />
                <ProFormDateTimePicker
                    name="validFrom"
                    label="展示开始时间"
                />
                <ProFormDateTimePicker
                    name="validTo"
                    label="展示结束时间"
                />
                <ProFormSelect<AdminActivityConditionType>
                    name="conditionType"
                    label="达成条件"
                    allowClear
                    placeholder="不选 = 参与即达标"
                    options={Object.entries(ADMIN_ACTIVITY_CONDITION_TYPE_MAP).map(([value, label]) => ({ value, label }))}
                />
                <ProFormDigit
                    name="conditionThreshold"
                    label="条件阈值"
                    min={0}
                    fieldProps={{ precision: 0 }}
                    placeholder="进度/参与人数达标阈值"
                />
                <ProFormDigit
                    name="rewardStarlight"
                    label="奖励星光数量"
                    min={0}
                    fieldProps={{ precision: 0 }}
                    placeholder="0 或留空 = 不发星光"
                />
                <ProFormText
                    name="rewardBadgeCode"
                    label="奖励徽章编码"
                    placeholder="如 badge_spring_2026；留空 = 不发徽章"
                />
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    奖励在活动进行中/结束后由管理员手动发放；uk 幂等保证重复发放自动跳过，全程日志可审计。
                </Paragraph>
            </ModalForm>

            <Drawer
                title={logsRecord ? `奖励发放日志：${logsRecord.title}` : '奖励发放日志'}
                width={640}
                open={logsRecord !== null}
                onClose={() => setLogsRecord(null)}
            >
                <Table<AdminActivityRewardLog>
                    rowKey="id"
                    size="small"
                    loading={logsLoading}
                    dataSource={rewardLogs}
                    pagination={{ pageSize: 10 }}
                    columns={[
                        { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatTime(v) },
                        { title: '用户 ID', dataIndex: 'userId', width: 130 },
                        {
                            title: '奖励类型',
                            dataIndex: 'rewardType',
                            width: 100,
                            render: (v: AdminActivityRewardLog['rewardType']) =>
                                v === 'STARLIGHT' ? <Tag color="gold">星光</Tag> : <Tag color="purple">徽章</Tag>,
                        },
                        { title: '数量', dataIndex: 'amount', width: 80 },
                        { title: '关联徽章 ID', dataIndex: 'refId', render: (v: number | null) => v ?? '-' },
                    ]}
                />
            </Drawer>
        </>
    )
}
