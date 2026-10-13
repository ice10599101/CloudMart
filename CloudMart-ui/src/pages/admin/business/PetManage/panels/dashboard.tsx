import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Card,
  Col,
  Empty,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  getPetDashboard,
  type PetDashboard,
} from '@/api/admin/pet'

const { Text, Paragraph } = Typography

const PET_MANAGE_CSS = `
.pet-admin-trend { display: flex; align-items: flex-end; gap: 4px; height: 120px; }
.pet-admin-trend-bar { flex: 1; min-width: 6px; border-radius: 3px 3px 0 0; background: linear-gradient(180deg, #69c0ff, #1677ff); }
.pet-admin-trend-label { font-size: 10px; color: #8c8c8c; text-align: center; margin-top: 4px; }
`

/** 数据看板面板 */
export function DashboardPanel() {
  const [days, setDays] = useState(14)
  const [data, setData] = useState<PetDashboard | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getPetDashboard(days)
      if (res.success && res.data) {
        setData(res.data)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [days])

  useEffect(() => {
    void load()
  }, [load])

  const trendMax = useMemo(() => {
    if (!data) {
      return 1
    }
    return Math.max(1, ...data.trend.map((point) => point.activePets))
  }, [data])

  if (!data) {
    return <Empty description={loading ? '加载中…' : '暂无数据'} />
  }

  const { overview, distribution, aiUsage } = data

  return (
    <div>
      <style>{PET_MANAGE_CSS}</style>
      <Space style={{ marginBottom: 12 }}>
        <Select
          value={days}
          onChange={setDays}
          style={{ width: 140 }}
          options={[
            { value: 7, label: '近 7 天' },
            { value: 14, label: '近 14 天' },
            { value: 30, label: '近 30 天' },
          ]}
        />
        <Text type="secondary">活跃 = 当日有行为流水的宠物（去重）</Text>
      </Space>

      <Row gutter={[12, 12]}>
        <Col span={4}><Card size="small"><Statistic title="宠物总数" value={overview.totalPets} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日新增" value={overview.newPetsToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日活跃" value={overview.activePetsToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="7 日活跃" value={overview.activePets7d} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日对战" value={overview.battlesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日捞瓶" value={overview.bottlesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="关系数" value={overview.totalRelations} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="好友数" value={overview.totalFriends} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="家园数" value={overview.totalRooms} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="平均舒适度" value={overview.avgComfort} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日任务领取" value={overview.questsClaimedToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="在职宠物" value={overview.careerHired} /></Card></Col>
        {/* P1-8：AI 聊天成本可观测 */}
        <Col span={4}><Card size="small"><Statistic title="今日 AI 回复" value={aiUsage.aiRepliesToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="今日估算 token" value={aiUsage.tokensToday} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="AI 降级累计" value={aiUsage.fallbackTotal} /></Card></Col>
      </Row>

      <Card size="small" title="近 N 日活跃宠物" style={{ marginTop: 16 }} loading={loading}>
        <div className="pet-admin-trend">
          {data.trend.map((point) => (
            <Tooltip key={point.date} title={`${point.date}：活跃 ${point.activePets} / 行为 ${point.activities} / 对战 ${point.battles} / 留言 ${point.wallMessages} / 串门 ${point.visits}`}>
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div className="pet-admin-trend-bar" style={{ height: `${(point.activePets / trendMax) * 100}px` }} />
                <div className="pet-admin-trend-label">{point.date.slice(5)}</div>
              </div>
            </Tooltip>
          ))}
        </div>
      </Card>

      <Row gutter={[12, 12]} style={{ marginTop: 16 }}>
        <Col span={8}>
          <Card size="small" title="种类分布">
            {distribution.species.length === 0 ? (
              <Empty />
            ) : (
              distribution.species.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="等级分布">
            {distribution.levelBuckets.map((bucket) => (
              <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
              </Paragraph>
            ))}
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="职业就业 Top">
            {distribution.careers.length === 0 ? (
              <Empty />
            ) : (
              distribution.careers.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 只</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="家具摆放 Top">
            {distribution.topFurniture.length === 0 ? (
              <Empty />
            ) : (
              distribution.topFurniture.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value} 件</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title="亲密度 Top">
            {distribution.topIntimacy.length === 0 ? (
              <Empty />
            ) : (
              distribution.topIntimacy.map((bucket) => (
                <Paragraph key={bucket.name} style={{ marginBottom: 4 }}>
                  <Text>{bucket.name}</Text> <Text type="secondary">{bucket.value}</Text>
                </Paragraph>
              ))
            )}
          </Card>
        </Col>
        <Col span={24}>
          <Card size="small" title="物品购买结构（宠物背包）">
            <Space wrap>
              {Object.entries(overview.purchasesByType).length === 0 && <Text type="secondary">暂无购买记录</Text>}
              {Object.entries(overview.purchasesByType).map(([type, count]) => (
                <Tag key={type} color="blue">
                  {type}: {count}
                </Tag>
              ))}
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
