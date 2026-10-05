/**
 * 陪伴面板（三期）：亲密度 · 陪伴会话 / 日记 · 相册 / 记忆 / 陪伴设置。
 *
 * 后端语义（PetIntimacyController / PetCompanionFeatureService）：
 * - 陪伴计时以**服务端会话**为权威：首次心跳只建立基准（本次不计时），之后按服务端时钟差累计；
 *   客户端上报的秒数仅供参考，不参与收益判定。心跳 VO 与亲密度 overview 是两个 VO，
 *   禁止把心跳结果写进亲密度状态（心跳不含 levelName/toNext 等面板字段）。
 * - 日记由领域事件写入（客户端不可伪造），按游标分页；他人仅可见 PUBLIC 条目。
 * - 相册只提供"上传/删除"，**没有列表接口**——已上传资产随日记的 assetIds 展示，
 *   所以这里只维护本次会话上传的资产，不做假列表。
 * - 记忆开关（自动抽取 / 注入上下文）服务端无读取端点，界面只给显式动作按钮，不谎报当前状态。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    clearPetMemories,
    deletePetAlbumAsset,
    deletePetMemory,
    editPetMemory,
    getPetIntimacy,
    getPetMemorySettings,
    getPetNotifyPrefs,
    getPetOnboarding,
    listPetAlbum,
    listPetDiary,
    listPetMemories,
    retryPetAlbumBinding,
    sendPetCompanionHeartbeat,
    setPetMemorySettings,
    skipPetOnboarding,
    updatePetDiaryVisibility,
    stopCompanionSession,
    updatePetNotifyPrefs,
    uploadPetAlbumAsset,
    type PetCompanionSessionVO,
    type PetDiaryEntry,
    type PetIntimacyInfo,
    type PetMemory,
    type PetNotifyPref,
    type PetOnboardingProgress,
} from '@/api/pet'
import { uploadFileAsset } from '@/api/file'
import type { ApiResponse } from '@/types/api'
import { CreamButton, CreamChip } from './Cream'
import styles from './companionBoard.module.css'

type CompanionTab = 'intimacy' | 'diary' | 'memory' | 'settings'

const COMPANION_TABS: Array<{ key: CompanionTab; label: string }> = [
    { key: 'intimacy', label: '亲密度' },
    { key: 'diary', label: '日记 · 相册' },
    { key: 'memory', label: '记忆' },
    { key: 'settings', label: '陪伴设置' },
]

/** 心跳间隔与每次上报秒数（服务端按自己的时钟差结算，客户端值仅供参考） */
const HEARTBEAT_SECONDS = 60
const HEARTBEAT_INTERVAL_MS = 60_000

const MEMORY_TYPE_LABEL: Record<string, string> = {
    FAVORITE: '喜好',
    HABIT: '习惯',
    FACT: '事实',
}


function fmtSeconds(seconds: number): string {
    const total = Math.max(0, Math.round(seconds))
    const h = Math.floor(total / 3600)
    const m = Math.floor((total % 3600) / 60)
    if (h > 0) {
        return `${h} 小时 ${m} 分`
    }
    return m > 0 ? `${m} 分钟` : `${total} 秒`
}

