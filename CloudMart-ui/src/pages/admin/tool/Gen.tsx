import { useRef, useState } from 'react'
import type { FormInstance } from 'antd'
import {
    ProTable,
    ModalForm,
    ProFormText,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Drawer, Modal, Space, Table, Tabs, Tag, Typography } from 'antd'
import { EyeOutlined, DownloadOutlined, TableOutlined } from '@ant-design/icons'
import {
    getGenTables,
    getGenTableDetail,
    previewGenCode,
    downloadGenCode,
} from '@/api/admin/tool'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * 代码生成工作台（后端 mall-gen 服务，网关 /api/gen/**）。
 *
 * 能力：库表浏览 / 表结构详情（列→Java 字段映射）/ 生成配置
 * （包名/模块/业务名/前缀去除）/ 代码预览（按模板分组）/ 打包下载。
 */

const { Text } = Typography

interface GenTableRecord {
    tableName: string
    tableComment: string
    createTime: string
    updateTime: string | null
}

interface GenColumnRecord {
    columnName: string
    columnComment: string
    columnType: string
    columnKey: string
    isNullable: string
    columnDefault: string | null
    extra: string
    ordinalPosition: number
    javaType: string
    javaField: string
}

interface GenPreviewFile {
    templateName: string
    fileName: string
    content: string
}

/** 生成配置（与后端 GenConfigRequest 对齐；tableName 由所选行带入） */
interface GenConfigFormValues {
    packageName?: string
    moduleName?: string
    businessName?: string
    functionName?: string
    tablePrefix?: string
}

