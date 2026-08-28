import { useEffect, useState } from 'react'
import { Badge, Button, Descriptions, Input, InputNumber, Popconfirm, Statistic, Tabs, Tag, Typography } from 'antd'
import {
    auditAdminWarmEvent,
    createAdminFence,
    deleteAdminFence,
    getAdminMapAudit,
    listAdminFences,
    listAdminSuspicious,
    listAdminFreezes,
    listAdminWarmEvents,
    toggleAdminFence,
    unfreezeAdminUser,
    updateAdminFence,
} from '@/api/admin/wish'
import type {
    AdminFenceRecord,
    AdminLbsFreeze,
    AdminLbsSuspicious,
    AdminMapAudit,
    AdminWarmEventRecord,
} from '@/api/admin/wish'
import { useMessage } from '@/utils/useMessage'

const { Paragraph, Text } = Typography

/**
 * LBS 管理（Sprint 3.1 隐私审计面板 + Sprint 3.2 围栏管理/温暖事件审核）：
 * 围栏中心坐标仅服务端存储（geohash7），用户端 API 永不回传；
 * 温暖事件 DFA 命中敏感词自动隐藏，此处恢复/驳回。
 */
export default function MapAdmin() {
    const message = useMessage()
    const [audit, setAudit] = useState<AdminMapAudit | null>(null)
    const [loading, setLoading] = useState(true)
    const [fences, setFences] = useState<AdminFenceRecord[]>([])
    const [events, setEvents] = useState<AdminWarmEventRecord[]>([])
    const [suspicious, setSuspicious] = useState<AdminLbsSuspicious[]>([])
    const [freezes, setFreezes] = useState<AdminLbsFreeze[]>([])
    const [fenceForm, setFenceForm] = useState({
        name: '',
        wishId: '',
        centerLat: '23.1059',
        centerLng: '113.3236',
        radiusM: '100',
    })

    const load = async () => {
        setLoading(true)
        try {
            const res = await getAdminMapAudit()
            if (res.data.success) setAudit(res.data.data)
        } catch {
            // 拦截器已提示
        } finally {
            setLoading(false)
        }
    }

    const loadFences = async () => {
        try {
            const res = await listAdminFences()
            if (res.data.success) setFences(res.data.data ?? [])
        } catch {
            // 拦截器已提示
        }
    }

    const loadEvents = async () => {
        try {
            const res = await listAdminWarmEvents({ page: 1, size: 50 })
            if (res.data.success) setEvents(res.data.data ?? [])
        } catch {
            // 拦截器已提示
        }
    }

    const loadRisk = async () => {
        try {
            const [suspRes, freezeRes] = await Promise.all([listAdminSuspicious(), listAdminFreezes()])
            if (suspRes.data.success) setSuspicious(suspRes.data.data ?? [])
            if (freezeRes.data.success) setFreezes(freezeRes.data.data ?? [])
        } catch {
            // 拦截器已提示
        }
    }

    useEffect(() => {
        load()
        loadFences()
        loadEvents()
        loadRisk()
    }, [])

    const handleCreateFence = async () => {
        if (!fenceForm.name || !fenceForm.wishId) {
            message.warning('名称与心愿 ID 必填')
            return
        }
        try {
            await createAdminFence({
                name: fenceForm.name,
                wishId: Number(fenceForm.wishId),
                centerLat: Number(fenceForm.centerLat),
                centerLng: Number(fenceForm.centerLng),
                radiusM: Number(fenceForm.radiusM),
            })
            message.success('围栏已创建')
            loadFences()
        } catch {
            // 拦截器已提示
        }
    }

    const auditPanel = (
        <div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 24 }}>
                隐私审计依据（文档 3.1 隐私审计清单）：DB 仅存 wish.geohash（geohash7，约 153m 网格），
                无 lat/lng 原始坐标列；展示坐标为网格中心 + wishId 种子确定性偏移（0-50m）；
                日志全链路不打印原始坐标。
            </Typography.Paragraph>
            {loading ? (
                <Text type="secondary">加载中...</Text>
            ) : audit ? (
                <>
                    <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap', marginBottom: 24 }}>
                        <Statistic title="公开心愿总数" value={audit.publicWishCount} />
                        <Statistic title="已具备 geohash" value={audit.geohashCovered} />
                        <Statistic
                            title="缺 geohash（3.1 前创建）"
                            value={audit.geohashMissing}
                            valueStyle={{ color: audit.geohashMissing > 0 ? '#ffb347' : undefined }}
                        />
                        <Statistic title="覆盖网格（geohash6）" value={audit.distinctCell6} />
                    </div>
                    <Descriptions column={1} bordered size="small">
                        {Object.entries(audit.strategy).map(([key, text]) => (
                            <Descriptions.Item key={key} label={key}>
                                {text}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                    <Paragraph type="secondary" style={{ marginTop: 16, fontSize: 12 }}>
                        geohashMissing 为 Phase 1/2 期间创建的心愿（当时无坐标采集），新发布 PUBLIC
                        心愿携带定位后会自动覆盖；缺失心愿不出现在附近地图（不影响其他功能）。
                    </Paragraph>
                </>
            ) : (
                <Button onClick={load}>重新加载</Button>
            )}
        </div>
    )

    const fencePanel = (
        <div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                围栏中心坐标仅服务端存储（geohash7），用户端 API 永不回传；半径最小 10m（半径 0 拒绝）；
                到达触发绑定心愿绽放（每围栏每用户每日幂等）。
            </Typography.Paragraph>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginBottom: 16 }}>
                <Input style={{ width: 140 }} placeholder="围栏名称" value={fenceForm.name}
                    onChange={(e) => setFenceForm((p) => ({ ...p, name: e.target.value }))} />
                <Input style={{ width: 180 }} placeholder="心愿 ID" value={fenceForm.wishId}
                    onChange={(e) => setFenceForm((p) => ({ ...p, wishId: e.target.value }))} />
                <InputNumber style={{ width: 110 }} placeholder="纬度" value={fenceForm.centerLat === '' ? undefined : Number(fenceForm.centerLat)}
                    onChange={(v) => setFenceForm((p) => ({ ...p, centerLat: String(v ?? '') }))} />
                <InputNumber style={{ width: 110 }} placeholder="经度" value={fenceForm.centerLng === '' ? undefined : Number(fenceForm.centerLng)}
                    onChange={(v) => setFenceForm((p) => ({ ...p, centerLng: String(v ?? '') }))} />
                <InputNumber style={{ width: 100 }} placeholder="半径(m)" value={fenceForm.radiusM === '' ? undefined : Number(fenceForm.radiusM)} min={10}
                    onChange={(v) => setFenceForm((p) => ({ ...p, radiusM: String(v ?? '') }))} />
                <Button type="primary" onClick={handleCreateFence}>创建围栏</Button>
            </div>
            {fences.map((fence) => (
                <div key={fence.id} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
                    <Text strong>{fence.name}</Text>
                    <Tag color={fence.isActive ? 'green' : 'default'}>{fence.isActive ? '启用' : '停用'}</Tag>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                        心愿 {fence.wishId} · 半径 {fence.radiusM}m · 中心 {fence.centerGeohash}（仅管理端可见）
                    </Text>
                    <Popconfirm title={`确定${fence.isActive ? '停用' : '启用'}该围栏吗？`}
                        onConfirm={async () => {
                            try {
                                await toggleAdminFence(fence.id, !fence.isActive)
                                loadFences()
                            } catch {
                                // 拦截器已提示
                            }
                        }}>
                        <Button size="small">{fence.isActive ? '停用' : '启用'}</Button>
                    </Popconfirm>
                    <Popconfirm title="确定删除该围栏吗？"
                        onConfirm={async () => {
                            try {
                                await deleteAdminFence(fence.id)
                                message.success('已删除')
                                loadFences()
                            } catch {
                                // 拦截器已提示
                            }
                        }}>
                        <Button size="small" danger>删除</Button>
                    </Popconfirm>
                </div>
            ))}
            {fences.length === 0 && <Text type="secondary">暂无围栏</Text>}
        </div>
    )

    const eventPanel = (
        <div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                UGC 审核：DFA 命中敏感词的事件已自动隐藏（AUTO_HIDDEN）；此处可恢复/驳回。
            </Typography.Paragraph>
            {events.map((event) => (
                <div key={event.id} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
                    <Badge status={event.isVisible ? 'success' : 'error'} />
                    <div style={{ width: 320 }}>
                        <Text strong>{event.title}</Text>
                        <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>{event.content}</Text>
                        </div>
                    </div>
                    <Tag>{event.auditStatus}</Tag>
                    <Button size="small" type="link" onClick={async () => {
                        try {
                            await auditAdminWarmEvent(event.id, 'APPROVED')
                            loadEvents()
                        } catch {
                            // 拦截器已提示
                        }
                    }}>通过</Button>
                    <Button size="small" type="link" danger onClick={async () => {
                        try {
                            await auditAdminWarmEvent(event.id, 'REJECTED')
                            loadEvents()
                        } catch {
                            // 拦截器已提示
                        }
                    }}>驳回</Button>
                </div>
            ))}
            {events.length === 0 && <Text type="secondary">暂无事件</Text>}
        </div>
    )

    const riskPanel = (
        <div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                位置伪造检测：速度 &gt;15km/h 的跳跃记可疑（交通枢纽放宽），24h 内连续 3 次 → 冻结 24h。
                解冻请二次确认。轨迹 Redis TTL 25h 自动过期，清理任务每小时兜底统计。
            </Typography.Paragraph>
            <Text strong>冻结中的用户（{freezes.length}）</Text>
            <div style={{ margin: '8px 0 20px' }}>
                {freezes.map((f) => (
                    <div key={f.id} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
                        <Badge status="error" />
                        <Text strong>用户 {f.userId}</Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>{f.reason}</Text>
                        <Tag>冻结至 {new Date(f.frozenUntil).toLocaleString('zh-CN')}</Tag>
                        <Popconfirm title={`确定解冻用户 ${f.userId} 吗？`}
                            onConfirm={async () => {
                                try {
                                    await unfreezeAdminUser(f.userId)
                                    message.success('已解冻')
                                    loadRisk()
                                } catch {
                                    // 拦截器已提示
                                }
                            }}>
                            <Button size="small" type="primary" ghost>解冻</Button>
                        </Popconfirm>
                    </div>
                ))}
                {freezes.length === 0 && <Text type="secondary">暂无冻结用户</Text>}
            </div>
            <Text strong>可疑跳跃记录（{suspicious.length}）</Text>
            <div style={{ marginTop: 8 }}>
                {suspicious.map((s) => (
                    <div key={s.id} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginBottom: 6 }}>
                        <Tag color="red">{s.speedKmh} km/h</Tag>
                        <Text style={{ fontSize: 12 }}>用户 {s.userId}</Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                            {s.fromCell} → {s.toCell} · {new Date(s.createdAt).toLocaleString('zh-CN')}
                        </Text>
                    </div>
                ))}
                {suspicious.length === 0 && <Text type="secondary">暂无可疑记录</Text>}
            </div>
        </div>
    )

    return (
        <Tabs
            defaultActiveKey="audit"
            items={[
                { key: 'audit', label: '隐私审计', children: auditPanel },
                { key: 'fences', label: '围栏管理', children: fencePanel },
                { key: 'events', label: '温暖事件审核', children: eventPanel },
                { key: 'risk', label: 'LBS 风控', children: riskPanel },
            ]}
        />
    )
}
