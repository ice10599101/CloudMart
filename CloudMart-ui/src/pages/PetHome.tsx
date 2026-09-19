import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, Button, Empty, Input, InputNumber, Modal, Segmented, Select, Spin, Switch, Tag } from 'antd'
import { history } from 'umi'
import {
  acceptPetBattle,
  acceptPetFriend,
  acceptPetRelation,
  activatePet,
  applyPetCareer,
  buyPetFurniture,
  buyPetItem,
  claimPetCareerWork,
  claimPetDailyQuest,
  claimPetDailyQuestChest,
  dissolvePetRelation,
  getPetCareer,
  getPetDailyQuests,
  getPetFriends,
  getPetHome,
  getPetIntimacy,
  getPetRelations,
  getPetWall,
  likePetHome,
  likePetWallMessage,
  placePetFurniture,
  postPetWallMessage,
  promotePetCareer,
  rejectPetFriend,
  rejectPetRelation,
  removePetFurniture,
  removePetFriend,
  replyPetWallMessage,
  requestPetFriend,
  requestPetRelation,
  sendPetCompanionHeartbeat,
  startPetCareerWork,
  updatePetRoomSettings,
  updatePetRoomTheme,
  visitPetFriend,
  visitPetHome,
  deletePetWallMessage as deletePetWallMessageApi,
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
  type PetGender,
  type PetVisitNeighbor,
  type PetCareerPanel,
  type PetDailyQuestPanel,
  type PetFriendPanel,
  type PetHome,
  type PetIntimacyInfo,
  type PetRelationPanel,
  type PetWallPage,
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
  | 'home' | 'care' | 'daily' | 'social' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'

const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '家园', emoji: '🏠' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'daily', label: '任务', emoji: '✅' },
  { key: 'social', label: '社交', emoji: '🤝' },
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
    gender: pet.gender,
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
  const [gender, setGender] = useState<string>('MALE')
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
        gender: gender as PetGender,
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
      <h4 className={styles.sectionTitle}>性别</h4>
      <div className={styles.personalityRow}>
        <button
          type="button"
          className={`${styles.personalityChip} ${styles.genderChip} ${gender === 'MALE' ? styles.personalityActive : ''}`}
          onClick={() => setGender('MALE')}
        >
          ♂ 雄性
        </button>
        <button
          type="button"
          className={`${styles.personalityChip} ${styles.genderChip} ${styles.genderFemale} ${gender === 'FEMALE' ? styles.personalityActive : ''}`}
          onClick={() => setGender('FEMALE')}
        >
          ♀ 雌性
        </button>
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
      <p className={styles.profileLabel}>性别（领养时确定）</p>
      <p style={{ margin: 0 }}>
        <span className={pet.gender === 'FEMALE' ? styles.genderFemale : styles.genderMale} style={{ fontSize: 16 }}>
          {pet.gender === 'FEMALE' ? '♀ 雌性' : '♂ 雄性'}
        </span>
      </p>
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

type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'pets' | 'career'

