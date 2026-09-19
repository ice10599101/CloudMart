import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ActivityIndicator,
  ScrollView,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { WebView } from 'react-native-webview'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { petApi, type PetAchievement, type PetBattleItem, type PetBottleStatus, type PetChatMessage, type PetInfo, type PetJobItem, type PetOpponent, type PetStudyItem } from '@/api/pet'

/**
 * 社区宠物主页（App 端，实施文档 §4）。
 *
 * Cocos 宠物舞台通过 react-native-webview 嵌入（产物 URL 取 EXPO_PUBLIC_PET_GAME_URL，
 * 未配置/加载超时自动 Fail-Open 原生降级舞台）；宿主负责页面、导航与 mall-pet API，
 * 数值全部服务端结算，游戏只发意图。
 */

const SPECIES_EMOJI: Record<string, string> = { CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼' }
const PERSONALITY_LABEL: Record<string, string> = {
  LIVELY: '活泼', GENTLE: '温柔', TSUNDERE: '傲娇', SIMPLE: '憨厚', COOL: '高冷', CHATTERBOX: '话痨',
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

/** 宿主 → Cocos 消息（与 pet-game PetGameBridge 同协议） */
type HostToGame =
  | { source: 'pet-host'; type: 'init'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'petState'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
  | { source: 'pet-host'; type: 'battleRounds'; rounds: Array<Record<string, unknown>>; won: boolean }

function toDisplayState(pet: PetInfo): Record<string, unknown> {
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
  }
}

interface BarProps {
  label: string
  value: number
  max: number
  color: string
}

function StateBar({ label, value, max, color }: BarProps) {
  const colors = useTheme()
  const ratio = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
      <Text style={{ width: 64, fontSize: FontSize.xs, color: colors.textSecondary }}>{label}</Text>
      <View style={{ flex: 1, height: 8, borderRadius: 999, backgroundColor: colors.border, overflow: 'hidden' }}>
        <View style={{ width: `${ratio * 100}%`, height: '100%', borderRadius: 999, backgroundColor: color }} />
      </View>
      <Text style={{ width: 56, fontSize: FontSize.xs, color: colors.textTertiary, textAlign: 'right' }}>
        {value}/{max}
      </Text>
    </View>
  )
}

function ChipButton({ label, onPress, disabled, primary }: { label: string; onPress: () => void; disabled?: boolean; primary?: boolean }) {
  const colors = useTheme()
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled}
      style={{
        paddingVertical: 10,
        paddingHorizontal: 16,
        borderRadius: BorderRadius.md,
        backgroundColor: disabled ? colors.border : primary ? colors.primary : colors.bgContainer,
        borderWidth: primary ? 0 : 1,
        borderColor: colors.border,
        alignItems: 'center',
      }}
    >
      <Text style={{ color: disabled ? colors.textTertiary : primary ? '#fff' : colors.text, fontSize: FontSize.sm }}>
        {label}
      </Text>
    </TouchableOpacity>
  )
}

