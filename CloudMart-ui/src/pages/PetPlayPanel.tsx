import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Empty, Select, Space, Tag, message as antdMessage } from 'antd'
import type { PetInfo } from '@/api/pet'
import type { PetFriendItem } from '@/api/pet'
import {
  acceptCooperation,
  claimCooperationReward,
  confirmOfflineDigest,
  createCooperation,
  endCustody,
  getCustodyStatus,
  getCollection,
  getCollectionStats,
  getOfflineDigest,
  getPetFriends,
  leaveCooperation,
  listCooperations,
  settleMinigame,
  startCustody,
  startMinigameRound,
  submitMinigameOps,
} from '@/api/pet'
import styles from './PetPlayPanel.module.css'

const CATCH_WINDOWS = 10
const SLOT_LABEL: Record<string, string> = { LEFT: '← 左', CENTER: '● 中', RIGHT: '右 →' }

type PlayTab = 'minigame' | 'custody' | 'digest' | 'coop' | 'collection'

const PLAY_TABS: Array<{ key: PlayTab; label: string; emoji: string }> = [
  { key: 'minigame', label: '接球', emoji: '🎯' },
  { key: 'custody', label: '托管', emoji: '🏨' },
  { key: 'digest', label: '离线回顾', emoji: '📜' },
  { key: 'coop', label: '好友合作', emoji: '🤝' },
  { key: 'collection', label: '图鉴', emoji: '📖' },
]

// ---------------- N04 接球小游戏 ----------------

interface ActiveRound {
  roundId: string
  deadlineAt: number
  sequence: Array<'LEFT' | 'CENTER' | 'RIGHT'>
  submitted: Set<number>
  successes: number
}

function MinigameTab({ pet }: { pet: PetInfo }) {
  const [round, setRound] = useState<ActiveRound | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<{ valid: boolean; reward: Record<string, number> } | null>(null)
  const [remaining, setRemaining] = useState(0)
  const timerRef = useRef<number | null>(null)

  // 倒计时（服务端 deadlineAt 为准；到点自动结算）
  useEffect(() => {
    if (!round) {
      return
    }
    timerRef.current = window.setInterval(() => {
      const left = Math.max(0, Math.round((round.deadlineAt - Date.now()) / 1000))
      setRemaining(left)
    }, 250)
    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current)
      }
    }
  }, [round])

  const doSettle = useCallback(async () => {
    if (!round) {
      return
    }
    setBusy(true)
    try {
      const { data: res } = await settleMinigame(round.roundId)
      if (res.success && res.data && res.data.status === 'SETTLED') {
        setResult({ valid: res.data.validCompletion, reward: res.data.reward })
        setRound(null)
      }
    } finally {
      setBusy(false)
    }
  }, [round])

  // 到点自动结算
  useEffect(() => {
    if (round && remaining === 0) {
      void doSettle()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining, round])

  const start = useCallback(async () => {
    setBusy(true)
    setResult(null)
    try {
      const { data: res } = await startMinigameRound(pet.petId)
      if (res.success && res.data) {
        // 序列由服务端随开局下发（§7.4：展示数据）；每窗目标前端仅作提示
        setRound({
          roundId: String(res.data.roundId),
          deadlineAt: new Date(res.data.deadlineAt).getTime(),
          sequence: res.data.sequence ?? [],
          submitted: new Set(),
          successes: 0,
        })
        setRemaining(Math.round((new Date(res.data.deadlineAt).getTime() - Date.now()) / 1000))
      }
    } finally {
      setBusy(false)
    }
  }, [pet.petId])

  const catchBall = useCallback(
    async (slot: 'LEFT' | 'CENTER' | 'RIGHT') => {
      if (!round) {
        return
      }
      // 当前窗口 = 已提交的最大窗口 + 1
      const current = (round.submitted.size > 0 ? Math.max(...round.submitted) : 0) + 1
      if (current > CATCH_WINDOWS) {
        return
      }
      const { data: res } = await submitMinigameOps(round.roundId, [
        { seq: current, windowIndex: current, slot },
      ])
      if (res.success) {
        setRound((prev) => {
          if (!prev) {
            return prev
          }
          const next = new Set(prev.submitted)
          next.add(current)
          return { ...prev, submitted: next, successes: next.size }
        })
      }
    },
    [round],
  )

  const currentWindow = round ? (round.submitted.size > 0 ? Math.max(...round.submitted) : 0) + 1 : 0
  const slotOf = (idx: number): 'LEFT' | 'CENTER' | 'RIGHT' | undefined => round?.sequence[idx - 1]

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>
        <span>🎯 接球小游戏</span>
        <span className={styles.meta}>每日 5 局有收益；超限转训练局（无收益）</span>
      </div>
      {!round && !result && (
        <Button type="primary" loading={busy} onClick={() => void start()}>
          开始一局（消耗 15 精力）
        </Button>
      )}
      {round && (
        <>
          <div className={styles.row}>
            <span className={styles.countdown}>{remaining}s</span>
            <span className={styles.meta}>
              当前第 {Math.min(currentWindow, CATCH_WINDOWS)}/{CATCH_WINDOWS} 窗 · 接住 {round.successes}
            </span>
          </div>
          <div className={styles.windowStrip}>
            {Array.from({ length: CATCH_WINDOWS }, (_, i) => i + 1).map((w) => {
              const cls = [styles.window]
              if (round.submitted.has(w)) {
                cls.push(styles.windowDone)
              } else if (w === currentWindow) {
                cls.push(styles.windowCurrent)
              } else if (w < currentWindow) {
                cls.push(styles.windowMissed)
              }
              const target = slotOf(w)
              return (
                <div key={w} className={cls.join(' ')}>
                  {w === currentWindow && target ? SLOT_LABEL[target] : w}
                </div>
              )
            })}
          </div>
          <div className={styles.slotButtons}>
            {(['LEFT', 'CENTER', 'RIGHT'] as const).map((slot) => (
              <Button key={slot} disabled={remaining === 0} onClick={() => void catchBall(slot)}>
                {SLOT_LABEL[slot]}
              </Button>
            ))}
          </div>
          <Button size="small" onClick={() => void doSettle()}>
            提前结束
          </Button>
        </>
      )}
      {result && (
        <div className={styles.row}>
          {result.valid ? (
            <span className={styles.rewardChip}>
              🎉 完成！经验 +{result.reward.exp} · 亲密度 +{result.reward.intimacy} · 心情 +
              {result.reward.happiness}
            </span>
          ) : (
            <span className={styles.meta}>本局未达标（需接住 3 个），无收益</span>
          )}
          <Button size="small" onClick={() => setResult(null)}>
            再来一局
          </Button>
        </div>
      )}
    </div>
  )
}

