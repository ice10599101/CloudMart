import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, Button, Empty, Input, Modal, Segmented, Spin, Switch, Tag } from 'antd'
import { history } from 'umi'
import {
  acceptPetBattle,
  challengePetBattle,
  claimPetBottle,
  claimPetStudy,
  claimPetWork,
  cleanPet,
  createPet,
  declinePetBattle,
  feedPet,
  getMyPet,
  getPetBottleStatus,
  listPetAchievements,
  listPetBattleHistory,
  listPetChatHistory,
  listPetJobs,
  listPetOpponents,
  listPetReminders,
  listPetStudies,
  playWithPet,
  renamePet,
  restPet,
  sendPetChat,
  startPetBottle,
  startPetStudy,
  startPetWork,
  updatePetPrivacy,
  type PetAchievement,
  type PetBattleItem,
  type PetBottleStatus,
  type PetChatMessage,
  type PetInfo,
  type PetJobItem,
  type PetOpponent,
  type PetReminder,
  type PetStudyItem,
} from '@/api/pet'
import PetStage, { type PetStageHandle } from '@/components/PetStage'
import type { BattleRound, PetDisplayState, PetIntentAction } from '@/components/PetStage/bridge'
import { useAuthStore } from '@/stores/auth'
import styles from './PetHome.module.css'

/**
 * 社区宠物主页（实施文档 §3）。
 *
 * 职责：宿主壳层——登录守卫、领养向导、Cocos 舞台托管、功能面板（打工/读书/捞瓶/对战/聊天/成就）、
 * API 调用与结果回灌。所有数值来自服务端，本页面不计算任何业务数值。
 */

const SPECIES_OPTIONS = [
  { value: 'CAT', emoji: '🐱', label: '橘猫' },
  { value: 'DOG', emoji: '🐶', label: '柴犬' },
  { value: 'RABBIT', emoji: '🐰', label: '兔子' },
  { value: 'FOX', emoji: '🦊', label: '小狐狸' },
  { value: 'PANDA', emoji: '🐼', label: '熊猫' },
] as const

const PERSONALITY_OPTIONS = [
  { value: 'LIVELY', label: '活泼' },
  { value: 'GENTLE', label: '温柔' },
  { value: 'TSUNDERE', label: '傲娇' },
  { value: 'SIMPLE', label: '憨厚' },
  { value: 'COOL', label: '高冷' },
  { value: 'CHATTERBOX', label: '话痨' },
] as const

const SPECIES_EMOJI: Record<string, string> = {
  CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼',
}

const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
}

type PanelKey = 'home' | 'work' | 'study' | 'bottle' | 'battle' | 'chat' | 'achievements'

const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '小窝', emoji: '🏠' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
]

/** PetInfo → Cocos 展示状态映射（零计算，纯搬运服务端数据） */
function toDisplayState(pet: PetInfo): PetDisplayState {
  return {
    name: pet.name,
    species: pet.species,
    growthStage: pet.growthStage,
    level: pet.level,
    expPercent: pet.expToNext > 0 ? (pet.exp / pet.expToNext) * 100 : 0,
    hp: pet.hp,
    maxHp: pet.maxHp,
    hunger: pet.hunger,
    happiness: pet.happiness,
    energy: pet.energy,
    cleanliness: pet.cleanliness,
    status: pet.status,
    speech: undefined,
  }
}

interface PetStateBarsProps {
  pet: PetInfo
}

/** 状态条（原生镜像展示，与 Cocos 场景互为冗余；数据同源服务端） */
function PetStateBars({ pet }: PetStateBarsProps) {
  const rows = [
    { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: '#ff6c6c' },
    { label: '🍖 饱食', value: pet.hunger, max: 100, color: '#ffb258' },
    { label: '💗 心情', value: pet.happiness, max: 100, color: '#ff69b4' },
    { label: '⚡ 精力', value: pet.energy, max: 100, color: '#62d88a' },
    { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: '#60beff' },
  ]
  return (
    <div className={styles.stateBars}>
      {rows.map((row) => (
        <div key={row.label} className={styles.stateRow}>
          <span className={styles.stateLabel}>{row.label}</span>
          <div className={styles.stateTrack}>
            <div
              className={styles.stateFill}
              style={{ width: `${Math.max(0, Math.min(100, (row.value / row.max) * 100))}%`, background: row.color }}
            />
          </div>
          <span className={styles.stateValue}>
            {row.value}/{row.max}
          </span>
        </div>
      ))}
      <div className={styles.attributes}>
        <Tag>💪 力量 {pet.strength}</Tag>
        <Tag>🧠 智力 {pet.intelligence}</Tag>
        <Tag>🏃 敏捷 {pet.agility}</Tag>
        <Tag>✨ 魅力 {pet.charm}</Tag>
        <Tag color="gold">经验 {pet.exp}/{pet.expToNext}</Tag>
      </div>
    </div>
  )
}

