import { useRef, useState } from 'react'
import { ModalForm, ProFormDigit, ProFormSelect, ProFormTextArea, ProFormText, ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Badge, Button, Input, Modal, Popconfirm, Select, Tabs, Tag, Typography } from 'antd'
import { CheckOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons'
import {
    AI_PROMPT_SCENE_LABELS,
    AI_PROMPT_STATUS_LABELS,
    createAdminAiPrompt,
    listAdminAiConfigs,
    listAdminAiPrompts,
    updateAdminAiConfig,
    updateAdminAiPromptStatus,
} from '@/api/admin/wish'
import type { AdminAiConfigRecord, AdminAiPromptRecord, AdminAiPromptScene } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

const { Paragraph, Text } = Typography

/**
 * AI 心愿助手管理（文档 Sprint 2.5 管理后台）：
 * Prompt 模板版本管理（正文不可变，修改须建新版本；同 scene 多条 ACTIVE
 * 按 trafficPercent 加权 A/B 分流；运行时 60s 缓存改后最迟 1 分钟生效）
 * + 提醒策略配置（更新即失效缓存实时生效）。
 */

interface PromptFormValues {
    scene: AdminAiPromptScene
    name: string
    content: string
    abGroup: 'ALL' | 'A' | 'B'
    trafficPercent: number
    remark?: string
}

export default function WishAi() {
    const promptTableRef = useRef<ActionType>(null)
    const message = useMessage()
    const [sceneFilter, setSceneFilter] = useState<AdminAiPromptScene | undefined>(undefined)
    const [createOpen, setCreateOpen] = useState(false)
    const [activePrompt, setActivePrompt] = useState<AdminAiPromptRecord | null>(null)
    const [activeTraffic, setActiveTraffic] = useState(100)
    const [configs, setConfigs] = useState<AdminAiConfigRecord[]>([])
    const [configDrafts, setConfigDrafts] = useState<Record<string, string>>({})
    const [savingKey, setSavingKey] = useState<string | null>(null)

    const loadConfigs = async () => {
        try {
            const res = await listAdminAiConfigs()
            const items = res.data.data ?? []
            setConfigs(items)
            setConfigDrafts(Object.fromEntries(items.map((c) => [c.configKey, c.configValue])))
        } catch {
            // 拦截器已提示
        }
    }

    const handleSaveConfig = async (record: AdminAiConfigRecord) => {
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
            await updateAdminAiConfig(record.configKey, value)
            message.success('已保存并实时生效')
            loadConfigs()
        } catch {
            // 拦截器已提示
        } finally {
            setSavingKey(null)
        }
    }

    /** 激活新版本：二次确认展示与当前生效版本的正文对比 */
    const handleActivate = async (record: AdminAiPromptRecord) => {
        try {
            const res = await listAdminAiPrompts(record.scene)
            const currentActive = (res.data.data ?? []).find(
                (p) => p.status === 'ACTIVE' && p.id !== record.id,
            )
            Modal.confirm({
                title: `激活「${record.name}」v${record.version}？`,
                width: 640,
                content: (
                    <div>
                        <Text strong>当前生效版本{currentActive ? `（v${currentActive.version} ${currentActive.name}）` : '（无）'}：</Text>
                        <Paragraph
                            style={{
                                background: '#f5f5f5',
                                padding: 8,
                                borderRadius: 8,
                                maxHeight: 140,
                                overflow: 'auto',
                                whiteSpace: 'pre-wrap',
                                fontSize: 12,
                            }}
                        >
                            {currentActive?.content ?? '（该场景暂无生效版本）'}
                        </Paragraph>
                        <Text strong>新版本正文：</Text>
                        <Paragraph
                            style={{
                                background: '#f0f9eb',
                                padding: 8,
                                borderRadius: 8,
                                maxHeight: 140,
                                overflow: 'auto',
                                whiteSpace: 'pre-wrap',
                                fontSize: 12,
                            }}
                        >
                            {record.content}
                        </Paragraph>
                        <Text type="warning">
                            激活后按流量百分比 {activeTraffic}% 参与 A/B 分流，最迟 1 分钟生效。
                            {currentActive ? '原生效版本保持 ACTIVE 时与新版本按权重分流。' : ''}
                        </Text>
                    </div>
                ),
                onOk: async () => {
                    try {
                        await updateAdminAiPromptStatus(record.id, {
                            status: 'ACTIVE',
                            trafficPercent: activeTraffic,
                        })
                        message.success('已激活')
                        promptTableRef.current?.reload()
                    } catch {
                        // 拦截器已提示
                    }
                },
            })
        } catch {
            // 拦截器已提示
        }
    }

    const promptColumns: ProColumns<AdminAiPromptRecord>[] = [
        { title: '场景', dataIndex: 'scene', width: 110, render: (_, r) => AI_PROMPT_SCENE_LABELS[r.scene] ?? r.scene },
        { title: '版本', dataIndex: 'version', width: 70, render: (_, r) => `v${r.version}` },
        { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) => (
                <Badge
                    status={r.status === 'ACTIVE' ? 'processing' : r.status === 'DRAFT' ? 'default' : 'warning'}
                    text={AI_PROMPT_STATUS_LABELS[r.status] ?? r.status}
                />
            ),
        },
        { title: 'A/B 分组', dataIndex: 'abGroup', width: 90 },
        { title: '流量 %', dataIndex: 'trafficPercent', width: 80 },
        { title: '变更说明', dataIndex: 'remark', width: 160, ellipsis: true },
        {
            title: '正文',
            dataIndex: 'content',
            width: 200,
            render: (_, r) => (
                <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0, fontSize: 12 }}>
                    {r.content}
                </Typography.Paragraph>
            ),
        },
        {
            title: '操作',
            valueType: 'option',
            width: 150,
            render: (_, r) => [
                r.status === 'DRAFT' && (
                    <Button
                        key="activate"
                        size="small"
                        type="link"
                        icon={<CheckOutlined />}
                        onClick={() => {
                            setActivePrompt(r)
                            setActiveTraffic(r.trafficPercent || 100)
                        }}
                    >
                        激活
                    </Button>
                ),
                r.status === 'ACTIVE' && (
                    <Popconfirm
                        key="archive"
                        title="下线后该版本退出 A/B 分流"
                        onConfirm={async () => {
                            try {
                                await updateAdminAiPromptStatus(r.id, { status: 'ARCHIVED' })
                                message.success('已下线')
                                promptTableRef.current?.reload()
                            } catch {
                                // 拦截器已提示
                            }
                        }}
                    >
                        <Button key="archiveBtn" size="small" type="link" danger icon={<StopOutlined />}>
                            下线
                        </Button>
                    </Popconfirm>
                ),
            ],
        },
    ]

    return (
        <Tabs
            defaultActiveKey="prompts"
            items={[
                {
                    key: 'prompts',
                    label: 'Prompt 管理',
                    children: (
                        <>
                            <div style={{ marginBottom: 12, display: 'flex', gap: 12, alignItems: 'center' }}>
                                <Select
                                    allowClear
                                    placeholder="全部场景"
                                    style={{ width: 180 }}
                                    value={sceneFilter}
                                    onChange={(v?: AdminAiPromptScene) => setSceneFilter(v)}
                                    options={Object.entries(AI_PROMPT_SCENE_LABELS).map(([value, label]) => ({ value, label }))}
                                />
                                <Button
                                    type="primary"
                                    icon={<PlusOutlined />}
                                    onClick={() => setCreateOpen(true)}
                                >
                                    建新版本
                                </Button>
                            </div>
                            <ProTable<AdminAiPromptRecord>
                                headerTitle="Prompt 模板版本（正文不可变，修改须建新版本）"
                                rowKey="id"
                                actionRef={promptTableRef}
                                search={false}
                                options={false}
                                columns={promptColumns}
                                request={() =>
                                    safeProTableRequest<AdminAiPromptRecord>(() => listAdminAiPrompts(sceneFilter))
                                }
                            />
                            <ModalForm<PromptFormValues>
                                title="创建新版本模板"
                                open={createOpen}
                                onOpenChange={setCreateOpen}
                                modalProps={{ destroyOnHidden: true }}
                                initialValues={{ abGroup: 'ALL', trafficPercent: 100 }}
                                onFinish={async (values) => {
                                    try {
                                        await createAdminAiPrompt({
                                            scene: values.scene,
                                            name: values.name,
                                            content: values.content,
                                            abGroup: values.abGroup,
                                            trafficPercent: values.trafficPercent,
                                            remark: values.remark,
                                        })
                                        message.success('已创建（初始为草稿，激活后生效）')
                                        promptTableRef.current?.reload()
                                        return true
                                    } catch {
                                        return false
                                    }
                                }}
                            >
                                <ProFormSelect
                                    name="scene"
                                    label="AI 场景"
                                    rules={[{ required: true, message: '请选择场景' }]}
                                    options={Object.entries(AI_PROMPT_SCENE_LABELS).map(([value, label]) => ({ value, label }))}
                                />
                                <ProFormText
                                    name="name"
                                    label="模板名称"
                                    rules={[{ required: true, message: '请输入名称' }, { max: 100 }]}
                                />
                                <ProFormTextArea
                                    name="content"
                                    label="Prompt 正文（支持 {placeholder} 变量，创建后不可修改）"
                                    rules={[{ required: true, message: '请输入正文' }]}
                                    fieldProps={{ rows: 8 }}
                                />
                                <ProFormSelect
                                    name="abGroup"
                                    label="A/B 分组"
                                    options={[
                                        { value: 'ALL', label: 'ALL（不分流）' },
                                        { value: 'A', label: 'A' },
                                        { value: 'B', label: 'B' },
                                    ]}
                                />
                                <ProFormDigit
                                    name="trafficPercent"
                                    label="流量百分比（1-100，同场景多条 ACTIVE 按此加权分流）"
                                    min={1}
                                    max={100}
                                    fieldProps={{ precision: 0 }}
                                />
                                <ProFormTextArea name="remark" label="版本变更说明" fieldProps={{ rows: 2 }} />
                            </ModalForm>
                            <ModalForm
                                title={`激活「${activePrompt?.name ?? ''}」v${activePrompt?.version ?? ''}（版本对比与二次确认）`}
                                open={!!activePrompt}
                                onOpenChange={(open) => {
                                    if (!open) setActivePrompt(null)
                                }}
                                submitter={{ searchConfig: { submitText: '确认激活' } }}
                                onFinish={async () => {
                                    if (activePrompt) {
                                        await handleActivate(activePrompt)
                                    }
                                    return true
                                }}
                            >
                                <div style={{ marginBottom: 12 }}>
                                    <Text strong>新版本正文预览：</Text>
                                    <Paragraph
                                        style={{
                                            background: '#f0f9eb',
                                            padding: 8,
                                            borderRadius: 8,
                                            maxHeight: 180,
                                            overflow: 'auto',
                                            whiteSpace: 'pre-wrap',
                                            fontSize: 12,
                                        }}
                                    >
                                        {activePrompt?.content}
                                    </Paragraph>
                                </div>
                                <ProFormDigit
                                    name="traffic"
                                    label="流量百分比（1-100）"
                                    min={1}
                                    max={100}
                                    initialValue={100}
                                    fieldProps={{ precision: 0, onChange: (v) => setActiveTraffic(Number(v) || 100) }}
                                />
                            </ModalForm>
                        </>
                    ),
                },
                {
                    key: 'configs',
                    label: '提醒策略配置',
                    children: (
                        <div>
                            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                                配置保存后主动失效缓存、实时生效（陪伴提醒频次/免打扰时段/预期管理限频/年度报告缓存时长）。
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
                                        style={{ width: 220 }}
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
                            {configs.length === 0 && (
                                <Button onClick={loadConfigs}>加载策略配置</Button>
                            )}
                        </div>
                    ),
                },
            ]}
        />
    )
}
