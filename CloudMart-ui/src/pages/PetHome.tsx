import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, Button, Empty, Input, Modal, Segmented, Spin, Switch, Tag } from 'antd'
import { history } from 'umi'
import {
  acceptPetBattle,
  activatePet,
  buyPetItem,
  challengePetBattle,
  claimPetBottle,
  claimPetEvent,
  claimPetStudy,
  claimPetWork,
  cleanPet,
  createPet,
  declinePetBattle,
  equipPetItem,
  evolvePet,
  feedPet,
  getMyPet,
  getPetBattle,
  getPetBottleStatus,
  getPetEvolution,
  getPetReminderUnreadCount,
  getPetShop,
  listMyPets,
  listPetAchievements,
  listPetBattleHistory,
  listPetChatHistory,
  listPetEvents,
  listPetInventory,
  listPetSkills,
  listPetVisitNeighbors,
  getPetRankings,
  getPetShareCard,
  listPetJobs,
  listPetOpponents,
  listPetReminders,
  listPetStudies,
  learnPetSkill,
  removePetSkin,
  unequipPetItem,
  updateAppearance,
  playWithPet,
  renamePet,
  restPet,
  sendPetChat,
  startPetBottle,
  startPetStudy,
  startPetWork,
  updatePetPrivacy,
  visitNeighborPet,
  wearPetSkin,
  type PetAchievement,
  type PetBattleItem,
  type PetBottleStatus,
  type PetChatMessage,
  type PetEventItem,
  type PetEvolutionStatus,
  type PetInfo,
  type PetInventoryItem,
  type PetItemType,
  type PetJobItem,
  type PetOpponent,
  type PetRankingResult,
  type PetRankingType,
  type PetReminder,
  type PetShopItem,
  type PetSkillItem,
  type PetStudyItem,
  type PetSummary,
  type PetVisitNeighbor,
} from '@/api/pet'
import { markAllAsRead, markAsRead } from '@/api/notification'
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

const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }

/** 宠物当前状态的口语化台词（展示用文案，不含任何业务数值判定） */
const STATUS_SPEECH: Record<string, string> = {
  IDLE: '主人，陪我玩一会嘛～',
  WORKING: '我正在打工赚星光呢！',
  STUDYING: '嘘——我在读书，别打扰我～',
  FISHING: '我去海边看看有没有漂流瓶！',
  RESTING: '呼…让我睡一小会儿…',
}

type PanelKey =
  | 'home' | 'care' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'

const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '小窝', emoji: '🏠' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
  { key: 'rankings', label: '排行', emoji: '📊' },
  { key: 'reminders', label: '提醒', emoji: '🔔' },
]

/** 领养可选外观（与服务端白名单一致：color/accessory） */
const APPEARANCE_COLORS = [
  { value: 'orange', label: '橘色', swatch: '#f0955a' },
  { value: 'gray', label: '灰色', swatch: '#a9aeb8' },
  { value: 'white', label: '白色', swatch: '#f4f4f8' },
  { value: 'brown', label: '棕色', swatch: '#9b6946' },
  { value: 'pink', label: '粉色', swatch: '#f5aabe' },
] as const

const APPEARANCE_ACCESSORIES = [
  { value: 'none', label: '不戴配饰' },
  { value: 'bell', label: '小铃铛' },
  { value: 'bowtie', label: '领结' },
  { value: 'glasses', label: '圆框眼镜' },
  { value: 'scarf', label: '围巾' },
] as const

/** 装备部位中文名（与服务端 PetEquipmentSlot 对应） */
const SLOT_LABEL: Record<string, string> = {
  HAT: '帽子', NECKLACE: '项圈', SCARF: '围巾', BACKPACK: '背包',
}

const RARITY_COLOR: Record<string, string> = { COMMON: 'default', RARE: 'blue', EPIC: 'purple' }

const ITEM_TYPE_LABEL: Record<PetItemType, string> = {
  EQUIPMENT: '装备', SKIN: '皮肤', SKILL_BOOK: '技能书',
}

const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}

/** 外观 JSON 解析（服务端写入的 {color, accessory}；解析失败回落种类配色，不阻断演出） */
function parseAppearance(raw: string | undefined): { color?: string; accessory?: string } {
  if (!raw) {
    return {}
  }
  try {
    const parsed = JSON.parse(raw) as { color?: string; accessory?: string }
    return { color: parsed.color, accessory: parsed.accessory }
  } catch {
    return {}
  }
}