export default function PetScreen() {
  const colors = useTheme()
  const params = useLocalSearchParams<{ intent?: string }>()
  const gameUrl = process.env.EXPO_PUBLIC_PET_GAME_URL
  const webRef = useRef<WebView>(null)

  const [loading, setLoading] = useState(true)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [noPet, setNoPet] = useState(false)
  const [gameReady, setGameReady] = useState(false)
  const [gameFailed, setGameFailed] = useState(!gameUrl)
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

  const postToGame = useCallback((message: HostToGame) => {
    webRef.current?.injectJavaScript(
      `window.__petHostMessage && window.__petHostMessage(${JSON.stringify(JSON.stringify(message))}); true;`,
    )
  }, [])

  const syncStage = useCallback((next: PetInfo) => {
    postToGame({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [postToGame])

  const refresh = useCallback(async () => {
    try {
      const { data: res } = await petApi.getMyPet()
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
        if (gameReady) syncStage(res.data)
      }
    } catch {
      setNoPet(true)
    } finally {
      setLoading(false)
    }
  }, [gameReady, syncStage])

  useEffect(() => { refresh() }, [refresh])

  useEffect(() => {
    if (!gameUrl) return
    const timer = setTimeout(() => setGameFailed((f) => !gameReady), 2500)
    return () => clearTimeout(timer)
  }, [gameUrl, gameReady])

  const loadPanelData = useCallback(async (key: PanelKey) => {
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
      const [opponentRes, historyRes] = await Promise.all([petApi.listOpponents(), petApi.listBattleHistory({ page: 1, pageSize: 10 })])
      if (opponentRes.data.success) setOpponents(opponentRes.data.data || [])
      if (historyRes.data.success) setHistory(historyRes.data.data || [])
    } else if (key === 'achievements') {
      const { data: res } = await petApi.listAchievements()
      if (res.success) setAchievements(res.data || [])
    } else if (key === 'chat') {
      const { data: res } = await petApi.chatHistory({ pageSize: 30 })
      if (res.success) setChatMessages((res.data || []).slice().reverse())
    }
  }, [])

  useEffect(() => {
    if (pet) loadPanelData(panel)
  }, [panel, pet, loadPanelData])

  // 微信 web-view 宿主页 navigateTo 落地：intent 参数触发对应动作
  useEffect(() => {
    const intent = params.intent
    if (!intent) return
    setPanel('home')
    if (['feed', 'play', 'clean', 'rest'].includes(intent)) {
      runInteraction(intent as 'feed' | 'play' | 'clean' | 'rest')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.intent])

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
    if (!pet) return
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      if (res.success && res.data) {
        setPet(res.data)
        if (gameReady) {
          syncStage(res.data)
          postToGame({ source: 'pet-host', type: 'actionResult', action, ok: true })
        }
      }
    } catch {
      if (gameReady) {
        postToGame({ source: 'pet-host', type: 'actionResult', action, ok: false, message: '现在不行哦' })
      }
    }
  }

  const adopt = async () => {
    if (!adoptName.trim()) return
    try {
      const { data: res } = await petApi.createPet({
        name: adoptName.trim(),
        species: adoptSpecies as PetInfo['species'],
        personality: adoptPersonality as PetInfo['personality'],
      })
      if (res.success) {
        setNoPet(false)
        setAdoptName('')
        refresh()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const onWebViewMessage = useCallback((event: { nativeEvent: { data: string } }) => {
    try {
      const message = JSON.parse(event.nativeEvent.data)
      if (message?.source !== 'pet-game') return
      if (message.type === 'ready') {
        setGameReady(true)
        if (pet) postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(pet) })
      } else if (message.type === 'intent') {
        const action = message.action as string
        if (['feed', 'play', 'clean', 'rest'].includes(action)) {
          runInteraction(action as 'feed' | 'play' | 'clean' | 'rest')
        } else {
          const mapping: Record<string, PanelKey> = {
            openWork: 'work', openStudy: 'study', openBottle: 'bottle',
            openBattle: 'battle', openChat: 'chat', openAchievements: 'achievements',
          }
          if (mapping[action]) setPanel(mapping[action])
        }
      }
    } catch {
      // 非 JSON 消息忽略
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pet, gameReady])

  const battleRespond = async (battle: PetBattleItem, accept: boolean) => {
    try {
      const { data: res } = accept ? await petApi.acceptBattle(battle.battleId) : await petApi.declineBattle(battle.battleId)
      if (res.success) {
        if (accept && res.data.status === 'FINISHED' && res.data.rounds && gameReady) {
          const rounds = JSON.parse(res.data.rounds) as Array<Record<string, unknown>>
          const myPetId = res.data.role === 'ATTACKER' ? res.data.attackerPetId : res.data.defenderPetId
          postToGame({ source: 'pet-host', type: 'battleRounds', rounds, won: res.data.winnerPetId === myPetId })
        }
        await loadPanelData('battle')
        refresh()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const sendChat = async () => {
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
    } catch {
      // 拦截器已提示（限频 429）
    }
  }

  const barRows = useMemo(() => {
    if (!pet) return []
    return [
      { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: '#ff6c6c' },
      { label: '🍖 饱食', value: pet.hunger, max: 100, color: '#ffb258' },
      { label: '💗 心情', value: pet.happiness, max: 100, color: '#ff69b4' },
      { label: '⚡ 精力', value: pet.energy, max: 100, color: '#62d88a' },
      { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: '#60beff' },
    ]
  }, [pet])

  if (loading) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgBase }}>
        <ActivityIndicator color={colors.primary} />
      </View>
    )
  }

  if (noPet || !pet) {
    return (
      <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.lg }}>
        <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: colors.text, textAlign: 'center', marginTop: Spacing.xl }}>
          🐾 领养一只属于你的宠物
        </Text>
        <Text style={{ fontSize: FontSize.sm, color: colors.textSecondary, textAlign: 'center', marginTop: Spacing.xs, marginBottom: Spacing.lg }}>
          陪你打工、读书、捞漂流瓶、聊天
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, justifyContent: 'center' }}>
          {Object.entries(SPECIES_EMOJI).map(([key, emoji]) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptSpecies(key)}
              style={{
                width: 88, alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg,
                borderWidth: 2, borderColor: adoptSpecies === key ? colors.primary : colors.border,
              }}
            >
              <Text style={{ fontSize: 36 }}>{emoji}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center', marginTop: Spacing.md }}>
          {Object.entries(PERSONALITY_LABEL).map(([key, label]) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptPersonality(key)}
              style={{
                paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
                borderWidth: 1, borderColor: adoptPersonality === key ? colors.primary : colors.border,
                backgroundColor: adoptPersonality === key ? colors.primary : 'transparent',
              }}
            >
              <Text style={{ color: adoptPersonality === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <TextInput
          value={adoptName}
          onChangeText={setAdoptName}
          maxLength={12}
          placeholder="给它取个名字（1-12 字）"
          placeholderTextColor={colors.textTertiary}
          style={{
            marginTop: Spacing.lg, paddingHorizontal: Spacing.md, paddingVertical: 12,
            borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border,
            color: colors.text, backgroundColor: colors.bgContainer,
          }}
        />
        <View style={{ marginTop: Spacing.md }}>
          <ChipButton label="领养它" primary onPress={adopt} disabled={!adoptName.trim()} />
        </View>
      </ScrollView>
    )
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.md, paddingBottom: 48 }}>
      {/* Cocos 舞台（未部署 Fail-Open 原生降级） */}
      {gameUrl && !gameFailed ? (
        <View style={{ height: 320, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.md, backgroundColor: '#18223a' }}>
          <WebView
            ref={webRef}
            source={{ uri: gameUrl }}
            onMessage={onWebViewMessage}
            scrollEnabled={false}
            style={{ flex: 1, backgroundColor: 'transparent' }}
          />
        </View>
      ) : (
        <View style={{
          height: 200, borderRadius: BorderRadius.lg, alignItems: 'center', justifyContent: 'center',
          backgroundColor: '#18223a', marginBottom: Spacing.md,
        }}>
          <Text style={{ fontSize: 96 }}>{SPECIES_EMOJI[pet.species] || '🐾'}</Text>
          <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: FontSize.xs, marginTop: Spacing.xs }}>原生模式 · 配置 EXPO_PUBLIC_PET_GAME_URL 启用 Cocos 舞台</Text>
        </View>
      )}

      {/* 头部信息 */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: colors.text }}>
          {SPECIES_EMOJI[pet.species]} {pet.name} · Lv.{pet.level}
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: colors.textSecondary }}>{STATUS_LABEL[pet.status]}</Text>
      </View>

      {/* 状态条 */}
      <View style={{ marginTop: Spacing.md, gap: 8 }}>
        {barRows.map((row) => <StateBar key={row.label} {...row} />)}
      </View>

      {/* 互动按钮（Cocos 意图与原生按钮共用同一 API） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md, flexWrap: 'wrap' }}>
        <ChipButton label="🍖 喂食" primary onPress={() => runInteraction('feed')} />
        <ChipButton label="🎾 玩耍" primary onPress={() => runInteraction('play')} />
        <ChipButton label="🫧 清洁" primary onPress={() => runInteraction('clean')} />
        <ChipButton label="💤 休息" primary onPress={() => runInteraction('rest')} />
      </View>

      {/* 面板切换 */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.md }}>
        {PANELS.map((item) => (
          <TouchableOpacity
            key={item.key}
            onPress={() => setPanel(item.key)}
            style={{
              paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999,
              backgroundColor: panel === item.key ? colors.primary : colors.bgContainer,
              borderWidth: 1, borderColor: panel === item.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: panel === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>
              {item.emoji} {item.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* 面板内容 */}
      <View style={{ marginTop: Spacing.md, backgroundColor: colors.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md, borderWidth: 1, borderColor: colors.border }}>
        {panel === 'home' && (
          <View style={{ gap: 6 }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>养成小贴士</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>💼 打工赚星光，📚 读书涨智力</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>⚔️ 对战由服务端计算，输了也有经验</Text>
          </View>
        )}

        {panel === 'work' && (
          <View style={{ gap: Spacing.sm }}>
            {jobs.map((job) => (
              <View key={String(job.configId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{job.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    ⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost} · ✨+{job.expReward} · ⭐+{job.currencyReward}
                  </Text>
                </View>
                {job.eligible
                  ? (
                    <ChipButton label="接单" primary onPress={async () => {
                      try {
                        const { data: res } = await petApi.startWork(job.configId)
                        if (res.success) { refresh(); loadPanelData('work') }
                      } catch { /* 拦截器已提示 */ }
                    }} />
                  )
                  : <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>Lv.{job.requiredLevel} 解锁</Text>}
              </View>
            ))}
            {pet.claimableActivityType === 'WORK' && (
              <ChipButton label="领取打工奖励" primary onPress={async () => {
                try {
                  const { data: res } = await petApi.claimWork()
                  if (res.success) { refresh(); loadPanelData('work') }
                } catch { /* 已领取 409 */ }
              }} />
            )}
          </View>
        )}

        {panel === 'study' && (
          <View style={{ gap: Spacing.sm }}>
            {studies.map((study) => (
              <View key={String(study.configId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{study.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    ⏱ {Math.round(study.durationSeconds / 60)} 分钟 · ✨+{study.expReward} · 🧠+{study.intelligenceReward}
                  </Text>
                </View>
                {study.eligible
                  ? (
                    <ChipButton label="上课" primary onPress={async () => {
                      try {
                        const { data: res } = await petApi.startStudy(study.configId)
                        if (res.success) { refresh(); loadPanelData('study') }
                      } catch { /* 拦截器已提示 */ }
                    }} />
                  )
                  : <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>Lv.{study.requiredLevel} 解锁</Text>}
              </View>
            ))}
            {pet.claimableActivityType === 'STUDY' && (
              <ChipButton label="领取学习奖励" primary onPress={async () => {
                try {
                  const { data: res } = await petApi.claimStudy()
                  if (res.success) { refresh(); loadPanelData('study') }
                } catch { /* 已领取 409 */ }
              }} />
            )}
          </View>
        )}

        {panel === 'bottle' && bottle && (
          <View style={{ gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm }}>🌊 {bottle.unlockedArea} · 估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%</Text>
            {bottle.fishing
              ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🐾 捞瓶中…剩余 {Math.floor(bottle.remainingSeconds / 60)}:{String(bottle.remainingSeconds % 60).padStart(2, '0')}</Text>
              : (
                <ChipButton
                  label={bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶'}
                  primary={!bottle.canClaim && bottle.cooldownRemainingSeconds <= 0}
                  disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
                  onPress={async () => {
                    try {
                      const { data: res } = bottle.canClaim ? await petApi.claimBottle() : await petApi.startBottle()
                      if (res.success) {
                        const result = res.data.result ? (JSON.parse(res.data.result) as { outcome?: string }) : null
                        if (result?.outcome === 'CAUGHT') {
                          router.push('/encounter-letters')
                        }
                        loadPanelData('bottle')
                        refresh()
                      }
                    } catch { /* 拦截器已提示 */ }
                  }}
                />
              )}
          </View>
        )}

        {panel === 'battle' && (
          <View style={{ gap: Spacing.md }}>
            {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
              <View key={String(battle.battleId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm }}>⚔️ 收到宠物 #{battle.attackerPetId} 的挑战</Text>
                <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                  <ChipButton label="应战" primary onPress={() => battleRespond(battle, true)} />
                  <ChipButton label="婉拒" onPress={() => battleRespond(battle, false)} />
                </View>
              </View>
            ))}
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>选择对手</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
              {opponents.map((opponent) => (
                <TouchableOpacity
                  key={`${String(opponent.petId)}-${opponent.name}`}
                  onPress={async () => {
                    try {
                      const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                      if (res.success && res.data.status === 'FINISHED' && res.data.rounds && gameReady) {
                        const rounds = JSON.parse(res.data.rounds) as Array<Record<string, unknown>>
                        postToGame({ source: 'pet-host', type: 'battleRounds', rounds, won: res.data.winnerPetId === res.data.attackerPetId })
                      }
                      loadPanelData('battle')
                      refresh()
                    } catch { /* 拦截器已提示 */ }
                  }}
                  style={{ width: 104, alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: colors.border }}
                >
                  <Text style={{ fontSize: 30 }}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
                  <Text style={{ color: colors.text, fontSize: FontSize.xs }}>{opponent.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {history.filter((battle) => battle.status !== 'PENDING').slice(0, 5).map((battle) => (
              <View key={String(battle.battleId)} style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  {battle.status === 'FINISHED' ? (battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId) ? '🏆 胜利' : '💧 战败') : battle.status === 'DECLINED' ? '🚫 被婉拒' : '⌛ 未应战'}
                </Text>
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>+{battle.expReward} 经验</Text>
              </View>
            ))}
          </View>
        )}

        {panel === 'chat' && (
          <View style={{ gap: Spacing.sm }}>
            <View style={{ maxHeight: 320, gap: 6 }}>
              {chatMessages.map((item) => (
                <View
                  key={String(item.messageId)}
                  style={{
                    alignSelf: item.role === 'USER' ? 'flex-end' : 'flex-start',
                    maxWidth: '85%',
                    padding: 10,
                    borderRadius: BorderRadius.lg,
                    backgroundColor: item.role === 'USER' ? colors.primary : colors.bgBase,
                  }}
                >
                  <Text style={{ color: item.role === 'USER' ? '#fff' : colors.text, fontSize: FontSize.sm }}>{item.content}</Text>
                </View>
              ))}
            </View>
            <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
              <TextInput
                value={chatInput}
                onChangeText={setChatInput}
                maxLength={500}
                placeholder="跟宠物聊聊…（每日 20 条）"
                placeholderTextColor={colors.textTertiary}
                style={{
                  flex: 1, paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
                  borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgBase,
                }}
              />
              <ChipButton label="发送" primary onPress={sendChat} />
            </View>
          </View>
        )}

        {panel === 'achievements' && (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
            {achievements.map((item) => (
              <View
                key={String(item.achievementId)}
                style={{
                  width: '47%', alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg,
                  borderWidth: 1, borderColor: colors.border, opacity: item.achieved ? 1 : 0.45,
                }}
              >
                <Text style={{ fontSize: 30 }}>{item.icon}</Text>
                <Text style={{ color: colors.text, fontSize: FontSize.xs, fontWeight: '600' }}>{item.name}</Text>
                <Text style={{ color: colors.textTertiary, fontSize: 10, textAlign: 'center' }}>{item.description}</Text>
              </View>
            ))}
          </View>
        )}
      </View>
    </ScrollView>
  )
}
