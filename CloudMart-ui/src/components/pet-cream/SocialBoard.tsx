/**
 * 社交面板（三期）：好友 / 邻居 / 关系 / 留言墙。
 *
 * 后端语义（与 PetFriendServiceImpl / PetRelationServiceImpl / PetWallService 对齐）：
 * - 好友申请按 userId 发起；对方已向我申请时，再发起即互相确认；
 * - 关系申请按 toPetId + relType 发起，情侣独占（已有 1 段再申请会 409）；
 * - 关系解除只对 ACTIVE 生效——待对方确认的申请没有撤回接口，界面上不做假按钮；
 * - 留言墙：只有墙主人能回复自己墙上的一级留言，作者与墙主人都能删除，点赞是"再点取消"。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Input, Spin } from 'antd'
import {
    acceptPetFriend,
    acceptPetRelation,
    deletePetWallMessage,
    dissolvePetRelation,
    getPetFriendFeedUnreadCount,
    getPetFriends,
    getPetRelations,
    getPetWall,
    likePetWallMessage,
    listPetFriendFeed,
    listPetVisitNeighbors,
    markPetFriendFeedRead,
    postPetWallMessage,
    rejectPetFriend,
    rejectPetRelation,
    removePetFriend,
    replyPetWallMessage,
    requestPetFriend,
    requestPetRelation,
    visitNeighborPet,
    visitPetFriend,
    type PetFriendFeedItem,
    type PetFriendPanel,
    type PetRelationPanel,
    type PetVisitNeighbor,
    type PetWallMessage,
    type PetWallPage,
} from '@/api/pet'
import type { ApiResponse } from '@/types/api'
import { CreamButton, CreamChip, SPECIES_EMOJI } from './Cream'
import styles from './socialBoard.module.css'

type SocialTab = 'friends' | 'neighbors' | 'relations' | 'wall'

const SOCIAL_TABS: Array<{ key: SocialTab; label: string }> = [
    { key: 'friends', label: '好友' },
    { key: 'neighbors', label: '邻居' },
    { key: 'relations', label: '关系' },
    { key: 'wall', label: '留言墙' },
]

/** 关系类型与后端 PetRelationType 一致（情侣独占，其余每类上限 3） */
const REL_TYPES: Array<{ value: string; label: string; note: string }> = [
    { value: 'COUPLE', label: '情侣', note: '独占' },
    { value: 'BESTIE', label: '闺蜜', note: '至多 3' },
    { value: 'BROTHER', label: '兄弟', note: '至多 3' },
    { value: 'CONFIDANT', label: '死党', note: '至多 3' },
]

const FEED_LABEL: Record<string, string> = {
    LEVEL_UP: '升级',
    WORK_COMPLETED: '打工完成',
    STUDY_COMPLETED: '读书完成',
    BATTLE_WIN: '对战获胜',
}