export default function CompanionBoard({ myPetId, onChanged }: {
    myPetId: number | string
    onChanged?: () => void
}) {
    const { message, modal } = App.useApp()
    const [tab, setTab] = useState<CompanionTab>('intimacy')
    const [busy, setBusy] = useState<string | null>(null)

    const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
    const [companionOn, setCompanionOn] = useState(false)
    const [session, setSession] = useState<PetCompanionSessionVO | null>(null)
    const seqRef = useRef(0)

    const [diary, setDiary] = useState<PetDiaryEntry[] | null>(null)
    const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
    const [diaryHasMore, setDiaryHasMore] = useState(false)
    const [assets, setAssets] = useState<Array<{ id: string; fileId: string; auditStatus: string; bindStatus: string | null; previewUrl: string | null; diaryEntryId: string | null }>>([])
    const [attachTarget, setAttachTarget] = useState<number | null>(null)

    const [memories, setMemories] = useState<PetMemory[] | null>(null)
    /** 记忆开关草稿（R03/T11：初值来自服务端 GET，不得以默认 true 覆盖已关闭设置） */
    const [extractDraft, setExtractDraft] = useState(true)
    const [useDraft, setUseDraft] = useState(true)
    const [memorySettingsLoaded, setMemorySettingsLoaded] = useState(false)
    const [editId, setEditId] = useState<number | null>(null)
    const [editValue, setEditValue] = useState('')

    const [pref, setPref] = useState<PetNotifyPref | null>(null)
    const [onboarding, setOnboarding] = useState<PetOnboardingProgress | null>(null)

    const run = useCallback(async <T,>(
        key: string,
        task: () => Promise<{ data: ApiResponse<T> }>,
    ): Promise<ApiResponse<T> | null> => {
        setBusy(key)
        try {
            const { data: res } = await task()
            if (!res.success) {
                message.warning(res.error?.message ?? '操作未成功')
            }
            return res
        } finally {
            setBusy(null)
        }
    }, [message])

    const loadIntimacy = useCallback(async () => {
        const { data: res } = await getPetIntimacy()
        if (res.success) {
            setIntimacy(res.data)
        }
    }, [])

    const loadDiary = useCallback(async (reset: boolean) => {
        const { data: res } = await listPetDiary(myPetId, reset ? undefined : (diaryCursor ?? undefined), 20)
        if (res.success) {
            setDiary(prev => (reset ? res.data.items : [...(prev ?? []), ...res.data.items]))
            setDiaryCursor(res.data.nextCursor)
            setDiaryHasMore(res.data.hasMore)
        }
    }, [diaryCursor, myPetId])

    const loadMemories = useCallback(async () => {
        const { data: res } = await listPetMemories(myPetId)
        if (res.success) {
            setMemories(res.data)
        }
    }, [myPetId])

    const loadSettings = useCallback(async () => {
        const [{ data: prefRes }, { data: onboardingRes }] = await Promise.all([
            getPetNotifyPrefs(),
            getPetOnboarding(),
        ])
        if (prefRes.success) {
            setPref(prefRes.data)
        }
        if (onboardingRes.success) {
            setOnboarding(onboardingRes.data)
        }
    }, [])

    const [pageVisible, setPageVisible] = useState(!document.hidden)

    useEffect(() => {
        if (tab === 'intimacy' && intimacy === null) {
            void loadIntimacy()
        }
        if (tab === 'diary' && diary === null) {
            void loadDiary(true)
        }
        if (tab === 'memory' && memories === null) {
            void loadMemories()
        }
        if (tab === 'memory' && !memorySettingsLoaded) {
            void (async () => {
                const { data: res } = await getPetMemorySettings(myPetId)
                if (res.success) {
                    setExtractDraft(res.data.extract)
                    setUseDraft(res.data.use)
                    setMemorySettingsLoaded(true)
                }
            })()
        }
        if (tab === 'settings' && pref === null) {
            void loadSettings()
        }
    }, [diary, intimacy, loadDiary, loadIntimacy, loadMemories, loadSettings, memories, memorySettingsLoaded, myPetId, pref, tab])

    /**
     * 陪伴心跳：开启期间按固定间隔上报；卸载时清理定时器（会话由服务端按失效间隔结算）。
     * R23/T37：仅页面可见时上报——切后台/最小化暂停心跳（服务端会话超时兜底），
     * 恢复可见时继续；"陪伴"必须是真实停留在宠物页。
     */
    useEffect(() => {
        if (!companionOn || document.hidden) {
            return
        }
        const onVisibility = () => setPageVisible(!document.hidden)
        document.addEventListener('visibilitychange', onVisibility)
        const timer = window.setInterval(() => {
            seqRef.current += 1
            void (async () => {
                const { data: res } = await sendPetCompanionHeartbeat(HEARTBEAT_SECONDS, seqRef.current)
                if (res.success) {
                    setSession(res.data)
                }
            })()
        }, HEARTBEAT_INTERVAL_MS)
        return () => {
            window.clearInterval(timer)
            document.removeEventListener('visibilitychange', onVisibility)
        }
    }, [companionOn, pageVisible])

    const startCompanion = useCallback(async () => {
        seqRef.current = 1
        const res = await run('companion', () => sendPetCompanionHeartbeat(HEARTBEAT_SECONDS, 1))
        if (res?.success) {
            setSession(res.data)
            setCompanionOn(true)
            message.success('开始陪伴，时长由服务端结算')
        }
    }, [message, run])

    const stopCompanion = useCallback(async () => {
        setCompanionOn(false)
        const res = await run('companion', () => stopCompanionSession())
        if (res?.success) {
            setSession(null)
            await loadIntimacy()
            message.success('陪伴结束，本次时长已结算')
            onChanged?.()
        }
    }, [loadIntimacy, message, onChanged, run])

    /** R04 相册上传：PRIVATE 上传拿 fileId → 绑定成功才算成功；绑定失败提示可重试，不把孤儿文件当相册成功 */
    const uploadPhoto = useCallback(async (file: File, diaryEntryId?: number) => {
        setBusy('album')
        try {
            const { data: up } = await uploadFileAsset(file, 'PRIVATE')
            if (!up.success) {
                message.warning(up.error?.message ?? '上传未成功')
                return
            }
            const res = await run('album', () => uploadPetAlbumAsset(myPetId, up.data.fileId, diaryEntryId))
            if (res?.success) {
                setAssets(prev => [{
                    id: res.data.assetId,
                    fileId: res.data.fileId,
                    auditStatus: res.data.auditStatus,
                    bindStatus: res.data.bindStatus,
                    previewUrl: res.data.previewUrl,
                    diaryEntryId: res.data.diaryEntryId,
                }, ...prev])
                message.success('照片已存入相册，待审核')
                await loadDiary(true)
            } else if (up.data.fileId) {
                // 绑定失败：文件已上传但未入相册——提示可删除孤儿文件，不算上传成功
                message.warning('照片绑定失败，可在文件管理中清理后重试')
            }
        } finally {
            setBusy(null)
        }
    }, [loadDiary, message, myPetId, run])

    const removeAsset = useCallback(async (assetId: string) => {
        const res = await run(`album-del:${assetId}`, () => deletePetAlbumAsset(myPetId, assetId))
        if (res?.success) {
            setAssets(prev => prev.filter(item => item.id !== assetId))
        }
    }, [myPetId, run])

    /** R04：BINDING 条目恢复（远程失败重试，幂等引用键收敛） */
    const retryBinding = useCallback(async (assetId: string) => {
        const res = await run(`album-retry:${assetId}`, () => retryPetAlbumBinding(myPetId, assetId))
        if (res?.success) {
            setAssets(prev => prev.map(item => (item.id === assetId
                ? { ...item, bindStatus: res.data.bindStatus, auditStatus: res.data.auditStatus }
                : item)))
        }
    }, [myPetId, run])

    /** R04：打开相册页时从服务端拉列表（审核状态/预览地址以服务端为准） */
    const loadAlbum = useCallback(async () => {
        const { data: res } = await listPetAlbum(myPetId)
        if (res.success) {
            setAssets(res.data.map(item => ({
                id: item.assetId,
                fileId: item.fileId,
                auditStatus: item.auditStatus,
                bindStatus: item.bindStatus,
                previewUrl: item.previewUrl,
                diaryEntryId: item.diaryEntryId,
            })))
        }
    }, [myPetId])

    useEffect(() => {
        if (tab === 'diary') {
            void loadAlbum()
        }
    }, [loadAlbum, tab])

    const saveMemory = useCallback(async (memory: PetMemory) => {
        const value = editValue.trim()
        if (!value) {
            message.warning('记忆内容不能为空')
            return
        }
        const res = await run(`memory:${memory.id}`,
            () => editPetMemory(myPetId, memory.id, { value }))
        if (res?.success) {
            setEditId(null)
            await loadMemories()
        }
    }, [editValue, loadMemories, message, myPetId, run])

    const removeMemory = useCallback(async (memoryId: number) => {
        const res = await run(`memory-del:${memoryId}`, () => deletePetMemory(myPetId, memoryId))
        if (res?.success) {
            await loadMemories()
        }
    }, [loadMemories, myPetId, run])

    const clearAllMemories = useCallback(() => {
        modal.confirm({
            title: '清空全部记忆？',
            content: '这只宠物的所有结构化记忆会被移除（含自动抽取的），且不可恢复。',
            okText: '清空',
            cancelText: '再想想',
            onOk: async () => {
                const res = await run('memory-clear', () => clearPetMemories(myPetId))
                if (res?.success) {
                    await loadMemories()
                }
            },
        })
    }, [loadMemories, modal, myPetId, run])

    const toggleMemorySetting = useCallback(async (extract: boolean, use: boolean) => {
        const res = await run('memory-setting', () => setPetMemorySettings(myPetId, { extract, use }))
        if (res?.success) {
            message.success('记忆设置已更新')
        }
    }, [message, myPetId, run])

    /** §7.2 日记可见性 PATCH（expectedVersion CAS，冲突提示刷新） */
    const toggleDiaryVisibility = useCallback(async (item: PetDiaryEntry, next: 'PUBLIC' | 'OWNER_ONLY') => {
        if (!item.version) {
            message.warning('该条目暂不支持切换可见性')
            return
        }
        const res = await run(`diary-vis:${item.id}`, () =>
            updatePetDiaryVisibility(myPetId, item.id, { visibility: next, expectedVersion: item.version ?? 1 }))
        if (res?.success) {
            message.success(next === 'PUBLIC' ? '已设为公开' : '已设为仅自己可见')
            const { data: relist } = await listPetDiary(myPetId, undefined, 20)
            if (relist.success) setDiary(relist.data.items)
        }
    }, [message, myPetId, run])

    const togglePref = useCallback(async (next: { muteDailyGreeting: boolean; dailyGreetingEnabled: boolean }) => {
        // §7.2：带当前 version 做 CAS，冲突提示刷新
        const res = await run('pref', () => updatePetNotifyPrefs({
            ...next,
            expectedVersion: pref?.version,
        }))
        if (res?.success && pref) {
            setPref({ ...pref, ...res.data })
            message.success('通知偏好已更新')
        }
    }, [pref, run])

    const skipOnboarding = useCallback(async () => {
        const res = await run('onboarding', () => skipPetOnboarding())
        if (res?.success) {
            await loadSettings()
        }
    }, [loadSettings, run])

    const levelPercent = (() => {
        if (!intimacy) {
            return 0
        }
        if (intimacy.nextLevelAt === null) {
            return 100
        }
        const span = Math.max(1, intimacy.nextLevelAt - intimacy.levelFloor)
        return Math.max(0, Math.min(100, Math.round(((intimacy.intimacy - intimacy.levelFloor) / span) * 100)))
    })()

    return (
        <div>
            <div className={styles.tabs}>
                {COMPANION_TABS.map(item => (
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

            {tab === 'intimacy' ? (
                intimacy === null ? <Spin /> : (
                    <>
                        <div className={styles.levelCard}>
                            <p className={styles.levelName}>
                                {intimacy.levelName}
                                <span className={styles.levelMeta}> · Lv.{intimacy.level}</span>
                            </p>
                            <p className={styles.levelMeta}>
                                亲密度 {intimacy.intimacy}
                                {intimacy.nextLevelAt === null
                                    ? ' · 已满级'
                                    : ` · 距下一级还需 ${intimacy.toNext}`}
                            </p>
                            <div className={styles.levelBar}>
                                <span>Lv.{intimacy.level}</span>
                                <span className={styles.levelBarTrack}>
                                    <span className={styles.levelTrack}>
                                        <span className={styles.levelFill} style={{ width: `${levelPercent}%` }} />
                                    </span>
                                </span>
                                <span>Lv.{intimacy.level + 1}</span>
                            </div>
                        </div>

                        <div className={styles.summary}>
                            <span>经验加成 <span className={styles.summaryValue}>+{intimacy.expBonusPercent}%</span></span>
                            <span>累计陪伴 <span className={styles.summaryValue}>
                                {fmtSeconds(intimacy.companionSeconds)}
                            </span></span>
                            <span>今日 <span className={styles.summaryValue}>
                                {fmtSeconds(intimacy.todayCompanionSeconds)} / {fmtSeconds(intimacy.dailyCompanionCapSeconds)}
                            </span></span>
                            <span>陪伴天数 <span className={styles.summaryValue}>{intimacy.companionDays}</span></span>
                            <span>连续 <span className={styles.summaryValue}>{intimacy.companionStreak} 天</span></span>
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>陪伴会话</p>
                            <p className={styles.blockDesc}>
                                开着页面一起待着也能涨亲密度：时长由服务端按会话结算，客户端只上报秒数
                            </p>
                            <div className={styles.sessionCard}>
                                <div className={styles.sessionMeta}>
                                    <span>状态：{companionOn ? '陪伴中' : session ? '已结束' : '未开始'}</span>
                                    {session ? (
                                        <>
                                            <span>会话 {session.status}</span>
                                            <span>本次计入 {fmtSeconds(session.creditedSeconds)}</span>
                                            <span>今日累计 {fmtSeconds(session.todayAcceptedSeconds)}</span>
                                            <span>今日亲密度 {session.todayGrantedPoints}/{session.dailyPointCap}</span>
                                        </>
                                    ) : null}
                                </div>
                                <div className={styles.sessionActions}>
                                    {companionOn ? (
                                        <CreamButton loading={busy === 'companion'} onClick={() => void stopCompanion()}>
                                            结束陪伴
                                        </CreamButton>
                                    ) : (
                                        <CreamButton loading={busy === 'companion'} onClick={() => void startCompanion()}>
                                            开始陪伴
                                        </CreamButton>
                                    )}
                                </div>
                            </div>
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>等级阶梯</p>
                            <div className={styles.ladder}>
                                {intimacy.levels.map(item => (
                                    <div
                                        key={item.level}
                                        className={`${styles.ladderItem} ${item.achieved ? styles.ladderAchieved : ''}`}
                                    >
                                        <span className={styles.ladderLevel}>Lv.{item.level}</span>
                                        {item.name}
                                        <span className={styles.ladderLevel}>{item.threshold}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </>
                )
            ) : null}

            {tab === 'diary' ? (
                diary === null ? <Spin /> : (
                    <>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>成长日记</p>
                            <p className={styles.blockDesc}>由宠物的大事自动生成，客户端不能代写</p>
                            {diary.length === 0 ? (
                                <p className={styles.empty}>还没有日记，陪它做点什么吧</p>
                            ) : diary.map(item => (
                                <div key={item.id} className={styles.diaryItem}>
                                    <div className={styles.diaryHead}>
                                        <span className={styles.diaryType}>{item.type}</span>
                                        {item.visibility !== 'PUBLIC' ? (
                                            <button type="button" style={{ border: 'none', background: 'none', padding: 0, cursor: 'pointer' }}
                                                title="点击设为公开"
                                                onClick={() => void toggleDiaryVisibility(item, 'PUBLIC')}>
                                                <CreamChip color="#8AA5BC">仅自己可见</CreamChip>
                                            </button>
                                        ) : (
                                            <button type="button" style={{ border: 'none', background: 'none', padding: 0, cursor: 'pointer' }}
                                                title="点击设为仅自己可见"
                                                onClick={() => void toggleDiaryVisibility(item, 'OWNER_ONLY')}>
                                                <CreamChip color="#7E9270">公开</CreamChip>
                                            </button>
                                        )}
                                        {item.assetIds && item.assetIds.length > 0 ? (
                                            <CreamChip color="#7E9270">含 {item.assetIds.length} 张照片</CreamChip>
                                        ) : null}
                                        <span>{item.createdAt.slice(5, 16).replace('T', ' ')}</span>
                                    </div>
                                    <p className={styles.diaryContent}>{item.content}</p>
                                    <div className={styles.diaryFoot}>
                                        {attachTarget === item.id ? (
                                            <>
                                                <label className={styles.filePick}>
                                                    <input
                                                        type="file"
                                                        accept="image/*"
                                                        hidden
                                                        onChange={event => {
                                                            const file = event.target.files?.[0]
                                                            event.target.value = ''
                                                            setAttachTarget(null)
                                                            if (file) {
                                                                void uploadPhoto(file, item.id)
                                                            }
                                                        }}
                                                    />
                                                    选择照片（挂到这条日记）
                                                </label>
                                                <CreamButton variant="ghost" onClick={() => setAttachTarget(null)}>
                                                    取消
                                                </CreamButton>
                                            </>
                                        ) : (
                                            <CreamButton variant="ghost" onClick={() => setAttachTarget(item.id)}>
                                                添加照片
                                            </CreamButton>
                                        )}
                                    </div>
                                </div>
                            ))}
                            {diaryHasMore ? (
                                <div className={styles.sessionActions}>
                                    <CreamButton variant="ghost" onClick={() => void loadDiary(false)}>
                                        加载更多
                                    </CreamButton>
                                </div>
                            ) : null}
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>相册</p>
                            <p className={styles.blockDesc}>
                                每只宠物 100 张；服务端暂未提供相册列表接口，这里只显示本次上传的
                            </p>
                            <div className={styles.sessionActions}>
                                <label className={styles.filePick}>
                                    <input
                                        type="file"
                                        accept="image/*"
                                        hidden
                                        onChange={event => {
                                            const file = event.target.files?.[0]
                                            event.target.value = ''
                                            if (file) {
                                                void uploadPhoto(file)
                                            }
                                        }}
                                    />
                                    上传照片
                                </label>
                                {busy === 'album' ? <Spin size="small" /> : null}
                            </div>
                            {assets.length === 0 ? (
                                <p className={styles.empty}>本次还没有上传</p>
                            ) : (
                                <div className={styles.albumGrid} style={{ marginTop: 10 }}>
                                    {assets.map(item => (
                                        <div key={item.id} className={styles.albumItem}>
                                            {item.previewUrl ? (
                                                <img className={styles.albumImg} src={item.previewUrl} alt="宠物相册照片" />
                                            ) : (
                                                <div className={styles.albumImg} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, color: '#888' }}>
                                                    {item.bindStatus === 'BINDING' ? '处理中…' : '待审核'}
                                                </div>
                                            )}
                                            <div className={styles.albumFoot}>
                                                <span>
                                                    {item.bindStatus === 'BINDING' ? '绑定中' : item.auditStatus === 'REJECTED' ? '未通过' : item.auditStatus === 'PENDING' ? '待审核' : '已通过'}
                                                    {item.diaryEntryId ? ` · 日记 #${item.diaryEntryId}` : ''}
                                                </span>
                                                {item.bindStatus === 'BINDING' ? (
                                                    <CreamButton
                                                        variant="ghost"
                                                        loading={busy === `album-retry:${item.id}`}
                                                        onClick={() => void retryBinding(item.id)}
                                                    >
                                                        重试
                                                    </CreamButton>
                                                ) : (
                                                    <CreamButton
                                                        variant="ghost"
                                                        loading={busy === `album-del:${item.id}`}
                                                        onClick={() => void removeAsset(item.id)}
                                                    >
                                                        删除
                                                    </CreamButton>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </>
                )
            ) : null}

            {tab === 'memory' ? (
                memories === null ? <Spin /> : (
                    <>
                        <div className={styles.summary}>
                            <span>共 <span className={styles.summaryValue}>{memories.length}</span> 条记忆</span>
                            <span>USER 编辑优先于 AUTO 抽取</span>
                        </div>

                        {memories.length === 0 ? (
                            <p className={styles.empty}>还没有记忆，多陪它聊聊就会慢慢积累</p>
                        ) : memories.map(item => (
                            <div key={item.id} className={styles.memoryItem}>
                                <div className={styles.memoryHead}>
                                    <span className={styles.memoryKey}>{item.memoryKey}</span>
                                    <CreamChip color="#D89AA0">
                                        {MEMORY_TYPE_LABEL[item.memoryType] ?? item.memoryType}
                                    </CreamChip>
                                    <CreamChip color={item.source === 'USER' ? '#7E9270' : '#8AA5BC'}>
                                        {item.source === 'USER' ? '我写的' : '自动抽取'}
                                    </CreamChip>
                                    <CreamChip color="#A97C50">重要度 {item.importance}</CreamChip>
                                    {item.enabled ? null : <CreamChip color="#9C8D7E">已停用</CreamChip>}
                                </div>
                                {editId === item.id ? (
                                    <>
                                        <div className={styles.inputRow}>
                                            <Input
                                                className={styles.input}
                                                value={editValue}
                                                onChange={event => setEditValue(event.target.value)}
                                                maxLength={200}
                                            />
                                        </div>
                                        <div className={styles.memoryActions}>
                                            <CreamButton
                                                loading={busy === `memory:${item.id}`}
                                                onClick={() => void saveMemory(item)}
                                            >
                                                保存
                                            </CreamButton>
                                            <CreamButton variant="ghost" onClick={() => setEditId(null)}>取消</CreamButton>
                                        </div>
                                    </>
                                ) : (
                                    <>
                                        <p className={styles.memoryValue}>{item.memoryValue}</p>
                                        <p className={styles.memoryMeta}>
                                            置信度 {item.confidence} · 更新于 {item.updatedAt.slice(5, 16).replace('T', ' ')}
                                        </p>
                                        <div className={styles.memoryActions}>
                                            <CreamButton
                                                variant="ghost"
                                                onClick={() => {
                                                    setEditId(item.id)
                                                    setEditValue(item.memoryValue)
                                                }}
                                            >
                                                编辑
                                            </CreamButton>
                                            <CreamButton
                                                variant="ghost"
                                                loading={busy === `memory-del:${item.id}`}
                                                onClick={() => void removeMemory(item.id)}
                                            >
                                                删除
                                            </CreamButton>
                                        </div>
                                    </>
                                )}
                            </div>
                        ))}

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>记忆开关</p>
                            <p className={styles.blockDesc}>
                                自动抽取与注入上下文相互独立（默认都开启）；服务端没有查询端点，
                                下面显示的是默认值，保存后即以本次设置为准
                            </p>
                            <div className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>自动抽取</p>
                                    <p className={styles.rowDesc}>从聊天里自动提炼记忆</p>
                                </div>
                                <div className={styles.picks}>
                                    <button
                                        type="button"
                                        className={`${styles.pick} ${extractDraft ? styles.pickActive : ''}`}
                                        onClick={() => setExtractDraft(true)}
                                    >
                                        开
                                    </button>
                                    <button
                                        type="button"
                                        className={`${styles.pick} ${!extractDraft ? styles.pickActive : ''}`}
                                        onClick={() => setExtractDraft(false)}
                                    >
                                        关
                                    </button>
                                </div>
                            </div>
                            <div className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>注入上下文</p>
                                    <p className={styles.rowDesc}>聊天时把记忆带进上下文</p>
                                </div>
                                <div className={styles.picks}>
                                    <button
                                        type="button"
                                        className={`${styles.pick} ${useDraft ? styles.pickActive : ''}`}
                                        onClick={() => setUseDraft(true)}
                                    >
                                        开
                                    </button>
                                    <button
                                        type="button"
                                        className={`${styles.pick} ${!useDraft ? styles.pickActive : ''}`}
                                        onClick={() => setUseDraft(false)}
                                    >
                                        关
                                    </button>
                                </div>
                            </div>
                            <div className={styles.sessionActions}>
                                <CreamButton
                                    loading={busy === 'memory-setting'}
                                    onClick={() => void toggleMemorySetting(extractDraft, useDraft)}
                                >
                                    保存记忆开关
                                </CreamButton>
                            </div>
                        </div>

                        {memories.length > 0 ? (
                            <div className={styles.block}>
                                <CreamButton
                                    variant="ghost"
                                    loading={busy === 'memory-clear'}
                                    onClick={() => clearAllMemories()}
                                >
                                    清空全部记忆
                                </CreamButton>
                            </div>
                        ) : null}
                    </>
                )
            ) : null}

            {tab === 'settings' ? (
                pref === null || onboarding === null ? <Spin /> : (
                    <>
                        <div className={styles.block}>
                            <p className={styles.blockTitle}>通知偏好</p>
                            <p className={styles.blockDesc}>只影响日常的主动问候推送</p>
                            <div className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>每日问候</p>
                                    <p className={styles.rowDesc}>宠物每天主动跟你打招呼</p>
                                </div>
                                <div className={styles.rowActions}>
                                    <CreamButton
                                        variant={pref.dailyGreetingEnabled ? undefined : 'ghost'}
                                        loading={busy === 'pref'}
                                        onClick={() => void togglePref({
                                            muteDailyGreeting: pref.muteDailyGreeting,
                                            dailyGreetingEnabled: !pref.dailyGreetingEnabled,
                                        })}
                                    >
                                        {pref.dailyGreetingEnabled ? '已开启' : '已关闭'}
                                    </CreamButton>
                                </div>
                            </div>
                            <div className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>免打扰</p>
                                    <p className={styles.rowDesc}>开启后不再推送问候（不影响其他提醒）</p>
                                </div>
                                <div className={styles.rowActions}>
                                    <CreamButton
                                        variant={pref.muteDailyGreeting ? undefined : 'ghost'}
                                        loading={busy === 'pref'}
                                        onClick={() => void togglePref({
                                            muteDailyGreeting: !pref.muteDailyGreeting,
                                            dailyGreetingEnabled: pref.dailyGreetingEnabled,
                                        })}
                                    >
                                        {pref.muteDailyGreeting ? '已免打扰' : '正常提醒'}
                                    </CreamButton>
                                </div>
                            </div>
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>新手引导</p>
                            <p className={styles.blockDesc}>
                                步骤由完成动作推进，客户端只能查看或跳过
                            </p>
                            <div className={styles.row}>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>
                                        进度 {onboarding.currentStep}/{onboarding.totalSteps}
                                        {onboarding.completed ? (
                                            <CreamChip color="#7E9270">已完成</CreamChip>
                                        ) : null}
                                    </p>
                                    <p className={styles.rowDesc}>
                                        {onboarding.skippable ? '可以跳过引导' : '当前步骤不可跳过'}
                                    </p>
                                </div>
                                <div className={styles.rowActions}>
                                    <CreamButton
                                        variant="ghost"
                                        disabled={!onboarding.skippable || onboarding.completed}
                                        loading={busy === 'onboarding'}
                                        onClick={() => void skipOnboarding()}
                                    >
                                        跳过引导
                                    </CreamButton>
                                </div>
                            </div>
                        </div>
                    </>
                )
            ) : null}
        </div>
    )
}
