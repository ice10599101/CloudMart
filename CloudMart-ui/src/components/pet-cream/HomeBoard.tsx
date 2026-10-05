/**
 * 家园面板（三期）：布置 / 家具铺 / 墙纸地板 / 设置 / 串门。
 *
 * 房间规则（与后端 PetHomeServiceImpl 对齐）：
 * - 网格尺寸由服务端下发（默认 4×3），一个格子最多一件家具，同一种家具只能摆一件；
 * - 墙纸/地板是"穿戴在房间上"的风格键（WALL / FLOOR），不占格子，在「墙纸地板」页签更换；
 * - 换主题时两个字段会一起落库，所以每次都要把未改动的一侧原值带回去。
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    buyPetFurniture,
    getPetHome,
    likePetHome,
    listPetVisitNeighbors,
    placePetFurniture,
    removePetFurniture,
    updatePetRoomSettings,
    updatePetRoomTheme,
    visitPetHome,
    visitPetHomeEntry,
    type PetHome,
    type PetHomeItem,
    type PetRoomVisit,
    type PetVisitNeighbor,
} from '@/api/pet'
import { CreamButton, CreamChip, SPECIES_EMOJI } from './Cream'
import styles from './homeBoard.module.css'

type HomeTab = 'room' | 'shop' | 'theme' | 'settings' | 'visit'

const HOME_TABS: Array<{ key: HomeTab; label: string }> = [
    { key: 'room', label: '布置' },
    { key: 'shop', label: '家具铺' },
    { key: 'theme', label: '墙纸地板' },
    { key: 'settings', label: '设置' },
    { key: 'visit', label: '串门' },
]

/** 风格键分类：穿在房间上，不进网格 */
const THEME_LABEL: Record<string, string> = { WALL: '墙纸', FLOOR: '地板' }

const THEME_CATEGORIES = ['WALL', 'FLOOR'] as const

function isThemeItem(item: PetHomeItem): boolean {
    return item.category === 'WALL' || item.category === 'FLOOR'
}

function posKey(posX: number | null, posY: number | null): string | null {
    return posX === null || posY === null ? null : `${posX}:${posY}`
}

