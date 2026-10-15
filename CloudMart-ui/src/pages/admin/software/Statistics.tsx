import { useEffect, useState } from 'react'
import { Card, Col, Row, Spin, Statistic, Typography } from 'antd'
import { getSoftwareStatistics } from '@/api/admin/software'
import type { AdminSoftwareStatistics } from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

/**
 * 数据统计（方案 10.7，P1）：发布数、审核时长、获取链接次数/人数、收藏数、新评分、
 * 待处理失效反馈；口径与窗口随响应展示——不显示外部下载完成率（本站不掌握）。
 */
export default function SoftwareStatistics() {
  const message = useMessage()
  const [data, setData] = useState<AdminSoftwareStatistics | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    getSoftwareStatistics()
      .then((res) => setData(res.data.data))
      .catch(() => message.error('统计加载失败'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const windowDays = data?.windowDays ?? 30

  return (
    <Spin spinning={loading}>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        运营统计
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        {data?.windowNote ?? `趋势类口径为最近 ${windowDays} 天`}
      </Typography.Paragraph>

      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="当前在架软件" value={data?.publishedCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待审快照" value={data?.pendingReviewCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`平均审核时长（${windowDays} 天，分钟）`}
              value={data?.avgReviewMinutes ?? '-'}
              precision={data?.avgReviewMinutes != null ? 1 : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待处理失效反馈" value={data?.pendingFeedbacks ?? 0} />
          </Card>
        </Col>

        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`获取链接次数（${windowDays} 天）`}
              value={data?.accessCount ?? 0}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`获取链接去重人数（${windowDays} 天）`}
              value={data?.accessUsers ?? 0}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="收藏总量" value={data?.favoriteTotal ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title={`新增收藏（${windowDays} 天）`} value={data?.newFavorites ?? 0} />
          </Card>
        </Col>

        <Col xs={12} md={6}>
          <Card>
            <Statistic title={`新评分人数（${windowDays} 天）`} value={data?.newRatings ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title={`新评分均分（${windowDays} 天）`}
              value={data?.newRatingAvg ?? '-'}
              precision={data?.newRatingAvg != null ? 1 : undefined}
              suffix={data?.newRatingAvg != null ? '/ 5 星' : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="待处理举报" value={data?.pendingReports ?? 0} />
          </Card>
        </Col>
      </Row>
    </Spin>
  )
}