const CARE_TABS: Array<{ key: CareTab; label: string; emoji: string }> = [
  { key: 'shop', label: '商城', emoji: '🛒' },
  { key: 'inventory', label: '背包', emoji: '🎒' },
  { key: 'skills', label: '技能', emoji: '🌟' },
  { key: 'evolution', label: '进化', emoji: '🌠' },
  { key: 'events', label: '活动', emoji: '🎯' },
  { key: 'visit', label: '串门', emoji: '🚪' },
  { key: 'career', label: '职业', emoji: '💼' },
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

          {tab === 'career' && <CareerPanel onRefresh={onRefresh} />}
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

/** 职业面板（三期）：入职 / 职业工作 / 晋升 / 工作历史（挂在「养成 → 职业」页签下） */
function CareerPanel({ onRefresh }: { onRefresh: () => void }) {
  const { message } = App.useApp()
  const [panel, setPanel] = useState<PetCareerPanel | null>(null)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getPetCareer()
      if (res.success && res.data) {
        setPanel(res.data)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        message.success(text)
        await load()
        onRefresh()
      }
    } catch {
      // 拦截器已提示（条件不满足等业务码）
    } finally {
      setPending(null)
    }
  }

  if (loading && !panel) {
    return <Spin />
  }
  if (!panel) {
    return <p className={styles.bottleHint}>职业信息暂时打不开，稍后再试试吧</p>
  }

  const activity = panel.activeActivity

  return (
    <div>
      <p className={styles.careBalance}>
        {panel.careerCode
          ? `当前职业：${panel.icon ?? ''} ${panel.careerName}（${panel.careerLine} · ${panel.tier} 阶）· 已工作 ${panel.workCount} 次`
          : '还没有工作，挑一份喜欢的职业入职吧'}
        {panel.canPromote && panel.promoteToName
          ? ` · 可晋升为「${panel.promoteToName}」（消耗 ✨${panel.promoteStarCost}）`
          : ''}
        {panel.promoteLockReason && panel.promoteToName ? ` · 晋升条件：${panel.promoteLockReason}` : ''}
      </p>

      {panel.careerCode && (
        <span className={styles.careActions}>
          {activity ? (
            <Button
              size="small"
              type="primary"
              disabled={!activity.canClaim}
              loading={pending === 'career-claim'}
              onClick={() => run('career-claim', () => claimPetCareerWork(), '工钱到手啦！')}
            >
              {activity.canClaim
                ? '领取工作奖励'
                : `工作中，剩 ${Math.max(0, Math.ceil(activity.remainingSeconds / 60))} 分钟`}
            </Button>
          ) : (
            <Button
              size="small"
              type="primary"
              loading={pending === 'career-start'}
              onClick={() => run('career-start', () => startPetCareerWork(), '开始工作啦')}
            >
              去上班
            </Button>
          )}
          <Button
            size="small"
            disabled={!panel.canPromote}
            title={panel.promoteLockReason ?? undefined}
            loading={pending === 'career-promote'}
            onClick={() => run('career-promote', () => promotePetCareer(), '晋升成功！')}
          >
            晋升
          </Button>
        </span>
      )}

      <div className={styles.careGrid}>
        {panel.careers.map((career) => (
          <div key={career.code} className={styles.careCard}>
            <span className={styles.careIcon}>{career.icon}</span>
            <strong>
              {career.name} <Tag>{career.careerLine} · {career.tier} 阶</Tag>
            </strong>
            <span className={styles.careMeta}>{career.description}</span>
            <span className={styles.careMeta}>
              Lv.{career.requiredLevel}
              {career.requiredIntelligence > 0 ? ` · 智力 ${career.requiredIntelligence}` : ''}
              {' · '}
              {Math.round(career.durationSeconds / 60)} 分钟 · 精力 {career.energyCost} · 经验+{career.expReward} ✨+{career.currencyReward}
            </span>
            {career.workCount > 0 && <span className={styles.careMeta}>已工作 {career.workCount} 次</span>}
            <span className={styles.careActions}>
              {career.current ? (
                <Tag color="green">在职</Tag>
              ) : (
                <Button
                  size="small"
                  type="primary"
                  disabled={!career.eligible}
                  title={career.lockReason ?? undefined}
                  loading={pending === `apply-${career.code}`}
                  onClick={() => run(`apply-${career.code}`, () => applyPetCareer(career.code), '入职成功！')}
                >
                  {career.eligible ? '入职' : career.lockReason || '未解锁'}
                </Button>
              )}
            </span>
          </div>
        ))}
      </div>

      {panel.history.length > 0 && (
        <>
          <p className={styles.careBalance}>工作经历</p>
          <p className={styles.careMeta}>
            {panel.history
              .map((item) => `${item.name}（${item.workCount} 次 / ✨${item.totalCurrency}）`)
              .join(' · ')}
          </p>
        </>
      )}
    </div>
  )
}

