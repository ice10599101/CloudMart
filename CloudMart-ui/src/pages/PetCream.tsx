/**
 * 宠物家园 · 法式奶油风版（/pet-cream）。
 *
 * 定位：与旧版 PetHome 并行运行的验证页——设计系统与基础组件先在这里落地并接真实接口，
 * 14 个 C 端面板逐个迁入；全部完成后替换 PetHome.tsx 并切路由。
 *
 * 本期已接：宠物身份、五维状态、3D 舞台、养成四动作、改名、隐私开关、多宠物切换、领养向导。
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Input, Spin } from 'antd'
import { history } from 'umi'
import {
    activatePet,
    cleanPet,
    createPet,
    feedPet,
    getMyPet,
    listMyPets,
    playWithPet,
    renamePet,
    restPet,
    updatePetPrivacy,
    type PetInfo,
    type PetSpecies,
    type PetSummary,
} from '@/api/pet'
import PetStage, { type PetDisplayState, type PetIntentAction } from '@/components/PetStage'
import {
    CreamButton,
    CreamCard,
    CreamChip,
    CreamOrnament,
    CreamStage,
    CreamStatBar,
    FRUIT_ACCENT,
    SPECIES_EMOJI,
    STAT_TONE,
    fruitAccent,
} from '@/components/pet-cream/Cream'
import styles from './PetCream.module.css'

const STATUS_LABEL: Record<string, string> = {
    IDLE: '悠闲中',
    WORKING: '打工中',
    STUDYING: '读书中',
    FISHING: '捞瓶中',
    RESTING: '休息中',
}

const STATUS_SPEECH: Record<string, string> = {
    IDLE: '主人，陪我玩一会嘛～',
    WORKING: '我正在打工赚星光呢！',
    STUDYING: '嘘——我在读书，别打扰我～',
    FISHING: '我去海边看看有没有漂流瓶！',
    RESTING: '呼…让我睡一小会儿…',
}

const GROWTH_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长', ADULT: '成年' }

const FRUIT_OPTIONS: Array<{ value: PetSpecies; emoji: string; label: string }> = [
    { value: 'STRAWBERRY', emoji: '🍓', label: '草莓' },
    { value: 'ORANGE', emoji: '🍊', label: '橘子' },
    { value: 'WATERMELON', emoji: '🍉', label: '西瓜' },
    { value: 'BLUEBERRY', emoji: '🫐', label: '蓝莓' },
    { value: 'DRAGONFRUIT', emoji: '🐉', label: '火龙果' },
]

type CareAction = 'feed' | 'play' | 'clean' | 'rest'

const CARE_LABEL: Record<CareAction, string> = {
    feed: '喂食',
    play: '玩耍',
    clean: '清洁',
    rest: '休息',
}

export default function PetCreamPage() {
    const { message } = App.useApp()
    const [pet, setPet] = useState<PetInfo | null>(null)
    const [pets, setPets] = useState<PetSummary[]>([])
    const [loading, setLoading] = useState(true)
    const [busy, setBusy] = useState<CareAction | null>(null)
    const [renameValue, setRenameValue] = useState('')
    const [adoptName, setAdoptName] = useState('')
    const [adoptSpecies, setAdoptSpecies] = useState<PetSpecies>('STRAWBERRY')

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const { data: mine } = await getMyPet()
            setPet(mine.success ? mine.data : null)
            const { data: list } = await listMyPets()
            if (list.success) {
                setPets(list.data)
            }
        } catch {
            setPet(null)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        void load()
    }, [load])

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

    /** 舞台意图：养成四动作就地执行；其余面板仍在旧版页面，明确告知用户 */
    const onIntent = useCallback((action: PetIntentAction) => {
        if (action === 'feed' || action === 'play' || action === 'clean' || action === 'rest') {
            void doCare(action)
            return
        }
        message.info('这个面板的奶油风版本正在迁移中，稍后就在本页打开')
    }, [doCare, message])

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

    if (loading) {
        return (
            <div className={`${styles.page} ${styles.centered}`}>
                <Spin size="large" />
            </div>
        )
    }

    const accent = fruitAccent(pet?.species)

    return (
        <div className={styles.page}>
            <div className={styles.shell}>
                <header className={styles.masthead}>
                    <h1 className={styles.mastheadTitle}>Le Petit Jardin</h1>
                    <p className={styles.mastheadSub}>宠物小花园 · 五果相伴</p>
                    <div className={styles.mastheadRule} />
                    <div className={styles.mastheadActions}>
                        <CreamButton variant="ghost" onClick={() => history.push('/pet')}>返回旧版界面</CreamButton>
                    </div>
                </header>

                {!pet ? (
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
                            <div className={styles.renameRow}>
                                <Input
                                    value={renameValue}
                                    onChange={event => setRenameValue(event.target.value)}
                                    placeholder="新名字"
                                    maxLength={12}
                                />
                                <CreamButton variant="ghost" onClick={() => void submitRename()}>改名</CreamButton>
                            </div>
                        </CreamCard>

                        <CreamOrnament>❦</CreamOrnament>
                    </>
                )}
            </div>
        </div>
    )
}
