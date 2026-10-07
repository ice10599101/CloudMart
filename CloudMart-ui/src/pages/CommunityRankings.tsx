import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, List, Skeleton, Table, Tag } from 'antd'
import { TrophyOutlined } from '@ant-design/icons'
import {
  getMonthlyRanking,
  getMyRanking,
  getSeasonRanking,
  getRankingSeasons,
  type MyRanking,
  type RankingItem,
  type RankingSeason,
} from '@/api/growth'
import { useAuthStore } from '@/stores/auth'
import { history } from 'umi'
import styles from './CommunityRankings.module.css'

/**
 * 社区经验排行榜（P1-18：社区成长体系补齐）。
 * 当月经验榜（Redis 实时）+ 我的排名 + 历史赛季归档 + 赛季榜单详情。
 * 经验发放受 P1-7 日上限约束（200/日）。
 */

const TOP_STYLES = ['#ffd700', '#c0c0c0', '#cd7f32']

export default function CommunityRankings() {
  const { message } = App.useApp()
  const { user } = useAuthStore()

  const [loading, setLoading] = useState(true)
  const [top, setTop] = useState<RankingItem[]>([])
  const [mine, setMine] = useState<MyRanking | null>(null)
  const [seasons, setSeasons] = useState<RankingSeason[]>([])
  const [seasonsLoading, setSeasonsLoading] = useState(false)
  const [detailSeason, setDetailSeason] = useState<RankingSeason | null>(null)
  const [detailItems, setDetailItems] = useState<RankingItem[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [topRes, mineRes] = await Promise.all([
        getMonthlyRanking(50),
        getMyRanking(),
      ])
      if (topRes.data.success) setTop(topRes.data.data ?? [])
      if (mineRes.data.success) setMine(mineRes.data.data)
    } catch {
      message.error('排行榜加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (user) load()
  }, [user, load])

  const loadSeasons = async () => {
    setSeasonsLoading(true)
    try {
      const res = await getRankingSeasons(1, 20)
      if (res.data.success) setSeasons(res.data.data ?? [])
    } catch {
      message.error('赛季列表加载失败')
    } finally {
      setSeasonsLoading(false)
    }
  }

  const openSeason = async (season: RankingSeason) => {
    setDetailSeason(season)
    setDetailLoading(true)
    try {
      const res = await getSeasonRanking(season.id)
      setDetailItems(res.data.data ?? [])
    } catch {
      message.error('赛季详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  if (!user) {
    return (
      <div className={styles.page}>
        <Card><Button type="primary" onClick={() => history.push('/login')}>登录后查看排行榜</Button></Card>
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <Card className={styles.heroCard}>
        <div className={styles.heroBody}>
          <TrophyOutlined className={styles.heroIcon} />
          <div className={styles.heroInfo}>
            <h2 className={styles.heroTitle}>社区经验排行榜</h2>
            <p className={styles.heroDesc}>当月经验实时榜（Redis）· 每月自动归档为历史赛季 · 经验每日累计上限 200</p>
          </div>
          {mine && (
            <div className={styles.myRankBox}>
              <div className={styles.myRankNumber}>{mine.rankNo ?? '—'}</div>
              <div className={styles.myRankLabel}>我的排名（{mine.expValue ?? 0} 经验）</div>
            </div>
          )}
        </div>
      </Card>

      <Card title="当月榜单 Top 50" size="small">
        {loading ? (
          <Skeleton active />
        ) : top.length === 0 ? (
          <List locale={{ emptyText: '本月还没有上榜数据' }} />
        ) : (
          <div className={styles.rankList}>
            {top.map((item) => (
              <div key={`${item.rankNo}-${item.userId}`} className={styles.rankRow}>
                <span
                  className={styles.rankNo}
                  style={item.rankNo <= 3 ? { background: TOP_STYLES[item.rankNo - 1], color: '#fff' } : undefined}
                >
                  {item.rankNo}
                </span>
                <span className={styles.rankUser}>用户 #{item.userId}{item.userId === user.id ? '（我）' : ''}</span>
                <span className={styles.rankExp}>{item.expValue} 经验</span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card
        title="历史赛季"
        size="small"
        extra={<Button type="link" size="small" onClick={loadSeasons} loading={seasonsLoading}>刷新</Button>}
      >
        {seasons.length === 0 && !seasonsLoading ? (
          <List locale={{ emptyText: '还没有归档赛季（每月结算后生成），点击右上角刷新' }} />
        ) : (
          <List
            size="small"
            loading={seasonsLoading}
            dataSource={seasons}
            renderItem={(season) => (
              <List.Item
                className={styles.seasonRow}
                actions={[<Button key="detail" type="link" size="small" onClick={() => openSeason(season)}>查看榜单</Button>]}
              >
                <div className={styles.seasonInfo}>
                  <span className={styles.seasonName}>{season.name}</span>
                  <span className={styles.seasonRange}>{season.startDate} ~ {season.endDate}</span>
                  <Tag color={season.status === 1 ? 'default' : 'processing'}>
                    {season.status === 1 ? '已归档' : '进行中'}
                  </Tag>
                </div>
              </List.Item>
            )}
          />
        )}
      </Card>

      {detailSeason && (
        <Card
          title={`赛季榜单：${detailSeason.name}`}
          size="small"
          extra={<Button type="link" size="small" onClick={() => setDetailSeason(null)}>收起</Button>}
        >
          <Table
            size="small"
            rowKey={(r) => `${r.rankNo}-${r.userId}`}
            loading={detailLoading}
            dataSource={detailItems}
            pagination={false}
            columns={[
              { title: '名次', dataIndex: 'rankNo', width: 80 },
              { title: '用户 ID', dataIndex: 'userId', width: 120 },
              { title: '经验值', dataIndex: 'expValue', width: 120 },
            ]}
            locale={{ emptyText: '该赛季暂无榜单记录' }}
          />
        </Card>
      )}
    </div>
  )
}
