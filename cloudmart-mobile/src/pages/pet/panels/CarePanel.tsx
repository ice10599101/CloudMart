import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Input, Text, View } from '@tarojs/components'
import {
  petApi,
  petCompanionApi,
  type PetEquipPreview,
  type PetEventItem,
  type PetEvolutionStatus,
  type PetInventoryItem,
  type PetInfo,
  type PetShopItem,
  type PetSkillItem,
  type PetSummary,
  type PetVisitNeighbor,
  type PetVisitorLog,
} from '@/api/pet'
import { notificationApi } from '@/api/notification'
import { getPersistedIntentKey } from '@/utils/request'
import {
  CARE_TABS,
  EVENT_TYPE_LABEL,
  GROWTH_STAGE_LABEL,
  ITEM_TYPE_LABEL,
  SLOT_LABEL,
  SPECIES_EMOJI,
  friendlyError,
  type CareTab,
  type PanelKey,
} from '../constants'
import { CARE_ERROR_HINT } from './shared'
import { CareerPanel } from './CareerPanel'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from '../index.module.scss'

/**
 * 养成面板（原文档 §1.1 串门 / §89 商城·背包·技能·进化·活动·多宠物）。
 * R30 自 index.tsx 拆出：统一动作 runCare（服务端权威 → 提示 → 刷新面板与主宠）；
 * 购买类走 runPurchase（断网未知时按持久意图原键查态，绝不重新下单，R25）。
 */