// ---------------- N05 有限托管 ----------------

function CustodyTab() {
  const [status, setStatus] = useState<{ active: boolean; endsAt?: string; careFeedUsed?: number; careCleanUsed?: number; weekUsed?: boolean; nextAvailableAt?: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    const { data: res } = await getCustodyStatus()
    if (res.success && res.data) {
      setStatus(res.data)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const start = async () => {
    setBusy(true)
    try {
      const { data: res } = await startCustody()
      if (res.success) {
        antdMessage.success('托管已开始（24 小时）')
        await load()
      }
    } finally {
      setBusy(false)
    }
  }

  const end = async () => {
    setBusy(true)
    try {
      await endCustody()
      antdMessage.success('托管已结束')
      await load()
    } finally {
      setBusy(false)
    }
  }

  const endsIn = status?.active && status.endsAt
    ? Math.max(0, Math.round((new Date(status.endsAt).getTime() - Date.now()) / 3600000))
    : 0

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>
        <span>🏨 有限托管</span>
        <span className={styles.meta}>每自然周 1 次 · 最长 24 小时 · 不产出养成收益</span>
      </div>
      {!status ? null : status.active ? (
        <Space direction="vertical" size={4}>
          <span>
            托管中 · 剩余约 <b>{endsIn}</b> 小时
          </span>
          <span className={styles.meta}>
            已照顾：喂食 {status.careFeedUsed ?? 0}/2 · 清洁 {status.careCleanUsed ?? 0}/1
          </span>
          <Button size="small" loading={busy} onClick={() => void end()}>
            提前结束（名额不恢复）
          </Button>
        </Space>
      ) : status.weekUsed ? (
        <span className={styles.meta}>本周托管已使用，下周一再试</span>
      ) : (
        <Button type="primary" loading={busy} onClick={() => void start()}>
          开始托管（24 小时）
        </Button>
      )}
    </div>
  )
}

// ---------------- N05 离线摘要 ----------------

interface DigestData {
  from: string
  throughAt: string
  offlineHours: number
  finishedTasks: number
  claimableTasks: number
  visits: number
  milestones: number
  hasCursor: boolean
}

function DigestTab() {
  const [digest, setDigest] = useState<DigestData | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    const { data: res } = await getOfflineDigest()
    if (res.success && res.data) {
      setDigest(res.data)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const confirm = async () => {
    if (!digest) {
      return
    }
    setBusy(true)
    try {
      const { data: res } = await confirmOfflineDigest(digest.throughAt)
      if (res.success) {
        antdMessage.success('已确认阅读，新事件下次仍会展示')
        await load()
      }
    } finally {
      setBusy(false)
    }
  }

  if (!digest) {
    return <div className={styles.section}><Empty description="加载中" /></div>
  }

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>
        <span>📜 离线回顾</span>
        <span className={styles.meta}>
          离线约 {digest.offlineHours} 小时（至 {new Date(digest.throughAt).toLocaleString()}）
        </span>
      </div>
      <div className={styles.row}>
        <Tag color={digest.finishedTasks > 0 ? 'green' : 'default'}>已完成任务 {digest.finishedTasks}</Tag>
        <Tag color={digest.claimableTasks > 0 ? 'gold' : 'default'}>待领取 {digest.claimableTasks}</Tag>
        <Tag color={digest.visits > 0 ? 'blue' : 'default'}>来访 {digest.visits}</Tag>
        <Tag color={digest.milestones > 0 ? 'purple' : 'default'}>里程碑 {digest.milestones}</Tag>
      </div>
      <span className={styles.meta}>确认只代表已读；确认期间新发生的事件下次仍会展示。</span>
      <div>
        <Button type="primary" size="small" loading={busy} onClick={() => void confirm()}>
          我知道了
        </Button>
      </div>
    </div>
  )
}

// ---------------- N06 好友合作 ----------------

interface CoopRow {
  id: string
  weekStart: string
  inviterUserId: string
  inviteeUserId: string | null
  status: string
}

function useMyUserId(): string | null {
  // 与请求层同源：登录态由 auth store 维护；这里从 localStorage 读取解析（JWT sub）
  const [userId, setUserId] = useState<string | null>(null)
  useEffect(() => {
    try {
      const token = localStorage.getItem('access_token')
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
        setUserId(String(payload.sub))
      }
    } catch {
      setUserId(null)
    }
  }, [])
  return userId
}

