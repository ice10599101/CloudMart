import { useEffect, useState } from 'react'
import { Button, Descriptions, Statistic, Typography } from 'antd'
import { getAdminMapAudit } from '@/api/admin/wish'
import type { AdminMapAudit } from '@/api/admin/wish'
import { useMessage } from '@/utils/useMessage'

const { Paragraph, Text } = Typography

/**
 * LBS 隐私审计面板（Sprint 3.1 管理后台）：
 * PUBLIC 心愿 geohash 覆盖统计 + 模糊化策略说明（存储/偏移/聚合/日志
 * 四环节审计依据，文档 3.1 隐私审计清单）。
 */
export default function MapAdmin() {
    const message = useMessage()
    const [audit, setAudit] = useState<AdminMapAudit | null>(null)
    const [loading, setLoading] = useState(true)

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

    useEffect(() => {
        load()
    }, [])

    return (
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
}
