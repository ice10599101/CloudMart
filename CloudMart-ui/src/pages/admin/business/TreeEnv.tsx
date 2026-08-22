import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormSelect, ProFormTextArea } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tabs, Tag, Typography } from 'antd'
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons'
import {
    getAdminEnvConfigs,
    createAdminEnvConfig,
    updateAdminEnvConfig,
    updateAdminEnvConfigStatus,
    getAdminSpecialEvents,
    triggerAdminSpecialEvent,
    endAdminSpecialEvent,
    ENV_CATEGORY_MAP,
    ENV_PARTICLE_MAP,
} from '@/api/admin/wish'
import type { AdminEnvConfigRecord, AdminEnvParticle, AdminSpecialEventRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 生命树环境管理（文档 Sprint 2.2：特殊事件触发台 + 环境配置管理）。
 *
 * 环境配置表化：新增"中秋"等环境仅插入配置行，四端渲染不改代码。
 * visual 采用结构化表单（天空/树冠/树心三色 + 粒子）而非裸 JSON
 * 文本域（参照徽章 condition 模式），提交时序列化为 JSON 字符串，
 * Service 层结构校验兜底。
 */

interface EnvConfigFormValues {
    envCode: string
    category: keyof typeof ENV_CATEGORY_MAP
    name: string
    description?: string
    priority: number
    skyColor?: string
    crownColor?: string
    lightCoreColor?: string
    particle?: AdminEnvParticle
}

interface TriggerEventFormValues {
    eventCode: string
    title?: string
    description?: string
    durationMinutes?: number
}

/** 序列化 visual：仅保留填写字段，全空时提交空对象（清空渲染参数） */
function serializeVisual(values: EnvConfigFormValues): string {
    const visual: Record<string, string> = {}
    if (values.skyColor) visual.skyColor = values.skyColor
    if (values.crownColor) visual.crownColor = values.crownColor
    if (values.lightCoreColor) visual.lightCoreColor = values.lightCoreColor
    if (values.particle) visual.particle = values.particle
    return JSON.stringify(visual)
}

const COLOR_RULES = [
    { pattern: /^#([0-9a-fA-F]{6})$/, message: '格式为 #rrggbb，如 #0c1b3a' },
]

function formatTime(value: string | null): string {
    return value ? new Date(value.includes('T') ? `${value}Z` : value).toLocaleString() : '-'
}

/** 特殊事件触发台 + 历史记录 */
function SpecialEventPanel() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const [triggerVisible, setTriggerVisible] = useState(false)
    const [eventOptions, setEventOptions] = useState<{ value: string; label: string }[]>([])
    const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

    const loadEventOptions = async () => {
        const configs = await getAdminEnvConfigs()
        const options = (configs.data.data ?? [])
            .filter((item) => item.category === 'SPECIAL_EVENT' && item.isActive)
            .map((item) => ({ value: item.envCode, label: `${item.name}（${item.envCode}）` }))
        setEventOptions(options)
        return options
    }

    const handleTrigger = async (values: TriggerEventFormValues) => {
        return confirmSubmit(async () => {
            const res = await triggerAdminSpecialEvent({
                eventCode: values.eventCode,
                title: values.title,
                description: values.description,
                durationMinutes: values.durationMinutes,
            })
            message.success(`事件已触发（#${res.data.data?.id ?? ''}），四端轮询后同步展示`)
            actionRef.current?.reload()
        })
    }

    const handleEnd = async (record: AdminSpecialEventRecord) => {
        await endAdminSpecialEvent(record.id)
        message.success('事件已结束，全站恢复常规环境展示')
        actionRef.current?.reload()
    }

    const columns: ProColumns<AdminSpecialEventRecord>[] = [
        { title: 'ID', dataIndex: 'id', width: 150, search: false, ellipsis: true },
        { title: '事件代码', dataIndex: 'eventCode', width: 140, search: false },
        { title: '标题', dataIndex: 'title', width: 140, search: false },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            search: false,
            render: (_, record) =>
                record.status === 'ACTIVE' ? <Tag color="processing">进行中</Tag> : <Tag color="default">已结束</Tag>,
        },
        { title: '触发时间(UTC)', dataIndex: 'triggeredAt', width: 170, search: false, render: (_, r) => formatTime(r.triggeredAt) },
        {
            title: '结束时间(UTC)',
            dataIndex: 'expiresAt',
            width: 170,
            search: false,
            render: (_, record) =>
                record.expiresAt ? formatTime(record.expiresAt) : <Typography.Text type="secondary">手动结束</Typography.Text>,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 110,
            fixed: 'right',
            render: (_, record) =>
                record.status === 'ACTIVE'
                    ? [
                        <Popconfirm key="end" title="确认结束？全站将恢复常规环境展示" onConfirm={() => handleEnd(record)}>
                            <Button type="link" size="small" danger>
                                手动结束
                            </Button>
                        </Popconfirm>,
                    ]
                    : [<Typography.Text key="none" type="secondary">-</Typography.Text>],
        },
    ]

    return (
        <>
            <ProTable<AdminSpecialEventRecord>
                headerTitle="特殊事件历史"
                actionRef={actionRef}
                rowKey="id"
                search={false}
                scroll={{ x: 950 }}
                request={async () => {
                    return safeProTableRequest<AdminSpecialEventRecord>(() => getAdminSpecialEvents())
                }}
                toolBarRender={() => [
                    <Button
                        key="trigger"
                        type="primary"
                        icon={<ThunderboltOutlined />}
                        onClick={async () => {
                            const options = await loadEventOptions()
                            if (options.length === 0) {
                                message.warning('无已启用的特殊事件配置，请先在「环境配置」中启用')
                                return
                            }
                            setTriggerVisible(true)
                        }}
                    >
                        触发特殊事件
                    </Button>,
                ]}
                columns={columns}
                pagination={false}
            />

            <ModalForm<TriggerEventFormValues>
                title="触发全站特殊事件"
                open={triggerVisible}
                onOpenChange={createHandleOpenChange(setTriggerVisible)}
                onFinish={handleTrigger}
                modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
                width={520}
            >
                <ProFormSelect
                    name="eventCode"
                    label="事件"
                    options={eventOptions}
                    rules={[{ required: true, message: '请选择事件' }]}
                    extra="仅列出已启用的特殊事件配置；触发后自动结束当前活跃事件（单活跃语义）"
                />
                <ProFormText
                    name="title"
                    label="事件标题"
                    placeholder="默认取配置名称"
                    rules={[{ max: 64, message: '标题不能超过64字符' }]}
                />
                <ProFormTextArea
                    name="description"
                    label="事件描述"
                    placeholder="展示给全站用户的说明，可留空"
                    max={255}
                    fieldProps={{ rows: 2, showCount: true }}
                />
                <ProFormDigit
                    name="durationMinutes"
                    label="持续分钟数"
                    placeholder="留空 = 持续至手动结束"
                    min={1}
                    max={525600}
                    fieldProps={{ precision: 0 }}
                    extra="到期自动结束并恢复常规环境"
                />
            </ModalForm>
        </>
    )
}