export default function SocialBoard({ myPetId, onChanged }: {
    myPetId: number | string
    onChanged?: () => void
}) {
    const { message } = App.useApp()
    const [tab, setTab] = useState<SocialTab>('friends')
    const [busy, setBusy] = useState<string | null>(null)

    const [friends, setFriends] = useState<PetFriendPanel | null>(null)
    const [feed, setFeed] = useState<PetFriendFeedItem[] | null>(null)
    const [feedUnread, setFeedUnread] = useState(0)

    const [neighbors, setNeighbors] = useState<PetVisitNeighbor[] | null>(null)
    const [relations, setRelations] = useState<PetRelationPanel | null>(null)
    const [relTarget, setRelTarget] = useState<{ petId: number; petName: string } | null>(null)
    const [relType, setRelType] = useState<string>('BESTIE')
    const [relMessage, setRelMessage] = useState('')

    const [wall, setWall] = useState<PetWallPage | null>(null)
    const [wallPage, setWallPage] = useState(1)
    const [postDraft, setPostDraft] = useState('')
    const [replyDraft, setReplyDraft] = useState('')
    const [replyTarget, setReplyTarget] = useState<number | null>(null)

    /** 统一的忙碌态 + 失败提示包装：成功返回响应体，失败提示后返回响应体（success=false） */
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

    const loadFriends = useCallback(async () => {
        const { data: res } = await getPetFriends()
        if (res.success) {
            setFriends(res.data)
        }
    }, [])

    const loadFeed = useCallback(async () => {
        const [{ data: listRes }, { data: unreadRes }] = await Promise.all([
            listPetFriendFeed({ size: 20 }),
            getPetFriendFeedUnreadCount(),
        ])
        if (listRes.success) {
            setFeed(listRes.data)
        }
        if (unreadRes.success) {
            setFeedUnread(unreadRes.data)
        }
    }, [])

    const loadNeighbors = useCallback(async () => {
        const { data: res } = await listPetVisitNeighbors()
        if (res.success) {
            setNeighbors(res.data)
        }
    }, [])

    const loadRelations = useCallback(async () => {
        const { data: res } = await getPetRelations()
        if (res.success) {
            setRelations(res.data)
        }
    }, [])

    const loadWall = useCallback(async (page: number) => {
        const { data: res } = await getPetWall(myPetId, page, 10)
        if (res.success) {
            setWall(res.data)
            setWallPage(res.data.page)
        }
    }, [myPetId])

    useEffect(() => {
        if (tab === 'friends' && friends === null) {
            void loadFriends()
            void loadFeed()
        }
        if (tab === 'neighbors' && neighbors === null) {
            void loadNeighbors()
        }
        if (tab === 'relations' && relations === null) {
            void loadRelations()
        }
        if (tab === 'wall' && wall === null) {
            void loadWall(1)
        }
    }, [friends, loadFeed, loadFriends, loadNeighbors, loadRelations, loadWall,
        neighbors, relations, tab, wall])

    // ---------------- 好友 ----------------

    const befriend = useCallback(async (userId: number | string) => {
        const res = await run(`friend:${userId}`, () => requestPetFriend(userId))
        if (res?.success) {
            message.success('好友申请已发出')
            await loadFriends()
        }
    }, [loadFriends, message, run])

    const handleFriend = useCallback(async (userId: number, accept: boolean) => {
        const res = await run(`${accept ? 'accept' : 'reject'}:${userId}`,
            () => (accept ? acceptPetFriend(userId) : rejectPetFriend(userId)))
        if (res?.success) {
            message.success(accept ? '已加为好友' : '已拒绝申请')
            await loadFriends()
        }
    }, [loadFriends, message, run])

    const unfriend = useCallback(async (userId: number) => {
        const res = await run(`remove:${userId}`, () => removePetFriend(userId))
        if (res?.success) {
            message.success('已解除好友')
            await loadFriends()
        }
    }, [loadFriends, message, run])

    const visitFriend = useCallback(async (userId: number) => {
        const res = await run(`visit:${userId}`, () => visitPetFriend(userId))
        if (res?.success) {
            message.success('串门完成，两只宠物都更开心了')
            await loadFriends()
            onChanged?.()
        }
    }, [loadFriends, message, onChanged, run])

    const readFeed = useCallback(async () => {
        const res = await run('feed', () => markPetFriendFeedRead())
        if (res?.success) {
            setFeedUnread(0)
        }
    }, [run])

    // ---------------- 邻居 ----------------

    const visitNeighbor = useCallback(async (petId: number | string) => {
        const res = await run(`neighbor:${petId}`, () => visitNeighborPet(petId))
        if (res?.success) {
            message.success('串门成功')
            await loadNeighbors()
            onChanged?.()
        }
    }, [loadNeighbors, message, onChanged, run])

    // ---------------- 关系 ----------------

    const sendRelation = useCallback(async () => {
        if (!relTarget) {
            return
        }
        const res = await run('rel-send', () => requestPetRelation({
            toPetId: relTarget.petId,
            relType,
            message: relMessage.trim() || undefined,
        }))
        if (res?.success) {
            message.success('关系申请已发出')
            setRelTarget(null)
            setRelMessage('')
            await loadRelations()
        }
    }, [loadRelations, message, relMessage, relTarget, relType, run])

    const handleRelation = useCallback(async (relationId: number, accept: boolean) => {
        const res = await run(`rel-${accept ? 'accept' : 'reject'}:${relationId}`,
            () => (accept ? acceptPetRelation(relationId) : rejectPetRelation(relationId)))
        if (res?.success) {
            message.success(accept ? '关系已建立' : '已拒绝')
            await loadRelations()
        }
    }, [loadRelations, message, run])

    const dissolveRelation = useCallback(async (relationId: number) => {
        const res = await run(`rel-dissolve:${relationId}`, () => dissolvePetRelation(relationId))
        if (res?.success) {
            message.success('已解除关系')
            await loadRelations()
        }
    }, [loadRelations, message, run])

    // ---------------- 留言墙 ----------------

    const postWall = useCallback(async () => {
        const content = postDraft.trim()
        if (!content) {
            message.warning('写点什么再贴上去吧')
            return
        }
        const res = await run('wall-post', () => postPetWallMessage({ petId: myPetId, content }))
        if (res?.success) {
            setPostDraft('')
            await loadWall(1)
        }
    }, [loadWall, message, myPetId, postDraft, run])

    const replyWall = useCallback(async (messageId: number) => {
        const content = replyDraft.trim()
        if (!content) {
            message.warning('回复不能为空')
            return
        }
        const res = await run(`wall-reply:${messageId}`,
            () => replyPetWallMessage({ messageId, content }))
        if (res?.success) {
            setReplyDraft('')
            setReplyTarget(null)
            await loadWall(wallPage)
        }
    }, [loadWall, message, replyDraft, run, wallPage])

    const removeWall = useCallback(async (messageId: number) => {
        const res = await run(`wall-del:${messageId}`, () => deletePetWallMessage(messageId))
        if (res?.success) {
            await loadWall(wallPage)
        }
    }, [loadWall, run, wallPage])

    const likeWall = useCallback(async (messageId: number) => {
        const res = await run(`wall-like:${messageId}`, () => likePetWallMessage(messageId))
        if (res?.success && wall) {
            const { liked, likeCount } = res.data
            const patch = (list: PetWallMessage[]): PetWallMessage[] => list.map(item => (
                item.id === messageId
                    ? { ...item, liked, likeCount }
                    : { ...item, replies: patch(item.replies) }
            ))
            setWall({ ...wall, messages: patch(wall.messages) })
        }
    }, [run, wall])

    const wallPages = wall ? Math.max(1, Math.ceil(wall.total / Math.max(1, wall.size))) : 1

    return (
        <div>
            <div className={styles.tabs}>
                {SOCIAL_TABS.map(item => (
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

            {tab === 'friends' ? (
                friends === null ? <Spin /> : (
                    <>
                        <div className={styles.summary}>
                            <span>好友 <span className={styles.summaryValue}>
                                {friends.friends.length}/{friends.maxFriends}
                            </span></span>
                            <span>今日互访 <span className={styles.summaryValue}>
                                {friends.todayVisitCount}/{friends.dailyVisitLimit}
                            </span></span>
                            <span>剩余互访 <span className={styles.summaryValue}>
                                {friends.remainingVisits}
                            </span></span>
                            <span>动态未读 <span className={styles.summaryValue}>{feedUnread}</span></span>
                        </div>

                        {friends.incoming.length > 0 ? (
                            <div className={styles.block}>
                                <p className={styles.blockTitle}>收到的申请</p>
                                {friends.incoming.map(item => (
                                    <div key={item.userId} className={styles.row}>
                                        <span className={styles.rowIcon}>
                                            {SPECIES_EMOJI[item.species ?? ''] ?? '🐾'}
                                        </span>
                                        <div className={styles.rowMain}>
                                            <p className={styles.rowTitle}>{item.nickname}</p>
                                            <p className={styles.rowDesc}>
                                                {item.petName ? `宠物：${item.petName}` : '还没有宠物'}
                                            </p>
                                        </div>
                                        <div className={styles.rowActions}>
                                            <CreamButton
                                                loading={busy === `accept:${item.userId}`}
                                                onClick={() => void handleFriend(item.userId, true)}
                                            >
                                                同意
                                            </CreamButton>
                                            <CreamButton
                                                variant="ghost"
                                                loading={busy === `reject:${item.userId}`}
                                                onClick={() => void handleFriend(item.userId, false)}
                                            >
                                                拒绝
                                            </CreamButton>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : null}

                        {friends.outgoing.length > 0 ? (
                            <div className={styles.block}>
                                <p className={styles.blockTitle}>我发出的申请</p>
                                {friends.outgoing.map(item => (
                                    <div key={item.userId} className={styles.row}>
                                        <span className={styles.rowIcon}>
                                            {SPECIES_EMOJI[item.species ?? ''] ?? '🐾'}
                                        </span>
                                        <div className={styles.rowMain}>
                                            <p className={styles.rowTitle}>
                                                {item.nickname}
                                                <CreamChip color="#A97C50">待确认</CreamChip>
                                            </p>
                                        </div>
                                        <div className={styles.rowActions}>
                                            <CreamButton
                                                variant="ghost"
                                                loading={busy === `remove:${item.userId}`}
                                                onClick={() => void unfriend(item.userId)}
                                            >
                                                撤回
                                            </CreamButton>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : null}

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>好友</p>
                            {friends.friends.length === 0 ? (
                                <p className={styles.empty}>还没有好友，去「邻居」里加一个吧</p>
                            ) : friends.friends.map(item => (
                                <div key={item.userId} className={styles.row}>
                                    <span className={styles.rowIcon}>
                                        {SPECIES_EMOJI[item.species ?? ''] ?? '🐾'}
                                    </span>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            {item.nickname}
                                            {item.petName ? (
                                                <CreamChip color="#A97C50">{item.petName}</CreamChip>
                                            ) : null}
                                            {item.visitedToday ? (
                                                <CreamChip color="#7E9270">今日已互访</CreamChip>
                                            ) : null}
                                        </p>
                                        <p className={styles.rowDesc}>串门 {item.visitCount} 次</p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            loading={busy === `visit:${item.userId}`}
                                            onClick={() => void visitFriend(item.userId)}
                                        >
                                            互访
                                        </CreamButton>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `remove:${item.userId}`}
                                            onClick={() => void unfriend(item.userId)}
                                        >
                                            删除
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>好友动态</p>
                            <p className={styles.blockDesc}>升级、打工读书完成、对战获胜会出现在这里</p>
                            {feed === null ? <Spin /> : feed.length === 0 ? (
                                <p className={styles.empty}>暂无动态</p>
                            ) : feed.map(item => (
                                <div key={item.feedId} className={styles.feedRow}>
                                    <span className={styles.feedTag}>
                                        {FEED_LABEL[item.eventType] ?? item.eventType}
                                    </span>
                                    <span className={styles.feedText}>{item.text}</span>
                                    <span className={styles.feedTime}>
                                        {item.createdAt ? item.createdAt.slice(5, 16).replace('T', ' ') : ''}
                                    </span>
                                </div>
                            ))}
                            {feedUnread > 0 ? (
                                <div className={styles.inputRow}>
                                    <CreamButton variant="ghost" loading={busy === 'feed'} onClick={() => void readFeed()}>
                                        标记 {feedUnread} 条已读
                                    </CreamButton>
                                </div>
                            ) : null}
                        </div>
                    </>
                )
            ) : null}

            {tab === 'neighbors' ? (
                neighbors === null ? <Spin /> : neighbors.length === 0 ? (
                    <p className={styles.empty}>还没有邻居开放家园</p>
                ) : (
                    <div className={styles.block}>
                        <p className={styles.blockTitle}>邻居</p>
                        <p className={styles.blockDesc}>串门让两只宠物都得好处；同一邻居每天只能串一次</p>
                        {neighbors.map(item => (
                            <div key={String(item.petId)} className={styles.row}>
                                <span className={styles.rowIcon}>
                                    {SPECIES_EMOJI[item.species] ?? '🐾'}
                                </span>
                                <div className={styles.rowMain}>
                                    <p className={styles.rowTitle}>
                                        {item.name}
                                        <CreamChip color="#A97C50">Lv.{item.level}</CreamChip>
                                        {item.visitedToday ? (
                                            <CreamChip color="#7E9270">今日已串门</CreamChip>
                                        ) : null}
                                    </p>
                                    <p className={styles.rowDesc}>主人：{item.ownerNickname}</p>
                                </div>
                                <div className={styles.rowActions}>
                                    <CreamButton
                                        loading={busy === `neighbor:${item.petId}`}
                                        onClick={() => void visitNeighbor(item.petId)}
                                    >
                                        串门
                                    </CreamButton>
                                    <CreamButton
                                        variant="ghost"
                                        loading={busy === `friend:${item.ownerUserId}`}
                                        onClick={() => void befriend(item.ownerUserId)}
                                    >
                                        加好友
                                    </CreamButton>
                                </div>
                            </div>
                        ))}
                    </div>
                )
            ) : null}

            {tab === 'relations' ? (
                relations === null ? <Spin /> : (
                    <>
                        <div className={styles.summary}>
                            {relations.limits.map(limit => (
                                <span key={limit.relType}>
                                    {limit.label} <span className={styles.summaryValue}>
                                        {limit.current}/{limit.max}
                                    </span>
                                    {limit.exclusive ? '（独占）' : ''}
                                </span>
                            ))}
                        </div>

                        {relations.incoming.length > 0 ? (
                            <div className={styles.block}>
                                <p className={styles.blockTitle}>收到的关系申请</p>
                                {relations.incoming.map(item => (
                                    <div key={String(item.id)} className={styles.row}>
                                        <span className={styles.rowIcon}>
                                            {SPECIES_EMOJI[item.species] ?? '🐾'}
                                        </span>
                                        <div className={styles.rowMain}>
                                            <p className={styles.rowTitle}>
                                                {item.petName}
                                                <CreamChip color="#D89AA0">{item.relTypeLabel ?? item.relType}</CreamChip>
                                            </p>
                                            <p className={styles.rowDesc}>
                                                主人：{item.ownerNickname}
                                                {item.message ? ` · “${item.message}”` : ''}
                                            </p>
                                        </div>
                                        <div className={styles.rowActions}>
                                            <CreamButton
                                                loading={busy === `rel-accept:${item.id}`}
                                                onClick={() => void handleRelation(Number(item.id), true)}
                                            >
                                                同意
                                            </CreamButton>
                                            <CreamButton
                                                variant="ghost"
                                                loading={busy === `rel-reject:${item.id}`}
                                                onClick={() => void handleRelation(Number(item.id), false)}
                                            >
                                                拒绝
                                            </CreamButton>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : null}

                        {relations.outgoing.length > 0 ? (
                            <div className={styles.block}>
                                <p className={styles.blockTitle}>我发出的申请</p>
                                {relations.outgoing.map(item => (
                                    <div key={String(item.id)} className={styles.row}>
                                        <span className={styles.rowIcon}>
                                            {SPECIES_EMOJI[item.species] ?? '🐾'}
                                        </span>
                                        <div className={styles.rowMain}>
                                            <p className={styles.rowTitle}>
                                                {item.petName}
                                                <CreamChip color="#A97C50">{item.relTypeLabel ?? item.relType}</CreamChip>
                                                <CreamChip color="#8AA5BC">待对方确认</CreamChip>
                                            </p>
                                        </div>
                                    </div>
                                ))}
                                <p className={styles.note}>
                                    待确认的申请没有撤回接口，等对方处理或联系对方拒绝。
                                </p>
                            </div>
                        ) : null}

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>已建立的关系</p>
                            {relations.relations.length === 0 ? (
                                <p className={styles.empty}>还没有关系，去下面候选里发起一个吧</p>
                            ) : relations.relations.map(item => (
                                <div key={String(item.id)} className={styles.row}>
                                    <span className={styles.rowIcon}>
                                        {SPECIES_EMOJI[item.species] ?? '🐾'}
                                    </span>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            {item.petName}
                                            <CreamChip color="#D89AA0">{item.relTypeLabel ?? item.relType}</CreamChip>
                                            <CreamChip color="#A97C50">
                                                亲密度 {item.intimacy}（{item.intimacyLevelName ?? `Lv.${item.intimacyLevel}`}）
                                            </CreamChip>
                                        </p>
                                        <p className={styles.rowDesc}>
                                            主人：{item.ownerNickname}
                                            {item.intimacyToNext > 0 ? ` · 再 ${item.intimacyToNext} 点升级` : ''}
                                        </p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `rel-dissolve:${item.id}`}
                                            onClick={() => void dissolveRelation(Number(item.id))}
                                        >
                                            解除
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className={styles.block}>
                            <p className={styles.blockTitle}>候选宠物</p>
                            <p className={styles.blockDesc}>选一只发起关系申请；情侣是独占关系</p>
                            {relations.candidates.length === 0 ? (
                                <p className={styles.empty}>暂时没有可申请的宠物</p>
                            ) : relations.candidates.map(item => (
                                <div key={item.petId} className={styles.row}>
                                    <span className={styles.rowIcon}>
                                        {SPECIES_EMOJI[item.species] ?? '🐾'}
                                    </span>
                                    <div className={styles.rowMain}>
                                        <p className={styles.rowTitle}>
                                            {item.petName}
                                            <CreamChip color="#A97C50">Lv.{item.level}</CreamChip>
                                        </p>
                                        <p className={styles.rowDesc}>主人：{item.ownerNickname}</p>
                                    </div>
                                    <div className={styles.rowActions}>
                                        <CreamButton
                                            variant="ghost"
                                            onClick={() => setRelTarget({ petId: item.petId, petName: item.petName })}
                                        >
                                            申请
                                        </CreamButton>
                                    </div>
                                </div>
                            ))}
                            {relTarget ? (
                                <div className={styles.block}>
                                    <p className={styles.blockTitle}>向 {relTarget.petName} 申请</p>
                                    <div className={styles.picks}>
                                        {REL_TYPES.map(item => (
                                            <button
                                                key={item.value}
                                                type="button"
                                                className={`${styles.pick} ${relType === item.value ? styles.pickActive : ''}`}
                                                onClick={() => setRelType(item.value)}
                                            >
                                                {item.label}
                                                <span className={styles.pickNote}> · {item.note}</span>
                                            </button>
                                        ))}
                                    </div>
                                    <div className={styles.inputRow}>
                                        <Input
                                            className={styles.input}
                                            value={relMessage}
                                            onChange={event => setRelMessage(event.target.value)}
                                            placeholder="附一句话（可留空）"
                                            maxLength={40}
                                        />
                                        <CreamButton loading={busy === 'rel-send'} onClick={() => void sendRelation()}>
                                            发送
                                        </CreamButton>
                                        <CreamButton variant="ghost" onClick={() => setRelTarget(null)}>取消</CreamButton>
                                    </div>
                                </div>
                            ) : null}
                        </div>
                    </>
                )
            ) : null}

            {tab === 'wall' ? (
                wall === null ? <Spin /> : (
                    <>
                        <div className={styles.summary}>
                            <span>{wall.petName} 的留言墙 · 共 {wall.total} 条</span>
                            <span>每日上限 <span className={styles.summaryValue}>{wall.dailyPostLimit}</span></span>
                            {wall.roomPublic ? null : <span>房间未公开，仅好友可见</span>}
                        </div>

                        <div className={styles.inputRow}>
                            <Input
                                className={styles.input}
                                value={postDraft}
                                onChange={event => setPostDraft(event.target.value)}
                                placeholder="给自家墙上贴一句话…"
                                maxLength={120}
                            />
                            <CreamButton loading={busy === 'wall-post'} onClick={() => void postWall()}>张贴</CreamButton>
                        </div>

                        <div className={styles.block}>
                            {wall.messages.length === 0 ? (
                                <p className={styles.empty}>墙上还空着，贴第一条吧</p>
                            ) : wall.messages.map(item => (
                                <div key={item.id} className={styles.wallCard}>
                                    <div className={styles.wallHead}>
                                        <span className={styles.wallWho}>
                                            {item.authorPetName ?? item.authorNickname}
                                        </span>
                                        {item.mood ? <CreamChip color="#8AA5BC">{item.mood}</CreamChip> : null}
                                        <span className={styles.wallTime}>{item.createdAt.slice(5, 16).replace('T', ' ')}</span>
                                    </div>
                                    <p className={styles.wallContent}>{item.content}</p>
                                    <div className={styles.wallFoot}>
                                        <CreamButton
                                            variant="ghost"
                                            loading={busy === `wall-like:${item.id}`}
                                            onClick={() => void likeWall(item.id)}
                                        >
                                            {item.liked ? '取消赞' : '点赞'} {item.likeCount}
                                        </CreamButton>
                                        {item.owner && item.parentId === null ? (
                                            <CreamButton
                                                variant="ghost"
                                                onClick={() => setReplyTarget(replyTarget === item.id ? null : item.id)}
                                            >
                                                回复
                                            </CreamButton>
                                        ) : null}
                                        {item.mine || item.owner ? (
                                            <CreamButton
                                                variant="ghost"
                                                loading={busy === `wall-del:${item.id}`}
                                                onClick={() => void removeWall(item.id)}
                                            >
                                                删除
                                            </CreamButton>
                                        ) : null}
                                    </div>
                                    {replyTarget === item.id ? (
                                        <div className={styles.inputRow}>
                                            <Input
                                                className={styles.input}
                                                value={replyDraft}
                                                onChange={event => setReplyDraft(event.target.value)}
                                                placeholder="主人回复…"
                                                maxLength={120}
                                            />
                                            <CreamButton
                                                loading={busy === `wall-reply:${item.id}`}
                                                onClick={() => void replyWall(item.id)}
                                            >
                                                回复
                                            </CreamButton>
                                        </div>
                                    ) : null}
                                    {item.replies.length > 0 ? (
                                        <div className={styles.wallReplies}>
                                            {item.replies.map(reply => (
                                                <p key={reply.id} className={styles.wallReply}>
                                                    <span className={styles.wallReplyWho}>
                                                        {reply.ownerReply ? '主人' : reply.authorNickname}：
                                                    </span>
                                                    {reply.content}
                                                </p>
                                            ))}
                                        </div>
                                    ) : null}
                                </div>
                            ))}
                        </div>

                        {wallPages > 1 ? (
                            <div className={styles.pager}>
                                <CreamButton
                                    variant="ghost"
                                    disabled={wallPage <= 1}
                                    onClick={() => void loadWall(wallPage - 1)}
                                >
                                    上一页
                                </CreamButton>
                                <span>{wallPage} / {wallPages}</span>
                                <CreamButton
                                    variant="ghost"
                                    disabled={wallPage >= wallPages}
                                    onClick={() => void loadWall(wallPage + 1)}
                                >
                                    下一页
                                </CreamButton>
                            </div>
                        ) : null}
                    </>
                )
            ) : null}
        </div>
    )
}