interface ActivityCardProps {
  pet: PetInfo
  onClaimWork: () => void
  onClaimStudy: () => void
  onOpenBottle: () => void
}

/** 进行中/可领取活动卡片（倒计时只做展示，完成判定在服务端） */
function ActivityCard({ pet, onClaimWork, onClaimStudy, onOpenBottle }: ActivityCardProps) {
  const [remaining, setRemaining] = useState(0)
  const finishedAt = pet.activityFinishedAt ? new Date(pet.activityFinishedAt.replace(' ', 'T')) : null

  useEffect(() => {
    if (!finishedAt || !pet.activityType) {
      return
    }
    const tick = () => setRemaining(Math.max(0, Math.round((finishedAt.getTime() - Date.now()) / 1000)))
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [finishedAt, pet.activityType])

  if (pet.claimableActivityType) {
    const labels: Record<string, string> = {
      WORK: '打工结束啦，快来领取奖励！',
      STUDY: '我读完啦！领取学习奖励',
      BOTTLE_FISHING: '我捞到漂流瓶啦！快去看看',
    }
    return (
      <div className={`${styles.activityCard} ${styles.activityReady}`}>
        <span>🎉 {labels[pet.claimableActivityType] || '有任务可以领取啦'}</span>
        <Button type="primary" size="small" onClick={
          pet.claimableActivityType === 'WORK' ? onClaimWork
            : pet.claimableActivityType === 'STUDY' ? onClaimStudy : onOpenBottle
        }>
          去领取
        </Button>
      </div>
    )
  }
  if (pet.activityType && remaining > 0) {
    const names: Record<string, string> = {
      WORK: '打工中', STUDY: '读书中', BOTTLE_FISHING: '在海边捞漂流瓶…',
    }
    const minutes = Math.floor(remaining / 60)
    const seconds = remaining % 60
    return (
      <div className={styles.activityCard}>
        <span>🐾 {names[pet.activityType] || '忙碌中'} · 剩余 {minutes}:{String(seconds).padStart(2, '0')}</span>
        <span className={styles.activityHint}>关掉页面也没关系，宠物会自己完成</span>
      </div>
    )
  }
  return null
}

interface AdoptWizardProps {
  onAdopted: () => void
}

/** 领养向导：选种类 → 选性格 → 取名 */
function AdoptWizard({ onAdopted }: AdoptWizardProps) {
  const { message } = App.useApp()
  const [species, setSpecies] = useState<string>('CAT')
  const [personality, setPersonality] = useState<string>('LIVELY')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    if (!name.trim()) {
      message.warning('先给宠物取个名字吧')
      return
    }
    setSubmitting(true)
    try {
      const { data: res } = await createPet({
        name: name.trim(),
        species: species as typeof SPECIES_OPTIONS[number]['value'],
        personality: personality as typeof PERSONALITY_OPTIONS[number]['value'],
      })
      if (res.success) {
        message.success('领养成功！好好照顾它哦～')
        onAdopted()
      }
    } catch (error) {
      // 拦截器已提示；并发领养 409 也在此兜底刷新
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.adopt}>
      <h2 className={styles.adoptTitle}>🐾 领养一只属于你的宠物</h2>
      <p className={styles.adoptDesc}>它会是你在社区里的小伙伴：陪你打工、读书、捞漂流瓶、聊天</p>
      <div className={styles.speciesRow}>
        {SPECIES_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`${styles.speciesCard} ${species === option.value ? styles.speciesActive : ''}`}
            onClick={() => setSpecies(option.value)}
          >
            <span className={styles.speciesEmoji}>{option.emoji}</span>
            <span>{option.label}</span>
          </button>
        ))}
      </div>
      <div className={styles.personalityRow}>
        {PERSONALITY_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`${styles.personalityChip} ${personality === option.value ? styles.personalityActive : ''}`}
            onClick={() => setPersonality(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
      <div className={styles.nameRow}>
        <Input
          value={name}
          maxLength={12}
          placeholder="给它取个名字（1-12 字）"
          onChange={(e) => setName(e.target.value)}
          onPressEnter={submit}
        />
        <Button type="primary" loading={submitting} onClick={submit}>
          领养它
        </Button>
      </div>
    </div>
  )
}