/** PetInfo → Cocos 展示状态映射（零计算，纯搬运服务端数据） */
function toDisplayState(pet: PetInfo): PetDisplayState {
  const appearance = parseAppearance(pet.appearance)
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
    activityName: pet.activityType ?? undefined,
    speech: STATUS_SPEECH[pet.status] ?? undefined,
    color: appearance.color,
    accessory: appearance.accessory,
    evolutionStage: pet.evolutionStage,
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
        <Tag color="blue">{GROWTH_STAGE_LABEL[pet.growthStage] || pet.growthStage}</Tag>
        <Tag>💪 力量 {pet.strength}</Tag>
        <Tag>🧠 智力 {pet.intelligence}</Tag>
        <Tag>🏃 敏捷 {pet.agility}</Tag>
        <Tag>✨ 魅力 {pet.charm}</Tag>
        <Tag color="gold">经验 {pet.exp}/{pet.expToNext}</Tag>
        {pet.feedRemainingToday !== null && pet.feedRemainingToday !== undefined && (
          <Tag color="orange">今日可喂食 {pet.feedRemainingToday} 次</Tag>
        )}
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
  const [color, setColor] = useState<string>('orange')
  const [accessory, setAccessory] = useState<string>('none')
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
        color,
        accessory,
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
      <h4 className={styles.sectionTitle}>外观颜色</h4>
      <div className={styles.personalityRow}>
        {APPEARANCE_COLORS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`${styles.personalityChip} ${color === option.value ? styles.personalityActive : ''}`}
            onClick={() => setColor(option.value)}
          >
            <span className={styles.colorDot} style={{ background: option.swatch }} />
            {option.label}
          </button>
        ))}
      </div>
      <h4 className={styles.sectionTitle}>配饰</h4>
      <div className={styles.personalityRow}>
        {APPEARANCE_ACCESSORIES.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`${styles.personalityChip} ${accessory === option.value ? styles.personalityActive : ''}`}
            onClick={() => setAccessory(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
      <h4 className={styles.sectionTitle}>性格</h4>
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
        const result = res.data?.result
          ? (JSON.parse(res.data.result) as { outcome?: string; rarity?: string; specialContent?: string })
          : null
        if (result?.outcome === 'CAUGHT' && result.rarity && result.rarity !== 'NORMAL') {
          // 特殊瓶：稀有瓶/宠物瓶/彩蛋瓶（内容服务端生成，原文档 §19）
          const rarityLabel = result.rarity === 'RARE' ? '稀有瓶' : result.rarity === 'PET' ? '宠物瓶' : '彩蛋瓶'
          Modal.info({
            title: `捞到了${rarityLabel}！`,
            content: result.specialContent || '获得特殊奖励！',
            onOk: () => { onRefresh() },
          })
        } else if (result?.outcome === 'CAUGHT') {
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
  const [replaying, setReplaying] = useState<string | null>(null)

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

  /** 历史回放：拉取含回合流水的详情，交给服务端数据驱动 Cocos 演出（原文档 §63） */
  const replay = async (battleId: number | string) => {
    setReplaying(String(battleId))
    try {
      const { data: res } = await getPetBattle(battleId)
      if (res.success && res.data?.rounds) {
        onBattleResult(res.data)
      } else {
        message.info('这场对战没有可回放的回合记录')
      }
    } catch {
      // 拦截器已提示
    } finally {
      setReplaying(null)
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
                <span className={styles.historyActions}>
                  <span>+{battle.expReward} 经验{battle.currencyReward > 0 ? ` · +${battle.currencyReward} 星光` : ''}</span>
                  {battle.status === 'FINISHED' && (
                    <Button size="small" loading={replaying === String(battle.battleId)} onClick={() => replay(battle.battleId)}>
                      回放
                    </Button>
                  )}
                </span>
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

interface ChatPanelProps {
  onRefresh: () => void
  /** 宠物回复后同步到 Cocos 气泡（宿主与场景互为冗余展示） */
  onReply?: (content: string) => void
}

/** 聊天面板：三层结构（固定行为/状态/AI）由服务端裁决；本面板只负责收发与展示 */
function ChatPanel({ onRefresh, onReply }: ChatPanelProps) {
  const { message } = App.useApp()
  const [messages, setMessages] = useState<PetChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [cursor, setCursor] = useState<number | string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    listPetChatHistory({ pageSize: 30 }).then(({ data: res }) => {
      if (res.success) {
        const page = (res.data || []).slice().reverse()
        setMessages(page)
        // 服务端按 messageId 倒序返回：首页最后一条（时间最早）即下一页游标
        const oldest = page[0]
        if (res.data && res.data.length >= 30 && oldest) {
          setCursor(oldest.messageId)
          setHasMore(true)
        }
      }
    })
  }, [])

  /** 加载更早的历史（cursor 分页，原文档 §26：历史不全量回放） */
  const loadMore = async () => {
    if (!cursor || loadingMore) {
      return
    }
    setLoadingMore(true)
    try {
      const { data: res } = await listPetChatHistory({ cursor, pageSize: 30 })
      if (res.success) {
        const older = (res.data || []).slice().reverse()
        if (older.length === 0) {
          setHasMore(false)
        } else {
          setMessages((prev) => [...older, ...prev])
          const nextCursor = older[0]?.messageId ?? null
          setCursor(nextCursor)
          setHasMore(older.length >= 30)
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoadingMore(false)
    }
  }

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
        onReply?.(res.data.content)
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
        {hasMore && (
          <div className={styles.chatMoreRow}>
            <Button size="small" loading={loadingMore} onClick={loadMore}>加载更早的对话</Button>
          </div>
        )}
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
                <span>
                  {reminder.priority === 'P0' && <Tag color="red">P0</Tag>}
                  {reminder.priority === 'P1' && <Tag color="orange">P1</Tag>}
                  {reminder.content}
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

/**
 * 宠物提醒中心（原文档 §27-31）。
 *
 * 提醒本体落在 mall-notification（type=PET），宠物只是"新的说话方式"；
 * 已读回写复用现有通知接口，不另建已读表。
 */
function RemindersPanel() {
  const { message } = App.useApp()
  const [items, setItems] = useState<PetReminder[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(() => {
    setLoading(true)
    listPetReminders()
      .then(({ data: res }) => { if (res.success) setItems(res.data || []) })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  const markRead = async (item: PetReminder) => {
    if (item.isRead) {
      return
    }
    try {
      await markAsRead(Number(item.notificationId))
      setItems((prev) => prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r)))
    } catch {
      // 已读是弱一致操作，失败不影响查看
    }
  }

  const markAllRead = async () => {
    try {
      await markAllAsRead()
      setItems((prev) => prev.map((r) => ({ ...r, isRead: true })))
      message.success('宠物的话都读完啦')
    } catch {
      // 同上
    }
  }

  if (loading) return <Spin />
  if (items.length === 0) {
    return (
      <Empty description="还没有提醒～宠物会在打工完成、捞到漂流瓶、有人评论你时主动开口" />
    )
  }
  return (
    <div className={styles.reminderPanel}>
      <div className={styles.reminderHeader}>
        <span>
          宠物想对你说（{items.filter((item) => !item.isRead).length} 条未读）
        </span>
        <Button size="small" onClick={markAllRead}>全部已读</Button>
      </div>
      <div className={styles.historyList}>
        {items.map((item) => (
          <div
            key={item.notificationId}
            className={`${styles.reminderRow} ${item.isRead ? '' : styles.reminderUnread}`}
            onClick={() => markRead(item)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter') markRead(item) }}
          >
            <div className={styles.reminderTitleRow}>
              <strong>{item.title}</strong>
              {item.priority === 'P0' && <Tag color="red">P0 重要</Tag>}
              {item.priority === 'P1' && <Tag color="orange">P1</Tag>}
              {item.priority === 'P2' && <Tag>P2</Tag>}
            </div>
            <span className={styles.reminderContent}>{item.content}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/** 外观选项（与服务端白名单一致） */
const COLOR_OPTIONS = ['orange', 'gray', 'white', 'brown', 'pink'] as const
const ACCESSORY_OPTIONS = [
  { value: 'none', label: '无' },
  { value: 'bell', label: '铃铛' },
  { value: 'bowtie', label: '领结' },
  { value: 'glasses', label: '眼镜' },
  { value: 'scarf', label: '围巾' },
] as const

/** 排行榜面板（原文档 §80：等级/胜场/捞瓶三榜 + 我的名次） */
function RankingsPanel() {
  const [type, setType] = useState<PetRankingType>('LEVEL')
  const [result, setResult] = useState<PetRankingResult | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let stale = false
    setLoading(true)
    getPetRankings(type).then(({ data: res }) => {
      if (!stale && res.success) setResult(res.data)
    }).finally(() => { if (!stale) setLoading(false) })
    return () => { stale = true }
  }, [type])

  const SPECIES_ICON: Record<string, string> = { CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼' }
  return (
    <div className={styles.rankingsPanel}>
      <Segmented
        block
        value={type}
        onChange={(key) => setType(key as PetRankingType)}
        options={[
          { value: 'LEVEL', label: '等级榜' },
          { value: 'BATTLE_WIN', label: '胜场榜' },
          { value: 'BOTTLE', label: '捞瓶榜' },
        ]}
      />
      {loading ? <Spin /> : (
        <>
          <div className={styles.rankList}>
            {result?.top20.length === 0 && <Empty description="还没有宠物上榜" />}
            {result?.top20.map((item) => (
              <div key={String(item.petId)} className={`${styles.rankRow} ${item.isMe ? styles.rankMe : ''}`}>
                <span className={styles.rankNo}>
                  {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : `#${item.rank}`}
                </span>
                <span className={styles.rankPet}>
                  {SPECIES_ICON[item.species] || '🐾'} {item.name}
                  <span className={styles.rankOwner}>@{item.ownerNickname}</span>
                </span>
                <span className={styles.rankValue}>
                  {type === 'LEVEL' ? `Lv.${item.value}` : item.value}
                </span>
              </div>
            ))}
          </div>
          {result?.myRank !== null && result?.myRank !== undefined && (
            <div className={styles.myRankBox}>
              我的{type === 'LEVEL' ? '等级' : type === 'BATTLE_WIN' ? '胜场' : '捞瓶'}：
              <strong>{result.myValue}</strong> · 全服第 <strong>{result.myRank}</strong> 名
            </div>
          )}
          {(result?.myRank === null || result?.myRank === undefined) && (
            <div className={styles.myRankBox}>领养宠物并公开后即可上榜冲榜！</div>
          )}
        </>
      )}
    </div>
  )
}

interface ProfileModalProps {
  pet: PetInfo
  open: boolean
  onClose: () => void
  onSaved: () => void
}

/** 宠物档案弹窗：改名 + 外观编辑（颜色/配饰，原文档 §47 PUT /appearance） */
function ProfileModal({ pet, open, onClose, onSaved }: ProfileModalProps) {
  const { message } = App.useApp()
  const [name, setName] = useState(pet.name)
  const [color, setColor] = useState<string>('orange')
  const [accessory, setAccessory] = useState<string>('none')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setName(pet.name)
      try {
        const appearance = JSON.parse(pet.appearance) as { color?: string; accessory?: string }
        setColor(appearance.color || 'orange')
        setAccessory(appearance.accessory || 'none')
      } catch {
        // 外观 JSON 异常时使用默认
      }
    }
  }, [open, pet])

  const save = async () => {
    setSaving(true)
    try {
      let renamed = false
      if (name.trim() !== pet.name) {
        const { data: res } = await renamePet({ name: name.trim() })
        if (res.success) renamed = true
      }
      const { data: res2 } = await updateAppearance({ color, accessory })
      if (res2.success) {
        message.success(renamed ? '档案已更新（含改名）！' : '档案已更新！')
        onSaved()
        onClose()
      }
    } catch (error) {
      if ((error as { code?: string }).code === 'PET_RENAME_COOLDOWN') {
        message.warning('改名 30 天一次，本次只更新外观')
        // 外观仍可保存
        try {
          const { data: res2 } = await updateAppearance({ color, accessory })
          if (res2.success) { onSaved(); onClose() }
        } catch { /* 拦截器已提示 */ }
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="宠物档案" open={open} onCancel={onClose} onOk={save} confirmLoading={saving} okText="保存">
      <p className={styles.profileLabel}>名字（30 天可改一次）</p>
      <Input value={name} maxLength={12} onChange={(e) => setName(e.target.value)} />
      <p className={styles.profileLabel}>颜色</p>
      <div className={styles.swatchRow}>
        {COLOR_OPTIONS.map((option) => (
          <button
            key={option}
            type="button"
            className={`${styles.swatch} ${color === option ? styles.swatchActive : ''} swatch-${option}`}
            onClick={() => setColor(option)}
          >
            {option}
          </button>
        ))}
      </div>
      <p className={styles.profileLabel}>配饰</p>
      <Segmented
        value={accessory}
        onChange={(key) => setAccessory(key as string)}
        options={ACCESSORY_OPTIONS.map((option) => ({ value: option.value, label: option.label }))}
      />
    </Modal>
  )
}

/** 分享按钮组：拉取卡片文案 → 复制 → 跳转发帖页（原文档 §36） */
function ShareButtons() {
  const { message } = App.useApp()
  const [sharing, setSharing] = useState<string | null>(null)

  const share = async (type: 'LEVEL_UP' | 'ACHIEVEMENT' | 'BOTTLE' | 'BATTLE' | 'DAILY', label: string) => {
    setSharing(type)
    try {
      const { data: res } = await getPetShareCard(type)
      if (res.success && res.data) {
        const text = `${res.data.title}
${res.data.content}`
        await navigator.clipboard.writeText(text)
        message.success(`已复制${label}文案，去社区发帖分享吧！`)
        history.push('/publish')
      }
    } catch {
      message.error('复制失败，请重试')
    } finally {
      setSharing(null)
    }
  }

  return (
    <div className={styles.shareRow}>
      <Button size="small" loading={sharing === 'LEVEL_UP'} onClick={() => share('LEVEL_UP', '成长')}>📤 分享成长</Button>
      <Button size="small" loading={sharing === 'ACHIEVEMENT'} onClick={() => share('ACHIEVEMENT', '成就')}>📤 分享成就</Button>
      <Button size="small" loading={sharing === 'BOTTLE'} onClick={() => share('BOTTLE', '捞瓶')}>📤 分享捞瓶</Button>
      <Button size="small" loading={sharing === 'BATTLE'} onClick={() => share('BATTLE', '对战')}>📤 分享战绩</Button>
      <Button size="small" loading={sharing === 'DAILY'} onClick={() => share('DAILY', '日常')}>📤 分享日常</Button>
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

type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'pets'

const CARE_TABS: Array<{ key: CareTab; label: string; emoji: string }> = [
  { key: 'shop', label: '商城', emoji: '🛒' },
  { key: 'inventory', label: '背包', emoji: '🎒' },
  { key: 'skills', label: '技能', emoji: '🌟' },
  { key: 'evolution', label: '进化', emoji: '🌠' },
  { key: 'events', label: '活动', emoji: '🎯' },
  { key: 'visit', label: '串门', emoji: '🚪' },
  { key: 'pets', label: '宠物', emoji: '🐾' },
]

interface CarePanelProps {
  pet: PetInfo
  onRefresh: () => void
}

/**
 * 养成面板（原文档 §1.1 宠物串门 / §89 多宠物·装备·技能·进化·皮肤商城·社区活动）。
 *
 * 与其它面板同约定：所有数值/价格/门槛由服务端下发，本组件只做展示与意图发起；
 * 按钮禁用依据服务端返回的 eligible/lockReason，不在前端复算规则。
 */
function CarePanel({ pet, onRefresh }: CarePanelProps) {
  const { message } = App.useApp()
  const [tab, setTab] = useState<CareTab>('shop')
  const [reloadKey, setReloadKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState<string | null>(null)
  const [shop, setShop] = useState<{ starlightBalance: number | null; items: PetShopItem[] }>({ starlightBalance: null, items: [] })
  const [inventory, setInventory] = useState<PetInventoryItem[]>([])
  const [skills, setSkills] = useState<PetSkillItem[]>([])
  const [evolution, setEvolution] = useState<PetEvolutionStatus | null>(null)
  const [events, setEvents] = useState<PetEventItem[]>([])
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [pets, setPets] = useState<PetSummary[]>([])
  const [visitMessage, setVisitMessage] = useState<string | null>(null)

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])

  useEffect(() => {
    let stale = false
    setLoading(true)
    const load = async () => {
      try {
        if (tab === 'shop') {
          const { data: res } = await getPetShop()
          if (!stale && res.success && res.data) setShop(res.data)
        } else if (tab === 'inventory') {
          const { data: res } = await listPetInventory()
          if (!stale && res.success) setInventory(res.data || [])
        } else if (tab === 'skills') {
          const { data: res } = await listPetSkills()
          if (!stale && res.success) setSkills(res.data || [])
        } else if (tab === 'evolution') {
          const { data: res } = await getPetEvolution()
          if (!stale && res.success) setEvolution(res.data)
        } else if (tab === 'events') {
          const { data: res } = await listPetEvents()
          if (!stale && res.success) setEvents(res.data || [])
        } else if (tab === 'visit') {
          const { data: res } = await listPetVisitNeighbors()
          if (!stale && res.success) setNeighbors(res.data || [])
        } else {
          const { data: res } = await listMyPets()
          if (!stale && res.success) setPets(res.data || [])
        }
      } catch {
        // 拦截器已提示（如未领养 404）
      } finally {
        if (!stale) setLoading(false)
      }
    }
    load()
    return () => { stale = true }
  }, [tab, reloadKey])

  /** 统一动作执行：服务端权威 → 提示 → 刷新本面板与主宠状态 */
  const run = async (key: string, action: () => Promise<{ data: { success: boolean; data?: unknown } }>, successText: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        message.success(successText)
        reload()
        onRefresh()
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      const hints: Record<string, string> = {
        PET_ITEM_ALREADY_OWNED: '已经拥有这个物品啦',
        PET_ITEM_NOT_OWNED: '先拥有才能使用哦',
        PET_SKILL_BOOK_REQUIRED: '先去商城买这本技能书',
        PET_SKILL_ALREADY_LEARNED: '这个技能已经学会啦',
        PET_LEVEL_REQUIRED: '等级还不够，再养养吧',
        PET_EVOLUTION_REQUIRED: '需要先完成进化',
        PET_EVOLUTION_MAX: '已经进化到最高阶段啦',
        PET_PET_LIMIT_REACHED: '宠物数量已达上限',
        PET_VISIT_COOLDOWN: '今天已经去过这家啦',
        PET_VISIT_SELF: '不能给自己串门哦',
        PET_EVENT_NOT_FINISHED: '活动还没完成哦',
        PET_EVENT_ALREADY_CLAIMED: '奖励已经领过啦',
        PET_EVENT_ENDED: '活动已经结束啦',
        WISH_STARLIGHT_INSUFFICIENT: '星光不够啦，先让宠物去打工赚点吧',
      }
      if (code && hints[code]) {
        message.warning(hints[code])
      }
    } finally {
      setPending(null)
    }
  }

  return (
    <div className={styles.carePanel}>
      <Segmented
        block
        size="small"
        value={tab}
        onChange={(key) => setTab(key as CareTab)}
        options={CARE_TABS.map((item) => ({ value: item.key, label: `${item.emoji} ${item.label}` }))}
      />
      {loading ? (
        <Spin />
      ) : (
        <>
          {tab === 'shop' && (
            <div>
              <p className={styles.careBalance}>
                ✨ 星光余额：{shop.starlightBalance === null ? '暂不可用（服务降级）' : shop.starlightBalance}
              </p>
              <div className={styles.careGrid}>
                {shop.items.map((item) => (
                  <div key={`${item.itemType}-${item.code}`} className={styles.careCard}>
                    <span className={styles.careIcon}>{item.icon}</span>
                    <strong>{item.name}</strong>
                    <span className={styles.careMeta}>
                      {ITEM_TYPE_LABEL[item.itemType]}
                      {item.slot ? ` · ${SLOT_LABEL[item.slot] || item.slot}` : ''}
                      {item.species ? ` · 限定${item.species}` : ''}
                    </span>
                    <span className={styles.careMeta}>
                      {item.itemType === 'SKILL_BOOK'
                        ? `技能效果：${item.effect ?? ''} ${item.effectValue ?? ''}`
                        : item.itemType === 'EQUIPMENT'
                          ? `力量+${item.bonusStrength} 智力+${item.bonusIntelligence} 敏捷+${item.bonusAgility} 魅力+${item.bonusCharm} 生命+${item.bonusMaxHp}`
                          : `外观：${item.color ?? ''} / ${item.accessory ?? ''}`}
                    </span>
                    <span className={styles.careMeta}>
                      <Tag color={RARITY_COLOR[item.rarity] || 'default'}>{item.rarity}</Tag>
                      需要 Lv.{item.requiredLevel}
                      {item.requiredEvolutionStage > 0 ? ` · 进化 ${item.requiredEvolutionStage} 阶` : ''}
                    </span>
                    <span className={styles.careActions}>
                      <span className={styles.carePrice}>✨ {item.priceStarlight}</span>
                      {item.owned ? (
                        <Tag color="green">已拥有</Tag>
                      ) : (
                        <Button
                          size="small"
                          type="primary"
                          disabled={!item.eligible}
                          loading={pending === `buy-${item.code}`}
                          title={item.lockReason ?? undefined}
                          onClick={() => run(`buy-${item.code}`, () => buyPetItem({ itemType: item.itemType, itemCode: item.code }), '购买成功！')}
                        >
                          {item.eligible ? '购买' : (item.lockReason || '未解锁')}
                        </Button>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'inventory' && (
            <div>
              {inventory.length === 0 ? (
                <p className={styles.bottleHint}>背包还是空的，去商城逛逛吧～</p>
              ) : (
                <div className={styles.careGrid}>
                  {inventory.map((item) => (
                    <div key={`${item.itemType}-${item.code}`} className={styles.careCard}>
                      <span className={styles.careIcon}>{item.icon}</span>
                      <strong>{item.name}</strong>
                      <span className={styles.careMeta}>
                        {ITEM_TYPE_LABEL[item.itemType]}
                        {item.slot ? ` · ${SLOT_LABEL[item.slot] || item.slot}` : ''}
                        {item.used ? ' · 已学习' : ''}
                      </span>
                      <span className={styles.careActions}>
                        {item.equipped && <Tag color="gold">使用中</Tag>}
                        {item.itemType === 'EQUIPMENT' && (
                          item.equipped ? (
                            <Button
                              size="small"
                              loading={pending === `unequip-${item.slot}`}
                              onClick={() => run(`unequip-${item.slot}`, () => unequipPetItem(item.slot ?? ''), '已卸下')}
                            >
                              卸下
                            </Button>
                          ) : (
                            <Button
                              size="small"
                              type="primary"
                              loading={pending === `equip-${item.code}`}
                              onClick={() => run(`equip-${item.code}`, () => equipPetItem(item.code), '已穿戴')}
                            >
                              穿戴
                            </Button>
                          )
                        )}
                        {item.itemType === 'SKIN' && (
                          item.equipped ? (
                            <Button
                              size="small"
                              loading={pending === 'remove-skin'}
                              onClick={() => run('remove-skin', () => removePetSkin(), '已换回原生外观')}
                            >
                              卸下
                            </Button>
                          ) : (
                            <Button
                              size="small"
                              type="primary"
                              loading={pending === `skin-${item.code}`}
                              onClick={() => run(`skin-${item.code}`, () => wearPetSkin(item.code), '已穿上新皮肤')}
                            >
                              穿戴
                            </Button>
                          )
                        )}
                        {item.itemType === 'SKILL_BOOK' && !item.used && (
                          <span className={styles.careMeta}>去「技能」页学习</span>
                        )}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === 'skills' && (
            <div className={styles.careGrid}>
              {skills.map((skill) => (
                <div key={skill.code} className={styles.careCard}>
                  <span className={styles.careIcon}>{skill.icon}</span>
                  <strong>{skill.name}</strong>
                  <span className={styles.careMeta}>
                    <Tag color={skill.skillType === 'ACTIVE' ? 'volcano' : 'geekblue'}>
                      {skill.skillType === 'ACTIVE' ? '主动技' : '被动技'}
                    </Tag>
                    {skill.effectText}
                  </span>
                  <span className={styles.careMeta}>{skill.description}</span>
                  <span className={styles.careActions}>
                    <span className={styles.carePrice}>✨ {skill.priceStarlight}</span>
                    {skill.learned ? (
                      <Tag color="green">已学会</Tag>
                    ) : skill.bookOwned ? (
                      <Button
                        size="small"
                        type="primary"
                        disabled={pet.level < skill.requiredLevel}
                        loading={pending === `learn-${skill.code}`}
                        onClick={() => run(`learn-${skill.code}`, () => learnPetSkill(skill.code), '学会新技能啦！')}
                      >
                        {pet.level < skill.requiredLevel ? `需要 Lv.${skill.requiredLevel}` : '学习'}
                      </Button>
                    ) : (
                      <Tag>需要技能书（商城购买）</Tag>
                    )}
                  </span>
                </div>
              ))}
            </div>
          )}

          {tab === 'evolution' && evolution && (
            <div>
              <p className={styles.careBalance}>
                当前进化阶段：{evolution.currentStage} / {evolution.maxStage}
              </p>
              {evolution.nextCode === null ? (
                <p className={styles.bottleHint}>🎉 已经进化到最高阶段啦</p>
              ) : (
                <div className={styles.careCard}>
                  <span className={styles.careIcon}>{evolution.icon || '🌠'}</span>
                  <strong>{evolution.nextName}</strong>
                  <span className={styles.careMeta}>{evolution.nextDescription}</span>
                  <span className={styles.careMeta}>
                    需要 Lv.{evolution.requiredLevel} · 消耗 ✨{evolution.costStarlight}
                  </span>
                  <span className={styles.careMeta}>
                    生命+{evolution.bonusMaxHp} 力量+{evolution.bonusStrength} 智力+{evolution.bonusIntelligence}
                    敏捷+{evolution.bonusAgility} 魅力+{evolution.bonusCharm}
                  </span>
                  {evolution.unlockSkinCode && (
                    <span className={styles.careMeta}>解锁皮肤：{evolution.unlockSkinCode}</span>
                  )}
                  <span className={styles.careActions}>
                    <Button
                      type="primary"
                      size="small"
                      disabled={!evolution.canEvolve}
                      loading={pending === 'evolve'}
                      onClick={() => run('evolve', () => evolvePet(), '进化成功！')}
                    >
                      {evolution.canEvolve ? '进化' : (evolution.lockReason || '条件未满足')}
                    </Button>
                  </span>
                </div>
              )}
            </div>
          )}

          {tab === 'events' && (
            <div className={styles.careGrid}>
              {events.length === 0 && <p className={styles.bottleHint}>暂时没有进行中的活动</p>}
              {events.map((event) => (
                <div key={event.code} className={styles.careCard}>
                  <span className={styles.careIcon}>🎯</span>
                  <strong>{event.name}</strong>
                  <span className={styles.careMeta}>{event.description}</span>
                  <span className={styles.careMeta}>
                    {EVENT_TYPE_LABEL[event.eventType] || event.eventType} 进度：{event.progress}/{event.targetValue}
                    {event.expired ? '（已结束）' : ''}
                  </span>
                  <span className={styles.careMeta}>
                    奖励：经验+{event.rewardExp} ✨+{event.rewardStarlight}
                    {event.rewardItemCode ? ` · 物品 ${event.rewardItemCode}` : ''}
                  </span>
                  <span className={styles.careActions}>
                    {event.claimed ? (
                      <Tag color="green">已领取</Tag>
                    ) : event.expired ? (
                      <Tag>已结束</Tag>
                    ) : (
                      <Button
                        size="small"
                        type="primary"
                        disabled={!event.claimable}
                        loading={pending === `event-${event.code}`}
                        onClick={() => run(`event-${event.code}`, () => claimPetEvent(event.code), '奖励到手啦！')}
                      >
                        {event.claimable ? '领取奖励' : `还差 ${Math.max(0, event.targetValue - event.progress)} 次`}
                      </Button>
                    )}
                  </span>
                </div>
              ))}
            </div>
          )}

          {tab === 'visit' && (
            <div>
              {visitMessage && <p className={styles.careBalance}>{visitMessage}</p>}
              {neighbors.length === 0 ? (
                <p className={styles.bottleHint}>暂时没有可串门的邻居，稍后再来看看吧</p>
              ) : (
                <div className={styles.careGrid}>
                  {neighbors.map((neighbor) => (
                    <div key={neighbor.petId} className={styles.careCard}>
                      <span className={styles.careIcon}>{SPECIES_EMOJI[neighbor.species] || '🐾'}</span>
                      <strong>{neighbor.name}</strong>
                      <span className={styles.careMeta}>
                        Lv.{neighbor.level} · {neighbor.ownerNickname}
                        {neighbor.evolutionStage > 0 ? ` · 进化${neighbor.evolutionStage}阶` : ''}
                      </span>
                      <span className={styles.careActions}>
                        {neighbor.visitedToday ? (
                          <Tag>今日已去过</Tag>
                        ) : (
                          <Button
                            size="small"
                            type="primary"
                            loading={pending === `visit-${neighbor.petId}`}
                            onClick={() => run(`visit-${neighbor.petId}`, async () => {
                              const res = await visitNeighborPet(neighbor.petId)
                              if (res.data.success && res.data.data) {
                                setVisitMessage(res.data.data.message)
                              }
                              return res
                            }, '串门成功！')}
                          >
                            去串门
                          </Button>
                        )}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === 'pets' && (
            <div>
              <p className={styles.careBalance}>宠物 {pet.petCount} / {pet.maxPets}（日常玩法作用于主宠）</p>
              <div className={styles.careGrid}>
                {pets.map((item) => (
                  <div key={item.petId} className={styles.careCard}>
                    <span className={styles.careIcon}>{SPECIES_EMOJI[item.species] || '🐾'}</span>
                    <strong>{item.name}</strong>
                    <span className={styles.careMeta}>
                      Lv.{item.level} · {GROWTH_STAGE_LABEL[item.growthStage] || item.growthStage}
                      {item.evolutionStage > 0 ? ` · 进化${item.evolutionStage}阶` : ''}
                    </span>
                    <span className={styles.careActions}>
                      {item.isActive ? (
                        <Tag color="gold">主宠</Tag>
                      ) : (
                        <Button
                          size="small"
                          loading={pending === `activate-${item.petId}`}
                          onClick={() => run(`activate-${item.petId}`, () => activatePet(item.petId), `已切换为 ${item.name}`)}
                        >
                          设为主宠
                        </Button>
                      )}
                    </span>
                  </div>
                ))}
                {pet.petCount < pet.maxPets && (
                  <div className={styles.careCard}>
                    <span className={styles.careIcon}>➕</span>
                    <strong>再领养一只</strong>
                    <span className={styles.careMeta}>还有 {pet.maxPets - pet.petCount} 个名额</span>
                    <span className={styles.careActions}>
                      <Button size="small" onClick={() => history.push('/pet?adopt=1')}>去领养</Button>
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </div>
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
  const [profileOpen, setProfileOpen] = useState(false)
  const [unreadReminders, setUnreadReminders] = useState(0)
  const [pendingInteraction, setPendingInteraction] = useState<PetIntentAction | null>(null)
  const [adoptOpen, setAdoptOpen] = useState(false)
  const stageRef = useRef<PetStageHandle>(null)

  useEffect(() => {
    if (!user && !userLoading) {
      history.replace('/login?redirect=/pet')
    }
  }, [user, userLoading])

  /** 宠物提醒未读数（入口角标；服务降级由后端返回 0，不阻断页面） */
  const loadUnread = useCallback(async () => {
    try {
      const { data: res } = await getPetReminderUnreadCount()
      if (res.success) {
        setUnreadReminders(res.data ?? 0)
      }
    } catch {
      // 未读数属展示型数据：失败保持原值
    }
  }, [])

  const syncStage = useCallback((next: PetInfo) => {
    stageRef.current?.post({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [])

  /** 宠物台词同步到 Cocos 气泡（聊天回复/点击宠物；文案由服务端生成，宿主不拼业务数据） */
  const speakToStage = useCallback((content: string) => {
    stageRef.current?.post({ source: 'pet-host', type: 'chatBubble', content })
  }, [])

  /** 场景内点击宠物：给一句即时反馈，避免点了没反应 */
  const handlePetTapped = useCallback(() => {
    if (!pet) {
      return
    }
    speakToStage(`${pet.name}：${STATUS_SPEECH[pet.status] ?? '主人，点点我干嘛呀～'}`)
  }, [pet, speakToStage])

  const handleChatReply = useCallback((content: string) => {
    speakToStage(content)
  }, [speakToStage])

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
      loadUnread()
    }
  }, [user, refresh, loadUnread])

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
    if (action === 'openProfile') { setProfileOpen(true); return }
    if (action === 'openRankings') { setPanel('rankings'); return }
    if (action === 'openCare') { setPanel('care'); return }
    // 以下 action 类型已收窄为互动四件套
    setPendingInteraction(action)
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
    } finally {
      setPendingInteraction(null)
    }
  }, [pet, syncStage])

  /** ?adopt=1（多宠物：从个人中心/养成面板直接进入领养） */
  useEffect(() => {
    if (!noPet) {
      const params = new URLSearchParams(window.location.search)
      if (params.get('adopt') === '1') {
        setAdoptOpen(true)
      }
    }
  }, [noPet])

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
          onPetTapped={handlePetTapped}
          fallback={
            <div className={styles.nativeStage}>
              <span className={styles.nativePet}>{SPECIES_EMOJI[pet.species] || '🐾'}</span>
              <span className={styles.nativeSpeech}>Cocos 舞台未部署 · 原生模式</span>
            </div>
          }
        />
        <PetStateBars pet={pet} />
        <div className={styles.interactionRow}>
          <Button size="small" loading={pendingInteraction === 'feed'} onClick={() => handleIntent('feed')}>🍖 喂食</Button>
          <Button size="small" loading={pendingInteraction === 'play'} onClick={() => handleIntent('play')}>🎾 玩耍</Button>
          <Button size="small" loading={pendingInteraction === 'clean'} onClick={() => handleIntent('clean')}>🧼 清洁</Button>
          <Button size="small" loading={pendingInteraction === 'rest'} onClick={() => handleIntent('rest')}>😴 休息</Button>
          <Button size="small" onClick={() => setProfileOpen(true)}>📇 档案</Button>
          <Button size="small" onClick={() => setPanel('care')}>🎒 养成</Button>
        </div>
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
        onChange={(key) => {
          setPanel(key as PanelKey)
          if (key === 'reminders') {
            loadUnread()
          }
        }}
        options={PANELS.map((item) => ({
          value: item.key,
          label: item.key === 'reminders' && unreadReminders > 0
            ? `${item.emoji} ${item.label} ${unreadReminders}`
            : `${item.emoji} ${item.label}`,
        }))}
      />

      <div className={styles.panelBody}>
        {panel === 'care' && <CarePanel pet={pet} onRefresh={refresh} />}
        {panel === 'work' && <WorkPanel pet={pet} onRefresh={refresh} />}
        {panel === 'study' && <StudyPanel pet={pet} onRefresh={refresh} />}
        {panel === 'bottle' && <BottlePanel onRefresh={refresh} />}
        {panel === 'battle' && <BattlePanel onRefresh={refresh} onBattleResult={handleBattleResult} />}
        {panel === 'chat' && <ChatPanel onRefresh={refresh} onReply={handleChatReply} />}
        {panel === 'achievements' && (
          <>
            <AchievementsPanel />
            <ShareButtons />
          </>
        )}
        {panel === 'reminders' && <RemindersPanel />}
        {panel === 'rankings' && <RankingsPanel />}
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
      <ProfileModal pet={pet} open={profileOpen} onClose={() => setProfileOpen(false)} onSaved={refresh} />
      <Modal
        open={adoptOpen}
        footer={null}
        width={720}
        title="再领养一只宠物"
        onCancel={() => setAdoptOpen(false)}
      >
        {pet.petCount >= pet.maxPets ? (
          <p className={styles.bottleHint}>宠物数量已达上限（{pet.maxPets} 只），先好好照顾现有的伙伴吧～</p>
        ) : (
          <AdoptWizard onAdopted={() => { setAdoptOpen(false); refresh() }} />
        )}
      </Modal>
    </div>
  )
}
