// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Alert, Text, TouchableOpacity, View } from 'react-native'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import {
  petApi,
  type PetEquipPreview,
  type PetEventItem,
  type PetEvolutionStatus,
  type PetInfo,
  type PetInventoryItem,
  type PetShopItem,
  type PetSkillItem,
  type PetSummary,
  type PetVisitNeighbor,
} from '@/api/pet'
import { errorMessage, getPersistedIntentKey } from '@/utils/request'
import {
  CARE_ERROR_HINT,
  CARE_TABS,
  type CareTab,
  ChipButton,
  EVENT_TYPE_LABEL,
  GROWTH_STAGE_LABEL,
  ITEM_TYPE_LABEL,
  SLOT_LABEL,
  SPECIES_EMOJI,
} from '../shared'
import { CareerPanel } from './CareerPanel'

/**
 * 养成面板（商城/背包/技能/进化/活动/串门/职业/多宠物）。
 * 子页签惰性加载；所有写操作服务端权威 → 提示 → 重载页签数据并回调宿主刷新主宠。
 */
export function CarePanel(
  { pet, onRefresh, onAdoptMore }: { pet: PetInfo; onRefresh: () => void; onAdoptMore: () => void },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [careTab, setCareTab] = useState<CareTab>('shop')
  const [carePending, setCarePending] = useState<string | null>(null)
  const [shopBalance, setShopBalance] = useState<number | string | null>(null)
  const [shopItems, setShopItems] = useState<PetShopItem[]>([])
  const [inventory, setInventory] = useState<PetInventoryItem[]>([])
  const [skills, setSkills] = useState<PetSkillItem[]>([])
  const [evolution, setEvolution] = useState<PetEvolutionStatus | null>(null)
  const [events, setEvents] = useState<PetEventItem[]>([])
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [careMessage, setCareMessage] = useState<string | null>(null)
  const [equipPreview, setEquipPreview] = useState<PetEquipPreview | null>(null)

  /** 养成面板数据（按子页签惰性加载，减少无谓请求） */
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
      } else {
        const { data: res } = await petApi.listMyPets()
        setMyPets(res.data || [])
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    async function run() {
      await loadCareData(careTab)
    }
    void run()
  }, [careTab, loadCareData])

  /** 养成面板统一动作：服务端权威 → 提示 → 刷新面板与主宠状态 */
  const runCare = async (key: string, action: () => Promise<{ data: { success: boolean } }>, successText: string) => {
    setCarePending(key)
    try {
      await action()
      Alert.alert('操作成功', successText)
      await loadCareData(careTab)
      onRefresh()
    } catch (error) {
      const code = (error as { code?: string }).code
      Alert.alert('暂时不能这么做', (code && CARE_ERROR_HINT[code]) || errorMessage(error, '请稍后再试'))
    } finally {
      setCarePending(null)
    }
  }

  /**
   * R25：购买（扣费类）专用动作——断网/超时结果未知时，按持久意图原键查购买状态，
   * 绝不重新下单（未知时先查原单；同键重放由服务端收敛）。
   */
  const runPurchase = async (item: PetShopItem) => {
    // PET-01：显式携带目标宠物（服务端在业务事务内校验归属），payload 同时作为持久意图指纹
    const payload = { petId: pet.petId, itemType: item.itemType, itemCode: item.code }
    setCarePending(`buy-${item.code}`)
    try {
      await petApi.buyItem(payload)
      Alert.alert('操作成功', '购买成功！')
      await loadCareData(careTab)
      onRefresh()
    } catch (error) {
      const code = (error as { code?: string }).code
      if (code === 'PET_REQUEST_IN_PROGRESS') {
        Alert.alert('购买处理中', '购买正在处理中，稍后查看背包')
        return
      }
      if (code === 'NETWORK_ERROR') {
        const requestKey = await getPersistedIntentKey('POST', '/pet/purchases', payload)
        if (requestKey) {
          try {
            const { data: status } = await petApi.getPurchaseRequestStatus(requestKey)
            if (status.data?.status === 'COMPLETED') {
              Alert.alert('操作成功', '购买成功！')
              await loadCareData(careTab)
              onRefresh()
              return
            }
            if (status.data?.status === 'PROCESSING') {
              Alert.alert('购买处理中', '购买正在处理中，稍后查看背包')
              return
            }
          } catch {
            // 查询也失败：落到通用网络提示
          }
        }
        Alert.alert('网络不稳定', '稍后可原样重试（不会重复扣款）')
        return
      }
      Alert.alert('暂时不能这么做', (code && CARE_ERROR_HINT[code]) || errorMessage(error, '购买失败，请稍后再试'))
    } finally {
      setCarePending(null)
    }
  }

  /** B12 装备替换预览：同码再点收起（只读不写入） */
  const toggleEquipPreview = useCallback(async (itemCode: string) => {
    if (equipPreview?.itemCode === itemCode) {
      setEquipPreview(null)
      return
    }
    try {
      const { data: res } = await petApi.previewEquip(itemCode)
      setEquipPreview(res.data)
    } catch {
      Alert.alert('暂时不能预览', '请稍后再试')
    }
  }, [equipPreview?.itemCode])

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {CARE_TABS.map((tab) => (
          <TouchableOpacity
            key={tab.key}
            onPress={() => setCareTab(tab.key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
              backgroundColor: careTab === tab.key ? colors.primary : colors.bgBase,
              borderWidth: 1, borderColor: careTab === tab.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: careTab === tab.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{tab.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {careMessage && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{careMessage}</Text>}

      <TouchableOpacity
        onPress={() => router.push('/pet-play')}
        style={{
          alignItems: 'center', paddingVertical: 10, borderRadius: BorderRadius.lg,
          borderWidth: 1, borderColor: PetCreamSemantic.brassSoftBorder, backgroundColor: PetCreamSemantic.brassSoftBg,
        }}
      >
        <Text style={{ color: PetCreamTheme.primary, fontSize: FontSize.sm, fontWeight: '600' }}>🪙 宠物币钱包</Text>
      </TouchableOpacity>

      <TouchableOpacity
        onPress={() => router.push('/pet-play')}
        style={{
          alignItems: 'center', paddingVertical: 10, borderRadius: BorderRadius.lg,
          borderWidth: 1, borderColor: colors.primary, backgroundColor: 'rgba(0, 212, 255, 0.08)',
        }}
      >
        <Text style={{ color: colors.primary, fontSize: FontSize.sm, fontWeight: '600' }}>🎮 玩法中心</Text>
      </TouchableOpacity>

      {careTab === 'shop' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            🪙 宠物币余额：{shopBalance === null ? '暂不可用' : shopBalance}
          </Text>
          {shopItems.map((item) => (
            <View key={`${item.itemType}-${item.code}`} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
            }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.icon} {item.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}</Text>
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                需要 Lv.{item.requiredLevel}{item.requiredEvolutionStage > 0 ? ` · 进化 ${item.requiredEvolutionStage} 阶` : ''}
                {(item.bonusStrength + item.bonusIntelligence + item.bonusAgility + item.bonusCharm + item.bonusMaxHp) > 0
                  ? ` · 力量+${item.bonusStrength} 智力+${item.bonusIntelligence} 敏捷+${item.bonusAgility} 魅力+${item.bonusCharm} 生命+${item.bonusMaxHp}`
                  : ''}
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {item.priceCoins}</Text>
                {item.owned ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已拥有</Text>
                ) : (
                  <ChipButton
                    label={item.eligible ? '购买' : (item.lockReason ?? '未解锁')}
                    primary
                    disabled={!item.eligible || carePending === `buy-${item.code}`}
                    onPress={() => void runPurchase(item)}
                  />
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'inventory' && (
        <View style={{ gap: Spacing.sm }}>
          {inventory.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>背包还是空的，去商城逛逛吧～</Text>}
          {inventory.map((item) => (
            <View key={`${item.itemType}-${item.code}`} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
            }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.icon} {item.name}
                {item.equipped ? ' · 使用中' : ''}{item.used ? ' · 已学习' : ''}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                {item.itemType === 'EQUIPMENT' && (
                  item.equipped ? (
                    <>
                      <ChipButton label="卸下" onPress={() => runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')} />
                      <ChipButton label={equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                        onPress={() => void toggleEquipPreview(item.code)} />
                    </>
                  ) : (
                    <>
                      <ChipButton label="穿戴" primary onPress={() => runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')} />
                      <ChipButton label={equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                        onPress={() => void toggleEquipPreview(item.code)} />
                    </>
                  )
                )}
                {item.itemType === 'SKIN' && (
                  item.equipped ? (
                    <ChipButton label="卸下" onPress={() => runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')} />
                  ) : (
                    <ChipButton label="穿戴" primary onPress={() => runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')} />
                  )
                )}
                {item.itemType === 'FOOD' && (
                  <ChipButton label="喂食" primary onPress={() => runCare(`feed-${item.code}`, () => petApi.feedItem(item.code), '吃掉了，状态好多了')} />
                )}
              </View>
              {item.itemType === 'EQUIPMENT' && equipPreview?.itemCode === item.code && (
                <Text style={{ color: colors.textSecondary, fontSize: 10 }}>
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
        <View style={{ gap: Spacing.sm }}>
          {skills.map((skill) => (
            <View key={skill.code} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
            }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {skill.icon} {skill.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{skill.skillType === 'ACTIVE' ? '主动技' : '被动技'} · {skill.effectText}</Text>
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{skill.description}</Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {skill.priceCoins} · 需要 Lv.{skill.requiredLevel}</Text>
                {skill.learned ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已学会</Text>
                ) : skill.bookOwned ? (
                  <ChipButton
                    label="学习"
                    primary
                    disabled={carePending === `learn-${skill.code}`}
                    onPress={() => runCare(`learn-${skill.code}`, () => petApi.learnSkill(skill.code), '学会新技能啦！')}
                  />
                ) : (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>需要技能书</Text>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'evolution' && evolution && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            当前进化阶段：{evolution.currentStage} / {evolution.maxStage}
          </Text>
          {evolution.nextCode === null ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>🎉 已经进化到最高阶段啦</Text>
          ) : (
            <View style={{ padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2 }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{evolution.icon} {evolution.nextName}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{evolution.nextDescription}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                需要 Lv.{evolution.requiredLevel} · 消耗 ✨{evolution.costCoins} · 生命+{evolution.bonusMaxHp} 力量+{evolution.bonusStrength} 智力+{evolution.bonusIntelligence} 敏捷+{evolution.bonusAgility} 魅力+{evolution.bonusCharm}
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                <ChipButton
                  label={evolution.canEvolve ? '进化' : (evolution.lockReason ?? '条件未满足')}
                  primary
                  disabled={!evolution.canEvolve || carePending === 'evolve'}
                  onPress={() => runCare('evolve', () => petApi.evolve(pet.petId, evolution.currentStage), '进化成功！')}
                />
              </View>
            </View>
          )}
        </View>
      )}

      {careTab === 'events' && (
        <View style={{ gap: Spacing.sm }}>
          {events.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂时没有进行中的活动</Text>}
          {events.map((event) => (
            <View key={event.code} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
            }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🎯 {event.name}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {EVENT_TYPE_LABEL[event.eventType] ?? event.eventType} 进度 {event.progress}/{event.targetValue} · 奖励 经验+{event.rewardExp} ✨+{event.rewardCoins}
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                {event.claimed ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已领取</Text>
                ) : event.occurrenceId ? (
                  <ChipButton
                    label={event.claimable ? '领取奖励' : `还差 ${Math.max(0, event.targetValue - event.progress)} 次`}
                    primary
                    disabled={!event.claimable || carePending === `event-${event.occurrenceId}`}
                    onPress={() => runCare(
                      `event-${event.occurrenceId}`,
                      // C15：领奖唯一入口——按期次 occurrenceId（旧 eventCode 旁路已删除）
                      () => petApi.claimEventOccurrence(event.occurrenceId as string),
                      '奖励到手啦！')}
                  />
                ) : (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    {event.claimable ? '等待新一期开放' : '进行中'}
                  </Text>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'visit' && (
        <View style={{ gap: Spacing.sm }}>
          {neighbors.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂时没有可串门的邻居</Text>}
          {neighbors.map((neighbor) => (
            <View key={String(neighbor.petId)} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
            }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {SPECIES_EMOJI[neighbor.species] ?? '🐾'} {neighbor.name}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                Lv.{neighbor.level} · {neighbor.ownerNickname}{neighbor.evolutionStage > 0 ? ` · 进化${neighbor.evolutionStage}阶` : ''}
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                {neighbor.visitedToday ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>今日已去过</Text>
                ) : (
                  <ChipButton
                    label="去串门"
                    primary
                    disabled={carePending === `visit-${neighbor.petId}`}
                    onPress={() => runCare(`visit-${neighbor.petId}`, async () => {
                      const res = await petApi.visitNeighbor(neighbor.petId)
                      setCareMessage(res.data.data.message)
                      return res
                    }, '串门成功！')}
                  />
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {careTab === 'career' && <CareerPanel onRefresh={onRefresh} />}

      {careTab === 'pets' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            宠物 {pet.petCount} / {pet.maxPets}（日常玩法作用于主宠）
          </Text>
          {myPets.map((item) => (
            <View key={String(item.petId)} style={{
              padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border,
              flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
            }}>
              <View>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                  {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}{item.isActive ? ' · 主宠' : ''}
                </Text>
                <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                  Lv.{item.level} · {GROWTH_STAGE_LABEL[item.growthStage] ?? item.growthStage}{item.evolutionStage > 0 ? ` · 进化${item.evolutionStage}阶` : ''}
                </Text>
              </View>
              {!item.isActive && (
                <ChipButton
                  label="设为主宠"
                  disabled={carePending === `activate-${item.petId}`}
                  onPress={() => runCare(`activate-${item.petId}`, () => petApi.activatePet(item.petId), `已切换为 ${item.name}`)}
                />
              )}
            </View>
          ))}
          {pet.petCount < pet.maxPets && (
            <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
              <ChipButton label="再领养一只" onPress={onAdoptMore} />
            </View>
          )}
        </View>
      )}
    </View>
  )
}
