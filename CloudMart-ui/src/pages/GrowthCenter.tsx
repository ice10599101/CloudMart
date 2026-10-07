import { useCallback, useEffect, useState } from 'react'
import { App, Avatar, Button, Card, List, Progress, Skeleton, Tag } from 'antd'
import { ArrowUpOutlined, CrownFilled } from '@ant-design/icons'
import {
  getExpLogs,
  getLevelConfigs,
  getUserDecorations,
  getUserLevel,
  setAvatarFrame,
  type ExpLogRecord,
  type LevelConfig,
  type UserLevelInfo,
} from '@/api/growth'
import { useAuthStore } from '@/stores/auth'
import { history } from 'umi'
import styles from './GrowthCenter.module.css'

/**
 * 成长中心（P1-18：社区成长体系补齐）。
 * 等级进度 + 等级阶梯权益 + 头像框装扮（Lv2+ 权益，ALLOWED_AVATAR_FRAMES 七种）+
 * 经验日志（分页；来源见 EXP_SOURCE_META）。
 */

/** 经验来源文案（对齐 addExp source 枚举；含 P1-7 日上限语义提示） */
const EXP_SOURCE_META: Record<string, { label: string; icon: string }> = {
  POST: { label: '发布帖子', icon: '📝' },
  COMMENT: { label: '发表评论', icon: '💬' },
  LIKE_RECEIVED: { label: '收到点赞', icon: '❤️' },
  FOLLOW_RECEIVED: { label: '获得新粉丝', icon: '✨' },
  CHECK_IN: { label: '每日签到', icon: '📅' },
  GOOD_POST: { label: '帖子被加精', icon: '🏅' },
}

/** 头像框选项（对齐后端 GrowthServiceImpl.ALLOWED_AVATAR_FRAMES） */
const AVATAR_FRAMES: Array<{ key: string; label: string; ring: string }> = [
  { key: 'none', label: '无框', ring: 'transparent' },
  { key: 'gold', label: '鎏金', ring: 'linear-gradient(135deg,#ffd700,#b8860b)' },
  { key: 'purple', label: '紫电', ring: 'linear-gradient(135deg,#a855f7,#6b21a8)' },
  { key: 'green', label: '翠微', ring: 'linear-gradient(135deg,#52c41a,#237804)' },
  { key: 'pink', label: '樱粉', ring: 'linear-gradient(135deg,#ff8fa3,#e94560)' },
  { key: 'rainbow', label: '虹彩', ring: 'linear-gradient(135deg,#ff4d4f,#faad14,#52c41a,#1677ff,#a855f7)' },
]

