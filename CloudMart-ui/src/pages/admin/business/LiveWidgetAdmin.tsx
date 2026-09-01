import { useEffect, useState } from 'react'
import { Button, InputNumber, Popconfirm, Select, Table, Tag, Typography } from 'antd'
import {
    listAdminLiveWidgetConfigs,
    saveAdminLiveWidgetConfig,
    toggleAdminLiveWidgetVisible,
} from '@/api/admin/wish'
import type { AdminLiveWidgetConfig } from '@/api/admin/wish'
import { useMessage } from '@/utils/useMessage'

const { Text } = Typography

const POSITION_OPTIONS = [
    { value: 'TOP_LEFT', label: '左上' },
    { value: 'TOP_RIGHT', label: '右上' },
    { value: 'BOTTOM_LEFT', label: '左下' },
    { value: 'BOTTOM_RIGHT', label: '右下' },
].map((o) => ({ ...o, label: o.label }))

/**
 * 直播心愿挂件配置（Sprint 3.4 管理后台）：
 * 主播维度 position/style/可见性配置（保存即失效缓存 10s 内生效）；
 * 全局降级开关走灰度控制台 feature=wish_live_widget（比例 0=全局隐藏）。
 */
export default function LiveWidgetAdmin() {
    const message = useMessage()
    const [configs, setConfigs] = useState<AdminLiveWidgetConfig[]>([])
    const [streamerId, setStreamerId] = useState<string>('')
    const [position, setPosition] = useState('BOTTOM_RIGHT')
    const [saving, setSaving] = useState(false)

    const load = async () => {
        try {
            const res = await listAdminLiveWidgetConfigs()
            if (res.data.success) setConfigs(res.data.data ?? [])
        } catch {
            // 拦截器已提示
        }
    }

    useEffect(() => {
        load()
    }, [])

    const handleSave = async () => {
        const sid = Number(streamerId)
        if (!sid) {
            message.warning('请填写主播用户 ID')
            return
        }
        setSaving(true)
        try {
            await saveAdminLiveWidgetConfig(sid, { position, isVisible: true })
            message.success('已保存，10s 内生效')
            load()
        } catch {
            // 拦截器已提示
        } finally {
            setSaving(false)
        }
    }

    return (
        <div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                挂件数据实时聚合主播进行中心愿进度/打卡天数/星光（Redis 缓存 10s，打卡后 10s 内更新）。
                全局降级开关：灰度控制台 → feature「wish_live_widget」比例置 0 → 所有挂件隐藏。
            </Typography.Paragraph>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 16 }}>
                <InputNumber
                    style={{ width: 160 }}
                    placeholder="主播用户 ID"
                    value={streamerId === '' ? undefined : Number(streamerId)}
                    onChange={(v) => setStreamerId(String(v ?? ''))}
                />
                <Select style={{ width: 120 }} value={position} onChange={setPosition} options={POSITION_OPTIONS} />
                <Button type="primary" loading={saving} onClick={handleSave}>保存配置</Button>
            </div>
            <Table<AdminLiveWidgetConfig>
                rowKey={(r) => String(r.streamerId)}
                dataSource={configs}
                pagination={false}
                columns={[
                    { title: '主播 ID', dataIndex: 'streamerId', width: 120 },
                    {
                        title: '位置',
                        dataIndex: 'position',
                        width: 100,
                        render: (_, r) => POSITION_OPTIONS.find((o) => o.value === r.position)?.label ?? r.position,
                    },
                    {
                        title: '样式',
                        dataIndex: 'styleConfig',
                        render: (_, r) => r.styleConfig ?? '-',
                    },
                    {
                        title: '状态',
                        dataIndex: 'isVisible',
                        width: 100,
                        render: (_, r) => (
                            <Tag color={r.isVisible ? 'green' : 'default'}>{r.isVisible ? '展示中' : '已隐藏'}</Tag>
                        ),
                    },
                    {
                        title: '更新时间',
                        dataIndex: 'updatedAt',
                        width: 160,
                        render: (_, r) => <Text style={{ fontSize: 12 }}>{new Date(r.updatedAt).toLocaleString('zh-CN')}</Text>,
                    },
                    {
                        title: '操作',
                        width: 100,
                        render: (_, r) => (
                            <Popconfirm title={`确定隐藏主播 ${r.streamerId} 的挂件吗？`}
                                onConfirm={async () => {
                                    try {
                                        await toggleAdminLiveWidgetVisible(r.streamerId, !r.isVisible)
                                        message.success('已切换')
                                        load()
                                    } catch {
                                        // 拦截器已提示
                                    }
                                }}>
                                <Button size="small">{r.isVisible ? '隐藏' : '恢复'}</Button>
                            </Popconfirm>
                        ),
                    },
                ]}
            />
        </div>
    )
}
