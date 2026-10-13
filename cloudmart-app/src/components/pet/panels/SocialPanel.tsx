// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { Alert, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo, type PetFriendPanel, type PetRelationPanel, type PetWallPage } from '@/api/pet'
import { CARE_ERROR_HINT, ChipButton } from '../shared'

export function SocialPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [tab, setTab] = useState<'relation' | 'friend' | 'wall' | 'safety'>('relation')
  const [relations, setRelations] = useState<PetRelationPanel | null>(null)
  const [friends, setFriends] = useState<PetFriendPanel | null>(null)
  const [wall, setWall] = useState<PetWallPage | null>(null)
  const [wallInput, setWallInput] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyInput, setReplyInput] = useState('')
  const [tip, setTip] = useState<string | null>(null)
  const [pending, setPending] = useState<string | null>(null)
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
        setRelations(res.data)
      } else if (tab === 'friend') {
        const { data: res } = await petApi.getFriends()
        setFriends(res.data)
      } else if (tab === 'wall') {
        const { data: res } = await petApi.getWall(pet.petId, 1, 10)
        setWall(res.data)
      } else if (tab === 'safety') {
        const { data: res } = await petApi.listBlocks()
        setBlocks(res.data ?? [])
      }
    } catch {
      // 拦截器已提示
    }
  }, [tab, pet.petId])

  useEffect(() => {
    void loadActive()
  }, [loadActive])

  const doBlock = async () => {
    const target = blockInput.trim()
    if (!target) {
      setTip('先填要拉黑的用户 ID')
      return
    }
    await run('block', () => petApi.blockUser(target), '已拉黑')
    setBlockInput('')
  }

  const doUnblock = async (userId: number) => {
    await run(`unblock-${userId}`, () => petApi.unblockUser(userId), '已解除拉黑')
  }

  const doReport = async () => {
    const targetId = reportTargetId.trim()
    const reason = reportReason.trim()
    if (!targetId || !reason) {
      setTip('目标 ID 和理由都要填')
      return
    }
    await run('report', () => petApi.reportTarget({ targetType: reportType, targetId, reason }), '已提交，管理员会处理')
    setReportTargetId('')
    setReportReason('')
    void loadMyReports()
  }

  /** PET-23：我的举报结果（可公开的处理结果；不含内部审核字段） */
  const [myReports, setMyReports] = useState<import('@/api/pet').PetReportMine[] | null>(null)
  const loadMyReports = useCallback(async () => {
    try {
      const { data: res } = await petApi.listMyReports()
      setMyReports(res.data ?? [])
    } catch {
      setMyReports([])
    }
  }, [])

  useEffect(() => {
    void loadMyReports()
  }, [loadMyReports])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      await action()
      Alert.alert('成功', text)
      await loadActive()
      onRefresh()
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  const cardStyle = {
    padding: Spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8',
    gap: 4,
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {([
          ['relation', '💞 关系'],
          ['friend', '🫂 好友'],
          ['wall', '📝 留言墙'],
          ['safety', '🛡️ 安全'],
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
      {tip && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{tip}</Text>}

      {tab === 'relation' && relations && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {relations.limits.map((limit) => `${limit.label} ${limit.current}/${limit.max}`).join(' · ')}
          </Text>
          {relations.incoming.map((item) => (
            <View key={String(item.id)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.petName}（{item.ownerNickname}）想成为{item.relTypeLabel}
              </Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="同意"
                  primary
                  disabled={pending === `a-${item.id}`}
                  onPress={() => run(`a-${item.id}`, () => petApi.acceptRelation(item.id as number), '关系建立啦！')}
                />
                <ChipButton
                  label="拒绝"
                  disabled={pending === `r-${item.id}`}
                  onPress={() => run(`r-${item.id}`, () => petApi.rejectRelation(item.id as number), '已拒绝')}
                />
              </View>
            </View>
          ))}
          {relations.relations.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有关系，去下面认识一只新宠物吧</Text>
          )}
          {relations.relations.map((item) => (
            <View key={String(item.id)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.petName} · {item.relTypeLabel}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.ownerNickname} · 亲密度 {item.intimacy}（{item.intimacyLevelName}）
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                <ChipButton
                  label="解除"
                  disabled={pending === `d-${item.id}`}
                  onPress={() => run(`d-${item.id}`, () => petApi.dissolveRelation(item.id as number), '已解除关系')}
                />
              </View>
            </View>
          ))}
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>可以认识的宠物</Text>
          {relations.candidates.map((candidate) => (
            <View key={candidate.petId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {candidate.petName}（Lv.{candidate.level} · {candidate.ownerNickname}）
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                <ChipButton
                  label="情侣"
                  disabled={pending === `cr-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `cr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'COUPLE' }),
                      '申请已发出～',
                    )
                  }
                />
                <ChipButton
                  label="闺蜜"
                  disabled={pending === `br-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `br-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'BESTIE' }),
                      '申请已发出～',
                    )
                  }
                />
                <ChipButton
                  label="死党"
                  disabled={pending === `dr-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `dr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'CONFIDANT' }),
                      '申请已发出～',
                    )
                  }
                />
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'friend' && friends && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            今日互访 {friends.todayVisitCount}/{friends.dailyVisitLimit} · 好友上限 {friends.maxFriends}
          </Text>
          {friends.incoming.map((item) => (
            <View key={item.userId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{item.nickname}</Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="同意"
                  primary
                  disabled={pending === `fa-${item.userId}`}
                  onPress={() => run(`fa-${item.userId}`, () => petApi.acceptFriend(item.userId), '成为好友啦！')}
                />
                <ChipButton
                  label="拒绝"
                  disabled={pending === `fr-${item.userId}`}
                  onPress={() => run(`fr-${item.userId}`, () => petApi.rejectFriend(item.userId), '已拒绝')}
                />
              </View>
            </View>
          ))}
          {friends.friends.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有好友，先互相串个门吧</Text>
          )}
          {friends.friends.map((item) => (
            <View key={item.userId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.nickname} · {item.petName ?? '—'}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>互访 {item.visitCount} 次</Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                <ChipButton
                  label="去互访"
                  primary
                  disabled={pending === `fv-${item.userId}`}
                  onPress={() =>
                    run(
                      `fv-${item.userId}`,
                      async () => {
                        const res = await petApi.visitFriend(item.userId)
                        setTip(res.data.data.message)
                        return res
                      },
                      '互访成功！',
                    )
                  }
                />
                <ChipButton
                  label="邀请协作"
                  disabled={pending === `coop-${item.userId}`}
                  onPress={() => run(`coop-${item.userId}`, () => petApi.createCooperation(item.userId), '协作邀请已发出')}
                />
                <ChipButton
                  label="删除"
                  disabled={pending === `fd-${item.userId}`}
                  onPress={() => run(`fd-${item.userId}`, () => petApi.removeFriend(item.userId), '已删除好友')}
                />
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'wall' && wall && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {wall.petName} 的留言墙 · {wall.ownerNickname} · 共 {wall.total} 条（每日可留言 {wall.dailyPostLimit} 条）
          </Text>
          <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
            <TextInput
              value={wallInput}
              onChangeText={setWallInput}
              maxLength={120}
              placeholder="写一句留言吧"
              placeholderTextColor={colors.textTertiary}
              style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
            />
            <ChipButton
              label="留言"
              primary
              disabled={pending === 'post'}
              onPress={() =>
                run(
                  'post',
                  async () => {
                    const res = await petApi.postWallMessage({ petId: pet.petId, content: wallInput })
                    setWallInput('')
                    return res
                  },
                  '留言成功！',
                )
              }
            />
          </View>
          {wall.messages.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有留言，说点什么吧～</Text>
          )}
          {wall.messages.map((item) => (
            <View key={item.id} style={cardStyle}>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.authorNickname}
                {item.ownerReply ? '（我的回复）' : ''} · {item.createdAt?.slice(5, 16).replace('T', ' ')}
              </Text>
              <Text style={{ color: colors.text, fontSize: FontSize.sm }}>{item.content}</Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label={`${item.liked ? '取消赞' : '点赞'} ${item.likeCount}`}
                  disabled={pending === `wl-${item.id}`}
                  onPress={() => run(`wl-${item.id}`, () => petApi.likeWallMessage(item.id), '已更新点赞')}
                />
                {item.owner && item.parentId === null && (
                  <ChipButton
                    label="回复"
                    onPress={() => {
                      setReplyTo(replyTo === item.id ? null : item.id)
                      setReplyInput('')
                    }}
                  />
                )}
                {(item.mine || item.owner) && (
                  <ChipButton
                    label="删除"
                    disabled={pending === `wd-${item.id}`}
                    onPress={() => run(`wd-${item.id}`, () => petApi.deleteWallMessage(item.id), '已删除')}
                  />
                )}
              </View>
              {replyTo === item.id && (
                <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                  <TextInput
                    value={replyInput}
                    onChangeText={setReplyInput}
                    maxLength={120}
                    placeholder="回复这条留言"
                    placeholderTextColor={colors.textTertiary}
                    style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
                  />
                  <ChipButton
                    label="发送"
                    primary
                    disabled={pending === `wr-${item.id}`}
                    onPress={() =>
                      run(
                        `wr-${item.id}`,
                        async () => {
                          const res = await petApi.replyWallMessage({ messageId: item.id, content: replyInput })
                          setReplyTo(null)
                          return res
                        },
                        '回复成功！',
                      )
                    }
                  />
                </View>
              )}
              {item.replies.map((reply) => (
                <Text key={reply.id} style={{ color: colors.textTertiary, fontSize: 10 }}>
                  ↳ {reply.authorNickname}：{reply.content}
                </Text>
              ))}
            </View>
          ))}
        </View>
      )}
      {tab === 'safety' && (
        <View style={{ gap: Spacing.sm }}>
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>屏蔽名单</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>屏蔽后双方不能新增拜访收益、挑战、留言与好友申请</Text>
            {blocks.length === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>名单是空的</Text>
            ) : blocks.map((userId) => (
              <View key={userId} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{`用户 #${userId}`}</Text>
                <ChipButton label="解除" disabled={pending === `unblock-${userId}`} onPress={() => void doUnblock(userId)} />
              </View>
            ))}
            <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
              <TextInput
                value={blockInput}
                onChangeText={setBlockInput}
                placeholder="用户 ID"
                placeholderTextColor={colors.textTertiary}
                keyboardType="numeric"
                style={{ flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs }}
              />
              <ChipButton label="拉黑" disabled={pending === 'block'} onPress={() => void doBlock()} />
            </View>
          </View>

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>举报</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>进入管理员处理队列；类型：留言墙内容 / 漂流瓶内容 / 昵称</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
              {([['WALL_MESSAGE', '留言墙内容'], ['BOTTLE_CONTENT', '漂流瓶内容'], ['NICKNAME', '昵称']] as [string, string][]).map(([value, label]) => (
                <TouchableOpacity
                  key={value}
                  onPress={() => setReportType(value)}
                  style={{
                    paddingVertical: 4, paddingHorizontal: 10, borderRadius: 999,
                    backgroundColor: reportType === value ? colors.primary : colors.bgBase,
                    borderWidth: 1, borderColor: reportType === value ? colors.primary : colors.border,
                  }}
                >
                  <Text style={{ fontSize: 10, color: reportType === value ? '#fff' : colors.textSecondary }}>{label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput
              value={reportTargetId}
              onChangeText={setReportTargetId}
              placeholder="目标 ID（留言 / 瓶子 / 用户）"
              placeholderTextColor={colors.textTertiary}
              style={{ borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs }}
            />
            <TextInput
              value={reportReason}
              onChangeText={setReportReason}
              placeholder="理由（200 字内）"
              placeholderTextColor={colors.textTertiary}
              maxLength={200}
              multiline
              style={{ borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs, minHeight: 60 }}
            />
            <ChipButton label="提交举报" primary disabled={pending === 'report'} onPress={() => void doReport()} />
            {myReports && myReports.length > 0 ? (
              <View style={{ marginTop: Spacing.sm, gap: Spacing.xs }}>
                <Text style={{ color: colors.textTertiary, fontSize: 10, fontWeight: '600' }}>我的举报</Text>
                {myReports.slice(0, 10).map((report) => {
                  const statusLabel: Record<string, string> = {
                    PENDING: '处理中', RESOLVED: '已处理', REJECTED: '未成立',
                  }
                  return (
                    <View key={String(report.reportId)} style={{ borderTopWidth: 1, borderTopColor: colors.border, paddingTop: Spacing.xs, gap: 2 }}>
                      <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                        {`#${String(report.reportId)} · ${report.reason} · ${statusLabel[report.status] ?? report.status}`}
                      </Text>
                      {report.handleReason ? (
                        <Text style={{ color: colors.textTertiary, fontSize: 10 }}>处理结果：{report.handleReason}</Text>
                      ) : null}
                    </View>
                  )
                })}
              </View>
            ) : null}
          </View>
        </View>
      )}
    </View>
  )
}

/** 家园面板：房间布置 / 家具商城 / 邻里拜访与设置 */