export default function HomeBoard({ onChanged }: { onChanged?: () => void }) {
    const { message } = App.useApp()
    const [home, setHome] = useState<PetHome | null>(null)
    const [tab, setTab] = useState<HomeTab>('room')
    /** 正在等待落位的家具编码（选中后点空格子摆放） */
    const [placingCode, setPlacingCode] = useState<string | null>(null)
    const [busy, setBusy] = useState<string | null>(null)
    const [welcome, setWelcome] = useState('')
    const [neighbors, setNeighbors] = useState<PetVisitNeighbor[] | null>(null)
    const [room, setRoom] = useState<PetRoomVisit | null>(null)

    const load = useCallback(async () => {
        const { data: res } = await getPetHome()
        if (res.success) {
            setHome(res.data)
            setWelcome(res.data.welcomeMessage ?? '')
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

    const loadNeighbors = useCallback(async () => {
        const { data: res } = await listPetVisitNeighbors()
        if (res.success) {
            setNeighbors(res.data)
        }
    }, [])

    useEffect(() => {
        if (tab === 'visit' && neighbors === null) {
            void loadNeighbors()
        }
    }, [loadNeighbors, neighbors, tab])

    const placedByCell = useMemo(() => {
        const map = new Map<string, PetHomeItem>()
        home?.placed.forEach(item => {
            const key = posKey(item.posX, item.posY)
            if (key) {
                map.set(key, item)
            }
        })
        return map
    }, [home])

    const placedCodes = useMemo(
        () => new Set((home?.placed ?? []).map(item => item.code)),
        [home],
    )

    const placeable = useMemo(
        () => (home?.inventory ?? []).filter(item => !isThemeItem(item)),
        [home],
    )

    const place = useCallback(async (posX: number, posY: number) => {
        if (!placingCode) {
            message.warning('先在下面的家具里选一件，再点格子放下')
            return
        }
        setBusy('place')
        try {
            const { data: res } = await placePetFurniture({ furnitureCode: placingCode, posX, posY })
            if (res.success) {
                setHome(res.data)
                setPlacingCode(null)
                message.success('摆好了，房间更舒服啦')
                onChanged?.()
            } else {
                message.warning(res.error?.message ?? '这个位置放不下')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged, placingCode])

    /** busyKey：列表里的「卸下」按家具编码占位（背包项的 posX/posY 为 null，无法按格子匹配） */
    const removeAt = useCallback(async (posX: number, posY: number, busyKey?: string) => {
        setBusy(busyKey ?? `remove:${posX}:${posY}`)
        try {
            const { data: res } = await removePetFurniture(posX, posY)
            if (res.success) {
                setHome(res.data)
                message.success('已收回背包')
                onChanged?.()
            } else {
                message.warning(res.error?.message ?? '卸下未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const removeByCode = useCallback(async (code: string) => {
        const target = home?.placed.find(item => item.code === code)
        if (!target || target.posX === null || target.posY === null) {
            return
        }
        await removeAt(target.posX, target.posY, `removeCode:${code}`)
    }, [home, removeAt])

    const buy = useCallback(async (item: PetHomeItem) => {
        setBusy(`buy:${item.code}`)
        try {
            const { data: res } = await buyPetFurniture(item.code)
            if (res.success) {
                message.success(`${item.name} 已入背包`)
                await load()
                onChanged?.()
            } else {
                message.warning(res.error?.message ?? '购买未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [load, message, onChanged])

    /** 换墙纸/地板：另一侧带原值一起提交（后端两个字段都会落库，null = 恢复默认） */
    const applyTheme = useCallback(async (wallCode: string | null, floorCode: string | null) => {
        setBusy('theme')
        try {
            const { data: res } = await updatePetRoomTheme({ wallCode, floorCode })
            if (res.success) {
                setHome(res.data)
                message.success('房间焕然一新')
                onChanged?.()
            } else {
                message.warning(res.error?.message ?? '换装未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message, onChanged])

    const togglePublic = useCallback(async () => {
        setBusy('settings')
        try {
            const { data: res } = await updatePetRoomSettings({ isPublic: !home?.isPublic })
            if (res.success) {
                setHome(res.data)
                message.success(res.data.isPublic ? '已开放，欢迎邻居来串门' : '已关闭来访')
            } else {
                message.warning(res.error?.message ?? '设置未保存')
            }
        } finally {
            setBusy(null)
        }
    }, [home?.isPublic, message])

    const saveWelcome = useCallback(async () => {
        const text = welcome.trim()
        if (!text) {
            message.warning('写一句欢迎语吧')
            return
        }
        setBusy('welcome')
        try {
            const { data: res } = await updatePetRoomSettings({ welcomeMessage: text })
            if (res.success) {
                setHome(res.data)
                message.success('欢迎语已更新')
            } else {
                message.warning(res.error?.message ?? '设置未保存')
            }
        } finally {
            setBusy(null)
        }
    }, [message, welcome])

    const visit = useCallback(async (petId: number | string) => {
        setBusy(`visit:${petId}`)
        try {
            const { data: res } = await visitPetHomeEntry(petId)
            if (res.success) {
                setRoom(res.data)
                message.success(res.data.message || '串门成功')
            } else {
                message.warning(res.error?.message ?? '没能进门')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    const like = useCallback(async (petId: number | string) => {
        setBusy('like')
        try {
            const { data: res } = await likePetHome(petId)
            if (res.success) {
                setRoom(prev => (prev ? { ...prev, likeCount: res.data.likeCount, liked: true } : prev))
                message.success(res.data.message || '已点赞')
            } else {
                message.warning(res.error?.message ?? '点赞未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    if (!home) {
        return <Spin />
    }

    const gridCells = Array.from(
        { length: Math.max(1, home.gridWidth) * Math.max(1, home.gridHeight) },
        (_, index) => ({
            posX: index % Math.max(1, home.gridWidth),
            posY: Math.floor(index / Math.max(1, home.gridWidth)),
        }),
    )

    return (
        <div>
            <div className={styles.tabs}>
                {HOME_TABS.map(item => (
                    <button
                        key={item.key}
                        type="button"
                        className={`${styles.tab} ${tab === item.key ? styles.tabActive : ''}`}
                        onClick={() => setTab(item.key)}
                    >
                        {item.label}
                    </button>
                ))}
            </div>

            <div className={styles.summary}>
                <span>舒适度 <span className={styles.summaryValue}>{home.comfort}</span></span>
                <span>来访 <span className={styles.summaryValue}>{home.visitCount}</span></span>
                <span>点赞 <span className={styles.summaryValue}>{home.likeCount}</span></span>
                <span>
                    休息加成 <span className={styles.summaryValue}>
                        +{home.comfortRestHappinessBonus}
                    </span>（舒适度达 {home.comfortBonusThreshold} 起）
                </span>
                <span>今日回家礼 <span className={styles.summaryValue}>
                    {home.dailyEnterRewarded ? '已领' : '下次进入领取'}
                </span></span>
            </div>

            {tab === 'room' ? (
                <>
                    <div className={styles.room}>
                        <div
                            className={styles.roomGrid}
                            style={{ gridTemplateColumns: `repeat(${home.gridWidth}, minmax(0, 1fr))` }}
                        >
                            {gridCells.map(cell => {
                                const item = placedByCell.get(`${cell.posX}:${cell.posY}`)
                                const filled = Boolean(item)
                                const target = !filled && Boolean(placingCode)
                                return (
                                    <button
                                        key={`${cell.posX}:${cell.posY}`}
                                        type="button"
                                        className={`${styles.cell} ${filled ? styles.cellFilled : ''} ${target ? styles.cellTarget : ''}`}
                                        aria-label={item
                                            ? `${item.name}，位于第 ${cell.posY + 1} 行第 ${cell.posX + 1} 列，点击收回背包`
                                            : `第 ${cell.posY + 1} 行第 ${cell.posX + 1} 列空位`}
                                        onClick={() => {
                                            if (item) {
                                                if (placingCode) {
                                                    message.warning('这个格子已经有家具啦')
                                                    return
                                                }
                                                void removeAt(cell.posX, cell.posY)
                                                return
                                            }
                                            void place(cell.posX, cell.posY)
                                        }}
                                    >
                                        {item ? (
                                            <>
                                                <span className={styles.cellIcon}>{item.icon}</span>
                                                <span className={styles.cellName}>{item.name}</span>
                                            </>
                                        ) : (
                                            <span className={styles.cellPlus}>+</span>
                                        )}
                                    </button>
                                )
                            })}
                        </div>
                    </div>
                    <p className={styles.hint}>
                        {placingCode
                            ? <>已选中 <span className={styles.hintStrong}>{placingCode}</span>，点一个空格子放下；再点「取消」可放弃</>
                            : '点格子上的家具可收回背包；先从下面选一件，再点空格子摆放'}
                    </p>

                    <div className={styles.block}>
                        <p className={styles.blockTitle}>我的家具</p>
                        <p className={styles.blockDesc}>
                            共 {placeable.length} 件（背包里的家具摆出来才计舒适度）
                        </p>
                        {placeable.length === 0 ? (
                            <p className={styles.empty}>还没有家具，去「家具铺」挑一件吧</p>
                        ) : placeable.map(item => {
                            const placed = placedCodes.has(item.code)
                            return (
                                <div
                                    key={item.code}
                                    className={`${styles.row} ${placingCode === item.code ? styles.rowActive : ''}`}
                                >
                                    <span className={styles.rowIcon}>{item.icon}</span>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            {item.name}
                                            <CreamChip color="#A97C50">{item.categoryLabel}</CreamChip>
                                            {placed ? <CreamChip color="#7E9270">已摆放</CreamChip> : null}
                                        </p>
                                        <p className={styles.rowDesc}>
                                            {item.description || '一件温柔的小家具'} · 舒适度 +{item.comfort}
                                        </p>
                                    </div>
                                    {placed ? (
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `removeCode:${item.code}`}
                                            onClick={() => void removeByCode(item.code)}
                                        >
                                            卸下
                                        </CreamButton>
                                    ) : (
                                        <CreamButton
                                            variant="ghost"
                                            onClick={() => setPlacingCode(prev => (prev === item.code ? null : item.code))}
                                        >
                                            {placingCode === item.code ? '取消' : '摆放'}
                                        </CreamButton>
                                    )}
                                </div>
                            )
                        })}
                    </div>
                </>
            ) : null}

            {tab === 'shop' ? (
                <div className={styles.block}>
                    <p className={styles.blockTitle}>家具铺</p>
                    <p className={styles.blockDesc}>
                        墙纸地板在「墙纸地板」页签更换，其余分类摆进房间网格
                    </p>
                    {home.shop.map(item => (
                        <div key={item.code} className={styles.row}>
                            <span className={styles.rowIcon}>{item.icon}</span>
                            <div className={styles.rowMain}>
                                <p className={styles.rowTitle}>
                                    {item.name}
                                    <CreamChip color={isThemeItem(item) ? '#8AA5BC' : '#A97C50'}>
                                        {item.categoryLabel}
                                    </CreamChip>
                                    {item.themeActive ? <CreamChip color="#D89AA0">使用中</CreamChip> : null}
                                </p>
                                <p className={styles.rowDesc}>
                                    {item.description || '暂无描述'}
                                    {item.comfort > 0 ? ` · 舒适度 +${item.comfort}` : ''}
                                </p>
                            </div>
                            <span className={styles.price}>⭐ {item.priceStarlight}</span>
                            {item.owned ? (
                                <CreamChip color="#7E9270">已拥有</CreamChip>
                            ) : (
                                <CreamButton
                                    variant="ghost"
                                    disabled={!item.eligible}
                                    loading={busy === `buy:${item.code}`}
                                    onClick={() => void buy(item)}
                                >
                                    {item.lockReason ?? '购买'}
                                </CreamButton>
                            )}
                        </div>
                    ))}
                </div>
            ) : null}

            {tab === 'theme' ? (
                <div className={styles.block}>
                    {THEME_CATEGORIES.map(category => {
                        const current = category === 'WALL' ? home.wallCode : home.floorCode
                        const options = home.inventory.filter(item => item.category === category)
                        return (
                            <div key={category} className={styles.block}>
                                <p className={styles.blockTitle}>{THEME_LABEL[category]}</p>
                                <p className={styles.blockDesc}>
                                    当前：{options.find(item => item.code === current)?.name ?? '默认'}
                                </p>
                                <div className={styles.swatches}>
                                    <button
                                        type="button"
                                        className={`${styles.swatch} ${current === null ? styles.swatchActive : ''}`}
                                        onClick={() => void applyTheme(
                                            category === 'WALL' ? null : home.wallCode,
                                            category === 'FLOOR' ? null : home.floorCode,
                                        )}
                                    >
                                        默认
                                    </button>
                                    {options.map(item => (
                                        <button
                                            key={item.code}
                                            type="button"
                                            className={`${styles.swatch} ${item.code === current ? styles.swatchActive : ''}`}
                                            onClick={() => void applyTheme(
                                                category === 'WALL' ? item.code : home.wallCode,
                                                category === 'FLOOR' ? item.code : home.floorCode,
                                            )}
                                        >
                                            <span className={styles.swatchIcon}>{item.icon}</span>
                                            {item.name}
                                        </button>
                                    ))}
                                </div>
                                {options.length === 0 ? (
                                    <p className={styles.hint}>还没有{THEME_LABEL[category]}，去家具铺买一款吧</p>
                                ) : null}
                            </div>
                        )
                    })}
                </div>
            ) : null}

            {tab === 'settings' ? (
                <div className={styles.block}>
                    <div className={styles.settingsRow}>
                        <span>允许邻居来访：{home.isPublic ? '已开放' : '已关闭'}</span>
                        <CreamButton variant="ghost" loading={busy === 'settings'} onClick={() => void togglePublic()}>
                            {home.isPublic ? '关闭来访' : '开放来访'}
                        </CreamButton>
                    </div>
                    <p className={styles.hint}>关闭后其他用户无法进入你的家园，已获得的点赞与来访数会保留。</p>
                    <div className={styles.block}>
                        <p className={styles.blockTitle}>欢迎语</p>
                        <p className={styles.blockDesc}>邻居进门时看到的一句话（最多 40 字）</p>
                        <div className={styles.inputRow}>
                            <Input
                                className={styles.input}
                                value={welcome}
                                onChange={event => setWelcome(event.target.value)}
                                placeholder="欢迎来我家做客～"
                                maxLength={40}
                            />
                            <CreamButton variant="ghost" loading={busy === 'welcome'} onClick={() => void saveWelcome()}>
                                保存
                            </CreamButton>
                        </div>
                    </div>
                </div>
            ) : null}

            {tab === 'visit' ? (
                <div className={styles.block}>
                    {room ? (
                        <>
                            <div className={styles.visitCard}>
                                <p className={styles.visitName}>
                                    {SPECIES_EMOJI[room.species] ?? '🐾'} {room.petName}
                                </p>
                                <div className={styles.visitMeta}>
                                    <span>主人：{room.ownerNickname}</span>
                                    <span>Lv.{room.level}</span>
                                    <span>舒适度 {room.comfort}</span>
                                    <span>点赞 {room.likeCount}</span>
                                    {room.friend ? <span>好友</span> : null}
                                </div>
                                <p className={styles.visitWelcome}>“{room.welcomeMessage}”</p>
                            </div>
                            <div className={styles.visitGrid}>
                                {room.placed.length === 0 ? (
                                    <p className={styles.empty}>这间房还空着呢</p>
                                ) : room.placed.map(item => (
                                    <div key={item.code} className={styles.visitCell}>
                                        <span className={styles.cellIcon}>{item.icon}</span>
                                        <span className={styles.cellName}>{item.name}</span>
                                    </div>
                                ))}
                            </div>
                            <div className={styles.visitActions}>
                                <CreamButton
                                    variant="ghost"
                                    disabled={room.liked}
                                    loading={busy === 'like'}
                                    onClick={() => void like(room.petId)}
                                >
                                    {room.liked ? '已点赞' : '点赞'}
                                </CreamButton>
                                <CreamButton variant="ghost" onClick={() => setRoom(null)}>换一家</CreamButton>
                            </div>
                        </>
                    ) : neighbors === null ? (
                        <Spin />
                    ) : neighbors.length === 0 ? (
                        <p className={styles.empty}>还没有邻居开放家园，先去把自家布置好吧</p>
                    ) : neighbors.map(item => (
                        <div key={String(item.petId)} className={styles.row}>
                            <span className={styles.rowIcon}>{SPECIES_EMOJI[item.species] ?? '🐾'}</span>
                            <div className={styles.rowMain}>
                                <p className={styles.rowTitle}>
                                    {item.name}
                                    <CreamChip color="#A97C50">Lv.{item.level}</CreamChip>
                                    {item.visitedToday ? <CreamChip color="#7E9270">今日已串门</CreamChip> : null}
                                </p>
                                <p className={styles.rowDesc}>主人：{item.ownerNickname}</p>
                            </div>
                            <CreamButton
                                variant="ghost"
                                loading={busy === `visit:${item.petId}`}
                                onClick={() => void visit(item.petId)}
                            >
                                参观
                            </CreamButton>
                        </div>
                    ))}
                </div>
            ) : null}
        </div>
    )
}
