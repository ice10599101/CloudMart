import { useState, useEffect } from 'react'
import { Empty, Tag, Button, Card, Row, Col } from 'antd'
import {
  StarOutlined,
  FireOutlined,
  PlusOutlined,
  RightOutlined,
  HeartOutlined,
  NodeIndexOutlined,
  LockOutlined,
  RobotOutlined,
  BarChartOutlined,
  TeamOutlined,
  TrophyOutlined,
  EnvironmentOutlined,
  MailOutlined,
  FlagOutlined,
  BellOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { getHomeAggregation } from '@/api/wish'
import type { HomeAggregation, TodayRecommendItem, MyWishSummary, HotResonanceItem } from '@/api/wish'
import Skeleton from '@/components/Skeleton'
import styles from './WishHome.module.css'
import WishBGM from '@/components/WishBGM'

const FRUIT_TYPE_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_TYPE_COLORS: Record<string, string> = {
  GLOW: '#00D4FF',
  RESONANCE: '#9370DB',
  BLOOM: '#FF6B6B',
  SPARK: '#FFD700',
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishHome() {
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<HomeAggregation | null>(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await getHomeAggregation()
        if (res.data.success) {
          setData(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  if (loading) {
    return (
        <div className={`${styles.loadingContainer} wish-universe-theme`}>
          <Skeleton variant="wish-home" />
        </div>
    )
  }

  if (!data) {
    return (
        <div className={`${styles.emptyContainer} wish-universe-theme`}>
          <Empty description="暂无数据" />
        </div>
    )
  }

  return (
      <div className={`${styles.container} wish-universe-theme`}>
        {/* 顶部 Banner */}
        <div className={styles.banner}>
          <div className={styles.bannerContent}>
            <h1 className={styles.title}>心愿宇宙</h1>
            <p className={styles.subtitle}>每一个心愿，都是一颗种子</p>
            <Button
                type="primary"
                size="large"
                icon={<PlusOutlined />}
                onClick={() => history.push('/wish/create')}
                className={styles.createBtn}
            >
              许下心愿
            </Button>
          </div>
          <div className={styles.starField}>
            {Array.from({ length: 30 }).map((_, i) => (
                <div
                    key={i}
                    className={styles.star}
                    style={{
                      left: `${Math.random() * 100}%`,
                      top: `${Math.random() * 100}%`,
                      animationDelay: `${Math.random() * 3}s`,
                      opacity: Math.random() * 0.8 + 0.2,
                    }}
                />
            ))}
          </div>
        </div>

        {/* 入口导航 */}
        <div className={styles.entryNav}>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/list')}
              >
                <StarOutlined className={styles.entryIcon} style={{ color: '#00D4FF' }} />
                <div className={styles.entryText}>心愿广场</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/tree')}
              >
                <NodeIndexOutlined className={styles.entryIcon} style={{ color: '#9370DB' }} />
                <div className={styles.entryText}>世界生命树</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/capsules')}
              >
                <LockOutlined className={styles.entryIcon} style={{ color: '#4ECDC4' }} />
                <div className={styles.entryText}>时间胶囊</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/my')}
              >
                <HeartOutlined className={styles.entryIcon} style={{ color: '#FF6B6B' }} />
                <div className={styles.entryText}>我的心愿</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/create')}
              >
                <PlusOutlined className={styles.entryIcon} style={{ color: '#FFD700' }} />
                <div className={styles.entryText}>发布心愿</div>
              </Card>
            </Col>
            {data?.entries?.aiAssistantEntry !== false && (
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/assistant')}
              >
                <RobotOutlined className={styles.entryIcon} style={{ color: '#00D4FF' }} />
                <div className={styles.entryText}>AI 助手</div>
              </Card>
            </Col>
            )}
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/annual-report')}
              >
                <BarChartOutlined className={styles.entryIcon} style={{ color: '#9370DB' }} />
                <div className={styles.entryText}>年度报告</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/match')}
              >
                <TeamOutlined className={styles.entryIcon} style={{ color: '#4ECDC4' }} />
                <div className={styles.entryText}>同路人小队</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/leaderboard')}
              >
                <TrophyOutlined className={styles.entryIcon} style={{ color: '#FFD700' }} />
                <div className={styles.entryText}>排行榜</div>
              </Card>
            </Col>
            {data?.entries?.mapEntry !== false && (
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/map')}
              >
                <EnvironmentOutlined className={styles.entryIcon} style={{ color: '#4ECDC4' }} />
                <div className={styles.entryText}>附近心愿</div>
              </Card>
            </Col>
            )}
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/encounters')}
              >
                <MailOutlined className={styles.entryIcon} style={{ color: '#FFD700' }} />
                <div className={styles.entryText}>相遇信笺</div>
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/activities')}
              >
                <FlagOutlined className={styles.entryIcon} style={{ color: '#4ECDC4' }} />
                <div className={styles.entryText}>社区活动</div>
              </Card>
            </Col>
            {/* 心愿通知偏好（合规 34.6，对齐 Mobile wishHome / APP settings 入口） */}
            <Col xs={12} md={6}>
              <Card
                  hoverable
                  className={styles.entryCard}
                  onClick={() => history.push('/wish/notification-prefs')}
              >
                <BellOutlined className={styles.entryIcon} style={{ color: '#9370DB' }} />
                <div className={styles.entryText}>通知偏好</div>
              </Card>
            </Col>
          </Row>
        </div>

        {/* 今日推荐 */}
        {data.todayRecommend.length > 0 && (
            <section className={styles.section}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>
                  <FireOutlined style={{ color: '#FF6B35' }} /> 今日推荐
                </h2>
                <Button
                    type="link"
                    onClick={() => history.push('/wish/list')}
                    className={styles.moreBtn}
                >
                  查看更多 <RightOutlined />
                </Button>
              </div>
              <Row gutter={[16, 16]}>
                {data.todayRecommend.map((item: TodayRecommendItem) => (
                    <Col xs={24} sm={12} md={8} lg={6} xl={4} key={item.wishId}>
                      <Card
                          hoverable
                          className={styles.wishCard}
                          cover={
                            item.coverUrl ? (
                                <img loading="lazy" src={item.coverUrl} alt={item.title} className={styles.cardCover} />
                            ) : (
                                <div className={styles.cardCoverPlaceholder}>
                                  <StarOutlined style={{ fontSize: 32, color: '#5A6F8E' }} />
                                </div>
                            )
                          }
                          onClick={() => history.push(`/wish/${item.wishId}`)}
                      >
                        <Card.Meta
                            title={<span className={styles.cardTitle}>{item.title}</span>}
                            description={
                              <div className={styles.cardDesc}>
                        <span style={{ color: FRUIT_TYPE_COLORS[item.fruitType] }}>
                          {FRUIT_TYPE_LABELS[item.fruitType]}
                        </span>
                                <span>{item.authorNickname}</span>
                                <span>{formatCount(item.supportCount)} 互动</span>
                              </div>
                            }
                        />
                      </Card>
                    </Col>
                ))}
              </Row>
            </section>
        )}

        {/* 我的心愿 */}
        {data.myWishes.length > 0 && (
            <section className={styles.section}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>
                  <HeartOutlined style={{ color: '#FF6B6B' }} /> 我的心愿
                </h2>
                <Button
                    type="link"
                    onClick={() => history.push('/wish/my')}
                    className={styles.moreBtn}
                >
                  查看全部 <RightOutlined />
                </Button>
              </div>
              <Row gutter={[16, 16]}>
                {data.myWishes.map((item: MyWishSummary) => (
                    <Col xs={24} sm={12} md={8} key={item.wishId}>
                      <Card
                          hoverable
                          className={styles.myWishCard}
                          onClick={() => history.push(`/wish/${item.wishId}`)}
                      >
                        <div className={styles.myWishContent}>
                          <div className={styles.myWishHeader}>
                            <Tag color={FRUIT_TYPE_COLORS[item.fruitType]}>
                              {FRUIT_TYPE_LABELS[item.fruitType]}
                            </Tag>
                            <span className={styles.myWishTitle}>{item.title}</span>
                          </div>
                          <div className={styles.progressWrap}>
                            <div className={styles.progressBg}>
                              <div
                                  className={styles.progressBar}
                                  style={{
                                    width: `${item.progress}%`,
                                    background: FRUIT_TYPE_COLORS[item.fruitType],
                                  }}
                              />
                            </div>
                            <span className={styles.progressText}>{item.progress}%</span>
                          </div>
                        </div>
                      </Card>
                    </Col>
                ))}
              </Row>
            </section>
        )}

        {/* 热门共鸣 */}
        {data.hotResonance.length > 0 && (
            <section className={styles.section}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>
                  <FireOutlined style={{ color: '#FFD700' }} /> 热门共鸣
                </h2>
              </div>
              <Card className={styles.hotListCard}>
                {data.hotResonance.map((item: HotResonanceItem, index: number) => (
                    <div
                        key={item.wishId}
                        className={styles.hotItem}
                        onClick={() => history.push(`/wish/${item.wishId}`)}
                    >
                <span
                    className={styles.hotRank}
                    style={{
                      color: index < 3 ? ['#FF6B35', '#FFA500', '#FFD700'][index] : '#5A6F8E',
                    }}
                >
                  {index + 1}
                </span>
                      <span className={styles.hotTitle}>{item.title}</span>
                      <span className={styles.hotCount}>{formatCount(item.supportCount)} 互动</span>
                    </div>
                ))}
              </Card>
            </section>
        )}
        <WishBGM />
      </div>
  )
}
