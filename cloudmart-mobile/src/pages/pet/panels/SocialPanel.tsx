import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Input, Text, View } from '@tarojs/components'
import {
  petApi,
  petCompanionApi,
  type PetFriendFeedItem,
  type PetFriendPanel,
  PetInfo,
  PetRelationPanel,
  PetWallPage,
} from '@/api/pet'
import { CARE_ERROR_HINT } from './shared'
import styles from '../index.module.scss'
import cream from '@/components/pet-cream/pet-cream.module.scss'

/** 社交面板（三期）：关系/好友/留言墙/动态四个子 Tab（P2-4 拆出；F3 增加动态） */
export function SocialPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const [tab, setTab] = useState<'relation' | 'friend' | 'wall' | 'feed' | 'safety'>('relation')
  const [feed, setFeed] = useState<PetFriendFeedItem[] | null>(null)
  const [feedLoadingMore, setFeedLoadingMore] = useState(false)
  const [relations, setRelations] = useState<PetRelationPanel | null>(null)
  const [friends, setFriends] = useState<PetFriendPanel | null>(null)
  const [wall, setWall] = useState<PetWallPage | null>(null)
  const [wallInput, setWallInput] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyInput, setReplyInput] = useState('')
  const [tip, setTip] = useState<string | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  // F3 动态未读数（页签角标；打开动态页签即自动推进水位）
  const [feedUnread, setFeedUnread] = useState(0)
  // B14 屏蔽与举报（名单接口只返回 userId 数组；举报 targetType 白名单 WALL_MESSAGE/BOTTLE_CONTENT/NICKNAME）
  const [blocks, setBlocks] = useState<number[]>([])
  const [blockInput, setBlockInput] = useState('')
  const [reportType, setReportType] = useState('WALL_MESSAGE')
  const [reportTargetId, setReportTargetId] = useState('')
  const [reportReason, setReportReason] = useState('')

  const loadActive = useCallback(async () => {
    try {
      if (tab === 'relation') {
        const { data: res } = await petApi.getRelations()
        if (res.success && res.data) {
          setRelations(res.data)
        }
      } else if (tab === 'friend') {
        const { data: res } = await petApi.getFriends()
        if (res.success && res.data) {
          setFriends(res.data)
        }
      } else if (tab === 'feed') {
        const { data: res } = await petApi.getFriendFeed({ size: 20 })
        if (res.success) {
          setFeed(res.data || [])
        }
        // 打开即推进已读水位（幂等，失败静默）
        void petApi.markFriendFeedRead().catch(() => undefined)
      } else if (tab === 'safety') {
        const { data: res } = await petCompanionApi.listBlocks()
        if (res.success) {
          setBlocks(res.data || [])
        }
      } else {
        const { data: res } = await petApi.getWall(pet.petId, 1, 10)
        if (res.success && res.data) {
          setWall(res.data)
        }
      }
    } catch {
      // 拦截器已提示
    }
  }, [tab, pet.petId])

  useEffect(() => {
    void loadActive()
  }, [loadActive])

  // 动态未读数（进面板拉一次；打开动态页签会自动推进水位）
  useEffect(() => {
    petApi.getFriendFeedUnread().then(({ data: res }) => {
      if (res.success) setFeedUnread(Number(res.data ?? 0))
    }).catch(() => undefined)
  }, [])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await loadActive()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  /** B14 拉黑：幂等；屏蔽后双向限制新增互动 */
  const doBlock = async () => {
    const target = blockInput.trim()
    if (!target) {
      setTip('先填要拉黑的用户 ID')
      return
    }
    await run('block', () => petCompanionApi.blockUser(target), '已拉黑')
    setBlockInput('')
  }

  const doUnblock = async (userId: number) => {
    await run(`unblock-${userId}`, () => petCompanionApi.unblockUser(userId), '已解除拉黑')
  }

  /** B14 举报：进入管理员处理队列 */
  const doReport = async () => {
    const targetId = reportTargetId.trim()
    const reason = reportReason.trim()
    if (!targetId || !reason) {
      setTip('目标 ID 和理由都要填')
      return
    }
    await run('report', () => petCompanionApi.reportTarget({ targetType: reportType, targetId, reason }), '已提交，管理员会处理')
    setReportTargetId('')
    setReportReason('')
  }

  /** N06 邀请好友协作（双方各贡献满 3 个业务日即可完成领奖） */
  const inviteCooperation = async (userId: number) => {
    await run(`coop-${userId}`, () => petApi.createCooperation(userId), '协作邀请已发出')
  }

  return (
    <View>
      <View className={cream.metaRow}>
        {([
          ['relation', '💞 关系'],
          ['friend', '🫂 好友'],
          ['wall', '📝 留言墙'],
          ['feed', feedUnread > 0 ? `📣 动态(${feedUnread})` : '📣 动态'],
          ['safety', '🛡️ 安全'],
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

      {tab === 'relation' && relations && (
        <View>
          <Text className={styles.jobMeta}>
            {relations.limits.map((limit) => `${limit.label} ${limit.current}/${limit.max}`).join(' · ')}
          </Text>
          <View className={styles.jobList}>
            {relations.incoming.map((item) => (
              <View key={String(item.id)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.petName}（{item.ownerNickname}）
                  </Text>
                  <Text className={styles.jobMeta}>想成为{item.relTypeLabel}{item.message ? `：「${item.message}」` : ''}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `a-${item.id}`}
                  onClick={() => run(`a-${item.id}`, () => petApi.acceptRelation(item.id as number), '关系建立啦！')}
                >
                  同意
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `r-${item.id}`}
                  onClick={() => run(`r-${item.id}`, () => petApi.rejectRelation(item.id as number), '已拒绝')}
                >
                  拒绝
                </Button>
              </View>
            ))}
            {relations.relations.map((item) => (
              <View key={String(item.id)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.petName} · {item.relTypeLabel}
                  </Text>
                  <Text className={styles.jobMeta}>
                    {item.ownerNickname} · 亲密度 {item.intimacy}（{item.intimacyLevelName}）
                  </Text>
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `d-${item.id}`}
                  onClick={() => run(`d-${item.id}`, () => petApi.dissolveRelation(item.id as number), '已解除关系')}
                >
                  解除
                </Button>
              </View>
            ))}
          </View>
          {relations.candidates.length > 0 && <Text className={styles.sectionTitle}>可以认识的宠物</Text>}
          <View className={styles.jobList}>
            {relations.candidates.map((candidate) => (
              <View key={candidate.petId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {candidate.petName}（Lv.{candidate.level} · {candidate.ownerNickname}）
                  </Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `cr-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `cr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'COUPLE' }),
                      '申请已发出～',
                    )
                  }
                >
                  情侣
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `br-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `br-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'BESTIE' }),
                      '申请已发出～',
                    )
                  }
                >
                  闺蜜
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fr-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `fr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'CONFIDANT' }),
                      '申请已发出～',
                    )
                  }
                >
                  死党
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'friend' && friends && (
        <View>
          <Text className={styles.jobMeta}>
            今日互访 {friends.todayVisitCount}/{friends.dailyVisitLimit} · 好友上限 {friends.maxFriends}
          </Text>
          <View className={styles.jobList}>
            {friends.incoming.map((item) => (
              <View key={item.userId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>{item.nickname}</Text>
                  <Text className={styles.jobMeta}>{item.petName ?? '还没有宠物'}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `fa-${item.userId}`}
                  onClick={() => run(`fa-${item.userId}`, () => petApi.acceptFriend(item.userId), '成为好友啦！')}
                >
                  同意
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fj-${item.userId}`}
                  onClick={() => run(`fj-${item.userId}`, () => petApi.rejectFriend(item.userId), '已拒绝')}
                >
                  拒绝
                </Button>
              </View>
            ))}
            {friends.friends.map((item) => (
              <View key={item.userId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.nickname} · {item.petName ?? '—'}
                  </Text>
                  <Text className={styles.jobMeta}>互访 {item.visitCount} 次</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `fv-${item.userId}`}
                  onClick={() =>
                    run(
                      `fv-${item.userId}`,
                      async () => {
                        const res = await petApi.visitFriend(item.userId)
                        if (res.data.success && res.data.data) {
                          setTip(res.data.data.message)
                        }
                        return res
                      },
                      '互访成功！',
                    )
                  }
                >
                  去互访
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `coop-${item.userId}`}
                  onClick={() => inviteCooperation(item.userId)}
                >
                  邀请协作
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fd-${item.userId}`}
                  onClick={() => run(`fd-${item.userId}`, () => petApi.removeFriend(item.userId), '已删除好友')}
                >
                  删除
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'feed' && (
        <View>
          {feed !== null && feed.length === 0 && (
            <Text className={styles.tip}>还没有好友动态～好友升级、打工归来、对战获胜时会出现在这里</Text>
          )}
          {feed?.map((item) => (
            <View key={String(item.feedId)} className={styles.rankRow}>
              <Text className={styles.rankNo}>
                {item.eventType === 'LEVEL_UP'
                  ? '⬆️'
                  : item.eventType === 'BATTLE_WIN'
                    ? '⚔️'
                    : item.eventType === 'STUDY_COMPLETED'
                      ? '📖'
                      : '💼'}
              </Text>
              <Text className={styles.rankName}>{item.text}</Text>
              <Text className={styles.rankValue}>{item.createdAt?.slice(5, 10) ?? ''}</Text>
            </View>
          ))}
          {feed !== null && feed.length > 0 && (
            <Button
              className={styles.miniBtnGhost}
              disabled={feedLoadingMore}
              onClick={async () => {
                const last = feed[feed.length - 1]
                if (!last) return
                setFeedLoadingMore(true)
                try {
                  const { data: res } = await petApi.getFriendFeed({ beforeId: last.feedId, size: 20 })
                  if (res.success) {
                    const more = res.data || []
                    setFeed(more.length > 0 ? [...feed, ...more] : feed)
                  }
                } catch {
                  // 拦截器已提示
                } finally {
                  setFeedLoadingMore(false)
                }
              }}
            >
              {feedLoadingMore ? '加载中…' : '加载更早的动态'}
            </Button>
          )}
        </View>
      )}

      {tab === 'wall' && wall && (
        <View>
          <Text className={styles.jobMeta}>
            {wall.petName} 的留言墙 · {wall.ownerNickname} · 共 {wall.total} 条（每日可留言 {wall.dailyPostLimit} 条）
          </Text>
          <View className={styles.actionRow}>
            <Input
              className={styles.nameInput}
              value={wallInput}
              maxlength={120}
              placeholder="写一句留言吧"
              onInput={(event) => setWallInput(event.detail.value)}
            />
            <Button
              className={styles.miniBtn}
              disabled={pending === 'post'}
              onClick={() =>
                run(
                  'post',
                  async () => {
                    const res = await petApi.postWallMessage({ petId: pet.petId, content: wallInput })
                    if (res.data.success) {
                      setWallInput('')
                    }
                    return res
                  },
                  '留言成功！',
                )
              }
            >
              留言
            </Button>
          </View>
          <View className={styles.jobList}>
            {wall.messages.map((item) => (
              <View key={item.id} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobMeta}>
                    {item.authorNickname}
                    {item.ownerReply ? '（我的回复）' : ''} · {item.createdAt?.slice(5, 16).replace('T', ' ')}
                  </Text>
                  <Text className={styles.jobName}>{item.content}</Text>
                  {item.replies.map((reply) => (
                    <Text key={reply.id} className={styles.jobMeta}>
                      ↳ {reply.authorNickname}：{reply.content}
                    </Text>
                  ))}
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `wl-${item.id}`}
                  onClick={() => run(`wl-${item.id}`, () => petApi.likeWallMessage(item.id), '已更新点赞')}
                >
                  {item.liked ? '取消赞' : '点赞'} {item.likeCount}
                </Button>
                {item.owner && item.parentId === null && (
                  <Button
                    className={styles.miniBtnGhost}
                    onClick={() => {
                      setReplyTo(replyTo === item.id ? null : item.id)
                      setReplyInput('')
                    }}
                  >
                    回复
                  </Button>
                )}
                {(item.mine || item.owner) && (
                  <Button
                    className={styles.miniBtnGhost}
                    disabled={pending === `wd-${item.id}`}
                    onClick={() => run(`wd-${item.id}`, () => petApi.deleteWallMessage(item.id), '已删除')}
                  >
                    删除
                  </Button>
                )}
              </View>
            ))}
          </View>
          {replyTo !== null && (
            <View className={styles.actionRow}>
              <Input
                className={styles.nameInput}
                value={replyInput}
                maxlength={120}
                placeholder="回复这条留言"
                onInput={(event) => setReplyInput(event.detail.value)}
              />
              <Button
                className={styles.miniBtn}
                disabled={pending === `wr-${replyTo}`}
                onClick={() =>
                  run(
                    `wr-${replyTo}`,
                    async () => {
                      const res = await petApi.replyWallMessage({ messageId: replyTo, content: replyInput })
                      if (res.data.success) {
                        setReplyTo(null)
                      }
                      return res
                    },
                    '回复成功！',
                  )
                }
              >
                发送
              </Button>
            </View>
          )}
        </View>
      )}
      {tab === 'safety' && (
        <View>
          <Text className={styles.sectionTitle}>屏蔽名单</Text>
          <Text className={styles.jobMeta}>屏蔽后双方不能新增拜访收益、挑战、留言与好友申请</Text>
          <View className={styles.jobList}>
            {blocks.length === 0 ? (
              <Text className={styles.tip}>名单是空的</Text>
            ) : blocks.map((userId) => (
              <View key={userId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>用户 #{userId}</Text>
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `unblock-${userId}`}
                  onClick={() => doUnblock(userId)}
                >
                  解除
                </Button>
              </View>
            ))}
          </View>
          <View className={styles.actionRow}>
            <Input
              className={styles.nameInput}
              value={blockInput}
              onInput={(e) => setBlockInput(e.detail.value)}
              placeholder="要拉黑的用户 ID"
              type="number"
            />
            <Button
              className={styles.miniBtn}
              disabled={pending === 'block'}
              onClick={() => void doBlock()}
            >
              拉黑
            </Button>
          </View>

          <Text className={styles.sectionTitle}>举报</Text>
          <Text className={styles.jobMeta}>进入管理员处理队列；类型：留言墙内容 / 漂流瓶内容 / 昵称</Text>
          <View className={styles.actionRow}>
            {[
              ['WALL_MESSAGE', '留言墙内容'],
              ['BOTTLE_CONTENT', '漂流瓶内容'],
              ['NICKNAME', '昵称'],
            ].map(([value, label]) => (
              <Button
                key={value}
                className={reportType === value ? styles.miniBtn : styles.miniBtnGhost}
                onClick={() => setReportType(value)}
              >
                {label}
              </Button>
            ))}
          </View>
          <Input
            className={styles.nameInput}
            value={reportTargetId}
            onInput={(e) => setReportTargetId(e.detail.value)}
            placeholder="目标 ID（留言 / 瓶子 / 用户）"
          />
          <Input
            className={styles.nameInput}
            value={reportReason}
            onInput={(e) => setReportReason(e.detail.value)}
            placeholder="理由（200 字内）"
            maxlength={200}
          />
          <Button
            className={styles.miniBtn}
            disabled={pending === 'report'}
            onClick={() => void doReport()}
          >
            提交举报
          </Button>
        </View>
      )}
    </View>
  )
}

