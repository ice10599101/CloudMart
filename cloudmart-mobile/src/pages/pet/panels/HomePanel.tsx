import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Input, Text, View } from '@tarojs/components'
import { petApi, type PetHome, PetVisitNeighbor } from '@/api/pet'
import { CARE_ERROR_HINT } from './shared'
import { CreamToggle } from '@/components/pet-cream'
import styles from '../index.module.scss'
import cream from '@/components/pet-cream/pet-cream.module.scss'

/** 家园面板（三期）：房间/家具商城/邻居串门（P2-4 自 index.tsx 拆出，行为不变） */
export function HomePanel({ onRefresh }: { onRefresh: () => void }) {
  const [home, setHome] = useState<PetHome | null>(null)
  const [tab, setTab] = useState<'room' | 'shop' | 'visit'>('room')
  const [pending, setPending] = useState<string | null>(null)
  const [welcome, setWelcome] = useState('')
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [tip, setTip] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getHome()
      if (res.success && res.data) {
        setHome(res.data)
        setWelcome(res.data.welcomeMessage)
      }
    } catch {
      // 拦截器已提示
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
        const { data: res } = await petApi.listVisitNeighbors()
        if (res.success) {
          setNeighbors(res.data ?? [])
        }
      } catch {
        // 展示型数据
      }
    })()
  }, [tab])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await load()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  if (!home) {
    return <Text className={styles.tip}>家园加载中…</Text>
  }

  return (
    <View>
      <Text className={styles.tip}>
        🏡 舒适度 {home.comfort} · 来访 {home.visitCount} · 点赞 {home.likeCount}
        {home.comfort >= home.comfortBonusThreshold ? ` · 休息心情 +${home.comfortRestHappinessBonus}` : ''}
      </Text>
      <View className={styles.actionRow}>
        {([
          ['room', '🛋️ 布置'],
          ['shop', '🛒 家具'],
          ['visit', '🚪 拜访'],
        ] as Array<[typeof tab, string]>).map(([key, label]) => (
          <View
            key={key}
            className={`${cream.tab} ${tab === key ? cream.tabActive : ""}`}
            onClick={() => setTab(key)}
          >
            <Text>{label}</Text>
          </View>
        ))}
      </View>
      {tip && <Text className={styles.tip}>{tip}</Text>}

      {tab === 'room' && (
        <View>
          <View className={styles.jobList}>
            {home.placed.map((item) => (
              <View key={`${item.posX}-${item.posY}`} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.icon} {item.name}
                  </Text>
                  <Text className={styles.jobMeta}>
                    位置（{item.posX},{item.posY}）· 舒适度 +{item.comfort}
                  </Text>
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `rm-${item.posX}-${item.posY}`}
                  onClick={() =>
                    run(`rm-${item.posX}-${item.posY}`, () => petApi.removeFurniture(item.posX ?? 0, item.posY ?? 0), '已收回仓库')
                  }
                >
                  收回
                </Button>
              </View>
            ))}
          </View>
          <Text className={styles.jobMeta}>
            当前墙纸/地板：{home.wallCode ?? '默认'} / {home.floorCode ?? '默认'}
          </Text>
          <View className={styles.actionRow}>
            {home.shop
              .filter((item) => item.owned)
              .slice(0, 6)
              .map((item) => (
                <Button
                  key={`theme-${item.code}`}
                  className={styles.miniBtnGhost}
                  disabled={pending === `theme-${item.code}`}
                  onClick={() =>
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
                >
                  {item.icon}
                  {item.name}
                </Button>
              ))}
          </View>
        </View>
      )}

      {tab === 'shop' && (
        <View>
          <Text className={styles.jobMeta}>
            移动端简化交互：点「摆放」自动找空格；精细摆位请用 Web 端
          </Text>
          <View className={styles.jobList}>
            {home.shop.map((item) => (
              <View key={item.code} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.icon} {item.name} · {item.categoryLabel}
                  </Text>
                  <Text className={styles.jobMeta}>
                    舒适度 +{item.comfort} · 需要 Lv.{item.requiredLevel} · ✨ {item.priceStarlight}
                  </Text>
                </View>
                {item.owned ? (
                  <Button
                    className={styles.miniBtn}
                    disabled={pending === `place-${item.code}`}
                    onClick={() =>
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
                  >
                    摆放
                  </Button>
                ) : (
                  <Button
                    className={item.eligible ? styles.miniBtn : styles.locked}
                    disabled={!item.eligible || pending === `buy-${item.code}`}
                    onClick={() => run(`buy-${item.code}`, () => petApi.buyFurniture(item.code), '买到啦')}
                  >
                    {item.eligible ? '购买' : item.lockReason ?? '未解锁'}
                  </Button>
                )}
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'visit' && (
        <View>
          <View className={styles.actionRow}>
            <Text className={styles.jobMeta}>允许来访</Text>
            <CreamToggle
              on={home.isPublic}
              onChange={(next) =>
                run('settings', () => petApi.updateRoomSettings({ isPublic: next }), '设置已更新')
              }
            />
            <Input
              className={styles.nameInput}
              value={welcome}
              maxlength={40}
              placeholder="欢迎语"
              onInput={(event) => setWelcome(event.detail.value)}
            />
            <Button
              className={styles.miniBtnGhost}
              disabled={pending === 'welcome'}
              onClick={() => run('welcome', () => petApi.updateRoomSettings({ welcomeMessage: welcome }), '欢迎语已更新')}
            >
              保存
            </Button>
          </View>
          <View className={styles.jobList}>
            {neighbors.map((neighbor) => (
              <View key={String(neighbor.petId)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {neighbor.name}（Lv.{neighbor.level}）
                  </Text>
                  <Text className={styles.jobMeta}>{neighbor.ownerNickname}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `v-${neighbor.petId}`}
                  onClick={() =>
                    run(
                      `v-${neighbor.petId}`,
                      async () => {
                        const res = await petApi.enterHome(neighbor.petId)
                        if (res.data.success && res.data.data) {
                          setTip(res.data.data.message)
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
                  className={styles.miniBtnGhost}
                  disabled={pending === `l-${neighbor.petId}`}
                  onClick={() => run(`l-${neighbor.petId}`, () => petApi.likeHome(neighbor.petId), '点赞成功')}
                >
                  点赞
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fq-${neighbor.petId}`}
                  onClick={() =>
                    run(`fq-${neighbor.petId}`, () => petApi.requestFriend(neighbor.ownerUserId), '好友申请已发出～')
                  }
                >
                  加好友
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}
    </View>
  )
}