function CoopTab() {
  const [coops, setCoops] = useState<CoopRow[]>([])
  const [friends, setFriends] = useState<PetFriendItem[]>([])
  const [invitee, setInvitee] = useState<number | string | undefined>(undefined)
  const [busy, setBusy] = useState(false)
  const myUserId = useMyUserId()

  const load = useCallback(async () => {
    const [coopsRes, friendsRes] = await Promise.all([listCooperations(), getPetFriends()])
    if (coopsRes.data.success && coopsRes.data.data) {
      setCoops(coopsRes.data.data as unknown as CoopRow[])
    }
    if (friendsRes.data.success && friendsRes.data.data) {
      setFriends(friendsRes.data.data.friends ?? [])
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const invite = async () => {
    if (!invitee) {
      return
    }
    setBusy(true)
    try {
      const { data: res } = await createCooperation(invitee)
      if (res.success) {
        antdMessage.success('邀请已发出，等待好友接受')
        await load()
      }
    } finally {
      setBusy(false)
    }
  }

  const act = async (id: string, action: 'accept' | 'leave' | 'claim') => {
    setBusy(true)
    try {
      if (action === 'accept') {
        await acceptCooperation(id)
        antdMessage.success('已接受，开始合作吧')
      } else if (action === 'leave') {
        await leaveCooperation(id)
        antdMessage.success('已退出（名额不恢复）')
      } else {
        const { data: res } = await claimCooperationReward(id)
        if (res.success && res.data) {
          const reward = res.data.reward
          antdMessage.success(
            res.data.duplicate
              ? '已领取过（返回原结果）'
              : reward.type === 'ITEM'
                ? `获得装饰 ${reward.itemCode}`
                : `获得替代奖励 ×${reward.amount}`,
          )
        }
      }
      await load()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>
        <span>🤝 好友合作周任务</span>
        <span className={styles.meta}>双方各 3 天有效照顾即达成 · 各限领一次</span>
      </div>
      <div className={styles.row}>
        <Select
          style={{ width: 220 }}
          placeholder="选择要邀请的好友"
          value={invitee}
          onChange={setInvitee}
          options={friends.map((f) => ({
            value: f.userId,
            label: f.nickname ?? String(f.userId),
          }))}
        />
        <Button type="primary" loading={busy} disabled={!invitee} onClick={() => void invite()}>
          发出邀请
        </Button>
      </div>
      {coops.length === 0 ? (
        <Empty description="还没有合作记录" />
      ) : (
        coops.map((coop) => {
          const isInviter = myUserId !== null && coop.inviterUserId === myUserId
          const canAccept = coop.status === 'INVITED' && !isInviter
          const canLeave = (coop.status === 'INVITED' || coop.status === 'ACTIVE')
          const canClaim = coop.status === 'COMPLETED'
          return (
            <div key={coop.id} className={styles.coopCard}>
              <div className={styles.row}>
                <Tag color={coop.status === 'COMPLETED' ? 'green' : coop.status === 'ACTIVE' ? 'blue' : 'gold'}>
                  {coop.status === 'INVITED' ? '待接受' : coop.status === 'ACTIVE' ? '进行中' : coop.status === 'COMPLETED' ? '已达成' : '已结束'}
                </Tag>
                <span className={styles.meta}>
                  {isInviter ? '我发起的' : '好友邀请'} · 周起始 {coop.weekStart}
                </span>
              </div>
              <div className={styles.row}>
                {canAccept && (
                  <Button size="small" type="primary" loading={busy} onClick={() => void act(coop.id, 'accept')}>
                    接受
                  </Button>
                )}
                {canClaim && (
                  <Button size="small" type="primary" loading={busy} onClick={() => void act(coop.id, 'claim')}>
                    领取奖励
                  </Button>
                )}
                {canLeave && (
                  <Button size="small" danger loading={busy} onClick={() => void act(coop.id, 'leave')}>
                    退出
                  </Button>
                )}
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}

// ---------------- N07 收藏图鉴 ----------------

interface CollectionEntryRow {
  entryCode: string
  name: string
  hint?: string
  unlocked: boolean
  firstPetName?: string
}

function CollectionTab() {
  const [entries, setEntries] = useState<CollectionEntryRow[]>([])
  const [stats, setStats] = useState<{ total: number; unlocked: number } | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      const [listRes, statsRes] = await Promise.all([getCollection(), getCollectionStats()])
      if (listRes.data.success && listRes.data.data) {
        setEntries(listRes.data.data as unknown as CollectionEntryRow[])
      }
      if (statsRes.data.success && statsRes.data.data) {
        setStats({
          total: Number(statsRes.data.data.total ?? 0),
          unlocked: Number(statsRes.data.data.unlocked ?? 0),
        })
      }
      setLoading(false)
    })()
  }, [])

  return (
    <div className={styles.section}>
      <div className={styles.sectionTitle}>
        <span>📖 收藏图鉴</span>
        {stats && (
          <span className={styles.meta}>
            已解锁 {stats.unlocked}/{stats.total}
          </span>
        )}
      </div>
      {loading ? (
        <span className={styles.meta}>加载中…</span>
      ) : entries.length === 0 ? (
        <Empty description="暂无图鉴条目" />
      ) : (
        <div className={styles.collectionGrid}>
          {entries.map((entry) => (
            <div
              key={entry.entryCode}
              className={`${styles.collectionCell} ${entry.unlocked ? '' : styles.collectionLocked}`}
            >
              <span className={styles.entryName}>{entry.unlocked ? entry.name : '？？？'}</span>
              <span className={styles.entryHint}>
                {entry.unlocked
                  ? entry.firstPetName
                    ? `首次获得：${entry.firstPetName}`
                    : '已解锁'
                  : (entry.hint ?? '尚未解锁')}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface PetPlayPanelProps {
  pet: PetInfo
}

/**
 * 玩法面板（N 系列 / §7.4-§7.7）：接球小游戏、有限托管、离线摘要、好友合作、收藏图鉴。
 *
 * 与其它面板同约定：规则/时窗/奖励全部服务端权威（§7.4 不信客户端分数）；
 * 摘要确认只推进展示上界 throughAt（BE-10）；合作领取按 claim 唯一事实幂等（BE-01）。
 */
export default function PetPlayPanel({ pet }: PetPlayPanelProps) {
  const [tab, setTab] = useState<PlayTab>('minigame')

  return (
    <div className={styles.panel}>
      <Select
        size="small"
        style={{ width: 200 }}
        value={tab}
        onChange={(v) => setTab(v as PlayTab)}
        options={PLAY_TABS.map((t) => ({ value: t.key, label: `${t.emoji} ${t.label}` }))}
      />
      {tab === 'minigame' && <MinigameTab pet={pet} />}
      {tab === 'custody' && <CustodyTab />}
      {tab === 'digest' && <DigestTab />}
      {tab === 'coop' && <CoopTab />}
      {tab === 'collection' && <CollectionTab />}
    </div>
  )
}