export function CarePanel({ pet, linkedProductEnabled = true, onRefresh, onNavigate }: {
  pet: PetInfo
  /** R19：兑换入口能力开关（已发权益状态仍展示；未知不隐藏） */
  linkedProductEnabled?: boolean
  onRefresh: () => void
  onNavigate: (key: PanelKey) => void
}) {
  const [careTab, setCareTab] = useState<CareTab>('shop')
  const [carePending, setCarePending] = useState<string | null>(null)
  const [shopBalance, setShopBalance] = useState<number | null>(null)
  const [shopItems, setShopItems] = useState<PetShopItem[]>([])
  const [inventory, setInventory] = useState<PetInventoryItem[]>([])
  const [skills, setSkills] = useState<PetSkillItem[]>([])
  const [evolution, setEvolution] = useState<PetEvolutionStatus | null>(null)
  const [events, setEvents] = useState<PetEventItem[]>([])
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  // 访客日志（§6 今日访客）
  const [todayVisitors, setTodayVisitors] = useState<PetVisitorLog[]>([])
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [careMessage, setCareMessage] = useState<string | null>(null)
  // 实物商品联动（§6）：双倍喂食权益 + 兑换码输入
  const [feedDoubled, setFeedDoubled] = useState(false)
  const [redeemCode, setRedeemCode] = useState('')
  const [redeeming, setRedeeming] = useState(false)
  // B12 装备替换预览（按行切换显示替换增量；只读不写入）
  const [equipPreview, setEquipPreview] = useState<PetEquipPreview | null>(null)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  /** 养成面板数据（按子页签惰性加载） */
  const loadCareData = useCallback(async (tab: CareTab) => {
    try {
      if (tab === 'shop') {
        const { data: res } = await petApi.getShop()
        setShopBalance(res.data.balance)
        setShopItems(res.data.items || [])
      } else if (tab === 'inventory') {
        const { data: res } = await petApi.listInventory()
        setInventory(res.data || [])
      } else if (tab === 'skills') {
        const { data: res } = await petApi.listSkills()
        setSkills(res.data || [])
      } else if (tab === 'evolution') {
        const { data: res } = await petApi.getEvolution()
        setEvolution(res.data)
      } else if (tab === 'events') {
        const { data: res } = await petApi.listEvents()
        setEvents(res.data || [])
      } else if (tab === 'visit') {
        const { data: res } = await petApi.listVisitNeighbors()
        setNeighbors(res.data || [])
        // 访客日志（§6）：随串门页签加载，失败静默
        petApi.listTodayVisitors()
          .then(({ data: r }) => { setTodayVisitors(r.data || []) })
          .catch(() => undefined)
      } else {
        const { data: res } = await petApi.listMyPets()
        setMyPets(res.data || [])
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [])

  useEffect(() => {
    void loadCareData(careTab)
  }, [careTab, loadCareData])

  // 实物商品联动（§6）：权益随养成面板加载
  useEffect(() => {
    petApi.getLinkedProductEntitlement()
      .then(({ data: r }) => { setFeedDoubled(r.data?.usable === true) })
      .catch(() => undefined)
  }, [])

  /** 养成面板统一动作：服务端权威 → 提示 → 刷新面板与主宠状态 */
  const runCare = async (key: string, action: () => Promise<{ data: { success: boolean } }>, successText: string) => {
    setCarePending(key)
    try {
      await action()
      toast(successText)
      await loadCareData(careTab)
      onRefresh()
      // 喂食可能消耗了双倍权益（§6）
      if (key.startsWith('feed-')) {
        petApi.getLinkedProductEntitlement()
          .then(({ data: r }) => { setFeedDoubled(r.data?.usable === true) })
          .catch(() => undefined)
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      toast((code && CARE_ERROR_HINT[code]) || '请稍后再试')
    } finally {
      setCarePending(null)
    }
  }

  /**
   * R25：购买（扣费类）专用动作——断网/超时结果未知时，按持久意图原键查购买状态，
   * 绝不重新下单（同键重放由服务端收敛，未知时先查原单）。
   */
  const runPurchase = async (item: PetShopItem) => {
    // PET-01：显式携带目标宠物（服务端在业务事务内校验归属），payload 同时作为持久意图指纹
    const payload = { petId: pet.petId, itemType: item.itemType, itemCode: item.code }
    setCarePending(`buy-${item.code}`)
    try {
      await petApi.buyItem(payload)
      toast('购买成功！')
      await loadCareData(careTab)
      onRefresh()
    } catch (error) {
      const code = (error as { code?: string }).code
      if (code === 'PET_REQUEST_IN_PROGRESS') {
        toast('购买正在处理中，稍后查看背包')
        return
      }
      if (code === 'NETWORK_ERROR') {
        const requestKey = getPersistedIntentKey('POST', '/pet/purchases', payload)
        if (requestKey) {
          try {
            const { data: status } = await petApi.getPurchaseRequestStatus(requestKey)
            if (status.data?.status === 'COMPLETED') {
              toast('购买成功！')
              await loadCareData(careTab)
              onRefresh()
              return
            }
            if (status.data?.status === 'PROCESSING') {
              toast('购买正在处理中，稍后查看背包')
              return
            }
          } catch {
            // 查询也失败：落到通用网络提示
          }
        }
        toast('网络不稳定，稍后可原样重试（不会重复扣款）')
        return
      }
      toast((code && CARE_ERROR_HINT[code]) || friendlyError(error))
    } finally {
      setCarePending(null)
    }
  }

  /** N-1 订阅消息：授权纪念日提醒（一次性订阅；requestSubscribeMessage 通过后 bind） */
  const ANNIVERSARY_TEMPLATE_ID = 'rXlUuz08BIT6FzccWByNPfqFj-X58lyYU0CypxH-Ris'
  const handleSubscribeAnniversary = async () => {
    try {
      // Taro 类型定义（H5）要求 entityIds；真实小程序 API 参数为 tmplIds —— 以运行时为准
      const sub = await Taro.requestSubscribeMessage({ tmplIds: [ANNIVERSARY_TEMPLATE_ID] } as unknown as Parameters<typeof Taro.requestSubscribeMessage>[0])
      if ((sub as Record<string, string>)[ANNIVERSARY_TEMPLATE_ID] === 'accept') {
        const login = await Taro.login()
        await notificationApi.bindSubscribe(login.code, 'ANNIVERSARY')
        toast('纪念日提醒已开启')
      } else {
        toast('已取消授权')
      }
    } catch {
      toast('授权失败，请稍后再试')
    }
  }

  /** 实物商品联动（§6）：核销商城实物零食兑换码 → 双倍喂食权益 */
  const handleRedeem = async () => {
    if (redeeming || !redeemCode.trim()) {
      if (!redeemCode.trim()) toast('请输入兑换码')
      return
    }
    setRedeeming(true)
    try {
      // R19：核销是资产类操作，带稳定幂等键（同码重试不产生重复台账）
      const requestKey = getPersistedIntentKey('POST', '/pet/linked-product/redeem', { code: redeemCode.trim() })
      const res = await petApi.redeemLinkedProduct(redeemCode.trim(), requestKey ?? undefined)
      setFeedDoubled(res.data.data?.usable === true)
      setRedeemCode('')
      const status = res.data.data?.status
      toast(status === 'VERIFYING' || status === 'PENDING'
        ? '兑换码核验中，稍后在此查看结果'
        : '核销成功！下一次喂食效果翻倍')
    } catch (error) {
      const code = (error as { code?: string }).code
      toast(code === 'PET_VALIDATION_ERROR' ? '兑换码无效或已核销' : '核销失败，请稍后再试')
    } finally {
      setRedeeming(false)
    }
  }

  /** B12 装备替换预览：同码再点收起 */
  const toggleEquipPreview = async (itemCode: string) => {
    if (equipPreview?.itemCode === itemCode) {
      setEquipPreview(null)
      return
    }
    try {
      const { data: res } = await petCompanionApi.previewEquip(itemCode)
      setEquipPreview(res.data)
    } catch {
      toast('预览未成功，稍后再试')
    }
  }

  return (
    <View className={styles.carePanel}>
      <View className={cream.metaRow}>
        {CARE_TABS.map((tab) => (
          <View
            key={tab.key}
            className={`${cream.tab} ${careTab === tab.key ? cream.tabActive : ''}`}
            onClick={() => setCareTab(tab.key)}
          >
            <Text>{tab.label}</Text>
          </View>
        ))}
      </View>
      {careMessage && <Text className={styles.tip}>{careMessage}</Text>}

      {careTab === 'shop' && (
        <View>
          <Text className={styles.tip}>
            🪙 宠物币余额：{shopBalance === null ? '暂不可用' : shopBalance}
          </Text>
          {shopItems.map((item) => (
            <View key={`${item.itemType}-${item.code}`} className={styles.careCard}>
              <Text className={styles.careTitle}>
                {item.icon} {item.name} · {ITEM_TYPE_LABEL[item.itemType]}
                {item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
              </Text>
              <Text className={styles.careMeta}>
                需要 Lv.{item.requiredLevel}
                {item.requiredEvolutionStage > 0 ? ` · 进化${item.requiredEvolutionStage}阶` : ''}
                {' · '}✨{item.priceCoins}
              </Text>
              <View className={styles.careActions}>
                {item.owned ? (
                  <Text className={styles.careMeta}>已拥有</Text>
                ) : (
                  <Button
                    className={styles.miniBtn}
                    disabled={!item.eligible || carePending === `buy-${item.code}`}
                    onClick={() => void runPurchase(item)}
                  >
                    {item.eligible ? '购买' : (item.lockReason || '未解锁')}
                  </Button>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'inventory' && (
        <View>
          {/* 实物商品联动（§6）：双倍喂食权益 + 兑换码核销 */}
          <View className={styles.linkedProductCard}>
            <Text className={styles.linkedProductBadge}>
              {feedDoubled ? '🎁 双倍喂食权益已就绪（下次喂食效果 x2）' : '🐱 买实物零食可兑换双倍喂食'}
            </Text>
            <View className={styles.anniversaryRow} onClick={handleSubscribeAnniversary}>
              <Text className={styles.anniversaryText}>🔔 开启宠物纪念日提醒</Text>
              <Text className={styles.anniversaryArrow}>›</Text>
            </View>
            {/* R19：兑换入口受能力开关控制（已发权益状态仍展示；未知不隐藏） */}
            {linkedProductEnabled && (
            <View className={styles.redeemRow}>
              <Input
                className={styles.redeemInput}
                placeholder='输入兑换码'
                value={redeemCode}
                onInput={(e) => setRedeemCode(e.detail.value)}
                maxlength={64}
              />
              <View className={styles.redeemBtn} onClick={redeeming ? undefined : () => void handleRedeem()}>
                <Text className={styles.redeemBtnText}>{redeeming ? '核销中...' : '核销'}</Text>
              </View>
            </View>
            )}
          </View>
          {inventory.length === 0 && <Text className={styles.tip}>背包还是空的，去商城逛逛吧～</Text>}
          {inventory.map((item) => (
            <View key={`${item.itemType}-${item.code}`} className={styles.careCard}>
              <Text className={styles.careTitle}>{item.icon} {item.name}</Text>
              <Text className={styles.careMeta}>
                {ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
                {item.equipped ? ' · 使用中' : ''}{item.used ? ' · 已学习' : ''}
              </Text>
              <View className={styles.careActions}>
                {item.itemType === 'EQUIPMENT' && (item.equipped ? (
                  <>
                    <Button className={styles.miniBtnGhost} onClick={() => void runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')}>卸下</Button>
                    <Button className={styles.miniBtnGhost} onClick={() => void toggleEquipPreview(item.code)}>
                      {equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                    </Button>
                  </>
                ) : (
                  <>
                    <Button className={styles.miniBtn} onClick={() => void runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')}>穿戴</Button>
                    <Button className={styles.miniBtnGhost} onClick={() => void toggleEquipPreview(item.code)}>
                      {equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                    </Button>
                  </>
                ))}
                {item.itemType === 'SKIN' && (item.equipped ? (
                  <Button className={styles.miniBtnGhost} onClick={() => void runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')}>卸下</Button>
                ) : (
                  <Button className={styles.miniBtn} onClick={() => void runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')}>穿戴</Button>
                ))}
                {item.itemType === 'FOOD' && (
                  <Button className={styles.miniBtn} onClick={() => void runCare(`feed-${item.code}`, () => petApi.feedItem(item.code), '吃掉了，状态好多了')}>喂食</Button>
                )}
              </View>
              {item.itemType === 'EQUIPMENT' && equipPreview?.itemCode === item.code && (
                <Text className={styles.careMeta}>
                  替换后 HP {equipPreview.after.maxHp}（{equipPreview.delta.maxHp >= 0 ? '+' : ''}{equipPreview.delta.maxHp}）
                  · 力 {equipPreview.after.strength}（{equipPreview.delta.strength >= 0 ? '+' : ''}{equipPreview.delta.strength}）
                  · 智 {equipPreview.after.intelligence}（{equipPreview.delta.intelligence >= 0 ? '+' : ''}{equipPreview.delta.intelligence}）
                  · 敏 {equipPreview.after.agility}（{equipPreview.delta.agility >= 0 ? '+' : ''}{equipPreview.delta.agility}）
                  · 魅 {equipPreview.after.charm}（{equipPreview.delta.charm >= 0 ? '+' : ''}{equipPreview.delta.charm}）
                </Text>
              )}
            </View>
          ))}
        </View>
      )}

      {careTab === 'skills' && (
        <View>
          {skills.map((skill) => (
            <View key={skill.code} className={styles.careCard}>
              <Text className={styles.careTitle}>
                {skill.icon} {skill.name} · {skill.skillType === 'ACTIVE' ? '主动技' : '被动技'}
              </Text>
              <Text className={styles.careMeta}>{skill.effectText} · ✨{skill.priceCoins} · 需要 Lv.{skill.requiredLevel}</Text>
              <Text className={styles.careMeta}>{skill.description}</Text>
              <View className={styles.careActions}>
                {skill.learned ? (
                  <Text className={styles.careMeta}>已学会</Text>
                ) : skill.bookOwned ? (
                  <Button
                    className={styles.miniBtn}
                    disabled={carePending === `learn-${skill.code}`}
                    onClick={() => void runCare(`learn-${skill.code}`, () => petApi.learnSkill(skill.code), '学会新技能啦！')}
                  >
                    学习
                  </Button>
                ) : (
                  <Text className={styles.careMeta}>需要技能书（商城购买）</Text>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'evolution' && evolution && (
        <View>
          <Text className={styles.tip}>当前进化阶段：{evolution.currentStage} / {evolution.maxStage}</Text>
          {evolution.nextCode === null ? (
            <Text className={styles.tip}>🎉 已经进化到最高阶段啦</Text>
          ) : (
            <View className={styles.careCard}>
              <Text className={styles.careTitle}>{evolution.icon} {evolution.nextName}</Text>
              <Text className={styles.careMeta}>{evolution.nextDescription}</Text>
              <Text className={styles.careMeta}>
                需要 Lv.{evolution.requiredLevel} · 消耗 ✨{evolution.costCoins} · 生命+{evolution.bonusMaxHp}
                力量+{evolution.bonusStrength} 智力+{evolution.bonusIntelligence} 敏捷+{evolution.bonusAgility} 魅力+{evolution.bonusCharm}
              </Text>
              <View className={styles.careActions}>
                <Button
                  className={styles.miniBtn}
                  disabled={!evolution.canEvolve || carePending === 'evolve'}
                  onClick={() => void runCare('evolve', () => petApi.evolve(pet.petId, evolution.currentStage), '进化成功！')}
                >
                  {evolution.canEvolve ? '进化' : (evolution.lockReason || '条件未满足')}
                </Button>
              </View>
            </View>
          )}
        </View>
      )}

      {careTab === 'events' && (
        <View>
          {events.length === 0 && <Text className={styles.tip}>暂时没有进行中的活动</Text>}
          {events.map((event) => (
            <View key={event.code} className={styles.careCard}>
              <Text className={styles.careTitle}>🎯 {event.name}</Text>
              <Text className={styles.careMeta}>
                {EVENT_TYPE_LABEL[event.eventType] ?? event.eventType} 进度 {event.progress}/{event.targetValue}
                {' · '}奖励 经验+{event.rewardExp} ✨+{event.rewardCoins}
              </Text>
              <View className={styles.careActions}>
                {event.claimed ? (
                  <Text className={styles.careMeta}>已领取</Text>
                ) : event.expired ? (
                  <Text className={styles.careMeta}>已结束</Text>
                ) : event.occurrenceId ? (
                  <Button
                    className={styles.miniBtn}
                    disabled={!event.claimable || carePending === `event-${event.occurrenceId}`}
                    onClick={() => void runCare(
                      `event-${event.occurrenceId}`,
                      // C15：领奖唯一入口——按期次 occurrenceId（旧 eventCode 旁路已删除）
                      () => petApi.claimEventOccurrence(event.occurrenceId as string),
                      '奖励到手啦！')}
                  >
                    {event.claimable ? '领取奖励' : `还差 ${Math.max(0, event.targetValue - event.progress)} 次`}
                  </Button>
                ) : (
                  <Text className={styles.careMeta}>
                    {event.claimable ? '等待新一期开放' : '进行中'}
                  </Text>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'visit' && (
        <View>
          {/* 访客日志（§6 今日访客） */}
          <View className={styles.visitorLogBox}>
            <Text className={styles.visitorLogTitle}>👣 今日访客（{todayVisitors.length}）</Text>
            {todayVisitors.length === 0 ? (
              <Text className={styles.tip}>今天还没有小伙伴来串门</Text>
            ) : (
              todayVisitors.map((v) => (
                <Text key={`${v.visitorUserId}-${v.visitorPetId}`} className={styles.visitorLogItem}>
                  {v.visitorNickname} 的 {v.visitorPetName} 来串过门啦
                </Text>
              ))
            )}
          </View>
          {neighbors.length === 0 && <Text className={styles.tip}>暂时没有可串门的邻居</Text>}
          {neighbors.map((neighbor) => (
            <View key={String(neighbor.petId)} className={styles.careCard}>
              <Text className={styles.careTitle}>
                {SPECIES_EMOJI[neighbor.species] || '🐾'} {neighbor.name}
              </Text>
              <Text className={styles.careMeta}>
                Lv.{neighbor.level} · {neighbor.ownerNickname}
                {neighbor.evolutionStage > 0 ? ` · 进化${neighbor.evolutionStage}阶` : ''}
              </Text>
              <View className={styles.careActions}>
                {neighbor.visitedToday ? (
                  <Text className={styles.careMeta}>今日已去过</Text>
                ) : (
                  <Button
                    className={styles.miniBtn}
                    disabled={carePending === `visit-${neighbor.petId}`}
                    onClick={() => void runCare(`visit-${neighbor.petId}`, async () => {
                      const res = await petApi.visitNeighbor(neighbor.petId)
                      setCareMessage(res.data.data.message)
                      return res
                    }, '串门成功！')}
                  >
                    去串门
                  </Button>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'career' && <CareerPanel onRefresh={onRefresh} />}
      {careTab === 'pets' && (
        <View>
          <Text className={styles.tip}>宠物 {pet.petCount} / {pet.maxPets}（日常玩法作用于主宠）</Text>
          {myPets.map((item) => (
            <View key={String(item.petId)} className={styles.careCard}>
              <Text className={styles.careTitle}>
                {SPECIES_EMOJI[item.species] || '🐾'} {item.name}{item.isActive ? ' · 主宠' : ''}
              </Text>
              <Text className={styles.careMeta}>
                Lv.{item.level} · {GROWTH_STAGE_LABEL[item.growthStage] ?? item.growthStage}
                {item.evolutionStage > 0 ? ` · 进化${item.evolutionStage}阶` : ''}
              </Text>
              <View className={styles.careActions}>
                {!item.isActive && (
                  <Button
                    className={styles.miniBtn}
                    disabled={carePending === `activate-${item.petId}`}
                    onClick={() => void runCare(`activate-${item.petId}`, () => petApi.activatePet(item.petId), `已切换为 ${item.name}`)}
                  >
                    设为主宠
                  </Button>
                )}
              </View>
            </View>
          ))}
          {pet.petCount < pet.maxPets && (
            <Button className={styles.miniBtnGhost} onClick={() => onNavigate('home')}>回小窝看看领养入口</Button>
          )}
        </View>
      )}
    </View>
  )
}