/** 家园面板（三期）：房间布置 + 家具商城 + 邻里拜访 + 家园设置 */
function HomePanel({ onRefresh }: { onRefresh: () => void }) {
  const { message } = App.useApp()
  const [home, setHome] = useState<PetHome | null>(null)
  const [tab, setTab] = useState<'room' | 'shop' | 'visit'>('room')
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState<string | null>(null)
  const [placeCode, setPlaceCode] = useState<string>('')
  const [posX, setPosX] = useState(0)
  const [posY, setPosY] = useState(0)
  const [welcome, setWelcome] = useState('')
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [visitResult, setVisitResult] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getPetHome()
      if (res.success && res.data) {
        setHome(res.data)
        setWelcome(res.data.welcomeMessage)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (tab !== 'visit') {
      return
    }
    void (async () => {
      try {
        const { data: res } = await listPetVisitNeighbors()
        if (res.success) {
          setNeighbors(res.data ?? [])
        }
      } catch {
        // 展示型数据：失败保持原值
      }
    })()
  }, [tab])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        message.success(text)
        await load()
        onRefresh()
      }
    } catch {
      // 拦截器已提示（含 lockReason 类业务码）
    } finally {
      setPending(null)
    }
  }

  if (loading && !home) {
    return <Spin />
  }
  if (!home) {
    return <p className={styles.bottleHint}>家园暂时打不开，稍后再试试吧</p>
  }

  const wallpaperOptions = home.shop.filter((item) => item.category === 'WALL' && item.owned)
  const floorOptions = home.shop.filter((item) => item.category === 'FLOOR' && item.owned)
  const placeableOwned = home.inventory.filter(
    (item) => item.category !== 'WALL' && item.category !== 'FLOOR',
  )

  return (
    <div className={styles.carePanel}>
      <p className={styles.careBalance}>
        🏡 舒适度 {home.comfort}（{home.comfort >= home.comfortBonusThreshold ? `休息心情 +${home.comfortRestHappinessBonus}` : `达到 ${home.comfortBonusThreshold} 触发休息加成`}）
        · 来访 {home.visitCount} · 点赞 {home.likeCount}
        {home.dailyEnterRewarded ? ' · 今日回家奖励已领' : ''}
      </p>
      <Segmented
        block
        size="small"
        value={tab}
        onChange={(key) => setTab(key as 'room' | 'shop' | 'visit')}
        options={[
          { value: 'room', label: '🛋️ 布置' },
          { value: 'shop', label: '🛒 家具' },
          { value: 'visit', label: '🚪 拜访' },
        ]}
      />

      {tab === 'room' && (
        <div>
          <div className={styles.homeGrid} style={{ gridTemplateColumns: `repeat(${home.gridWidth}, 1fr)` }}>
            {Array.from({ length: home.gridHeight }).flatMap((_, y) =>
              Array.from({ length: home.gridWidth }).map((__, x) => {
                const placed = home.placed.find((item) => item.posX === x && item.posY === y)
                return (
                  <div key={`${x}-${y}`} className={styles.homeCell} title={placed ? placed.name : `${x},${y}`}>
                    {placed ? (
                      <>
                        <span className={styles.careIcon}>{placed.icon}</span>
                        <span className={styles.careMeta}>{placed.name}</span>
                        <Button
                          size="small"
                          loading={pending === `remove-${x}-${y}`}
                          onClick={() => run(`remove-${x}-${y}`, () => removePetFurniture(x, y), '已收回仓库')}
                        >
                          收回
                        </Button>
                      </>
                    ) : (
                      <span className={styles.careMeta}>空位 {x},{y}</span>
                    )}
                  </div>
                )
              }),
            )}
          </div>
          <p className={styles.careMeta}>
            墙纸：
            <Select
              size="small"
              style={{ width: 140 }}
              value={home.wallCode ?? ''}
              onChange={(value) =>
                run(
                  'theme',
                  () => updatePetRoomTheme({ wallCode: String(value) || null, floorCode: home.floorCode }),
                  '已换墙纸',
                )
              }
              options={[{ value: '', label: '默认' }, ...wallpaperOptions.map((item) => ({ value: item.code, label: item.name }))]}
            />
            地板：
            <Select
              size="small"
              style={{ width: 140 }}
              value={home.floorCode ?? ''}
              onChange={(value) =>
                run(
                  'theme',
                  () => updatePetRoomTheme({ wallCode: home.wallCode, floorCode: String(value) || null }),
                  '已换地板',
                )
              }
              options={[{ value: '', label: '默认' }, ...floorOptions.map((item) => ({ value: item.code, label: item.name }))]}
            />
          </p>
        </div>
      )}

      {tab === 'shop' && (
        <div>
          <p className={styles.careMeta}>
            摆放家具：
            <Select
              size="small"
              style={{ width: 160 }}
              value={placeCode || undefined}
              placeholder={placeableOwned.length === 0 ? '先去下面买一件' : '选择已拥有的家具'}
              onChange={setPlaceCode}
              options={placeableOwned.map((item) => ({ value: item.code, label: `${item.icon} ${item.name}` }))}
            />
            X
            <InputNumber size="small" min={0} max={home.gridWidth - 1} value={posX} onChange={(v) => setPosX(v ?? 0)} />
            Y
            <InputNumber size="small" min={0} max={home.gridHeight - 1} value={posY} onChange={(v) => setPosY(v ?? 0)} />
            <Button
              size="small"
              type="primary"
              disabled={!placeCode}
              loading={pending === 'place'}
              onClick={() => run('place', () => placePetFurniture({ furnitureCode: placeCode, posX, posY }), '摆放好啦')}
            >
              摆放
            </Button>
          </p>
          <div className={styles.careGrid}>
            {home.shop.map((item) => (
              <div key={item.code} className={styles.careCard}>
                <span className={styles.careIcon}>{item.icon}</span>
                <strong>{item.name}</strong>
                <span className={styles.careMeta}>
                  {item.categoryLabel} · 舒适度 +{item.comfort} · {item.rarity}
                </span>
                <span className={styles.careMeta}>需要 Lv.{item.requiredLevel}</span>
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
                      onClick={() => run(`buy-${item.code}`, () => buyPetFurniture(item.code), '买到啦，去布置吧')}
                    >
                      {item.eligible ? '购买' : item.lockReason || '未解锁'}
                    </Button>
                  )}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'visit' && (
        <div>
          {visitResult && <p className={styles.careBalance}>{visitResult}</p>}
          <p className={styles.careMeta}>
            我的家园：
            <Switch
              size="small"
              checked={home.isPublic}
              onChange={(checked) => run('settings', () => updatePetRoomSettings({ isPublic: checked }), '设置已更新')}
            />
            允许来访
            <Input
              size="small"
              style={{ width: 200 }}
              maxLength={40}
              value={welcome}
              onChange={(event) => setWelcome(event.target.value)}
              placeholder="欢迎语（最多 40 字）"
            />
            <Button
              size="small"
              loading={pending === 'welcome'}
              onClick={() => run('welcome', () => updatePetRoomSettings({ welcomeMessage: welcome }), '欢迎语已更新')}
            >
              保存欢迎语
            </Button>
          </p>
          {neighbors.length === 0 ? (
            <p className={styles.bottleHint}>暂时没有可拜访的邻居</p>
          ) : (
            <div className={styles.careGrid}>
              {neighbors.map((neighbor) => (
                <div key={neighbor.petId} className={styles.careCard}>
                  <span className={styles.careIcon}>{SPECIES_EMOJI[neighbor.species] || '🐾'}</span>
                  <strong>{neighbor.name}</strong>
                  <span className={styles.careMeta}>
                    Lv.{neighbor.level} · {neighbor.ownerNickname}
                  </span>
                  <span className={styles.careActions}>
                    <Button
                      size="small"
                      type="primary"
                      loading={pending === `visit-${neighbor.petId}`}
                      onClick={() =>
                        run(
                          `visit-${neighbor.petId}`,
                          async () => {
                            const res = await visitPetHome(Number(neighbor.petId))
                            if (res.data.success && res.data.data) {
                              setVisitResult(res.data.data.message)
                            }
                            return res
                          },
                          '拜访成功！',
                        )
                      }
                    >
                      去家里看看
                    </Button>
                    <Button
                      size="small"
                      loading={pending === `like-${neighbor.petId}`}
                      onClick={() =>
                        run(`like-${neighbor.petId}`, () => likePetHome(Number(neighbor.petId)), '点赞成功')
                      }
                    >
                      点赞
                    </Button>
                    <Button
                      size="small"
                      loading={pending === `freq-${neighbor.petId}`}
                      onClick={() =>
                        run(
                          `freq-${neighbor.petId}`,
                          () => requestPetFriend(Number(neighbor.ownerUserId)),
                          '好友申请已发出～',
                        )
                      }
                    >
                      加好友
                    </Button>
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/** 每日任务面板（三期）：进度 + 领奖 + 全清宝箱 */
function DailyPanel({ onRefresh }: { onRefresh: () => void }) {
  const { message } = App.useApp()
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await getPetDailyQuests()
      if (res.success && res.data) {
        setPanel(res.data)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        message.success(text)
        await load()
        onRefresh()
      }
    } catch {
      // 拦截器已提示（未完成/已领取等业务码）
    } finally {
      setPending(null)
    }
  }

  if (loading && !panel) {
    return <Spin />
  }
  if (!panel) {
    return <p className={styles.bottleHint}>任务列表暂时打不开，稍后再试试吧</p>
  }

  return (
    <div className={styles.carePanel}>
      <p className={styles.careBalance}>
        今日进度 {panel.claimedCount}/{panel.totalCount}（已完成 {panel.completedCount}）
        · 全清宝箱 经验+{panel.chestExp} ✨+{panel.chestCurrency}
      </p>
      <div className={styles.careGrid}>
        {panel.quests.map((quest) => (
          <div key={quest.code} className={styles.careCard}>
            <span className={styles.careIcon}>{quest.icon}</span>
            <strong>{quest.name}</strong>
            <span className={styles.careMeta}>{quest.description}</span>
            <span className={styles.careMeta}>
              进度 {Math.min(quest.progress, quest.targetValue)}/{quest.targetValue} · 奖励 经验+{quest.expReward} ✨+{quest.currencyReward}
            </span>
            <span className={styles.careActions}>
              {quest.status === 'CLAIMED' ? (
                <Tag color="green">已领取</Tag>
              ) : (
                <Button
                  size="small"
                  type="primary"
                  disabled={!quest.claimable}
                  loading={pending === `quest-${quest.code}`}
                  onClick={() => run(`quest-${quest.code}`, () => claimPetDailyQuest(quest.code), '奖励到手啦！')}
                >
                  {quest.claimable ? '领取奖励' : quest.statusLabel}
                </Button>
              )}
            </span>
          </div>
        ))}
      </div>
      <span className={styles.careActions}>
        {panel.chestClaimed ? (
          <Tag color="gold">宝箱已领取</Tag>
        ) : (
          <Button
            type="primary"
            disabled={!panel.chestClaimable}
            loading={pending === 'chest'}
            onClick={() => run('chest', () => claimPetDailyQuestChest(), '宝箱开啦！')}
          >
            {panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}
          </Button>
        )}
      </span>
    </div>
  )
}

/** 社交面板（三期）：宠物关系 + 好友互访 + 留言墙 */
function SocialPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const { message } = App.useApp()
  const [tab, setTab] = useState<'relation' | 'friend' | 'wall'>('relation')
  const [relations, setRelations] = useState<PetRelationPanel | null>(null)
  const [friends, setFriends] = useState<PetFriendPanel | null>(null)
  const [wallPetId, setWallPetId] = useState<number>(Number(pet.petId))
  const [wall, setWall] = useState<PetWallPage | null>(null)
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [pending, setPending] = useState<string | null>(null)
  const [wallInput, setWallInput] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyInput, setReplyInput] = useState('')
  const [relationTarget, setRelationTarget] = useState<{ petId: number; name: string } | null>(null)
  const [relationType, setRelationType] = useState('BESTIE')
  const [relationMsg, setRelationMsg] = useState('')
  const [tip, setTip] = useState<string | null>(null)

  const loadRelations = useCallback(async () => {
    try {
      const { data: res } = await getPetRelations()
      if (res.success && res.data) {
        setRelations(res.data)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  const loadFriends = useCallback(async () => {
    try {
      const { data: res } = await getPetFriends()
      if (res.success && res.data) {
        setFriends(res.data)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  const loadWall = useCallback(async (targetPetId: number) => {
    try {
      const { data: res } = await getPetWall(targetPetId, 1, 10)
      if (res.success && res.data) {
        setWall(res.data)
      }
    } catch {
      // 未公开等业务码由拦截器提示
    }
  }, [])

  useEffect(() => {
    if (tab === 'relation') {
      void loadRelations()
    } else if (tab === 'friend') {
      void loadFriends()
    } else {
      void loadWall(wallPetId)
      void (async () => {
        try {
          const { data: res } = await listMyPets()
          if (res.success) {
            setMyPets(res.data ?? [])
          }
        } catch {
          // 展示型数据
        }
      })()
    }
  }, [tab, wallPetId, loadRelations, loadFriends, loadWall])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        message.success(text)
        await (tab === 'relation' ? loadRelations() : tab === 'friend' ? loadFriends() : loadWall(wallPetId))
        onRefresh()
      }
    } catch {
      // 拦截器已提示
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
        onChange={(key) => setTab(key as 'relation' | 'friend' | 'wall')}
        options={[
          { value: 'relation', label: '💞 关系' },
          { value: 'friend', label: '🫂 好友' },
          { value: 'wall', label: '📝 留言墙' },
        ]}
      />
      {tip && <p className={styles.careBalance}>{tip}</p>}

      {tab === 'relation' && relations && (
        <div>
          <p className={styles.careMeta}>
            {relations.limits.map((limit) => (
              <Tag key={limit.relType} color={limit.exclusive ? 'magenta' : 'blue'}>
                {limit.label} {limit.current}/{limit.max}
              </Tag>
            ))}
          </p>
          {relations.incoming.length > 0 && (
            <>
              <p className={styles.careBalance}>收到的申请</p>
              <div className={styles.careGrid}>
                {relations.incoming.map((item) => (
                  <div key={String(item.id)} className={styles.careCard}>
                    <span className={styles.careIcon}>{SPECIES_EMOJI[item.species] || '🐾'}</span>
                    <strong>{item.petName}</strong>
                    <span className={styles.careMeta}>
                      {item.ownerNickname} 想成为{item.relTypeLabel}
                      {item.message ? `：「${item.message}」` : ''}
                    </span>
                    <span className={styles.careActions}>
                      <Button
                        size="small"
                        type="primary"
                        loading={pending === `accept-${item.id}`}
                        onClick={() => run(`accept-${item.id}`, () => acceptPetRelation(item.id as number), '关系建立啦！')}
                      >
                        同意
                      </Button>
                      <Button
                        size="small"
                        loading={pending === `reject-${item.id}`}
                        onClick={() => run(`reject-${item.id}`, () => rejectPetRelation(item.id as number), '已拒绝')}
                      >
                        拒绝
                      </Button>
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
          <p className={styles.careBalance}>已建立的关系</p>
          {relations.relations.length === 0 ? (
            <p className={styles.bottleHint}>还没有关系，去下面认识一只新宠物吧</p>
          ) : (
            <div className={styles.careGrid}>
              {relations.relations.map((item) => (
                <div key={String(item.id)} className={styles.careCard}>
                  <span className={styles.careIcon}>{SPECIES_EMOJI[item.species] || '🐾'}</span>
                  <strong>
                    {item.petName} <Tag color="magenta">{item.relTypeLabel}</Tag>
                  </strong>
                  <span className={styles.careMeta}>
                    {item.ownerNickname} · 亲密度 {item.intimacy}（{item.intimacyLevelName}，还差 {item.intimacyToNext}）
                  </span>
                  <span className={styles.careActions}>
                    <Button
                      size="small"
                      danger
                      loading={pending === `dissolve-${item.id}`}
                      onClick={() => run(`dissolve-${item.id}`, () => dissolvePetRelation(item.id as number), '已解除关系')}
                    >
                      解除
                    </Button>
                  </span>
                </div>
              ))}
            </div>
          )}
          {relations.outgoing.length > 0 && (
            <p className={styles.careMeta}>
              等待回应：{relations.outgoing.map((item) => `${item.petName}（${item.relTypeLabel}）`).join('、')}
            </p>
          )}
          <p className={styles.careBalance}>可以认识的宠物</p>
          <div className={styles.careGrid}>
            {relations.candidates.map((candidate) => (
              <div key={candidate.petId} className={styles.careCard}>
                <span className={styles.careIcon}>{SPECIES_EMOJI[candidate.species] || '🐾'}</span>
                <strong>{candidate.petName}</strong>
                <span className={styles.careMeta}>
                  Lv.{candidate.level} · {candidate.ownerNickname}
                </span>
                <span className={styles.careActions}>
                  <Button
                    size="small"
                    type="primary"
                    onClick={() => {
                      setRelationTarget({ petId: candidate.petId, name: candidate.petName })
                      setRelationMsg('')
                      setTip(null)
                    }}
                  >
                    发起关系
                  </Button>
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'friend' && friends && (
        <div>
          <p className={styles.careMeta}>
            今日互访 {friends.todayVisitCount}/{friends.dailyVisitLimit}（剩余 {friends.remainingVisits}）· 好友上限 {friends.maxFriends}
          </p>
          {friends.incoming.length > 0 && (
            <>
              <p className={styles.careBalance}>收到的好友申请</p>
              <div className={styles.careGrid}>
                {friends.incoming.map((item) => (
                  <div key={item.userId} className={styles.careCard}>
                    <span className={styles.careIcon}>{SPECIES_EMOJI[item.species ?? ''] || '🐾'}</span>
                    <strong>{item.nickname}</strong>
                    <span className={styles.careMeta}>{item.petName ?? '还没有宠物'}</span>
                    <span className={styles.careActions}>
                      <Button
                        size="small"
                        type="primary"
                        loading={pending === `faccept-${item.userId}`}
                        onClick={() => run(`faccept-${item.userId}`, () => acceptPetFriend(item.userId), '成为好友啦！')}
                      >
                        同意
                      </Button>
                      <Button
                        size="small"
                        loading={pending === `freject-${item.userId}`}
                        onClick={() => run(`freject-${item.userId}`, () => rejectPetFriend(item.userId), '已拒绝')}
                      >
                        拒绝
                      </Button>
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
          <p className={styles.careBalance}>我的好友</p>
          {friends.friends.length === 0 ? (
            <p className={styles.bottleHint}>还没有好友，先去邻居家串门认识一下吧</p>
          ) : (
            <div className={styles.careGrid}>
              {friends.friends.map((item) => (
                <div key={item.userId} className={styles.careCard}>
                  <span className={styles.careIcon}>{SPECIES_EMOJI[item.species ?? ''] || '🐾'}</span>
                  <strong>{item.nickname}</strong>
                  <span className={styles.careMeta}>
                    {item.petName ?? '—'} Lv.{item.level ?? '-'} · 互访 {item.visitCount} 次
                  </span>
                  <span className={styles.careActions}>
                    <Button
                      size="small"
                      type="primary"
                      loading={pending === `fvisit-${item.userId}`}
                      onClick={() =>
                        run(
                          `fvisit-${item.userId}`,
                          async () => {
                            const res = await visitPetFriend(item.userId)
                            if (res.data.success && res.data.data) {
                              setTip(res.data.data.message)
                            }
                            return res
                          },
                          '互访成功！',
                        )
                      }
                    >
                      去互访
                    </Button>
                    <Button
                      size="small"
                      danger
                      loading={pending === `fremove-${item.userId}`}
                      onClick={() => run(`fremove-${item.userId}`, () => removePetFriend(item.userId), '已删除好友')}
                    >
                      删除
                    </Button>
                  </span>
                </div>
              ))}
            </div>
          )}
          {friends.outgoing.length > 0 && (
            <p className={styles.careMeta}>等待回应：{friends.outgoing.map((item) => item.nickname).join('、')}</p>
          )}
        </div>
      )}

      {tab === 'wall' && (
        <div>
          <p className={styles.careMeta}>
            看谁的留言墙：
            <Select
              size="small"
              style={{ width: 180 }}
              value={wallPetId}
              onChange={(value) => setWallPetId(Number(value))}
              options={myPets.map((item) => ({ value: item.petId, label: `${item.name}（我的）` }))}
            />
            {wall && <span> · 共 {wall.total} 条</span>}
          </p>
          {wall && (
            <>
              <p className={styles.careBalance}>
                {wall.petName} 的留言墙 · {wall.ownerNickname}
                {wall.welcomeMessage ? `：「${wall.welcomeMessage}」` : ''}（每日可留言 {wall.dailyPostLimit} 条）
              </p>
              <Input
                value={wallInput}
                maxLength={120}
                onChange={(event) => setWallInput(event.target.value)}
                placeholder="写一句留言吧（1-120 字）"
                addonAfter={
                  <Button
                    type="link"
                    size="small"
                    loading={pending === 'wall-post'}
                    onClick={() =>
                      run(
                        'wall-post',
                        async () => {
                          const res = await postPetWallMessage({ petId: wallPetId, content: wallInput })
                          if (res.data.success) {
                            setWallInput('')
                          }
                          return res
                        },
                        '留言成功！',
                      )
                    }
                  >
                    留言
                  </Button>
                }
              />
              {wall.messages.length === 0 ? (
                <p className={styles.bottleHint}>还没有留言，说点什么吧～</p>
              ) : (
                wall.messages.map((item) => (
                  <div key={item.id} className={styles.careCard} style={{ marginTop: 8 }}>
                    <span className={styles.careMeta}>
                      {item.authorPetName ? `${SPECIES_EMOJI[item.authorPetSpecies ?? ''] || '🐾'} ` : ''}
                      {item.authorNickname}
                      {item.ownerReply ? '（我的回复）' : ''} · {item.createdAt?.slice(5, 16).replace('T', ' ')}
                    </span>
                    <strong>{item.content}</strong>
                    <span className={styles.careActions}>
                      <Button
                        size="small"
                        loading={pending === `like-${item.id}`}
                        onClick={() => run(`like-${item.id}`, () => likePetWallMessage(item.id), item.liked ? '已取消点赞' : '点赞成功')}
                      >
                        {item.liked ? '取消赞' : '点赞'} {item.likeCount}
                      </Button>
                      {item.owner && item.parentId === null && (
                        <Button
                          size="small"
                          onClick={() => {
                            setReplyTo(replyTo === item.id ? null : item.id)
                            setReplyInput('')
                          }}
                        >
                          回复
                        </Button>
                      )}
                      {(item.mine || item.owner) && (
                        <Button
                          size="small"
                          danger
                          loading={pending === `del-${item.id}`}
                          onClick={() => run(`del-${item.id}`, () => deletePetWallMessageApi(item.id), '已删除')}
                        >
                          删除
                        </Button>
                      )}
                    </span>
                    {replyTo === item.id && (
                      <Input
                        value={replyInput}
                        maxLength={120}
                        onChange={(event) => setReplyInput(event.target.value)}
                        placeholder="回复这条留言"
                        addonAfter={
                          <Button
                            type="link"
                            size="small"
                            loading={pending === `reply-${item.id}`}
                            onClick={() =>
                              run(
                                `reply-${item.id}`,
                                async () => {
                                  const res = await replyPetWallMessage({ messageId: item.id, content: replyInput })
                                  if (res.data.success) {
                                    setReplyTo(null)
                                  }
                                  return res
                                },
                                '回复成功！',
                              )
                            }
                          >
                            发送
                          </Button>
                        }
                      />
                    )}
                    {item.replies.map((reply) => (
                      <span key={reply.id} className={styles.careMeta}>
                        ↳ {reply.authorNickname}：{reply.content}
                      </span>
                    ))}
                  </div>
                ))
              )}
            </>
          )}
        </div>
      )}

      <Modal
        title={relationTarget ? `和 ${relationTarget.name} 建立关系` : '建立关系'}
        open={relationTarget !== null}
        onCancel={() => setRelationTarget(null)}
        onOk={() =>
          run(
            'relation-request',
            async () => {
              const res = await requestPetRelation({
                toPetId: relationTarget?.petId as number,
                relType: relationType,
                message: relationMsg || undefined,
              })
              if (res.data.success) {
                setRelationTarget(null)
              }
              return res
            },
            '申请已发出，等对方回应～',
          )
        }
        confirmLoading={pending === 'relation-request'}
        okText="发送申请"
      >
        <Select
          style={{ width: '100%', marginBottom: 12 }}
          value={relationType}
          onChange={setRelationType}
          options={[
            { value: 'COUPLE', label: '情侣（专属，每只宠物仅一段）' },
            { value: 'BESTIE', label: '闺蜜' },
            { value: 'BROTHER', label: '兄弟' },
            { value: 'CONFIDANT', label: '死党' },
          ]}
        />
        <Input
          value={relationMsg}
          maxLength={40}
          onChange={(event) => setRelationMsg(event.target.value)}
          placeholder="留一句申请留言（可选，40 字以内）"
        />
      </Modal>
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
  const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
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

  /** 亲密度概览（展示型数据：失败保持原值，不打断页面） */
  const loadIntimacy = useCallback(async () => {
    try {
      const { data: res } = await getPetIntimacy()
      if (res.success && res.data) {
        setIntimacy(res.data)
      }
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  useEffect(() => {
    if (pet) {
      void loadIntimacy()
    }
  }, [pet?.petId, loadIntimacy])

  /**
   * 陪伴心跳：页面可见时每 60 秒上报一次（服务端按日封顶折算亲密度，防止挂机刷分）。
   * 页面隐藏时不上报——"陪伴"必须是真的在宠物页停留。
   */
  useEffect(() => {
    if (!pet) {
      return
    }
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') {
        return
      }
      void (async () => {
        try {
          const { data: res } = await sendPetCompanionHeartbeat(60)
          if (res.success && res.data) {
            setIntimacy(res.data)
          }
        } catch {
          // 心跳失败静默（下一轮重试）
        }
      })()
    }, 60_000)
    return () => window.clearInterval(timer)
  }, [pet?.petId])

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
    if (action === 'openRoom') { setPanel('home'); return }
    if (action === 'openDaily') { setPanel('daily'); return }
    if (action === 'openSocial') { setPanel('social'); return }
    if (action === 'openCareer') { setPanel('care'); return }
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
            {SPECIES_EMOJI[pet.species]} {pet.name}
            <span className={pet.gender === 'FEMALE' ? styles.genderFemale : styles.genderMale}>
              {pet.gender === 'FEMALE' ? '♀' : '♂'}
            </span>
            {' '}· Lv.{pet.level} · {STATUS_LABEL[pet.status] || '悠闲中'}
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
          <Button className={styles.gameBtn} size="small" loading={pendingInteraction === 'feed'} onClick={() => handleIntent('feed')}>🍖 喂食</Button>
          <Button className={styles.gameBtn} size="small" loading={pendingInteraction === 'play'} onClick={() => handleIntent('play')}>🎾 玩耍</Button>
          <Button className={styles.gameBtn} size="small" loading={pendingInteraction === 'clean'} onClick={() => handleIntent('clean')}>🧼 清洁</Button>
          <Button className={styles.gameBtn} size="small" loading={pendingInteraction === 'rest'} onClick={() => handleIntent('rest')}>😴 休息</Button>
          <Button className={styles.gameBtn} size="small" onClick={() => setProfileOpen(true)}>📇 档案</Button>
          <Button className={styles.gameBtn} size="small" onClick={() => setPanel('care')}>🎒 养成</Button>
        </div>
        {/* 三期：亲密度与陪伴（数值服务端权威，前端只展示；陪伴时长由心跳累计） */}
        <p className={styles.intimacyRow}>
          💞 亲密度 {intimacy ? `${intimacy.intimacy}（${intimacy.levelName}）` : `${pet.intimacy ?? 0}（${pet.intimacyLevelName ?? '初识'}）`}
          {intimacy && intimacy.nextLevelAt !== null ? ` · 还差 ${intimacy.toNext} 升到下一级` : ''}
          {' · '}经验加成 +{intimacy?.expBonusPercent ?? pet.intimacyExpBonusPercent ?? 0}%
          {' · '}已陪伴 {Math.floor((intimacy?.companionSeconds ?? pet.companionSeconds ?? 0) / 3600)} 小时
          {intimacy ? `（今日 ${Math.round(intimacy.todayCompanionSeconds / 60)} 分钟，连续 ${intimacy.companionStreak} 天）` : ''}
        </p>
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
        {panel === 'daily' && <DailyPanel onRefresh={refresh} />}
        {panel === 'social' && <SocialPanel pet={pet} onRefresh={refresh} />}
        {panel === 'home' && (
          <div>
            <HomePanel onRefresh={refresh} />
            <div className={styles.homePanel}>
              <h4 className={styles.sectionTitle}>{pet.name} 的养成日常</h4>
              <ul className={styles.tipsList}>
                <li>🍖 饱食和🧼清洁会随时间自然下降，记得回来照顾它</li>
                <li>💼 打工/职业赚星光，📚 读书涨智力（智力影响学习与捞瓶收益）</li>
                <li>🧸 家园舒适度越高，休息时心情恢复越多（记得摆放家具）</li>
                <li>🤝 关系/好友/留言墙都在「社交」里，互访双方都有收益</li>
                <li>💬 和它聊聊天，它会记住你的喜好哦</li>
              </ul>
            </div>
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
