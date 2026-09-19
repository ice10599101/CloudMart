import { useCallback, useEffect, useState } from 'react'
import { View, Text, Input, Button, ScrollView } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { petApi, type PetAchievement, type PetBattleItem, type PetBottleStatus, type PetChatMessage, type PetInfo, type PetJobItem, type PetOpponent, type PetStudyItem } from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

/**
 * 社区宠物原生管理页（小程序/H5，实施文档 §5）。
 *
 * 全平台可用的完整管理入口：领养/互动/打工/读书/捞瓶/对战/聊天/成就。
 * Cocos 舞台在独立页 petStage（web-view/iframe）；微信小程序 web-view 的
 * 实时意图经 navigateTo 携 intent 参数回落本页执行（平台约束见实施文档 §2.4）。
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

const SPECIES_EMOJI: Record<string, string> = { CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼' }
const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
}

type PanelKey = 'home' | 'work' | 'study' | 'bottle' | 'battle' | 'chat' | 'achievements'

const PANELS: Array<{ key: PanelKey; label: string }> = [
  { key: 'home', label: '🏠 小窝' },
  { key: 'work', label: '💼 打工' },
  { key: 'study', label: '📚 读书' },
  { key: 'bottle', label: '🍾 捞瓶' },
  { key: 'battle', label: '⚔️ 对战' },
  { key: 'chat', label: '💬 聊天' },
  { key: 'achievements', label: '🏆 成就' },
]

function friendlyError(error: unknown): string {
  const code = (error as { code?: string }).code
  const hints: Record<string, string> = {
    PET_NOT_FOUND: '你还没有宠物，先领养一只吧',
    PET_ALREADY_EXISTS: '你已经有一只宠物啦',
    PET_STATE_FULL: '已经吃饱/很干净啦',
    PET_ENERGY_INSUFFICIENT: '没有力气了，先休息一下吧',
    PET_HUNGER_TOO_LOW: '肚子太空了，先喂点东西吧',
    PET_ACTIVITY_CONFLICT: '宠物正在忙别的事',
    PET_ACTIVITY_ALREADY_CLAIMED: '奖励已经领取过啦',
    PET_ACTIVITY_NOT_FINISHED: '任务还没完成，再等等',
    PET_LEVEL_REQUIRED: '等级还不够，先多养成一下',
    PET_BOTTLE_COOLDOWN: '宠物刚回来还在休息',
    PET_AI_RATE_LIMITED: '今天聊得够多啦，明天再来吧',
    PET_INTERACTION_RATE_LIMITED: '今天喂得够多啦',
    PET_RENAME_COOLDOWN: '30 天只能改一次名',
    WISH_SERVICE_UNAVAILABLE: '心愿服务暂时不可用，稍后再试',
  }
  return hints[code ?? ''] || (error as { message?: string })?.message || '操作失败，请稍后再试'
}

export default function PetPage() {
  const { params } = useRouter()
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user: currentUser } = useAuthStore()

  const [loading, setLoading] = useState(true)
  const [noPet, setNoPet] = useState(false)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [panel, setPanel] = useState<PanelKey>('home')
  const [adoptSpecies, setAdoptSpecies] = useState('CAT')
  const [adoptPersonality, setAdoptPersonality] = useState('LIVELY')
  const [adoptName, setAdoptName] = useState('')
  const [jobs, setJobs] = useState<PetJobItem[]>([])
  const [studies, setStudies] = useState<PetStudyItem[]>([])
  const [opponents, setOpponents] = useState<PetOpponent[]>([])
  const [history, setHistory] = useState<PetBattleItem[]>([])
  const [bottle, setBottle] = useState<PetBottleStatus | null>(null)
  const [achievements, setAchievements] = useState<PetAchievement[]>([])
  const [chatMessages, setChatMessages] = useState<PetChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  const refresh = useCallback(async () => {
    try {
      const { data: res } = await petApi.getMyPet()
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
      }
    } catch (error) {
      setNoPet(true)
      if ((error as { code?: string }).code !== 'PET_NOT_FOUND') {
        toast(friendlyError(error))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const loadPanelData = useCallback(async (key: PanelKey) => {
    try {
      if (key === 'work') {
        const { data: res } = await petApi.listJobs()
        if (res.success) setJobs(res.data || [])
      } else if (key === 'study') {
        const { data: res } = await petApi.listStudies()
        if (res.success) setStudies(res.data || [])
      } else if (key === 'bottle') {
        const { data: res } = await petApi.getBottleStatus()
        if (res.success) setBottle(res.data)
      } else if (key === 'battle') {
        const [opponentRes, historyRes] = await Promise.all([
          petApi.listOpponents(),
          petApi.listBattleHistory({ page: 1, pageSize: 10 }),
        ])
        if (opponentRes.data.success) setOpponents(opponentRes.data.data || [])
        if (historyRes.data.success) setHistory(historyRes.data.data || [])
      } else if (key === 'achievements') {
        const { data: res } = await petApi.listAchievements()
        if (res.success) setAchievements(res.data || [])
      } else if (key === 'chat') {
        const { data: res } = await petApi.chatHistory({ pageSize: 30 })
        if (res.success) setChatMessages((res.data || []).slice().reverse())
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [])

  useEffect(() => {
    if (currentUser) {
      refresh()
    }
  }, [currentUser, refresh])

  useEffect(() => {
    if (pet) loadPanelData(panel)
  }, [panel, pet, loadPanelData])

  // 微信 web-view 舞台页的实时意图回落：navigateTo('/pages/pet/index?intent=feed')
  useEffect(() => {
    const intent = params.intent
    if (!intent || !pet) return
    setPanel('home')
    if (intent === 'feed' || intent === 'play' || intent === 'clean' || intent === 'rest') {
      void runInteraction(intent)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.intent, pet])

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      if (res.success && res.data) {
        setPet(res.data)
        toast('好嘞！')
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const adopt = async () => {
    if (!adoptName.trim()) {
      toast('先给宠物取个名字吧')
      return
    }
    try {
      const { data: res } = await petApi.createPet({
        name: adoptName.trim(),
        species: adoptSpecies as PetInfo['species'],
        personality: adoptPersonality as PetInfo['personality'],
      })
      if (res.success) {
        Taro.showToast({ title: '领养成功！', icon: 'success' })
        setAdoptName('')
        refresh()
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const openStage = () => {
    // H5 为 iframe 完整桥；微信小程序 web-view 只做观赏+状态展示（操作回本页）
    Taro.navigateTo({ url: '/pages/petStage/index' })
  }

  if (!currentUser && !loading) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <CustomNavBar title="我的宠物" />
        <View className={styles.empty} style={{ paddingTop: statusBarHeight + navBarHeight }}>
          <Text className={styles.emptyEmoji}>🐾</Text>
          <Text className={styles.emptyText}>登录后领养你的宠物吧</Text>
          <Button className={styles.primaryBtn} onClick={() => Taro.navigateTo({ url: '/pages/login/index' })}>去登录</Button>
        </View>
      </View>
    )
  }

  if (loading) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <CustomNavBar title="我的宠物" />
        <View className={styles.empty}><Text>加载中…</Text></View>
      </View>
    )
  }

  // 领养向导
  if (noPet || !pet) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <CustomNavBar title="我的宠物" />
        <ScrollView scrollY className={styles.adoptScroll} style={{ paddingTop: statusBarHeight + navBarHeight }}>
          <Text className={styles.adoptTitle}>🐾 领养一只属于你的宠物</Text>
          <Text className={styles.adoptDesc}>陪你打工、读书、捞漂流瓶、聊天</Text>
          <View className={styles.speciesRow}>
            {SPECIES_OPTIONS.map((option) => (
              <View
                key={option.value}
                className={`${styles.speciesCard} ${adoptSpecies === option.value ? styles.speciesActive : ''}`}
                onClick={() => setAdoptSpecies(option.value)}
              >
                <Text className={styles.speciesEmoji}>{option.emoji}</Text>
                <Text className={styles.speciesLabel}>{option.label}</Text>
              </View>
            ))}
          </View>
          <View className={styles.personalityRow}>
            {PERSONALITY_OPTIONS.map((option) => (
              <View
                key={option.value}
                className={`${styles.personalityChip} ${adoptPersonality === option.value ? styles.personalityActive : ''}`}
                onClick={() => setAdoptPersonality(option.value)}
              >
                <Text>{option.label}</Text>
              </View>
            ))}
          </View>
          <Input
            className={styles.nameInput}
            value={adoptName}
            maxlength={12}
            placeholder="给它取个名字（1-12 字）"
            onInput={(e) => setAdoptName(e.detail.value)}
          />
          <Button className={styles.primaryBtn} onClick={adopt}>领养它</Button>
        </ScrollView>
      </View>
    )
  }

  return (
    <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
      <CustomNavBar title="我的宠物" />
      <ScrollView scrollY className={styles.body} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        {/* 头部卡片 */}
        <View className={styles.heroCard}>
          <View className={styles.heroRow}>
            <Text className={styles.heroEmoji}>{SPECIES_EMOJI[pet.species] || '🐾'}</Text>
            <View className={styles.heroInfo}>
              <Text className={styles.heroName}>{pet.name} · Lv.{pet.level}</Text>
              <Text className={styles.heroStatus}>{STATUS_LABEL[pet.status]} · {pet.growthStage === 'BABY' ? '幼年' : pet.growthStage === 'YOUNG' ? '成长期' : '成年'}</Text>
            </View>
            <View className={styles.stageEntry} onClick={openStage}>
              <Text className={styles.stageEntryText}>进入舞台</Text>
            </View>
          </View>
          <View className={styles.bars}>
            {[
              { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: '#ff6c6c' },
              { label: '🍖 饱食', value: pet.hunger, max: 100, color: '#ffb258' },
              { label: '💗 心情', value: pet.happiness, max: 100, color: '#ff69b4' },
              { label: '⚡ 精力', value: pet.energy, max: 100, color: '#62d88a' },
              { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: '#60beff' },
            ].map((row) => (
              <View key={row.label} className={styles.barRow}>
                <Text className={styles.barLabel}>{row.label}</Text>
                <View className={styles.barTrack}>
                  <View className={styles.barFill} style={{ width: `${Math.min(100, (row.value / row.max) * 100)}%`, background: row.color }} />
                </View>
                <Text className={styles.barValue}>{row.value}/{row.max}</Text>
              </View>
            ))}
          </View>
          {/* 互动按钮 */}
          <View className={styles.actionRow}>
            <Button className={styles.actionBtn} onClick={() => runInteraction('feed')}>🍖 喂食</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('play')}>🎾 玩耍</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('clean')}>🫧 清洁</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('rest')}>💤 休息</Button>
          </View>
        </View>

        {/* 面板切换 */}
        <ScrollView scrollX className={styles.panelTabs} enhanced showScrollbar={false}>
          <View className={styles.panelTabsInner}>
            {PANELS.map((item) => (
              <View
                key={item.key}
                className={`${styles.panelTab} ${panel === item.key ? styles.panelTabActive : ''}`}
                onClick={() => setPanel(item.key)}
              >
                <Text>{item.label}</Text>
              </View>
            ))}
          </View>
        </ScrollView>

        <View className={styles.panelBody}>
          {panel === 'home' && (
            <View className={styles.tips}>
              <Text className={styles.tip}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
              <Text className={styles.tip}>💼 打工赚星光，📚 读书涨智力</Text>
              <Text className={styles.tip}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
              <Text className={styles.tip}>⚔️ 对战由服务端计算，输了也有经验</Text>
              <Text className={styles.tip}>💬 和它聊聊天，它会记住你的喜好哦</Text>
            </View>
          )}

          {panel === 'work' && (
            <View className={styles.jobList}>
              {jobs.map((job) => (
                <View key={String(job.configId)} className={styles.jobCard}>
                  <View className={styles.jobInfo}>
                    <Text className={styles.jobName}>{job.name}</Text>
                    <Text className={styles.jobMeta}>⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost} · ✨+{job.expReward} · ⭐+{job.currencyReward}</Text>
                  </View>
                  {job.eligible ? (
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.startWork(job.configId)
                        if (res.success) { toast('开工啦！'); loadPanelData('work'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>接单</Button>
                  ) : (
                    <Text className={styles.locked}>Lv.{job.requiredLevel}</Text>
                  )}
                </View>
              ))}
              {pet.claimableActivityType === 'WORK' && (
                <Button className={styles.primaryBtn} onClick={async () => {
                  try {
                    const { data: res } = await petApi.claimWork()
                    if (res.success) { toast('奖励已领取！'); loadPanelData('work'); refresh() }
                  } catch (error) { toast(friendlyError(error)) }
                }}>领取打工奖励</Button>
              )}
            </View>
          )}

          {panel === 'study' && (
            <View className={styles.jobList}>
              {studies.map((study) => (
                <View key={String(study.configId)} className={styles.jobCard}>
                  <View className={styles.jobInfo}>
                    <Text className={styles.jobName}>{study.name}</Text>
                    <Text className={styles.jobMeta}>⏱ {Math.round(study.durationSeconds / 60)} 分钟 · ✨+{study.expReward} · 🧠+{study.intelligenceReward}</Text>
                  </View>
                  {study.eligible ? (
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.startStudy(study.configId)
                        if (res.success) { toast('开始读书啦'); loadPanelData('study'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>上课</Button>
                  ) : (
                    <Text className={styles.locked}>Lv.{study.requiredLevel}</Text>
                  )}
                </View>
              ))}
              {pet.claimableActivityType === 'STUDY' && (
                <Button className={styles.primaryBtn} onClick={async () => {
                  try {
                    const { data: res } = await petApi.claimStudy()
                    if (res.success) { toast('学习奖励已领取！'); loadPanelData('study'); refresh() }
                  } catch (error) { toast(friendlyError(error)) }
                }}>领取学习奖励</Button>
              )}
            </View>
          )}

          {panel === 'bottle' && bottle && (
            <View className={styles.bottlePanel}>
              <Text className={styles.tip}>🌊 捞瓶区域：{bottle.unlockedArea}</Text>
              <Text className={styles.tip}>估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%（敏捷与等级加成）</Text>
              {bottle.fishing ? (
                <Text className={styles.tip}>🐾 捞瓶中…剩余 {Math.floor(bottle.remainingSeconds / 60)}:{String(bottle.remainingSeconds % 60).padStart(2, '0')}</Text>
              ) : (
                <Button
                  className={styles.primaryBtn}
                  disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
                  onClick={async () => {
                    try {
                      const { data: res } = bottle.canClaim ? await petApi.claimBottle() : await petApi.startBottle()
                      if (res.success) {
                        const result = res.data.result ? (JSON.parse(res.data.result) as { outcome?: string }) : null
                        if (result?.outcome === 'CAUGHT') {
                          Taro.showToast({ title: '捞到漂流瓶啦！', icon: 'success' })
                          Taro.navigateTo({ url: '/pages/encounterLetters/index' })
                        } else if (result?.outcome === 'EMPTY') {
                          toast('空手而归…宠物获得了经验')
                        } else {
                          toast('服务波动，稍后可重试领取')
                        }
                        loadPanelData('bottle')
                        refresh()
                      }
                    } catch (error) { toast(friendlyError(error)) }
                  }}
                >
                  {bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶（30 分钟）'}
                </Button>
              )}
            </View>
          )}

          {panel === 'battle' && (
            <View className={styles.battlePanel}>
              {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
                <View key={String(battle.battleId)} className={styles.pendingRow}>
                  <Text className={styles.tip}>⚔️ 收到宠物 #{battle.attackerPetId} 的挑战</Text>
                  <View className={styles.pendingBtns}>
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.acceptBattle(battle.battleId)
                        if (res.success) { toast('应战成功！'); loadPanelData('battle'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>应战</Button>
                    <Button className={styles.miniBtnGhost} onClick={async () => {
                      try {
                        await petApi.declineBattle(battle.battleId)
                        toast('已婉拒')
                        loadPanelData('battle')
                      } catch (error) { toast(friendlyError(error)) }
                    }}>婉拒</Button>
                  </View>
                </View>
              ))}
              <Text className={styles.sectionTitle}>选择对手</Text>
              <View className={styles.opponentGrid}>
                {opponents.map((opponent) => (
                  <View key={`${String(opponent.petId)}-${opponent.name}`} className={styles.opponentCard}
                    onClick={async () => {
                      try {
                        const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                        if (res.success) {
                          toast(res.data.status === 'FINISHED' ? '对战结束！' : '已发起挑战，等对方应战')
                          loadPanelData('battle')
                          refresh()
                        }
                      } catch (error) { toast(friendlyError(error)) }
                    }}
                  >
                    <Text className={styles.opponentEmoji}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
                    <Text className={styles.opponentName}>{opponent.name}</Text>
                    <Text className={styles.opponentMeta}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
                  </View>
                ))}
              </View>
            </View>
          )}

          {panel === 'chat' && (
            <View className={styles.chatPanel}>
              <View className={styles.chatList}>
                {chatMessages.map((item) => (
                  <View key={String(item.messageId)} className={item.role === 'USER' ? styles.chatMine : styles.chatPet}>
                    <Text className={styles.chatText}>{item.content}</Text>
                  </View>
                ))}
              </View>
              <View className={styles.chatInputRow}>
                <Input
                  className={styles.chatInput}
                  value={chatInput}
                  maxlength={500}
                  placeholder="跟宠物聊聊…（每日 20 条）"
                  onInput={(e) => setChatInput(e.detail.value)}
                />
                <Button className={styles.miniBtn} onClick={async () => {
                  const text = chatInput.trim()
                  if (!text) return
                  setChatInput('')
                  try {
                    const { data: res } = await petApi.chat(text)
                    if (res.success && res.data) {
                      setChatMessages((prev) => [...prev, {
                        messageId: `${res.data.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
                      }, res.data])
                    }
                  } catch (error) { toast(friendlyError(error)) }
                }}>发送</Button>
              </View>
            </View>
          )}

          {panel === 'achievements' && (
            <View className={styles.achGrid}>
              {achievements.map((item) => (
                <View key={String(item.achievementId)} className={`${styles.achCard} ${item.achieved ? '' : styles.achLocked}`}>
                  <Text className={styles.achIcon}>{item.icon}</Text>
                  <Text className={styles.achName}>{item.name}</Text>
                  <Text className={styles.achDesc}>{item.description}</Text>
                </View>
              ))}
            </View>
          )}
        </View>
      </ScrollView>
    </View>
  )
}
