/**
 * 宠物家园 · 法式奶油风（/pet 正式版）。
 *
 * 版面：招牌页头 → "家"卡片（身份条 + 3D 舞台）→ 状态仪表 → 养成动作 → 功能菜单 → 设置。
 * 功能面板以 CreamSheet 弹层承载：大体量面板在 components/pet-cream/ 下
 * （HomeBoard/SocialBoard/CompanionBoard/PlayBoard/MiscBoard/PveBoard/SeasonPassBoard/LinkedProductBoard），
 * 其余面板按域拆在 ./PetCream/panels/，共享常量/类型在 ./PetCream/constants.ts 与 ./PetCream/types.ts。
 * 外观修改在设置卡、卸下装备/皮肤与装备预览在背包面板、一键领任务在任务面板。
 * 功能菜单按 PANEL_GROUPS 分段（La Maison / Croissance / Les Jeux / Le Marché / Les Amis / Archives）。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, ConfigProvider, Input, Spin, theme as antdTheme } from 'antd'
import {
    activatePet,
    cleanPet,
    createPet,
    feedPet,
    getPetActions,
    getPetBootstrap,
    getMyPet,
    listMyPets,
    playWithPet,
    renamePet,
    restPet,
    setOwnerTitle,
    updateAppearance,
    updatePetPrivacy,
    type PetActionItem,
    type PetInfo,
    type PetSpecies,
    type PetSummary,
} from '@/api/pet'
import PetStage, { type PetDisplayState, type PetIntentAction } from '@/components/PetStage'
import HomeBoard from '@/components/pet-cream/HomeBoard'
import SocialBoard from '@/components/pet-cream/SocialBoard'
import CompanionBoard from '@/components/pet-cream/CompanionBoard'
import PlayBoard from '@/components/pet-cream/PlayBoard'
import MiscBoard from '@/components/pet-cream/MiscBoard'
import PveBoard from '@/components/pet-cream/PveBoard'
import SeasonPassBoard from '@/components/pet-cream/SeasonPassBoard'
import LinkedProductBoard from '@/components/pet-cream/LinkedProductBoard'
import {
    CreamButton,
    CreamCard,
    CreamChip,
    CreamOrnament,
    CreamSheet,
    CreamStage,
    CreamStatBar,
    FRUIT_ACCENT,
    SPECIES_EMOJI,
    STAT_TONE,
    fruitAccent,
} from '@/components/pet-cream/Cream'
import {
    ACTION_LABEL,
    APPEARANCE_ACCESSORIES,
    APPEARANCE_COLORS,
    CARE_LABEL,
    FRUIT_OPTIONS,
    GROWTH_LABEL,
    PANEL_CAPABILITY,
    PANEL_GROUPS,
    PANELS,
    STATUS_LABEL,
    STATUS_SPEECH,
    parseAppearance,
} from './PetCream/constants'
import type { CareAction, PanelKey } from './PetCream/types'
import { QuestsPanel } from './PetCream/panels/quests'
import { AchievementsPanel } from './PetCream/panels/achievements'
import { RemindersPanel } from './PetCream/panels/reminders'
import { RankingsPanel } from './PetCream/panels/rankings'
import { WalletPanel } from './PetCream/panels/wallet'
import { ActivitiesPanel, ActivityCenterPanel } from './PetCream/panels/activities'
import { CareerPanel } from './PetCream/panels/career'
import { BottlePanel } from './PetCream/panels/bottle'
import { BattlePanel } from './PetCream/panels/battle'
import { ChatPanel } from './PetCream/panels/chat'
import { InventoryPanel, ShopPanel } from './PetCream/panels/shop'
import { SkillsPanel } from './PetCream/panels/skills'
import { EventsPanel } from './PetCream/panels/events'
import styles from './PetCream.module.css'

// ---------------- 页面 ----------------

export default function PetCreamPage() {
    const { message } = App.useApp()
    const [pet, setPet] = useState<PetInfo | null>(null)
    const [pets, setPets] = useState<PetSummary[]>([])
    const [loading, setLoading] = useState(true)
    const [busy, setBusy] = useState<CareAction | null>(null)
    const [renameValue, setRenameValue] = useState('')
    // 主人称呼（宠物怎么叫主人）
    const [ownerTitleValue, setOwnerTitleValue] = useState('')
    const [adoptName, setAdoptName] = useState('')
    const [adoptSpecies, setAdoptSpecies] = useState<PetSpecies>('STRAWBERRY')
    // PET-23：URL 深链接驱动面板——?panel=center 直达对应面板（通知 payload/外部跳转用）
    const [activePanel, setActivePanel] = useState<PanelKey | null>(() => {
        const initial = new URLSearchParams(window.location.search).get('panel')
        return PANELS.some(item => item.key === initial) ? (initial as PanelKey) : null
    })
    /** 外观草稿（颜色/配饰），初始值来自 pet.appearance（服务端 JSON），保存后随宠物回写 */
    const [appearanceDraft, setAppearanceDraft] = useState<{ color: string; accessory: string } | null>(null)
    /** 动作可执行性（B06）：刷新随宠物一起拉取 */
    const [actions, setActions] = useState<PetActionItem[] | null>(null)
    /** R23/T36：加载代际——切宠/重载推进代际，迟到的旧响应不覆盖新状态 */
    const loadGenerationRef = useRef(0)
    /** R23：pet 镜像 ref——catch 分支读取当前值而无需把 pet 拉进 load 依赖（避免 effect 循环） */
    const petRef = useRef<typeof pet>(null)
    useEffect(() => {
        petRef.current = pet
    }, [pet])
    /** R23/T36：加载失败且无旧数据时展示重试页（500 不再伪装成未领养） */
    const [loadFailed, setLoadFailed] = useState(false)
    /** R19：bootstrap 能力开关——控制协作副本/通行证/实物权益入口显隐；加载失败保持 null（未知不隐藏） */
    const [capabilities, setCapabilities] = useState<Record<string, boolean> | null>(null)

    // R19：启动快照只取 capabilities；失败不阻断主流程，门控按"未知不隐藏"降级
    useEffect(() => {
        getPetBootstrap()
            .then(({ data: res }) => {
                if (res.success) {
                    setCapabilities(res.data.capabilities ?? {})
                }
            })
            .catch(() => undefined)
    }, [])

    const load = useCallback(async () => {
        const generation = ++loadGenerationRef.current
        setLoading(true)
        try {
            const { data: mine } = await getMyPet({ silentError: true })
            if (generation !== loadGenerationRef.current) {
                return // 迟到的旧响应：丢弃（服务端权威数据由更新的一次 load 承载）
            }
            setPet(mine.success ? mine.data : null)
            if (mine.success) {
                // 动作可执行性（B06）：喂食/玩耍等当前能否执行与不可执行原因，随宠物一起刷新
                const { data: actionsRes } = await getPetActions(mine.data.petId)
                if (actionsRes.success && generation === loadGenerationRef.current) {
                    setActions(actionsRes.data ?? [])
                }
            }
            const { data: list } = await listMyPets()
            if (list.success && generation === loadGenerationRef.current) {
                setPets(list.data)
            }
        } catch (error) {
            if (generation !== loadGenerationRef.current) {
                return
            }
            // R23/T36：仅"确实无宠物"（PET_NOT_FOUND）才显示领养入口；
            // 500/网络错误保留旧数据并提示可重试，不再伪装成"未领养"
            const code = (error as { code?: string })?.code
            if (code === 'PET_NOT_FOUND') {
                setPet(null)
            } else if (petRef.current !== null) {
                message.warning('刷新失败，显示的可能不是最新数据，请重试')
            } else {
                setLoadFailed(true)
            }
        } finally {
            if (generation === loadGenerationRef.current) {
                setLoading(false)
            }
        }
    }, [message])

    useEffect(() => {
        void load()
    }, [load])

    /** 外观草稿随宠物就位（切宠物/刷新后以服务端值为准） */
    useEffect(() => {
        if (pet && appearanceDraft === null) {
            setAppearanceDraft(parseAppearance(pet.appearance))
        }
    }, [appearanceDraft, pet])

    /** 保存外观：后端会同时卸下穿戴中的皮肤（避免"皮肤标记"与实际外观不一致） */
    const submitAppearance = useCallback(async () => {
        if (!appearanceDraft) {
            return
        }
        const { data: res } = await updateAppearance(appearanceDraft)
        if (res.success) {
            setPet(res.data)
            message.success('外观已更新，穿戴中的皮肤已卸下')
        }
    }, [appearanceDraft, message])

    const display = useMemo<PetDisplayState | null>(() => {
        if (!pet) {
            return null
        }
        return {
            name: pet.name,
            species: pet.species,
            gender: pet.gender,
            growthStage: pet.growthStage,
            level: pet.level,
            expPercent: pet.expToNext > 0 ? Math.round((pet.exp / pet.expToNext) * 100) : 0,
            hp: pet.hp,
            maxHp: pet.maxHp,
            hunger: pet.hunger,
            happiness: pet.happiness,
            energy: pet.energy,
            cleanliness: pet.cleanliness,
            status: pet.status,
            speech: STATUS_SPEECH[pet.status] ?? '今天也想和主人待在一起～',
        }
    }, [pet])

    const doCare = useCallback(async (action: CareAction) => {
        setBusy(action)
        try {
            const run = { feed: feedPet, play: playWithPet, clean: cleanPet, rest: restPet }[action]
            const { data: res } = await run()
            if (res.success) {
                setPet(res.data)
                message.success(`${CARE_LABEL[action]}完成`)
            } else {
                message.warning(res.error?.message ?? '操作未成功')
            }
        } finally {
            setBusy(null)
        }
    }, [message])

    /** 舞台意图：养成四动作就地执行；任务/成就/排行三类面板直接打开对应弹层 */
    const onIntent = useCallback((action: PetIntentAction) => {
        if (action === 'feed' || action === 'play' || action === 'clean' || action === 'rest') {
            void doCare(action)
            return
        }
        const mapped: Partial<Record<PetIntentAction, PanelKey>> = {
            openRoom: 'home',
            openSocial: 'social',
            openDaily: 'quests',
            openAchievements: 'achievements',
            openRankings: 'rankings',
            openWork: 'activities',
            openStudy: 'activities',
            openCareer: 'career',
            openBottle: 'bottle',
            openBattle: 'battle',
            openChat: 'chat',
            openCare: 'shop',
        }
        const target = mapped[action]
        if (target) {
            setActivePanel(target)
        }
    }, [doCare])

    const submitRename = useCallback(async () => {
        const name = renameValue.trim()
        if (!name) {
            message.warning('先写个名字吧')
            return
        }
        const { data: res } = await renamePet({ name })
        if (res.success) {
            setPet(res.data)
            setRenameValue('')
            message.success('改名成功')
        }
    }, [message, renameValue])

    const submitOwnerTitle = useCallback(async () => {
        const title = ownerTitleValue.trim()
        if (!title) {
            message.warning('先写个称呼吧（清空保存则恢复「主人」）')
            return
        }
        const { data: res } = await setOwnerTitle(title)
        if (res.success) {
            setPet(res.data)
            setOwnerTitleValue('')
            message.success('称呼已更新，宠物会这样叫你啦')
        }
    }, [message, ownerTitleValue])

    const togglePrivacy = useCallback(async (checked: boolean) => {
        const { data: res } = await updatePetPrivacy({ isPublic: checked })
        if (res.success) {
            setPet(prev => (prev ? { ...prev, isPublic: checked } : prev))
            message.success(checked ? '已公开到个人主页' : '已隐藏，只有你能看到它')
        }
    }, [message])

    const switchPet = useCallback(async (petId: number | string) => {
        const { data: res } = await activatePet(petId)
        if (res.success) {
            await load()
            message.success('已切换宠物')
        }
    }, [load, message])

    const adopt = useCallback(async () => {
        const name = adoptName.trim()
        if (!name) {
            message.warning('给新伙伴取个名字吧')
            return
        }
        const { data: res } = await createPet({
            name,
            species: adoptSpecies,
            personality: 'LIVELY',
            gender: 'MALE',
        })
        if (res.success) {
            setPet(res.data)
            message.success('领养成功，好好照顾它～')
            await load()
        }
    }, [adoptName, adoptSpecies, load, message])

    const activePanelMeta = PANELS.find(item => item.key === activePanel)

    /** R19：capabilities 明确为 false 才隐藏入口；未加载/加载失败（null）按未知不隐藏 */
    const panelVisible = useCallback((key: PanelKey) => {
        const capabilityKey = PANEL_CAPABILITY[key]
        return capabilityKey === undefined || capabilities?.[capabilityKey] !== false
    }, [capabilities])

    if (loading) {
        return (
            <div className={`${styles.page} ${styles.centered}`}>
                <Spin size="large" />
            </div>
        )
    }

    const accent = fruitAccent(pet?.species)

    return (
        <ConfigProvider
            theme={{
                // 站点全局是暗色 antd token，宠物页必须强制回浅色，否则 Input/Spin 全是深蓝底
                algorithm: antdTheme.defaultAlgorithm,
                token: { colorPrimary: '#C89B5A', borderRadius: 14 },
            }}
        >
        <div className={styles.page}>
            <div className={styles.shell}>
                <header className={styles.masthead}>
                    <h1 className={styles.mastheadTitle}>Le Petit Jardin</h1>
                    <p className={styles.mastheadSub}>宠物小花园 · 五果相伴</p>
                    <div className={styles.mastheadRule} />
                </header>

                {/* R23/T36：加载失败（500/网络错误且无旧数据）显示重试页——不伪装成未领养 */}
                {loadFailed && !pet ? (
                    <CreamCard variant="arch" label="Adoption" title="加载失败" subtitle="服务暂时不可用，请稍后重试">
                        <CreamButton block onClick={() => { setLoadFailed(false); void load() }}>
                            重新加载
                        </CreamButton>
                    </CreamCard>
                ) : !pet ? (
                    <CreamCard variant="arch" label="Adoption" title="领养一只水果伙伴" subtitle="选一只，给它取个名字">
                        <div className={styles.fruitGrid}>
                            {FRUIT_OPTIONS.map(item => (
                                <button
                                    key={item.value}
                                    type="button"
                                    className={`${styles.fruitCard} ${adoptSpecies === item.value ? styles.fruitCardActive : ''}`}
                                    onClick={() => setAdoptSpecies(item.value)}
                                    style={adoptSpecies === item.value ? { borderColor: FRUIT_ACCENT[item.value] } : undefined}
                                >
                                    <span className={styles.fruitEmoji}>{item.emoji}</span>
                                    <span>{item.label}</span>
                                </button>
                            ))}
                        </div>
                        <Input
                            className={styles.input}
                            value={adoptName}
                            onChange={event => setAdoptName(event.target.value)}
                            placeholder="给它取个名字"
                            maxLength={12}
                        />
                        <div className={styles.renameRow}>
                            <CreamButton block onClick={() => void adopt()}>确认领养</CreamButton>
                        </div>
                    </CreamCard>
                ) : (
                    <>
                        <CreamCard variant="arch" label="Ma Maison" accent={accent}>
                            <div className={styles.identityStrip}>
                                <div className={styles.avatar} style={{ boxShadow: `0 6px 14px ${accent}33, inset 0 2px 0 rgba(255,255,255,.95), inset 0 -4px 0 ${accent}2E` }}>
                                    <span>{SPECIES_EMOJI[pet.species] ?? '🐾'}</span>
                                </div>
                                <div>
                                    <h2 className={styles.petName} style={{ color: accent }}>
                                        {pet.name}
                                        <span className={styles.gender}>{pet.gender === 'FEMALE' ? '♀' : '♂'}</span>
                                    </h2>
                                    <div className={styles.metaRow}>
                                        <CreamChip color={accent}>{FRUIT_OPTIONS.find(item => item.value === pet.species)?.label ?? pet.species}</CreamChip>
                                        <CreamChip color={STAT_TONE.hunger}>Lv.{pet.level}</CreamChip>
                                        <CreamChip color={STAT_TONE.energy}>{GROWTH_LABEL[pet.growthStage] ?? pet.growthStage}</CreamChip>
                                        <CreamChip color={STAT_TONE.cleanliness}>{STATUS_LABEL[pet.status] ?? '悠闲中'}</CreamChip>
                                    </div>
                                    <p className={styles.speech}>“{STATUS_SPEECH[pet.status] ?? '今天也想和主人待在一起～'}”</p>
                                </div>
                            </div>
                            <CreamStage>
                                <PetStage
                                    pet={display}
                                    onIntent={onIntent}
                                    fallback={
                                        <div style={{ display: 'grid', placeItems: 'center', fontSize: 64 }}>
                                            {SPECIES_EMOJI[pet.species] ?? '🐾'}
                                        </div>
                                    }
                                />
                            </CreamStage>
                        </CreamCard>

                        <CreamCard variant="menu" label="État" title="状态">
                            <div className={styles.statsGrid}>
                                <CreamStatBar variant="cell" name="生命" value={pet.hp} max={pet.maxHp} color={STAT_TONE.hp} />
                                <CreamStatBar variant="cell" name="饱食" value={pet.hunger} max={100} color={STAT_TONE.hunger} />
                                <CreamStatBar variant="cell" name="心情" value={pet.happiness} max={100} color={STAT_TONE.happiness} />
                                <CreamStatBar variant="cell" name="精力" value={pet.energy} max={100} color={STAT_TONE.energy} />
                                <CreamStatBar variant="cell" name="清洁" value={pet.cleanliness} max={100} color={STAT_TONE.cleanliness} />
                            </div>
                            {actions && actions.length > 0 ? (
                                <div className={styles.petRow} style={{ marginTop: 12 }}>
                                    {actions.map(item => (
                                        <span key={item.action} title={item.reasonText ?? ''}>
                                            <CreamChip color={item.allowed ? STAT_TONE.energy : STAT_TONE.cleanliness}>
                                                {ACTION_LABEL[item.action] ?? item.action}
                                                {item.allowed
                                                    ? (item.rewardRemainingToday !== null ? ` · 余${item.rewardRemainingToday}` : '')
                                                    : ' · 暂不可'}
                                            </CreamChip>
                                        </span>
                                    ))}
                                </div>
                            ) : null}
                        </CreamCard>

                        <div className={styles.actions}>
                            {(Object.keys(CARE_LABEL) as CareAction[]).map(action => (
                                <CreamButton
                                    key={action}
                                    onClick={() => void doCare(action)}
                                    loading={busy === action}
                                    disabled={busy !== null && busy !== action}
                                >
                                    {CARE_LABEL[action]}
                                </CreamButton>
                            ))}
                        </div>

                        <CreamCard variant="menu" label="Menu" title="功能面板">
                            {PANEL_GROUPS.map(group => {
                                const items = PANELS.filter(item => item.group === group.key && panelVisible(item.key))
                                if (items.length === 0) {
                                    return null
                                }
                                return (
                                    <div key={group.key} className={styles.menuGroup}>
                                        <div className={styles.menuGroupLabel}>
                                            <span className={styles.menuGroupTitle}>{group.label}</span>
                                            <span className={styles.menuGroupHint}>{group.hint}</span>
                                        </div>
                                        <div className={styles.menuGrid}>
                                            {items.map(item => (
                                                <button
                                                    key={item.key}
                                                    type="button"
                                                    className={styles.menuItem}
                                                    onClick={() => {
                                                        setActivePanel(item.key)
                                                        // PET-23：同步深链接（replaceState 避免回退栈污染）
                                                        const url = new URL(window.location.href)
                                                        url.searchParams.set('panel', item.key)
                                                        window.history.replaceState(null, '', url.toString())
                                                    }}
                                                >
                                                    <span className={styles.menuItemEmoji}>{item.emoji}</span>
                                                    <span>{item.label}</span>
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                )
                            })}
                        </CreamCard>

                        {pets.length > 1 ? (
                            <CreamCard variant="menu" label="Famille" title="我的宠物">
                                <div className={styles.petRow}>
                                    {pets.map(item => (
                                        <button
                                            key={String(item.petId)}
                                            type="button"
                                            className={`${styles.petChip} ${item.isActive ? styles.petChipActive : ''}`}
                                            onClick={() => void switchPet(item.petId)}
                                        >
                                            {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}
                                        </button>
                                    ))}
                                </div>
                            </CreamCard>
                        ) : null}

                        <CreamCard variant="menu" label="Réglages" title="设置">
                            <div className={styles.footerRow}>
                                <span className={styles.speech}>主页展示：{pet.isPublic ? '已公开' : '已隐藏'}</span>
                                <CreamButton variant="ghost" onClick={() => void togglePrivacy(!pet.isPublic)}>
                                    {pet.isPublic ? '设为隐藏' : '设为公开'}
                                </CreamButton>
                            </div>
                            <p className={styles.panelTitle} style={{ marginTop: 14 }}>外观</p>
                            <p className={styles.panelDesc}>
                                当前：{(appearanceDraft && APPEARANCE_COLORS.find(c => c.value === appearanceDraft.color)?.label) ?? '—'} ·
                                {(appearanceDraft && APPEARANCE_ACCESSORIES.find(a => a.value === appearanceDraft.accessory)?.label) ?? '—'}
                                （保存会卸下穿戴中的皮肤）
                            </p>
                            <div className={styles.petRow}>
                                {APPEARANCE_COLORS.map(item => (
                                    <button
                                        key={item.value}
                                        type="button"
                                        className={`${styles.petChip} ${appearanceDraft?.color === item.value ? styles.petChipActive : ''}`}
                                        onClick={() => setAppearanceDraft(prev => ({
                                            color: item.value,
                                            accessory: prev?.accessory ?? 'none',
                                        }))}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.petRow}>
                                {APPEARANCE_ACCESSORIES.map(item => (
                                    <button
                                        key={item.value}
                                        type="button"
                                        className={`${styles.petChip} ${appearanceDraft?.accessory === item.value ? styles.petChipActive : ''}`}
                                        onClick={() => setAppearanceDraft(prev => ({
                                            color: prev?.color ?? 'orange',
                                            accessory: item.value,
                                        }))}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                            <div className={styles.renameRow}>
                                <CreamButton variant="ghost" onClick={() => void submitAppearance()}>保存外观</CreamButton>
                            </div>
                            <div className={styles.renameRow}>
                                <Input
                                    className={styles.input}
                                    value={renameValue}
                                    onChange={event => setRenameValue(event.target.value)}
                                    placeholder="新名字"
                                    maxLength={12}
                                />
                                <CreamButton variant="ghost" onClick={() => void submitRename()}>改名</CreamButton>
                            </div>
                            <div className={styles.renameRow}>
                                <Input
                                    className={styles.input}
                                    value={ownerTitleValue}
                                    onChange={event => setOwnerTitleValue(event.target.value)}
                                    placeholder={pet.ownerTitle ?? '宠物怎么叫你（默认「主人」）'}
                                    maxLength={12}
                                />
                                <CreamButton variant="ghost" onClick={() => void submitOwnerTitle()}>称呼</CreamButton>
                            </div>
                        </CreamCard>

                        <CreamOrnament>❦</CreamOrnament>
                    </>
                )}
            </div>

            {activePanel && activePanelMeta ? (
                <CreamSheet title={activePanelMeta.title} onClose={() => setActivePanel(null)}>
                    {activePanel === 'home' ? <HomeBoard onChanged={() => void load()} /> : null}
                    {activePanel === 'social' && pet ? (
                        <SocialBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'companion' && pet ? (
                        <CompanionBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'play' && pet ? (
                        <PlayBoard myPetId={pet.petId} onChanged={() => void load()} />
                    ) : null}
                    {activePanel === 'pve' ? (
                        <PveBoard enabled={capabilities?.cooperativePve !== false} />
                    ) : null}
                    {activePanel === 'seasonPass' ? (
                        <SeasonPassBoard enabled={capabilities?.seasonPass !== false} />
                    ) : null}
                    {activePanel === 'linkedProduct' ? (
                        <LinkedProductBoard enabled={capabilities?.linkedProduct !== false} />
                    ) : null}
                    {activePanel === 'misc' ? <MiscBoard onChanged={() => void load()} /> : null}
                    {activePanel === 'quests' ? <QuestsPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'activities' ? <ActivitiesPanel petStatus={pet?.status ?? 'IDLE'} onChanged={() => void load()} /> : null}
                    {activePanel === 'center' ? <ActivityCenterPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'career' ? <CareerPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'bottle' ? <BottlePanel onChanged={() => void load()} /> : null}
                    {activePanel === 'battle' ? <BattlePanel myPetId={pet?.petId ?? ''} onChanged={() => void load()} /> : null}
                    {activePanel === 'chat' && pet ? <ChatPanel petName={pet.name} /> : null}
                    {activePanel === 'shop' ? <ShopPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'inventory' ? <InventoryPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'skills' ? <SkillsPanel petId={pet?.petId ?? ''} petLevel={pet?.level ?? 1} onChanged={() => void load()} /> : null}
                    {activePanel === 'events' ? <EventsPanel onChanged={() => void load()} /> : null}
                    {activePanel === 'achievements' ? <AchievementsPanel /> : null}
                    {activePanel === 'reminders' ? <RemindersPanel /> : null}
                    {activePanel === 'rankings' ? <RankingsPanel /> : null}
                    {activePanel === 'wallet' ? <WalletPanel /> : null}
                </CreamSheet>
            ) : null}
        </div>
        </ConfigProvider>
    )
}