function formatTime(time: string) {
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

export default function GrowthCenter() {
  const { message } = App.useApp()
  const { user, fetchProfile } = useAuthStore()

  const [loading, setLoading] = useState(true)
  const [level, setLevel] = useState<UserLevelInfo | null>(null)
  const [configs, setConfigs] = useState<LevelConfig[]>([])
  const [logs, setLogs] = useState<ExpLogRecord[]>([])
  const [logsPage, setLogsPage] = useState(1)
  const [logsTotal, setLogsTotal] = useState(0)
  const [frameSaving, setFrameSaving] = useState<string | null>(null)
  const [currentFrame, setCurrentFrame] = useState('none')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [levelRes, configsRes, logsRes, decoRes] = await Promise.all([
        getUserLevel(),
        getLevelConfigs(),
        getExpLogs(1, 20),
        getUserDecorations([user?.id ?? 0]),
      ])
      if (levelRes.data.success) setLevel(levelRes.data.data)
      if (configsRes.data.success) setConfigs(configsRes.data.data ?? [])
      if (logsRes.data.success) {
        setLogs(logsRes.data.data ?? [])
        const total = logsRes.data.meta?.total
        setLogsTotal(typeof total === 'number' ? total : (logsRes.data.data ?? []).length)
      }
      if (decoRes.data.success) {
        setCurrentFrame(decoRes.data.data?.[String(user?.id ?? '')]?.avatarFrame || 'none')
      }
      setLogsPage(1)
    } catch {
      message.error('成长数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [message, user])

  useEffect(() => {
    if (user) load()
  }, [user, load])

  const loadLogs = async (page: number) => {
    try {
      const res = await getExpLogs(page, 20)
      if (res.data.success) {
        setLogs(res.data.data ?? [])
        setLogsPage(page)
        const total = res.data.meta?.total
        if (typeof total === 'number') setLogsTotal(total)
      }
    } catch {
      message.error('经验日志加载失败')
    }
  }

  const handleSetFrame = async (key: string) => {
    if (frameSaving) return
    if ((level?.level ?? 0) < 2) {
      message.warning('自定义头像框为 Lv2 权益，继续成长即可解锁')
      return
    }
    setFrameSaving(key)
    try {
      const res = await setAvatarFrame(key)
      if (res.data.success) {
        setCurrentFrame(key)
        message.success('头像框已更新')
        fetchProfile()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setFrameSaving(null)
    }
  }

  if (!user) {
    return (
      <div className={styles.page}>
        <Card><Button type="primary" onClick={() => history.push('/login')}>登录后查看成长</Button></Card>
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <Card className={styles.levelCard}>
        {loading || !level ? (
          <Skeleton avatar active />
        ) : (
          <div className={styles.levelBody}>
            <div className={styles.levelBadge}>
              <CrownFilled className={styles.levelCrown} />
              <span className={styles.levelNumber}>Lv{level.level}</span>
            </div>
            <div className={styles.levelInfo}>
              <div className={styles.levelTitleRow}>
                <span className={styles.levelTitle}>{level.levelTitle}</span>
                <Tag color="gold">总经验 {level.totalExp}</Tag>
              </div>
              <Progress
                percent={Math.min(100, level.expProgress ?? 0)}
                strokeColor={{ from: '#e94560', to: '#ff8fa3' }}
                format={() => `${level.exp} / ${level.nextLevelExp ?? '—'}`}
              />
              {level.nextLevelTitle && (
                <p className={styles.nextHint}>下一级：{level.nextLevelTitle}（Lv{level.level + 1}）</p>
              )}
            </div>
          </div>
        )}
      </Card>

      <Card title="等级权益阶梯" size="small">
        <List
          size="small"
          dataSource={configs}
          renderItem={(config) => {
            const reached = (level?.level ?? 0) >= config.level
            return (
              <List.Item className={reached ? styles.ladderReached : styles.ladderLocked}>
                <div className={styles.ladderRow}>
                  <span className={styles.ladderLevel}>Lv{config.level}</span>
                  <span className={styles.ladderTitle}>{config.title}</span>
                  <span className={styles.ladderBenefits}>{config.benefits}</span>
                  {reached ? <Tag color="green">已达成</Tag> : <Tag>未达成</Tag>}
                </div>
              </List.Item>
            )
          }}
        />
      </Card>

      <Card title="头像框装扮" size="small" extra={<span className={styles.frameHint}>Lv2+ 权益</span>}>
        <div className={styles.frameGrid}>
          {AVATAR_FRAMES.map((frame) => (
            <button
              key={frame.key}
              type="button"
              className={`${styles.frameItem} ${currentFrame === frame.key ? styles.frameItemActive : ''}`}
              onClick={() => handleSetFrame(frame.key)}
              disabled={frameSaving !== null}
            >
              <span className={styles.frameRing} style={{ background: frame.ring }}>
                <Avatar src={user.avatar || undefined} icon={!user.avatar && undefined}>
                  {user.nickname?.slice(0, 1)}
                </Avatar>
              </span>
              <span className={styles.frameLabel}>{frame.label}</span>
            </button>
          ))}
        </div>
      </Card>

      <Card title={<><ArrowUpOutlined /> 经验日志</>} size="small">
        <List
          size="small"
          dataSource={logs}
          locale={{ emptyText: '还没有经验记录，去发帖/评论/签到赚经验吧' }}
          renderItem={(log) => {
            const meta = EXP_SOURCE_META[log.source] ?? { label: log.source, icon: '📈' }
            return (
              <List.Item>
                <div className={styles.logRow}>
                  <span className={styles.logIcon}>{meta.icon}</span>
                  <div className={styles.logInfo}>
                    <span className={styles.logDesc}>{log.description || meta.label}</span>
                    <span className={styles.logTime}>{formatTime(log.createdAt)}</span>
                  </div>
                  <span className={styles.logExp}>+{log.expChange}</span>
                </div>
              </List.Item>
            )
          }}
          pagination={{
            current: logsPage,
            pageSize: 20,
            total: logsTotal,
            size: 'small',
            onChange: (page) => loadLogs(page),
          }}
        />
      </Card>
    </div>
  )
}