interface WorkPanelProps {
  pet: PetInfo
  onRefresh: () => void
}

/** 打工面板：岗位列表（服务端下发参数）+ 开工 + 领取 */
function WorkPanel({ pet, onRefresh }: WorkPanelProps) {
  const { message } = App.useApp()
  const [jobs, setJobs] = useState<PetJobItem[]>([])
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState<string | null>(null)
  const [claiming, setClaiming] = useState(false)

  useEffect(() => {
    let stale = false
    listPetJobs().then(({ data: res }) => {
      if (!stale && res.success) setJobs(res.data || [])
    }).finally(() => { if (!stale) setLoading(false) })
    return () => { stale = true }
  }, [pet.level])

  const start = async (configId: number | string) => {
    setStarting(String(configId))
    try {
      const { data: res } = await startPetWork(configId)
      if (res.success) {
        message.success('开工啦！干完活记得来领奖励')
        onRefresh()
      }
    } catch {
      // 拦截器已提示（互斥/等级/精力不足）
    } finally {
      setStarting(null)
    }
  }

  const claim = async () => {
    setClaiming(true)
    try {
      const { data: res } = await claimPetWork()
      if (res.success) {
        message.success('奖励已领取：经验 + 星光！')
        onRefresh()
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_ACTIVITY_ALREADY_CLAIMED') {
        message.info('奖励已经领取过啦')
        onRefresh()
      }
    } finally {
      setClaiming(false)
    }
  }

  if (loading) return <Spin />
  if (!jobs.length) return <Empty description="暂时没有可接的岗位" />
  return (
    <div className={styles.jobList}>
      {jobs.map((job) => (
        <div key={job.configId} className={styles.jobCard}>
          <div className={styles.jobInfo}>
            <strong>{job.name}</strong>
            <span className={styles.jobDesc}>{job.description}</span>
            <span className={styles.jobMeta}>
              ⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost} · 🍖-{job.hungerCost} · ✨+{job.expReward} · ⭐+{job.currencyReward}
            </span>
          </div>
          {job.eligible ? (
            <Button type="primary" size="small" loading={starting === String(job.configId)} onClick={() => start(job.configId)}>
              接单
            </Button>
          ) : (
            <Tag>Lv.{job.requiredLevel} 解锁</Tag>
          )}
        </div>
      ))}
      {pet.claimableActivityType === 'WORK' && (
        <Button type="primary" block loading={claiming} onClick={claim}>
          领取打工奖励
        </Button>
      )}
    </div>
  )
}

interface StudyPanelProps {
  pet: PetInfo
  onRefresh: () => void
}

