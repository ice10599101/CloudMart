// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Alert, Switch, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetVisitNeighbor, type PetHome } from '@/api/pet'
import { CARE_ERROR_HINT, ChipButton } from '../shared'

export function HomePanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [home, setHome] = useState<PetHome | null>(null)
  const [tab, setTab] = useState<'room' | 'shop' | 'visit'>('room')
  const [pending, setPending] = useState<string | null>(null)
  const [welcome, setWelcome] = useState('')
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [tip, setTip] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getHome()
      setHome(res.data)
      setWelcome(res.data.welcomeMessage)
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    async function run() {
      await load()
    }
    void run()
  }, [load])

  useEffect(() => {
    if (tab !== 'visit') {
      return
    }
    void (async () => {
      try {
        const { data: res } = await petApi.listVisitNeighbors()
        setNeighbors(res.data ?? [])
      } catch {
        // 展示型数据
      }
    })()
  }, [tab])

  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
      Alert.alert('成功', typeof text === 'function' ? text(res) : text)
      await load()
      onRefresh()
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  if (!home) {
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>家园加载中…</Text>
  }

  const cardStyle = {
    padding: Spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8',
    gap: 4,
  }
  const themeOptions = home.shop.filter((item) => item.owned)

  return (
    <View style={{ gap: Spacing.sm }}>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
        🏡 舒适度 {home.comfort} · 来访 {home.visitCount} · 点赞 {home.likeCount}
        {home.comfort >= home.comfortBonusThreshold ? ` · 休息心情 +${home.comfortRestHappinessBonus}` : ''}
      </Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {([
          ['room', '🛋️ 布置'],
          ['shop', '🛒 家具'],
          ['visit', '🚪 拜访'],
        ] as [typeof tab, string][]).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setTab(key)}
            style={{
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: 999,
              backgroundColor: tab === key ? colors.primary : colors.bgBase,
              borderWidth: 1,
              borderColor: tab === key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: tab === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'room' && (
        <View style={{ gap: Spacing.xs }}>
          {home.placed.map((item) => (
            <View key={`${item.posX}-${item.posY}`} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.text, fontSize: FontSize.xs }}>
                {item.icon} {item.name}（{item.posX},{item.posY}）舒适度 +{item.comfort}
              </Text>
              <ChipButton
                label="收回"
                disabled={pending === `rm-${item.posX}-${item.posY}`}
                onPress={() =>
                  run(`rm-${item.posX}-${item.posY}`, () => petApi.removeFurniture(item.posX ?? 0, item.posY ?? 0), '已收回仓库')
                }
              />
            </View>
          ))}
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            当前墙纸/地板：{home.wallCode ?? '默认'} / {home.floorCode ?? '默认'}
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
            {themeOptions.slice(0, 6).map((item) => (
              <ChipButton
                key={`theme-${item.code}`}
                label={`${item.icon}${item.name}`}
                disabled={pending === `theme-${item.code}`}
                onPress={() =>
                  run(
                    `theme-${item.code}`,
                    () =>
                      petApi.updateRoomTheme(
                        item.category === 'WALL'
                          ? { wallCode: item.code, floorCode: home.floorCode }
                          : { wallCode: home.wallCode, floorCode: item.code },
                      ),
                    '已更换主题',
                  )
                }
              />
            ))}
          </View>
        </View>
      )}

      {tab === 'shop' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            摆放：在「布置」页点家具即可（自动找空格）——移动端简化交互，精细摆位请用 Web 端
          </Text>
          {home.shop.map((item) => (
            <View key={item.code} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.icon} {item.name} · {item.categoryLabel} · 舒适度 +{item.comfort}
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {item.priceCoins}</Text>
                {item.owned ? (
                  <ChipButton
                    label="摆放"
                    disabled={pending === `place-${item.code}`}
                    onPress={() =>
                      run(
                        `place-${item.code}`,
                        () => {
                          const occupied = new Set(home.placed.map((placed) => `${placed.posX}-${placed.posY}`))
                          for (let y = 0; y < home.gridHeight; y += 1) {
                            for (let x = 0; x < home.gridWidth; x += 1) {
                              if (!occupied.has(`${x}-${y}`)) {
                                return petApi.placeFurniture({ furnitureCode: item.code, posX: x, posY: y })
                              }
                            }
                          }
                          return petApi.placeFurniture({ furnitureCode: item.code, posX: 0, posY: 0 })
                        },
                        '摆放好啦',
                      )
                    }
                  />
                ) : (
                  <ChipButton
                    label={item.eligible ? '购买' : item.lockReason ?? '未解锁'}
                    primary
                    disabled={!item.eligible || pending === `buy-${item.code}`}
                    onPress={() => run(`buy-${item.code}`, () => petApi.buyItem({ itemType: 'FURNITURE', itemCode: item.code }), '买到啦')}
                  />
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'visit' && (
        <View style={{ gap: Spacing.sm }}>
          {tip && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{tip}</Text>}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>允许来访</Text>
            <Switch
              value={home.isPublic}
              onValueChange={(checked) => run('settings', () => petApi.updateRoomSettings({ isPublic: checked }), '设置已更新')}
            />
            <TextInput
              value={welcome}
              onChangeText={setWelcome}
              maxLength={40}
              placeholder="欢迎语"
              placeholderTextColor={colors.textTertiary}
              style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
            />
            <ChipButton
              label="保存"
              disabled={pending === 'welcome'}
              onPress={() => run('welcome', () => petApi.updateRoomSettings({ welcomeMessage: welcome }), '欢迎语已更新')}
            />
          </View>
          {neighbors.map((neighbor) => (
            <View key={String(neighbor.petId)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {neighbor.name}（Lv.{neighbor.level} · {neighbor.ownerNickname}）
              </Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="去家里看看"
                  primary
                  disabled={pending === `v-${neighbor.petId}`}
                  onPress={() =>
                    run(
                      `v-${neighbor.petId}`,
                      async () => {
                        const res = await petApi.enterHome(neighbor.petId)
                        setTip(res.data.data.message)
                        return res
                      },
                      '拜访成功！',
                    )
                  }
                />
                <ChipButton
                  label="点赞"
                  disabled={pending === `l-${neighbor.petId}`}
                  onPress={() => run(`l-${neighbor.petId}`, () => petApi.likeHome(neighbor.petId), '点赞成功')}
                />
                <ChipButton
                  label="加好友"
                  disabled={pending === `fq-${neighbor.petId}`}
                  onPress={() =>
                    run(`fq-${neighbor.petId}`, () => petApi.requestFriend(neighbor.ownerUserId), '好友申请已发出～')
                  }
                />
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  )
}
