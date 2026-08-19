import { useState, useEffect } from 'react'
import { Button, Empty, Progress, Tag } from 'antd'
import { LoginOutlined, TrophyOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { getMyBadges } from '@/api/wish'
import type { BadgeWallItem, BadgeRarity } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import WishBGM from '@/components/WishBGM'
import styles from './BadgeWall.module.css'

/**
 * 徽章墙（文档 2.6）：已获得光晕态在前（获得时间倒序），
 * 未获得灰色锁定态展示获取方式与达成进度。
 */

const RARITY_META: Record<BadgeRarity, { label: string; color: string; emoji: string }> = {
  COMMON: { label: '普通', color: 'default', emoji: '🏅' },
  RARE: { label: '稀有', color: 'blue', emoji: '💠' },
  EPIC: { label: '史诗', color: 'purple', emoji: '💜' },
  LEGENDARY: { label: '传说', color: 'gold', emoji: '👑' },
}

export default function BadgeWall() {
  const { user } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [badges, setBadges] = useState<BadgeWallItem[]>([])

  useEffect(() => {
    if (!user) {
      setLoading(false)
      return
    }
    const load = async () => {
      try {
        const res = await getMyBadges()
        if (res.data.success) {
          setBadges(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user])

  if (!user) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <div className={styles.emptyContainer}>
          <Empty description="登录后即可查看你的徽章墙" />
          <Button type="primary" icon={<LoginOutlined />} onClick={() => history.push('/login')}>
            去登录
          </Button>
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Skeleton variant="list" count={4} />
      </div>
    )
  }

  const earnedCount = badges.filter((b) => b.earned).length

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>
          <TrophyOutlined style={{ color: '#FFD700', marginRight: 8 }} />
          我的徽章墙
        </h1>
        <span className={styles.summary}>
          已点亮 {earnedCount} / {badges.length} 枚徽章
        </span>
      </div>

      {badges.length === 0 ? (
        <div className={styles.emptyContainer}>
          <Empty description="暂无徽章定义，许下第一个心愿即可点亮第一枚" />
          <Button type="primary" onClick={() => history.push('/wish/create')}>
            发布心愿
          </Button>
        </div>
      ) : (
        <div className={styles.grid}>
          {badges.map((badge) => {
            const rarity = RARITY_META[badge.rarity] ?? RARITY_META.COMMON
            const icon = badge.icon && badge.icon.startsWith('http') ? (
              <img src={badge.icon} alt={badge.name} width={40} height={40} />
            ) : (
              rarity.emoji
            )
            return (
              <div
                key={badge.badgeId}
                className={`${styles.badgeCard} ${
                  badge.earned ? styles.badgeCardEarned : styles.badgeCardLocked
                }`}
              >
                {!badge.earned && <span className={styles.lockMark}>🔒</span>}
                <div
                  className={`${styles.badgeIcon} ${
                    badge.earned ? styles.badgeIconEarned : styles.badgeIconLocked
                  }`}
                >
                  {icon}
                </div>
                <div className={`${styles.badgeName} ${!badge.earned ? styles.badgeNameLocked : ''}`}>
                  {badge.name}
                </div>
                <div className={`${styles.badgeDesc} ${!badge.earned ? styles.badgeDescLocked : ''}`}>
                  {badge.description || badge.condition?.description || ''}
                </div>
                <Tag color={badge.earned ? rarity.color : 'default'}>{rarity.label}</Tag>
                {badge.earned ? (
                  badge.earnedAt && (
                    <div className={styles.earnedAt}>
                      {new Date(badge.earnedAt).toLocaleDateString('zh-CN')} 点亮
                    </div>
                  )
                ) : (
                  badge.progress && (
                    <div className={styles.progressRow}>
                      <Progress
                        percent={badge.progress.percentage}
                        showInfo={false}
                        strokeColor={{ from: '#00D4FF', to: '#9370DB' }}
                        trailColor="rgba(255,255,255,0.08)"
                      />
                      <span className={styles.progressText}>
                        {badge.progress.current}/{badge.progress.threshold}
                      </span>
                    </div>
                  )
                )}
              </div>
            )
          })}
        </div>
      )}
      <WishBGM />
    </div>
  )
}