/** 读书面板：课程列表 + 开课 + 领取 */
function StudyPanel({ pet, onRefresh }: StudyPanelProps) {
  const { message } = App.useApp()
  const [studies, setStudies] = useState<PetStudyItem[]>([])
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState<string | null>(null)
  const [claiming, setClaiming] = useState(false)

  useEffect(() => {
    let stale = false
    listPetStudies().then(({ data: res }) => {
      if (!stale && res.success) setStudies(res.data || [])
    }).finally(() => { if (!stale) setLoading(false) })
    return () => { stale = true }
  }, [pet.level])

  const start = async (configId: number | string) => {
    setStarting(String(configId))
    try {
      const { data: res } = await startPetStudy(configId)
      if (res.success) {
        message.success('开始读书啦，读完后智力会提升！')
        onRefresh()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setStarting(null)
    }
  }

  const claim = async () => {
    setClaiming(true)
    try {
      const { data: res } = await claimPetStudy()
      if (res.success) {
        message.success('学习奖励已领取！')
        onRefresh()
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_ACTIVITY_ALREADY_CLAIMED') {
        message.info('奖励已经领取过啦')
        onRefresh()
      }
    } finally {
      setClaiming(false)
    }
  }

  if (loading) return <Spin />
  if (!studies.length) return <Empty description="暂时没有可上的课程" />
  return (
    <div className={styles.jobList}>
      {studies.map((study) => (
        <div key={study.configId} className={styles.jobCard}>
          <div className={styles.jobInfo}>
            <strong>{study.name}</strong>
            <span className={styles.jobDesc}>{study.description}</span>
            <span className={styles.jobMeta}>
              <Tag color="blue">{study.category}</Tag>
              ⏱ {Math.round(study.durationSeconds / 60)} 分钟 · ⚡-{study.energyCost} · ✨+{study.expReward} · 🧠+{study.intelligenceReward}
            </span>
          </div>
          {study.eligible ? (
            <Button type="primary" size="small" loading={starting === String(study.configId)} onClick={() => start(study.configId)}>
              上课
            </Button>
          ) : (
            <Tag>Lv.{study.requiredLevel} 解锁</Tag>
          )}
        </div>
      ))}
      {pet.claimableActivityType === 'STUDY' && (
        <Button type="primary" block loading={claiming} onClick={claim}>
          领取学习奖励
        </Button>
      )}
    </div>
  )
}

interface BottlePanelProps {
  onRefresh: () => void
}

/** 捞瓶面板：状态/开始/领取；捞到后跳转现有漂流瓶页（复用 mall-wish，无第二套瓶子） */
function BottlePanel({ onRefresh }: BottlePanelProps) {
  const { message } = App.useApp()
  const [status, setStatus] = useState<PetBottleStatus | null>(null)
  const [busy, setBusy] = useState(false)
  const [remaining, setRemaining] = useState(0)

  const refreshStatus = useCallback(async () => {
    const { data: res } = await getPetBottleStatus()
    if (res.success) {
      setStatus(res.data)
      setRemaining(res.data?.remainingSeconds ?? 0)
    }
  }, [])

  useEffect(() => {
    refreshStatus()
  }, [refreshStatus])

  useEffect(() => {
    if (remaining <= 0) return
    const timer = window.setInterval(() => setRemaining((s) => Math.max(0, s - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [remaining])

  const start = async () => {
    setBusy(true)
    try {
      const { data: res } = await startPetBottle()
      if (res.success) {
        message.success('宠物出发去海边啦，30 分钟后回来！')
        await refreshStatus()
        onRefresh()
      }
    } catch {
      // 拦截器已提示（冷却/互斥/精力）
    } finally {
      setBusy(false)
    }
  }

  const claim = async () => {
    setBusy(true)
    try {
      const { data: res } = await claimPetBottle()
      if (res.success) {
        const result = res.data?.result ? (JSON.parse(res.data.result) as { outcome?: string }) : null
        if (result?.outcome === 'CAUGHT') {
          message.success('捞到漂流瓶啦！即将打开…')
          onRefresh()
          // 瓶子已按主人身份落入 mall-wish 捞瓶列表，直接跳转查看
          setTimeout(() => history.push('/wish/drift-bottle'), 800)
        } else if (result?.outcome === 'EMPTY') {
          message.info('这次空手而归…不过宠物获得了经验！')
          await refreshStatus()
          onRefresh()
        } else {
          message.warning('心愿服务暂时不可用，稍后可重试领取')
        }
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_ACTIVITY_ALREADY_CLAIMED') {
        message.info('这次结果已经领取过啦')
        await refreshStatus()
      }
    } finally {
      setBusy(false)
    }
  }

  if (!status) return <Spin />
  const successPercent = Math.round(status.estimatedSuccessRate * 100)
  return (
    <div className={styles.bottlePanel}>
      <div className={styles.bottleStatus}>
        <span className={styles.bottleArea}>🌊 捞瓶区域：{status.unlockedArea}（Lv 越高解锁越远）</span>
        <span>估算成功率 <strong>{successPercent}%</strong>（敏捷与等级加成，服务端结算）</span>
      </div>
      {status.fishing ? (
        <div className={styles.activityCard}>
          <span>🐾 捞瓶中…剩余 {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}</span>
          <span className={styles.activityHint}>关掉页面也没关系，捞到后会收到通知</span>
        </div>
      ) : status.canClaim ? (
        <Button type="primary" block loading={busy} onClick={claim}>
          查看捞瓶结果
        </Button>
      ) : status.lastOutcome === 'FAILED' ? (
        <Button type="primary" block loading={busy} onClick={claim}>
          上次服务波动，重试领取
        </Button>
      ) : (
        <Button type="primary" block loading={busy} disabled={status.cooldownRemainingSeconds > 0} onClick={start}>
          {status.cooldownRemainingSeconds > 0
            ? `冷却中（${Math.ceil(status.cooldownRemainingSeconds / 60)} 分钟）`
            : '让宠物去捞漂流瓶（30 分钟）'}
        </Button>
      )}
      {status.lastOutcome === 'EMPTY' && !status.canClaim && !status.fishing && (
        <p className={styles.bottleHint}>上次空手而归，敏捷越高成功率越高哦</p>
      )}
    </div>
  )
}

interface BattlePanelProps {
  onRefresh: () => void
  onBattleResult: (battle: PetBattleItem) => void
}

/** 对战面板：候选 + 挑战 + 待应战 + 历史（计算在服务端，本页面只发起意图与播放流水） */
function BattlePanel({ onRefresh, onBattleResult }: BattlePanelProps) {
  const { message } = App.useApp()
  const [opponents, setOpponents] = useState<PetOpponent[]>([])
  const [history, setHistory] = useState<PetBattleItem[]>([])
  const [loading, setLoading] = useState(true)
  const [challenging, setChallenging] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    const [opponentRes, historyRes] = await Promise.all([
      listPetOpponents(),
      listPetBattleHistory({ page: 1, pageSize: 10 }),
    ])
    if (opponentRes.data.success) setOpponents(opponentRes.data.data || [])
    if (historyRes.data.success) setHistory(historyRes.data.data || [])
    setLoading(false)
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const challenge = async (opponent: PetOpponent) => {
    setChallenging(String(opponent.petId))
    try {
      const { data: res } = await challengePetBattle(
        opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId },
      )
      if (res.success) {
        if (res.data.status === 'FINISHED' && res.data.rounds) {
          onBattleResult(res.data)
          onRefresh()
        } else {
          message.success('已发起挑战，等对方应战吧！')
        }
        await refresh()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setChallenging(null)
    }
  }

  const respond = async (battle: PetBattleItem, accept: boolean) => {
    try {
      const { data: res } = accept ? await acceptPetBattle(battle.battleId) : await declinePetBattle(battle.battleId)
      if (res.success) {
        if (accept && res.data.status === 'FINISHED' && res.data.rounds) {
          onBattleResult(res.data)
        } else if (!accept) {
          message.info('已婉拒这场挑战')
        }
        await refresh()
        onRefresh()
      }
    } catch {
      // 拦截器已提示（已被处理 409）
    }
  }

  if (loading) return <Spin />
  const pending = history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER')
  return (
    <div className={styles.battlePanel}>
      {pending.length > 0 && (
        <div className={styles.pendingBox}>
          <strong>⚔️ 收到挑战</strong>
          {pending.map((battle) => (
            <div key={battle.battleId} className={styles.pendingRow}>
              <span>来自宠物 #{battle.attackerPetId} 的挑战</span>
              <span>
                <Button size="small" type="primary" onClick={() => respond(battle, true)}>应战</Button>{' '}
                <Button size="small" onClick={() => respond(battle, false)}>婉拒</Button>
              </span>
            </div>
          ))}
        </div>
      )}
      <h4 className={styles.sectionTitle}>选择对手</h4>
      <div className={styles.opponentGrid}>
        {opponents.map((opponent) => (
          <div key={`${opponent.petId}-${opponent.name}`} className={styles.opponentCard}>
            <span className={styles.opponentEmoji}>{SPECIES_EMOJI[opponent.species] || '🐾'}</span>
            <strong>{opponent.name}</strong>
            <span className={styles.opponentMeta}>
              Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}
            </span>
            <Button
              size="small"
              type="primary"
              loading={challenging === String(opponent.petId) && opponent.petId !== 0}
              disabled={challenging !== null && challenging !== String(opponent.petId)}
              onClick={() => challenge(opponent)}
            >
              挑战
            </Button>
          </div>
        ))}
      </div>
      <h4 className={styles.sectionTitle}>最近对战</h4>
      {history.filter((battle) => battle.status !== 'PENDING').length === 0 ? (
        <p className={styles.bottleHint}>还没有对战记录，去挑战一只吧</p>
      ) : (
        <div className={styles.historyList}>
          {history
            .filter((battle) => battle.status !== 'PENDING')
            .map((battle) => (
              <div key={battle.battleId} className={styles.historyRow}>
                <span>
                  {battle.status === 'FINISHED'
                    ? battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId)
                      ? '🏆 胜利'
                      : '💧 战败'
                    : battle.status === 'DECLINED'
                      ? '🚫 对方婉拒'
                      : '⌛ 对方未应战'}
                  {' · '}
                  {battle.role === 'ATTACKER' ? '我发起' : '我应战'}
                </span>
                <span>+{battle.expReward} 经验{battle.currencyReward > 0 ? ` · +${battle.currencyReward} 星光` : ''}</span>
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

interface ChatPanelProps {
  onRefresh: () => void
}

/** 聊天面板：三层结构（固定行为/状态/AI）由服务端裁决；本面板只负责收发与展示 */
function ChatPanel({ onRefresh }: ChatPanelProps) {
  const { message } = App.useApp()
  const [messages, setMessages] = useState<PetChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    listPetChatHistory({ pageSize: 30 }).then(({ data: res }) => {
      if (res.success) setMessages((res.data || []).slice().reverse())
    })
  }, [])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages])

  const send = async () => {
    const text = input.trim()
    if (!text || sending) {
      return
    }
    setSending(true)
    setInput('')
    try {
      const { data: res } = await sendPetChat(text)
      if (res.success && res.data) {
        setMessages((prev) => [...prev, {
          messageId: `${res.data.messageId}-u`,
          role: 'USER',
          content: text,
          isAiReply: false,
          createdAt: null,
        }, res.data])
        onRefresh()
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_AI_RATE_LIMITED') {
        message.warning('今天聊得够多啦，宠物要睡觉了，明天再来吧')
      }
    } finally {
      setSending(false)
    }
  }

  return (
    <div className={styles.chatPanel}>
      <div ref={listRef} className={styles.chatList}>
        {messages.length === 0 && <p className={styles.bottleHint}>和宠物说点什么吧，它会记住你喜欢的事～</p>}
        {messages.map((item) => (
          <div key={item.messageId} className={item.role === 'USER' ? styles.chatMine : styles.chatPet}>
            <span className={styles.chatBubbleText}>{item.content}</span>
            {item.role === 'PET' && !item.isAiReply && (
              <span className={styles.chatTag}>模板回复</span>
            )}
          </div>
        ))}
      </div>
      <div className={styles.chatInputRow}>
        <Input
          value={input}
          maxLength={500}
          placeholder="跟宠物聊聊…（每日 20 条）"
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={send}
        />
        <Button type="primary" loading={sending} onClick={send}>
          发送
        </Button>
      </div>
    </div>
  )
}

/** 成就墙：全部启用成就 + 达成状态（灰显未达成） */
function AchievementsPanel() {
  const [items, setItems] = useState<PetAchievement[]>([])
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let stale = false
    Promise.all([listPetAchievements(), listPetReminders()]).then(([achRes, remRes]) => {
      if (!stale) {
        if (achRes.data.success) setItems(achRes.data.data || [])
        if (remRes.data.success) setReminders(remRes.data.data || [])
        setLoading(false)
      }
    })
    return () => { stale = true }
  }, [])

  if (loading) return <Spin />
  return (
    <div>
      <div className={styles.achGrid}>
        {items.map((item) => (
          <div key={item.achievementId} className={`${styles.achCard} ${item.achieved ? '' : styles.achLocked}`}>
            <span className={styles.achIcon}>{item.icon}</span>
            <strong>{item.name}</strong>
            <span className={styles.achDesc}>{item.description}</span>
            <Tag color={item.achieved ? 'gold' : 'default'}>
              {item.achieved ? '已达成' : `+${item.expReward} 经验`}
            </Tag>
          </div>
        ))}
      </div>
      {reminders.length > 0 && (
        <>
          <h4 className={styles.sectionTitle}>宠物动态</h4>
          <div className={styles.historyList}>
            {reminders.map((reminder) => (
              <div key={reminder.notificationId} className={styles.historyRow}>
                <span>{reminder.content}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

interface RenameModalProps {
  pet: PetInfo
  open: boolean
  onClose: () => void
  onRenamed: () => void
}

function RenameModal({ pet, open, onClose, onRenamed }: RenameModalProps) {
  const { message } = App.useApp()
  const [name, setName] = useState(pet.name)
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!name.trim()) {
      return
    }
    setSaving(true)
    try {
      const { data: res } = await renamePet({ name: name.trim() })
      if (res.success) {
        message.success('改名成功！')
        onRenamed()
        onClose()
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_RENAME_COOLDOWN') {
        message.warning('改名太频繁啦，30 天内只能改一次')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="给宠物改名" open={open} onCancel={onClose} onOk={save} confirmLoading={saving} okText="保存">
      <Input value={name} maxLength={12} onChange={(e) => setName(e.target.value)} />
      <p className={styles.bottleHint}>30 天可以改一次名</p>
    </Modal>
  )
}

export default function PetHomePage() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [noPet, setNoPet] = useState(false)
  const [panel, setPanel] = useState<PanelKey>('home')
  const [renameOpen, setRenameOpen] = useState(false)
  const stageRef = useRef<PetStageHandle>(null)

  useEffect(() => {
    if (!user && !userLoading) {
      history.replace('/login?redirect=/pet')
    }
  }, [user, userLoading])

  const syncStage = useCallback((next: PetInfo) => {
    stageRef.current?.post({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [])

  const refresh = useCallback(async () => {
    try {
      const { data: res } = await getMyPet()
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
        syncStage(res.data)
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_NOT_FOUND') {
        setNoPet(true)
      }
    } finally {
      setLoading(false)
    }
  }, [syncStage])

  useEffect(() => {
    if (user) {
      refresh()
    }
  }, [user, refresh])

  /** Cocos 意图 → 宿主 API 调用（数值/幂等全部服务端） */
  const handleIntent = useCallback(async (action: PetIntentAction) => {
    if (!pet) {
      return
    }
    const post = (ok: boolean, actionName: string, fallbackMessage: string) => {
      stageRef.current?.post({ source: 'pet-host', type: 'actionResult', action: actionName, ok, message: fallbackMessage })
    }
    if (action === 'openWork') { setPanel('work'); return }
    if (action === 'openStudy') { setPanel('study'); return }
    if (action === 'openBottle') { setPanel('bottle'); return }
    if (action === 'openBattle') { setPanel('battle'); return }
    if (action === 'openChat') { setPanel('chat'); return }
    if (action === 'openAchievements') { setPanel('achievements'); return }
    try {
      if (action === 'feed') {
        const { data: res } = await feedPet()
        if (res.success && res.data) { setPet(res.data); syncStage(res.data); post(true, action, '饱食度 +30') }
      } else if (action === 'play') {
        const { data: res } = await playWithPet()
        if (res.success && res.data) { setPet(res.data); syncStage(res.data); post(true, action, '心情 +20') }
      } else if (action === 'clean') {
        const { data: res } = await cleanPet()
        if (res.success && res.data) { setPet(res.data); syncStage(res.data); post(true, action, '清洁度 +40') }
      } else if (action === 'rest') {
        const { data: res } = await restPet()
        if (res.success && res.data) { setPet(res.data); syncStage(res.data); post(true, action, '精力回满！') }
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      const hints: Record<string, string> = {
        PET_STATE_FULL: '已经吃饱/很干净啦', PET_ENERGY_INSUFFICIENT: '没有力气了，休息一下吧',
        PET_ACTIVITY_CONFLICT: '宠物正在忙别的事',
        PET_INTERACTION_RATE_LIMITED: '今天喂得够多啦',
      }
      post(false, action, hints[code ?? ''] || '现在不行哦')
    }
  }, [pet, syncStage])

  const handleBattleResult = useCallback((battle: PetBattleItem) => {
    if (!battle.rounds) {
      return
    }
    const rounds = JSON.parse(battle.rounds) as BattleRound[]
    const myPetId = battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId
    stageRef.current?.post({
      source: 'pet-host',
      type: 'battleRounds',
      rounds,
      won: battle.winnerPetId === myPetId,
    })
  }, [])

  const displayState = useMemo(() => (pet ? toDisplayState(pet) : null), [pet])

  if (loading || userLoading) {
    return <div className={styles.pageLoading}><Spin size="large" /></div>
  }
  if (noPet || !pet) {
    return (
      <div className={styles.page}>
        <AdoptWizard onAdopted={() => { setNoPet(false); setLoading(true); refresh() }} />
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <div className={styles.stageCard}>
        <div className={styles.stageHeader}>
          <span className={styles.stageTitle}>
            {SPECIES_EMOJI[pet.species]} {pet.name} · Lv.{pet.level} · {STATUS_LABEL[pet.status] || '悠闲中'}
          </span>
          <span className={styles.stageActions}>
            <Button size="small" onClick={() => setRenameOpen(true)}>改名</Button>
            <span className={styles.privacyRow}>
              主页展示
              <Switch
                size="small"
                checked={pet.isPublic}
                onChange={async (checked) => {
                  const { data: res } = await updatePetPrivacy({ isPublic: checked })
                  if (res.success) {
                    message.success(checked ? '已公开到个人主页' : '已隐藏，只有你能看到它')
                    refresh()
                  }
                }}
              />
            </span>
          </span>
        </div>
        <PetStage
          ref={stageRef}
          pet={displayState}
          onIntent={handleIntent}
          onPetTapped={() => {/* 点击反馈由 Cocos 场景处理 */ }}
          fallback={
            <div className={styles.nativeStage}>
              <span className={styles.nativePet}>{SPECIES_EMOJI[pet.species] || '🐾'}</span>
              <span className={styles.nativeSpeech}>Cocos 舞台未部署 · 原生模式</span>
            </div>
          }
        />
        <PetStateBars pet={pet} />
        {pet.activityFinishedAt || pet.claimableActivityType ? (
          <ActivityCard
            pet={pet}
            onClaimWork={() => handleIntent('openWork')}
            onClaimStudy={() => handleIntent('openStudy')}
            onOpenBottle={() => setPanel('bottle')}
          />
        ) : null}
      </div>

      <Segmented
        className={styles.panelTabs}
        block
        value={panel}
        onChange={(key) => setPanel(key as PanelKey)}
        options={PANELS.map((item) => ({ value: item.key, label: `${item.emoji} ${item.label}` }))}
      />

      <div className={styles.panelBody}>
        {panel === 'work' && <WorkPanel pet={pet} onRefresh={refresh} />}
        {panel === 'study' && <StudyPanel pet={pet} onRefresh={refresh} />}
        {panel === 'bottle' && <BottlePanel onRefresh={refresh} />}
        {panel === 'battle' && <BattlePanel onRefresh={refresh} onBattleResult={handleBattleResult} />}
        {panel === 'chat' && <ChatPanel onRefresh={refresh} />}
        {panel === 'achievements' && <AchievementsPanel />}
        {panel === 'home' && (
          <div className={styles.homePanel}>
            <h4 className={styles.sectionTitle}>{pet.name} 的养成日常</h4>
            <ul className={styles.tipsList}>
              <li>🍖 饱食和🧼清洁会随时间自然下降，记得回来照顾它</li>
              <li>💼 打工赚星光，📚 读书涨智力（智力影响学习与捞瓶收益）</li>
              <li>🍾 宠物可以定时帮你捞社区漂流瓶，捞到会主动提醒你</li>
              <li>⚔️ 对战由服务端计算，输了也有经验，放心去挑战</li>
              <li>💬 和它聊聊天，它会记住你的喜好哦</li>
            </ul>
          </div>
        )}
      </div>

      <RenameModal pet={pet} open={renameOpen} onClose={() => setRenameOpen(false)} onRenamed={refresh} />
    </div>
  )
}
