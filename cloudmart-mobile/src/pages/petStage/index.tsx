import { useCallback, useEffect, useRef, useState } from 'react'
import { View, Text, WebView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import { PET_STAGE_URL, petApi, type PetInfo } from '@/api/pet'
import styles from './index.module.scss'

/**
 * Cocos 宠物舞台页（实施文档 §2.4/§5）。
 *
 * <p>H5：宿主（本页）与 iframe 内的 Cocos 游戏建立完整双向 postMessage 桥——
 * 宿主下发权威宠物状态/动作结果/聊天气泡，游戏只回传意图（intent）与点击事件，
 * 业务 API 仍由宿主调用，游戏不直接访问后端、不计算任何数值。</p>
 *
 * <p>微信小程序：web-view 为原生全屏组件无法实时双向通信，此处仅做观赏；
 * 游戏内的操作按钮会经 wx.miniProgram.navigateTo('/pages/pet/index?intent=xxx')
 * 实时回落到原生管理页执行（postMessage 在小程序侧仅在回退/分享时机可收）。</p>
 *
 * <p>产物未部署或加载失败时 Fail-Open：舞台不可交互，但全部管理功能在原生页完整可用。</p>
 */

/** 宿主 → 游戏（与 pet-game PetGameBridge 完全同协议） */
type HostToGame =
  | { source: 'pet-host'; type: 'init'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'petState'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
  | { source: 'pet-host'; type: 'battleRounds'; rounds: Array<Record<string, unknown>>; won: boolean }
  | { source: 'pet-host'; type: 'chatBubble'; content: string }

/** 意图 → 原生管理页路由参数（面板类交由管理页落地面板） */
const INTENT_TO_PANEL: Record<string, string> = {
  openWork: 'openWork',
  openStudy: 'openStudy',
  openBottle: 'openBottle',
  openBattle: 'openBattle',
  openChat: 'openChat',
  openAchievements: 'openAchievements',
  openRankings: 'openRankings',
  openProfile: 'openProfile',
  // 二期：养成面板（商城/背包/技能/进化/活动/串门/多宠物，原文档 §89）
  openCare: 'openCare',
}

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
    activityName: pet.activityType ?? undefined,
  }
}

export default function PetStagePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const isWeapp = process.env.TARO_ENV === 'weapp'

  const frameRef = useRef<HTMLIFrameElement | null>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [bubble, setBubble] = useState('加载宠物舞台中…')

  const postToGame = useCallback((message: HostToGame) => {
    if (isWeapp) return
    frameRef.current?.contentWindow?.postMessage(message, '*')
  }, [isWeapp])

  const syncStage = useCallback((next: PetInfo) => {
    postToGame({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [postToGame])

  // 拉取权威宠物状态（未登录/未领养时 Fail-Open：舞台仍可观赏）
  useEffect(() => {
    if (isWeapp) return
    petApi.getMyPet()
      .then(({ data: res }) => { if (res.success && res.data) setPet(res.data) })
      .catch(() => setFailed(true))
  }, [isWeapp])

  useEffect(() => {
    if (isWeapp || !ready || !pet) return
    postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(pet) })
  }, [isWeapp, ready, pet, postToGame])

  // H5 双向桥：接收游戏意图 → 调 API → 回灌权威状态
  useEffect(() => {
    if (isWeapp) return
    const handler = async (event: MessageEvent) => {
      const message = event.data as { source?: string; type?: string; action?: string }
      if (!message || message.source !== 'pet-game') return
      if (message.type === 'ready') {
        setReady(true)
        setBubble('')
        if (pet) postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(pet) })
        return
      }
      if (message.type === 'petTapped') {
        if (pet) postToGame({ source: 'pet-host', type: 'chatBubble', content: `${pet.name}：主人，点点我干嘛呀～` })
        return
      }
      if (message.type !== 'intent' || !message.action) return

      const action = message.action
      if (action === 'feed' || action === 'play' || action === 'clean' || action === 'rest') {
        try {
          const { data: res } =
            action === 'feed' ? await petApi.feed()
              : action === 'play' ? await petApi.play()
                : action === 'clean' ? await petApi.clean()
                  : await petApi.rest()
          if (res.success && res.data) {
            setPet(res.data)
            syncStage(res.data)
            postToGame({ source: 'pet-host', type: 'actionResult', action, ok: true })
          }
        } catch (error) {
          postToGame({
            source: 'pet-host', type: 'actionResult', action, ok: false,
            message: (error as { message?: string })?.message ?? '现在不行哦',
          })
        }
        return
      }
      // 面板/档案类意图回落到原生管理页执行的 with intent 参数
      const intent = INTENT_TO_PANEL[action]
      if (intent) {
        Taro.navigateTo({ url: `/pages/pet/index?intent=${intent}` })
      }
    }
    window.addEventListener('message', handler)
    return () => window.removeEventListener('message', handler)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isWeapp, pet, postToGame, syncStage])

  if (isWeapp) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <WebView src={PET_STAGE_URL} />
      </View>
    )
  }

  return (
    <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
      <CustomNavBar title="宠物舞台" />
      <View className={styles.body} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        <iframe
          ref={(node) => { frameRef.current = node }}
          src={PET_STAGE_URL}
          title="宠物舞台"
          className={styles.frame}
          onError={() => setFailed(true)}
        />
        <View className={styles.hintRow}>
          <Text className={styles.hint}>
            {failed
              ? '舞台产物暂不可用，全部功能可在「我的宠物」管理页使用'
              : ready
                ? '点一下宠物、或者让它去打工、读书、捞漂流瓶吧'
                : '正在唤醒宠物…'}
          </Text>
        </View>
        {bubble && !ready ? <View className={styles.hintRow}><Text className={styles.hint}>{bubble}</Text></View> : null}
      </View>
    </View>
  )
}
