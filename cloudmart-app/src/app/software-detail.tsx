import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  Alert,
  Modal,
  Image,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import * as Clipboard from 'expo-clipboard'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { softwareApi } from '@/api/software'
import type {
  SoftwareAccessState,
  SoftwareComment,
  SoftwareDetail,
  SoftwareRelease,
  SoftwareResolveResult,
} from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

const ACCESS_MODE_LABEL: Record<string, string> = {
  REGISTERED: '登录后即可获取',
  COMMUNITY_LEVEL: '需要社区等级',
  ADMIN_PAID: '付款查看（联系管理员开通）',
}

/**
 * 软件详情（S13 App 端）：介绍 / 版本与来源 / 获取链接 / 交流讨论。
 * 真实链接与提取码只在满足查看条件后返回，仅存内存——
 * 不写本地存储、不拼进分享文案（方案 §5.2/§11.1）。
 */
export default function SoftwareDetailScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const { id: rawId } = useLocalSearchParams<{ id?: string }>()
  const id = String(rawId ?? '')
  const { user } = useAuthStore()

  const [detail, setDetail] = useState<SoftwareDetail | null>(null)
  const [releases, setReleases] = useState<SoftwareRelease[]>([])
  const [accessState, setAccessState] = useState<SoftwareAccessState | null>(null)
  const [comments, setComments] = useState<SoftwareComment[]>([])
  const [releaseKey, setReleaseKey] = useState<string | undefined>(undefined)
  const [sourceId, setSourceId] = useState<string | undefined>(undefined)
  const [resolving, setResolving] = useState(false)
  const [resolved, setResolved] = useState<SoftwareResolveResult | null>(null)
  const [commentText, setCommentText] = useState('')
  const [sending, setSending] = useState(false)
  // SW11：回复目标（根楼 + 精确目标）与举报目标
  const [replyTarget, setReplyTarget] = useState<{
    rootId: string
    replyToCommentId: string
    nickname: string
  } | null>(null)
  const [reportModal, setReportModal] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [pendingReport, setPendingReport] = useState<{
    targetType: 'SOFTWARE' | 'SOFTWARE_COMMENT'
    targetId?: string
  } | null>(null)

  /** SW11：评论分页——加载更多追加，不再固定首批 20 条 */
  const [commentsPage, setCommentsPage] = useState(1)
  const [commentsHasMore, setCommentsHasMore] = useState(false)
  const [commentsLoading, setCommentsLoading] = useState(false)

  const loadComments = useCallback(async (nextPage = 1) => {
    setCommentsLoading(true)
    try {
      const res = await softwareApi.comments(id, nextPage, 20)
      const records = res.data.data?.records ?? []
      const total = res.data.data?.total ?? 0
      setComments((prev) =>
        nextPage === 1 ? records : [...prev, ...records.filter(
          (row) => !prev.some((p) => p.id === row.id),
        )],
      )
      setCommentsPage(nextPage)
      setCommentsHasMore(records.length > 0 && (nextPage - 1) * 20 + records.length < total)
    } finally {
      setCommentsLoading(false)
    }
  }, [id])

  const loadAll = useCallback(async () => {
    try {
      const [detailRes, releaseRes, stateRes] = await Promise.all([
        softwareApi.detail(id),
        softwareApi.releases(id, 1, 20),
        softwareApi.accessState(id).catch(() => null),
      ])
      setDetail(detailRes.data.data)
      const list = releaseRes.data.data?.records ?? []
      setReleases(list)
      setReleaseKey(list[0]?.releaseKey)
      setAccessState(stateRes?.data.data ?? null)
      await loadComments()
    } catch {
      // 错误提示由 request 层统一处理
    }
  }, [id, loadComments])

  useEffect(() => {
    if (id) void loadAll()
  }, [id, loadAll])

  const currentRelease = releases.find((item) => item.releaseKey === releaseKey)
  const sources = currentRelease?.sources ?? []

  const handleCopy = async (text: string, label: string) => {
    await Clipboard.setStringAsync(text)
  }

  // SF01：来源操作文案按类型区分
  const resolveActionLabel = (sourceType?: string) =>
    sourceType === 'HTTPS_DOWNLOAD'
      ? '获取安装包地址'
      : sourceType === 'OFFICIAL_PAGE'
        ? '前往官网'
        : sourceType === 'WEB_APP'
          ? '在线使用'
          : '获取下载链接';

  const handleResolve = async () => {
    if (!sourceId) return
    setResolving(true)
    try {
      const res = await softwareApi.resolve(id, sourceId)
      setResolved(res.data.data)
    } catch {
      // 门槛与业务原因由拦截器提示
    } finally {
      setResolving(false)
    }
  }

  const handleSendComment = async () => {
    const content = commentText.trim()
    if (!content) return
    setSending(true)
    try {
      await softwareApi.createComment(
        id,
        content,
        replyTarget?.rootId,
        replyTarget?.replyToCommentId,
      )
      setCommentText('')
      setReplyTarget(null)
      await loadComments()
    } catch {
      // 违规/限频等由拦截器提示；输入保留
    } finally {
      setSending(false)
    }
  }

  /** SW11：删除自己的评论 */
  const handleDeleteComment = async (comment: SoftwareComment) => {
    try {
      await softwareApi.deleteComment(id, comment.id)
      await loadComments()
    } catch {
      // 由拦截器提示
    }
  }

  /** SW11：软件/评论举报 */
  const handleReport = async (target: { targetType: 'SOFTWARE' | 'SOFTWARE_COMMENT'; targetId?: string }) => {
    try {
      await softwareApi.report(id, {
        targetType: target.targetType,
        targetId: target.targetId,
        reason: reportReason.trim() || 'OTHER',
      })
      setReportModal(false)
      setReportReason('')
      Alert.alert('提示', '举报已提交，管理员会尽快处理')
    } catch {
      // 重复举报/限频由拦截器提示
    }
  }

  /** SF02：来源反馈——链接类直提；兼容性类 Alert 选择并携带定位上下文 */
  const submitSourceFeedback = async (
    source: {
      sourceId: string
      releaseKey?: string
      platform?: string | null
      arch?: string | null
    },
    reasonCode: string,
  ) => {
    try {
      const compat = reasonCode === 'PLATFORM_MISMATCH' || reasonCode === 'INSTALL_FAILURE'
      await softwareApi.sourceFeedback(id, source.sourceId, reasonCode, compat
        ? {
            releaseKey: source.releaseKey,
            platform: source.platform,
            arch: source.arch,
          }
        : undefined)
      Alert.alert('提示', '感谢反馈，我们会尽快核实该来源')
    } catch {
      // 限频等由拦截器提示
    }
  }

  const handleSourceFeedback = (source: {
    sourceId: string
    releaseKey?: string
    platform?: string | null
    arch?: string | null
  }) => {
    Alert.alert('反馈问题类型', '选择要反馈的问题（仅提交指纹结论，不收集设备标识）', [
      { text: '链接失效', onPress: () => void submitSourceFeedback(source, 'LINK_INVALID') },
      {
        text: '平台不兼容',
        onPress: () => void submitSourceFeedback(source, 'PLATFORM_MISMATCH'),
      },
      { text: '安装失败', onPress: () => void submitSourceFeedback(source, 'INSTALL_FAILURE') },
      { text: '取消', style: 'cancel' },
    ])
  }

  /** SW11：撤销评分 */
  const handleRevokeRating = async () => {
    if (!detail) return
    try {
      await softwareApi.revokeRating(id)
      const res = await softwareApi.detail(id)
      setDetail(res.data.data)
    } catch {
      // 由拦截器提示
    }
  }

  const handleLike = async (comment: SoftwareComment) => {
    try {
      if (comment.likedByMe) {
        await softwareApi.unlikeComment(id, comment.id)
      } else {
        await softwareApi.likeComment(id, comment.id)
      }
      await loadComments()
    } catch {
      // 忽略
    }
  }

  const handleFavorite = async () => {
    if (!detail) return
    try {
      if (detail.isFavorited) {
        await softwareApi.unfavorite(id)
      } else {
        await softwareApi.favorite(id)
      }
      setDetail({ ...detail, isFavorited: !detail.isFavorited })
    } catch {
      // 忽略
    }
  }

  const handleSubscribe = async () => {
    if (!detail) return
    try {
      if (detail.isSubscribed) {
        await softwareApi.unsubscribe(id)
      } else {
        await softwareApi.subscribe(id)
      }
      setDetail({ ...detail, isSubscribed: !detail.isSubscribed })
    } catch {
      // 忽略
    }
  }

  const handleRate = async (score: number) => {
    if (!detail) return
    try {
      const res = await softwareApi.rate(id, score)
      setDetail({
        ...detail,
        myScore: res.data.data.myScore,
        ratingCount: res.data.data.ratingCount,
        ratingAvg: res.data.data.ratingAvg,
      })
    } catch {
      // 分享者自评等由拦截器提示
    }
  }

  if (!detail) {
    return (
      <View
        style={{
          flex: 1,
          backgroundColor: theme.bgPage,
          paddingTop: insets.top,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <ActivityIndicator color={theme.primary} />
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.md }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.xl, color: theme.text }}>‹</Text>
        </TouchableOpacity>
        <Text
          numberOfLines={1}
          style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, flex: 1, marginLeft: Spacing.sm }}
        >
          {detail.name}
        </Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, gap: Spacing.md, paddingBottom: Spacing.xxxl }}>
        <View style={{ flexDirection: 'row', gap: Spacing.md }}>
          <Image
            source={{ uri: detail.iconAssetId ? `/files/${detail.iconAssetId}` : undefined }}
            style={{ width: 56, height: 56, borderRadius: BorderRadius.md, backgroundColor: theme.bgInput }}
          />
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text }}>{detail.name}</Text>
            <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4 }}>
              {detail.summary}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 6 }}>
              {detail.ratingAvg == null
                ? '暂无评分'
                : `${detail.ratingAvg.toFixed(1)} 分 · ${detail.ratingCount} 人`}
              {'  '}收藏 {detail.favoriteCount}
              {'  '}
              {detail.categoryName ?? ''}
            </Text>
          </View>
        </View>

        <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
          <TouchableOpacity
            onPress={() => void handleFavorite()}
            style={{
              flex: 1,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.md,
              borderWidth: 1,
              borderColor: theme.primary,
              alignItems: 'center',
            }}
          >
            <Text style={{ color: theme.primary, fontSize: FontSize.md }}>
              {detail.isFavorited ? '★ 已收藏' : '☆ 收藏'}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => void handleSubscribe()}
            style={{
              flex: 1,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.md,
              borderWidth: 1,
              borderColor: theme.primary,
              alignItems: 'center',
            }}
          >
            <Text style={{ color: theme.primary, fontSize: FontSize.md }}>
              {detail.isSubscribed ? '🔔 已订阅' : '🔔 订阅更新'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* 评分（P1）：分享者不能给自己评分 */}
        <View style={{ borderRadius: BorderRadius.md, padding: Spacing.md, backgroundColor: theme.bgContainer }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>我的评分</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, marginTop: Spacing.sm }}>
            {[1, 2, 3, 4, 5].map((star) => (
              <TouchableOpacity
                key={star}
                disabled={detail.ownerId === String(user?.id ?? '')}
                onPress={() => void handleRate(star)}
              >
                <Text
                  style={{
                    fontSize: 28,
                    color: (detail.myScore ?? 0) >= star ? '#f5a623' : theme.textTertiary,
                  }}
                >
                  ★
                </Text>
              </TouchableOpacity>
            ))}
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
              {detail.ownerId === String(user?.id ?? '')
                ? '分享者不能给自己评分'
                : '点击星星评分（1~5）'}
            </Text>
            {(detail.myScore ?? 0) > 0 && (
              <TouchableOpacity onPress={() => void handleRevokeRating()}>
                <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs, marginTop: 4 }}>
                  撤销我的评分
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* SF02：安装指南/已知问题（随快照审核；有内容才显示） */}
        {(detail.installationGuide || detail.knownIssues) ? (
          <View style={{ borderRadius: BorderRadius.md, padding: Spacing.md, backgroundColor: theme.bgContainer }}>
            {detail.installationGuide ? (
              <View>
                <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
                  安装指南
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4, lineHeight: 20 }}>
                  {detail.installationGuide}
                </Text>
              </View>
            ) : null}
            {detail.knownIssues ? (
              <View style={{ marginTop: detail.installationGuide ? Spacing.md : 0 }}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
                  已知问题
                </Text>
                <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: 4, lineHeight: 20 }}>
                  {detail.knownIssues}
                </Text>
              </View>
            ) : null}
          </View>
        ) : null}

        {/* 下载来源与获取链接 */}
        <View style={{ borderRadius: BorderRadius.md, padding: Spacing.md, backgroundColor: theme.bgContainer }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>下载来源</Text>
          {releases.length > 1 ? (
            <View style={{ flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap', marginTop: Spacing.sm }}>
              {releases.map((release) => (
                <TouchableOpacity
                  key={release.releaseKey}
                  onPress={() => {
                    setReleaseKey(release.releaseKey)
                    setSourceId(undefined)
                    setResolved(null)
                  }}
                  style={{
                    paddingHorizontal: Spacing.md,
                    paddingVertical: 6,
                    borderRadius: BorderRadius.full,
                    borderWidth: 1,
                    borderColor: releaseKey === release.releaseKey ? theme.primary : theme.border,
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: releaseKey === release.releaseKey ? theme.primary : theme.textSecondary,
                    }}
                  >
                    {release.versionName}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          ) : null}

          <View style={{ gap: Spacing.sm, marginTop: Spacing.sm }}>
            {sources.map((source) => (
              <View
                key={source.sourceId}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: Spacing.xs,
                }}
              >
                <TouchableOpacity
                  onPress={() => {
                    setSourceId(source.sourceId)
                    setResolved(null)
                  }}
                  style={{
                    flex: 1,
                    paddingVertical: Spacing.sm,
                    paddingHorizontal: Spacing.md,
                    borderRadius: BorderRadius.sm,
                    borderWidth: 1,
                    borderColor: sourceId === source.sourceId ? theme.primary : theme.border,
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: sourceId === source.sourceId ? theme.primary : theme.textSecondary,
                    }}
                  >
                    {source.label || source.provider || source.sourceType}
                    {source.platform ? ` · ${source.platform}` : ''}
                  </Text>
                  {source.sizeLabel || source.note ? (
                    <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
                      {[source.sizeLabel, source.note].filter(Boolean).join(' · ')}
                    </Text>
                  ) : null}
                  {source.claimedSha256 ? (
                    <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
                      发布者提供的校验值（SHA-256）
                    </Text>
                  ) : null}
                </TouchableOpacity>
                {/* SW11：来源失效反馈（不向无权限者泄露 URL，仅提交指纹结论） */}
                <TouchableOpacity
                  onPress={() => handleSourceFeedback(source)}
                  style={{
                    paddingHorizontal: Spacing.sm,
                    paddingVertical: Spacing.sm,
                  }}
                >
                  <Text style={{ color: '#f5a623', fontSize: FontSize.xs }}>反馈问题</Text>
                </TouchableOpacity>
              </View>
            ))}
          </View>

          {accessState && !accessState.allowed ? (
            <View style={{ marginTop: Spacing.sm }}>
              <Text style={{ color: '#f5a623', fontSize: FontSize.md, fontWeight: '600' }}>
                {ACCESS_MODE_LABEL[accessState.accessMode] ?? '当前不可获取'}
              </Text>
              {accessState.accessMode === 'COMMUNITY_LEVEL' ? (
                <Text style={{ color: theme.textSecondary, fontSize: FontSize.sm, marginTop: 4 }}>
                  需要社区等级 {accessState.requiredLevel ?? '-'}，当前{' '}
                  {accessState.currentLevel ?? '-'}
                </Text>
              ) : null}
              {accessState.accessMode === 'ADMIN_PAID'
                ? accessState.contacts.map((contact) => (
                    <TouchableOpacity
                      key={contact.id}
                      onPress={() => void handleCopy(contact.contactValue, contact.name)}
                    >
                      <Text style={{ color: theme.primary, fontSize: FontSize.sm, marginTop: 4 }}>
                        {contact.name}：{contact.contactValue}（点击复制）
                      </Text>
                    </TouchableOpacity>
                  ))
                : null}
            </View>
          ) : resolved ? (
            <View style={{ marginTop: Spacing.sm }}>
              <TouchableOpacity onPress={() => void handleCopy(resolved.url, '链接')}>
                <Text style={{ color: theme.primary, fontSize: FontSize.sm }}>{resolved.url}</Text>
              </TouchableOpacity>
              {resolved.extractionCode ? (
                <TouchableOpacity
                  onPress={() => void handleCopy(resolved.extractionCode ?? '', '提取码')}
                >
                  <Text style={{ color: theme.text, fontSize: FontSize.md, fontWeight: '600', marginTop: 6 }}>
                    提取码：{resolved.extractionCode}（点击复制）
                  </Text>
                </TouchableOpacity>
              ) : null}
              <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs, marginTop: 6 }}>
                链接由发布者提供，请在外部平台完成下载
              </Text>
            </View>
          ) : (
            <TouchableOpacity
              disabled={!sourceId || resolving}
              onPress={() => void handleResolve()}
              style={{
                marginTop: Spacing.sm,
                paddingVertical: Spacing.sm,
                borderRadius: BorderRadius.md,
                backgroundColor: sourceId ? theme.primary : theme.bgInput,
                alignItems: 'center',
              }}
            >
              <Text style={{ color: '#0B1220', fontSize: FontSize.md, fontWeight: '700' }}>
                {resolving
                  ? '获取中…'
                  : resolveActionLabel(
                      sources.find((source) => source.sourceId === sourceId)?.sourceType,
                    )}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* 交流讨论 */}
        <View style={{ borderRadius: BorderRadius.md, padding: Spacing.md, backgroundColor: theme.bgContainer }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
              交流讨论（{detail.commentCount}）
            </Text>
            <TouchableOpacity
              onPress={() => {
                setReportReason('')
                setPendingReport({ targetType: 'SOFTWARE' })
                setReportModal(true)
              }}
            >
              <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs }}>举报本软件</Text>
            </TouchableOpacity>
          </View>
          <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.sm }}>
            <TextInput
              value={commentText}
              onChangeText={setCommentText}
              placeholder="留下你的看法"
              placeholderTextColor={theme.textTertiary}
              multiline
              style={{
                flex: 1,
                minHeight: 36,
                paddingHorizontal: Spacing.md,
                paddingVertical: 6,
                borderRadius: BorderRadius.md,
                backgroundColor: theme.bgInput,
                color: theme.text,
              }}
            />
            <TouchableOpacity onPress={() => void handleSendComment()} disabled={sending}>
              <Text style={{ color: theme.primary, fontSize: FontSize.md, lineHeight: 36 }}>
                {sending ? '发送中' : '发送'}
              </Text>
            </TouchableOpacity>
          </View>
          {replyTarget && (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginTop: 4,
              }}
            >
              <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs }}>
                回复 {replyTarget.nickname}
              </Text>
              <TouchableOpacity onPress={() => setReplyTarget(null)}>
                <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs }}>取消回复</Text>
              </TouchableOpacity>
            </View>
          )}

          {commentsHasMore && (
            <TouchableOpacity
              disabled={commentsLoading}
              onPress={() => void loadComments(commentsPage + 1)}
              style={{ alignItems: 'center', paddingVertical: 8, marginBottom: 8 }}
            >
              <Text style={{ color: '#3d5afe', fontSize: FontSize.sm }}>
                {commentsLoading ? '加载中…' : '加载更多评论'}
              </Text>
            </TouchableOpacity>
          )}
          {comments.length === 0 ? (
            <Text style={{ color: theme.textTertiary, fontSize: FontSize.sm, marginTop: Spacing.sm }}>
              还没有评论
            </Text>
          ) : (
            comments.map((comment) => (
              <View key={comment.id} style={{ marginTop: Spacing.md }}>
                <Text style={{ color: theme.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                  {comment.nickname}
                  {comment.pinned ? ' · 置顶' : ''}
                </Text>
                <Text style={{ color: theme.textSecondary, fontSize: FontSize.sm, marginTop: 4 }}>
                  {comment.content}
                </Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 }}>
                  <TouchableOpacity onPress={() => void handleLike(comment)}>
                    <Text style={{ color: comment.likedByMe ? '#ff6b6b' : theme.textTertiary, fontSize: FontSize.xs }}>
                      {comment.likedByMe ? '♥' : '♡'} {comment.likeCount}
                    </Text>
                  </TouchableOpacity>
                  <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
                    {/* SW11：回复（楼中楼，parentId 恒指向根楼） */}
                    <TouchableOpacity
                      onPress={() =>
                        setReplyTarget({
                          rootId: comment.parentId ?? comment.id,
                          replyToCommentId: comment.id,
                          nickname: comment.nickname,
                        })
                      }
                    >
                      <Text style={{ color: theme.primary, fontSize: FontSize.xs }}>回复</Text>
                    </TouchableOpacity>
                    {String(user?.id ?? '') === comment.userId ? (
                      <TouchableOpacity onPress={() => void handleDeleteComment(comment)}>
                        <Text style={{ color: '#d3392f', fontSize: FontSize.xs }}>删除</Text>
                      </TouchableOpacity>
                    ) : (
                      <TouchableOpacity
                        onPress={() => {
                          setReportReason('')
                          setReportModal(true)
                          setPendingReport({
                            targetType: 'SOFTWARE_COMMENT',
                            targetId: comment.id,
                          })
                        }}
                      >
                        <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs }}>举报</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                </View>
                {/* SW11：楼中楼展示 */}
                {(comment.replies ?? []).map((reply) => (
                  <View
                    key={reply.id}
                    style={{
                      marginTop: Spacing.xs,
                      paddingLeft: Spacing.md,
                      borderLeftWidth: 2,
                      borderLeftColor: theme.border,
                    }}
                  >
                    <Text style={{ color: theme.text, fontSize: FontSize.xs, fontWeight: '600' }}>
                      {reply.nickname}
                    </Text>
                    <Text style={{ color: theme.textSecondary, fontSize: FontSize.xs }}>
                      {reply.content}
                    </Text>
                  </View>
                ))}
              </View>
            ))
          )}
        </View>
      </ScrollView>

      {/* SW11：举报弹窗（软件/评论共用；理由可空走 OTHER） */}
      <Modal visible={reportModal} transparent animationType="fade" onRequestClose={() => setReportModal(false)}>
        <View
          style={{
            flex: 1,
            backgroundColor: 'rgba(0,0,0,0.45)',
            alignItems: 'center',
            justifyContent: 'center',
            padding: Spacing.lg,
          }}
        >
          <View style={{ width: '100%', borderRadius: BorderRadius.md, backgroundColor: theme.bgElevated ?? '#fff', padding: Spacing.md }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
              {pendingReport?.targetType === 'SOFTWARE_COMMENT' ? '举报该评论' : '举报本软件'}
            </Text>
            <TextInput
              value={reportReason}
              onChangeText={setReportReason}
              placeholder="举报原因（可留空，默认“其他”）"
              placeholderTextColor={theme.textTertiary}
              multiline
              style={{
                minHeight: 60,
                marginTop: Spacing.sm,
                padding: Spacing.sm,
                borderRadius: BorderRadius.sm,
                backgroundColor: theme.bgInput,
                color: theme.text,
              }}
            />
            <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.md, marginTop: Spacing.sm }}>
              <TouchableOpacity onPress={() => setReportModal(false)}>
                <Text style={{ color: theme.textTertiary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => pendingReport && void handleReport(pendingReport)}>
                <Text style={{ color: '#d3392f', fontWeight: '600' }}>提交举报</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  )
}