export default function Gen() {
    const message = useMessage()
    const actionRef = useRef<ActionType>(null)
    const configFormRef = useRef<FormInstance<GenConfigFormValues>>(undefined as unknown as FormInstance<GenConfigFormValues>)
    const [keyword, setKeyword] = useState<string | undefined>()
    const [configTable, setConfigTable] = useState<string | null>(null)
    const [detailTable, setDetailTable] = useState<string | null>(null)
    const [detailColumns, setDetailColumns] = useState<GenColumnRecord[]>([])
    const [detailLoading, setDetailLoading] = useState(false)
    const [previewVisible, setPreviewVisible] = useState(false)
    const [previewTable, setPreviewTable] = useState<string | null>(null)
    const [previewFiles, setPreviewFiles] = useState<GenPreviewFile[]>([])
    const [previewLoading] = useState(false)

    const openDetail = async (tableName: string) => {
        setDetailTable(tableName)
        setDetailColumns([])
        setDetailLoading(true)
        try {
            const { data: res } = await getGenTableDetail(tableName)
            const response = res as ApiResponse<{ columns: GenColumnRecord[] }>
            setDetailColumns(response.data?.columns ?? [])
        } finally {
            setDetailLoading(false)
        }
    }

    const buildConfig = (values: GenConfigFormValues) => ({
        tableName: configTable,
        packageName: values.packageName?.trim() || undefined,
        moduleName: values.moduleName?.trim() || undefined,
        businessName: values.businessName?.trim() || undefined,
        functionName: values.functionName?.trim() || undefined,
        tablePrefix: values.tablePrefix?.trim() || undefined,
    })

    const handlePreview = async (values: GenConfigFormValues) => {
        try {
            const { data: res } = await previewGenCode(buildConfig(values))
            const response = res as ApiResponse<GenPreviewFile[]>
            const files = response.data ?? []
            if (files.length === 0) {
                message.warning('未生成任何模板文件')
                return false
            }
            setPreviewFiles(files)
            setPreviewTable(configTable)
            setPreviewVisible(true)
            return true
        } catch {
            // 错误已由 request 拦截器提示
            return false
        }
    }

    const handleDownload = async (values: GenConfigFormValues) => {
        try {
            const response = await downloadGenCode(buildConfig(values))
            const blob = response.data as Blob
            // 失败时后端返回 JSON 信封（Content-Type application/json）
            if (blob.type.includes('application/json')) {
                const text = await blob.text()
                try {
                    const envelope = JSON.parse(text) as { error?: { message?: string } }
                    message.error(envelope.error?.message || '生成失败')
                } catch {
                    message.error('生成失败')
                }
                return
            }
            const url = window.URL.createObjectURL(blob)
            const link = document.createElement('a')
            link.href = url
            link.download = `${configTable}.zip`
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            window.URL.revokeObjectURL(url)
            message.success(`已下载 ${configTable}.zip`)
        } catch {
            message.error('下载失败')
        }
    }

    const columns: ProColumns<GenTableRecord>[] = [
        {
            title: '表名',
            dataIndex: 'tableName',
            width: 260,
            ellipsis: true,
            render: (_, record) => (
                <Space size={4}>
                    <TableOutlined style={{ color: 'var(--color-primary)' }} />
                    <Text copyable={{ text: record.tableName }}>{record.tableName}</Text>
                </Space>
            ),
        },
        {
            title: '表注释',
            dataIndex: 'tableComment',
            width: 240,
            ellipsis: true,
            render: (_, record) => record.tableComment || <Text type="secondary">-</Text>,
        },
        { title: '创建时间', dataIndex: 'createTime', width: 170, valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            width: 220,
            fixed: 'right',
            render: (_, record) => [
                <Button
                    key="detail"
                    type="link"
                    size="small"
                    icon={<TableOutlined />}
                    onClick={() => openDetail(record.tableName)}
                >
                    表结构
                </Button>,
                <Button
                    key="gen"
                    type="link"
                    size="small"
                    icon={<EyeOutlined />}
                    onClick={() => setConfigTable(record.tableName)}
                >
                    生成代码
                </Button>,
            ],
        },
    ]

    const previewTabs = Object.entries(
        previewFiles.reduce<Record<string, GenPreviewFile[]>>((acc, file) => {
            const key = file.templateName.split('/').pop() ?? file.templateName
            if (!acc[key]) acc[key] = []
            acc[key].push(file)
            return acc
        }, {}),
    ).map(([template, files]) => ({
        key: template,
        label: template,
        children: (
            <div style={{ maxHeight: 480, overflow: 'auto' }}>
                {files.map((file) => (
                    <div key={file.fileName} style={{ marginBottom: 16 }}>
                        <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--color-primary)' }}>
                            {file.fileName}
                        </div>
                        <pre
                            style={{
                                background: 'var(--color-bg-input)',
                                padding: 16,
                                borderRadius: 6,
                                fontSize: 13,
                                lineHeight: 1.6,
                                overflow: 'auto',
                                margin: 0,
                            }}
                        >
                            <code>{file.content}</code>
                        </pre>
                    </div>
                ))}
            </div>
        ),
    }))

    return (
        <>
            <ProTable<GenTableRecord>
                headerTitle="库表浏览"
                actionRef={actionRef}
                rowKey="tableName"
                search={false}
                options={{ reload: true, density: false, setting: false }}
                request={async (params) => {
                    // 后端返回全量表清单（不支持分页/搜索参数），此处做客户端过滤 + 分页
                    const result = await safeProTableRequest<GenTableRecord>(() => getGenTables())
                    const kw = (params.tableName ?? keyword ?? '').trim().toLowerCase()
                    const filtered = kw
                        ? result.data.filter(
                            (r) => r.tableName.toLowerCase().includes(kw) || (r.tableComment ?? '').toLowerCase().includes(kw),
                        )
                        : result.data
                    const current = params.current ?? 1
                    const pageSize = params.pageSize ?? 10
                    return {
                        data: filtered.slice((current - 1) * pageSize, current * pageSize),
                        total: filtered.length,
                        success: result.success,
                    }
                }}
                params={{ tableName: keyword }}
                toolbar={{ search: {
                    placeholder: '搜索表名 / 注释',
                    onSearch: (v) => { setKeyword(v || undefined); actionRef.current?.reload() },
                } }}
                columns={columns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 张表` }}
            />

            <ModalForm<GenConfigFormValues>
                title={`生成代码：${configTable ?? ''}`}
                open={configTable !== null}
                onOpenChange={(open) => { if (!open) setConfigTable(null) }}
                onFinish={handlePreview}
                formRef={configFormRef}
                submitter={{
                    render: (_, dom) => [
                        ...dom,
                        <Button key="download" type="primary" ghost icon={<DownloadOutlined />}
                            onClick={async () => {
                                const values = await configFormRef.current?.validateFields()
                                if (values) await handleDownload(values as GenConfigFormValues)
                            }}
                        >
                            打包下载
                        </Button>,
                    ],
                }}
                modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
            >
                <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
                    目标表：<Text strong>{configTable}</Text>；生成 Java 实体/Mapper/Service/Controller 脚手架并打包为 zip。
                </Typography.Paragraph>
                <ProFormText name="packageName" label="包名" placeholder="默认 com.cloudmart（服务配置 gen.package-name）" />
                <ProFormText name="moduleName" label="模块名" placeholder="如 admin / wish" />
                <ProFormText name="businessName" label="业务名" placeholder="如 user / wish" />
                <ProFormText name="functionName" label="功能名" placeholder="如 用户管理" />
                <ProFormText name="tablePrefix" label="去除表前缀" placeholder="如 t_ / sys_" />
            </ModalForm>

            <Drawer
                title={`表结构：${detailTable ?? ''}`}
                width={860}
                open={detailTable !== null}
                onClose={() => setDetailTable(null)}
            >
                <Table<GenColumnRecord>
                    rowKey="columnName"
                    size="small"
                    loading={detailLoading}
                    dataSource={detailColumns}
                    pagination={false}
                    columns={[
                        { title: '#', dataIndex: 'ordinalPosition', width: 48 },
                        {
                            title: '列名',
                            dataIndex: 'columnName',
                            width: 170,
                            render: (v: string, r) => (
                                <Space size={4}>
                                    <Text copyable={{ text: v }}>{v}</Text>
                                    {r.columnKey === 'PRI' && <Tag color="gold">PK</Tag>}
                                </Space>
                            ),
                        },
                        { title: '数据库类型', dataIndex: 'columnType', width: 130 },
                        { title: 'Java 字段', dataIndex: 'javaField', width: 130 },
                        { title: 'Java 类型', dataIndex: 'javaType', width: 110 },
                        { title: '可空', dataIndex: 'isNullable', width: 70, render: (v: string) => (v === 'YES' ? '是' : '否') },
                        { title: '默认值', dataIndex: 'columnDefault', width: 100, render: (v: string | null) => v ?? '-' },
                        { title: '备注', dataIndex: 'columnComment', ellipsis: true },
                    ]}
                />
            </Drawer>

            <Modal
                title={`代码预览：${previewTable ?? ''}`}
                open={previewVisible}
                onCancel={() => {
                    setPreviewVisible(false)
                    setPreviewFiles([])
                }}
                footer={null}
                width={960}
                destroyOnHidden
            >
                {previewLoading ? (
                    <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
                ) : (
                    <Tabs items={previewTabs} />
                )}
            </Modal>
        </>
    )
}