/** 环境配置管理（表化 CRUD + 上下架） */
function EnvConfigPanel() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const [modalVisible, setModalVisible] = useState(false)
    const [editingRecord, setEditingRecord] = useState<AdminEnvConfigRecord | null>(null)
    const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

    const handleSubmit = async (values: EnvConfigFormValues) => {
        const visual = serializeVisual(values)
        const payload = {
            envCode: editingRecord ? editingRecord.envCode : values.envCode,
            category: values.category,
            name: values.name,
            description: values.description,
            priority: values.priority,
            visual,
        }
        return confirmSubmit(async () => {
            if (editingRecord) {
                await updateAdminEnvConfig(editingRecord.id, payload)
                message.success('更新成功')
            } else {
                await createAdminEnvConfig(payload)
                message.success('创建成功')
            }
            setEditingRecord(null)
            actionRef.current?.reload()
        })
    }

    const handleToggleStatus = async (record: AdminEnvConfigRecord) => {
        await updateAdminEnvConfigStatus(record.id, !record.isActive)
        message.success(record.isActive ? '已下架（公开配置不返回，事件触发校验失败）' : '已上架')
        actionRef.current?.reload()
    }

    const columns: ProColumns<AdminEnvConfigRecord>[] = [
        { title: 'ID', dataIndex: 'id', width: 150, search: false, ellipsis: true },
        { title: '代码', dataIndex: 'envCode', width: 140, search: false },
        {
            title: '分类',
            dataIndex: 'category',
            width: 100,
            search: false,
            render: (_, record) => {
                const category = ENV_CATEGORY_MAP[record.category]
                return category ? <Tag color={category.color}>{category.label}</Tag> : record.category
            },
        },
        { title: '名称', dataIndex: 'name', width: 110, search: false },
        {
            title: '优先级',
            dataIndex: 'priority',
            width: 80,
            search: false,
            render: (_, record) => <Typography.Text code>{record.priority}</Typography.Text>,
        },
        {
            title: '渲染参数',
            dataIndex: 'visual',
            width: 240,
            search: false,
            ellipsis: true,
            render: (_, record) => {
                if (!record.visual) return <Typography.Text type="secondary">默认</Typography.Text>
                const parts: string[] = []
                if (record.visual.skyColor) parts.push(`天空 ${record.visual.skyColor}`)
                if (record.visual.crownColor) parts.push(`树冠 ${record.visual.crownColor}`)
                if (record.visual.lightCoreColor) parts.push(`树心 ${record.visual.lightCoreColor}`)
                if (record.visual.particle) {
                    const particle = ENV_PARTICLE_MAP[record.visual.particle]
                    parts.push(particle ? particle.label : record.visual.particle)
                }
                return parts.length > 0 ? (
                    <Typography.Text type="secondary">{parts.join(' · ')}</Typography.Text>
                ) : (
                    <Typography.Text type="secondary">默认</Typography.Text>
                )
            },
        },
        {
            title: '状态',
            dataIndex: 'isActive',
            width: 90,
            search: false,
            render: (_, record) =>
                record.isActive ? <Tag color="success">启用</Tag> : <Tag color="default">已停用</Tag>,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 150,
            fixed: 'right',
            render: (_, record) => [
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
                </Button>,
                <Popconfirm
                    key="toggle"
                    title={record.isActive ? '确认停用？公开配置不返回该环境' : '确认启用？'}
                    onConfirm={() => handleToggleStatus(record)}
                >
                    <Button type="link" size="small" danger={record.isActive}>
                        {record.isActive ? '停用' : '启用'}
                    </Button>
                </Popconfirm>,
            ],
        },
    ]

    return (
        <>
            <ProTable<AdminEnvConfigRecord>
                headerTitle="环境配置"
                actionRef={actionRef}
                rowKey="id"
                search={false}
                scroll={{ x: 1100 }}
                request={async () => {
                    return safeProTableRequest<AdminEnvConfigRecord>(() => getAdminEnvConfigs())
                }}
                toolBarRender={() => [
                    <Button
                        key="add"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => {
                            setEditingRecord(null)
                            setModalVisible(true)
                        }}
                    >
                        新增环境
                    </Button>,
                ]}
                columns={columns}
                pagination={false}
            />

            <ModalForm<EnvConfigFormValues>
                title={editingRecord ? `编辑环境：${editingRecord.envCode}` : '新增环境'}
                open={modalVisible}
                onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
                onFinish={handleSubmit}
                initialValues={
                    editingRecord
                        ? {
                            envCode: editingRecord.envCode,
                            category: editingRecord.category,
                            name: editingRecord.name,
                            description: editingRecord.description ?? undefined,
                            priority: editingRecord.priority,
                            skyColor: editingRecord.visual?.skyColor,
                            crownColor: editingRecord.visual?.crownColor,
                            lightCoreColor: editingRecord.visual?.lightCoreColor,
                            particle: editingRecord.visual?.particle,
                        }
                        : { category: 'SPECIAL_EVENT', priority: 50, particle: 'NONE' }
                }
                modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
                width={560}
            >
                {!editingRecord && (
                    <ProFormText
                        name="envCode"
                        label="环境代码"
                        placeholder="唯一编码，如 MID_AUTUMN"
                        rules={[
                            { required: true, message: '请输入环境代码' },
                            { max: 48, message: '代码不能超过48字符' },
                            { pattern: /^[A-Z][A-Z0-9_]*$/, message: '大写字母开头，仅大写字母/数字/下划线' },
                        ]}
                        extra="创建后不可修改（天气/季节/事件链路关联键）"
                    />
                )}
                <ProFormSelect
                    name="category"
                    label="环境分类"
                    options={Object.entries(ENV_CATEGORY_MAP).map(([value, { label }]) => ({ value, label }))}
                    rules={[{ required: true, message: '请选择环境分类' }]}
                />
                <ProFormText
                    name="name"
                    label="环境名称"
                    placeholder="如 中秋"
                    rules={[
                        { required: true, message: '请输入环境名称' },
                        { max: 64, message: '名称不能超过64字符' },
                    ]}
                />
                <ProFormTextArea
                    name="description"
                    label="环境描述"
                    placeholder="可留空"
                    max={255}
                    fieldProps={{ rows: 2, showCount: true }}
                />
                <ProFormDigit
                    name="priority"
                    label="渲染优先级"
                    placeholder="数值大者胜：特殊事件100/情绪80/天气50/季节30/时段10"
                    min={0}
                    max={999}
                    fieldProps={{ precision: 0 }}
                    rules={[{ required: true, message: '请输入渲染优先级' }]}
                />
                <ProFormText name="skyColor" label="天空色" placeholder="#0c1b3a" rules={COLOR_RULES} />
                <ProFormText name="crownColor" label="树冠色" placeholder="#7ef0c0" rules={COLOR_RULES} />
                <ProFormText name="lightCoreColor" label="树心光色" placeholder="#ffd700" rules={COLOR_RULES} />
                <ProFormSelect
                    name="particle"
                    label="粒子效果"
                    options={Object.entries(ENV_PARTICLE_MAP).map(([value, { label }]) => ({ value, label }))}
                    extra="天空色缺省时回退时段色；粒子为 NONE 时回退季节粒子"
                />
            </ModalForm>
        </>
    )
}

export default function TreeEnv() {
    return (
        <Tabs
            defaultActiveKey="events"
            items={[
                { key: 'events', label: '特殊事件触发台', children: <SpecialEventPanel /> },
                { key: 'configs', label: '环境配置管理', children: <EnvConfigPanel /> },
            ]}
        />
    )
}
