import { useCallback, useEffect, useState } from 'react'
import { Image, Input, ScrollView, Text, View } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { softwareApi } from '@/api/software'
import type {
  SoftwareAccessState,
  SoftwareComment,
  SoftwareDetail,
  SoftwareRelease,
  SoftwareResolveResult,
} from '@/api/software'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import RichText from '@/components/RichText'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

const ACCESS_MODE_LABEL: Record<string, string> = {
  REGISTERED: '登录后即可获取',
  COMMUNITY_LEVEL: '需要社区等级',
  ADMIN_PAID: '付款查看（联系管理员开通）',
}

function iconUrl(assetId: string | null): string | undefined {
  return assetId ? `/files/${assetId}` : undefined
}

/**
 * 软件详情（S13 移动端）：介绍 / 版本与来源 / 获取链接 / 交流讨论。
 * 真实链接与提取码只在满足查看条件后返回，仅保留在页面内存里——
 * 不写 Storage、不拼进分享文案（方案 §5.2/§11.1）。
 */
export default function SoftwareDetailPage() {
  const router = useRouter()
  const id = String(router.params.id ?? '')
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
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
  /** SW11：回复目标（根楼+精确目标）与举报目标 */
  const [replyTarget, setReplyTarget] = useState<{
    rootId: string
    replyToCommentId: string
    nickname: string
  } | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
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

  const handleCopy = (text: string, label: string) => {
    Taro.setClipboardData({ data: text })
      .then(() => Taro.showToast({ title: `${label}已复制`, icon: 'success' }))
      .catch(() => undefined)
  }

  const handleResolve = async () => {
    if (!sourceId) {
      await Taro.showToast({ title: '请先选择下载来源', icon: 'none' })
      return
    }
    setResolving(true)
    try {
      const res = await softwareApi.resolve(id, sourceId)
      setResolved(res.data.data)
    } catch {
      // 403/409 门槛与业务原因由拦截器提示
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
      await Taro.showToast({ title: '已发送', icon: 'success' })
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
  const handleReport = async () => {
    if (!pendingReport) return
    try {
      await softwareApi.report(id, {
        targetType: pendingReport.targetType,
        targetId: pendingReport.targetId,
        reason: reportReason.trim() || 'OTHER',
      })
      setReportOpen(false)
      setReportReason('')
      Taro.showToast({ title: '举报已提交', icon: 'success' })
    } catch {
      // 重复举报/限频由拦截器提示
    }
  }

  /** SF02：来源反馈——ActionSheet 选类型；兼容性类携带定位上下文（不收集设备标识） */
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
      Taro.showToast({ title: '感谢反馈', icon: 'success' })
    } catch {
      // 限频由拦截器提示
    }
  }

  const handleSourceFeedback = (source: {
    sourceId: string
    releaseKey?: string
    platform?: string | null
    arch?: string | null
  }) => {
    Taro.showActionSheet({
      itemList: ['链接失效', '平台不兼容', '安装失败'],
      success: (res) => {
        const codes = ['LINK_INVALID', 'PLATFORM_MISMATCH', 'INSTALL_FAILURE']
        void submitSourceFeedback(source, codes[res.tapIndex])
      },
    })
  }

  /** SW11：撤销评分（真实均分随刷新返回） */
  const handleRevokeRating = async () => {
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
        await Taro.showToast({ title: '已订阅更新', icon: 'success' })
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
      await Taro.showToast({ title: `已评 ${score} 星`, icon: 'success' })
    } catch {
      // 分享者自评等由拦截器提示
    }
  }

  if (!detail) {
    return (
      <View className={styles.page} style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <CustomNavBar title="软件详情" />
        <Text className={styles.loadingText}>加载中…</Text>
      </View>
    )
  }

  return (
    <ScrollView
      className={styles.page}
      scrollY
      style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}
    >
      <CustomNavBar title={detail.name} />

      <View className={styles.hero}>
        <Image
          className={styles.heroIcon}
          src={iconUrl(detail.iconAssetId) ?? '/assets/default-avatar.png'}
          mode="aspectFill"
        />
        <View className={styles.heroBody}>
          <Text className={styles.heroTitle}>{detail.name}</Text>
          <Text className={styles.heroSummary}>{detail.summary}</Text>
          <View className={styles.heroMeta}>
            {detail.categoryName ? (
              <Text className={styles.metaTag}>{detail.categoryName}</Text>
            ) : null}
            <Text className={styles.metaText}>
              {detail.ratingAvg == null
                ? '暂无评分'
                : `${detail.ratingAvg.toFixed(1)} 分 · ${detail.ratingCount} 人`}
            </Text>
            <Text className={styles.metaText}>收藏 {detail.favoriteCount}</Text>
          </View>
        </View>
      </View>

      <View className={styles.actionRow}>
        <Text className={styles.actionBtn} onClick={() => void handleFavorite()}>
          {detail.isFavorited ? '★ 已收藏' : '☆ 收藏'}
        </Text>
        <Text className={styles.actionBtn} onClick={() => void handleSubscribe()}>
          {detail.isSubscribed ? '🔔 已订阅' : '🔔 订阅更新'}
        </Text>
      </View>

      <View className={styles.card}>
        <Text className={styles.cardTitle}>我的评分</Text>
        <View className={styles.starRow}>
          {[1, 2, 3, 4, 5].map((star) => (
            <Text
              key={star}
              className={`${styles.star} ${
                (detail.myScore ?? 0) >= star ? styles.starActive : ''
              }`}
              onClick={() => void handleRate(star)}
            >
              ★
            </Text>
          ))}
          <Text className={styles.starHint}>
            {detail.ownerId === String(user?.id ?? '')
              ? '分享者不能给自己评分'
              : '点击星星评分（1~5）'}
          </Text>
          {(detail.myScore ?? 0) > 0 && (
            <Text className={styles.ratingRevoke} onClick={() => void handleRevokeRating()}>
              撤销我的评分
            </Text>
          )}
        </View>
      </View>

      {(detail.installationGuide || detail.knownIssues) && (
        <View className={styles.card}>
          {detail.installationGuide && (
            <>
              <Text className={styles.cardTitle}>安装指南</Text>
              <Text className={styles.guideText}>{detail.installationGuide}</Text>
            </>
          )}
          {detail.knownIssues && (
            <>
              <Text className={styles.cardTitle}>已知问题</Text>
              <Text className={styles.guideText}>{detail.knownIssues}</Text>
            </>
          )}
        </View>
      )}

      {/* UX03：短亮点/同类选择/发布者资料卡（无数据省略） */}
      {(detail.highlights ?? []).length > 0 && (
        <View className={styles.card}>
          <Text className={styles.cardTitle}>亮点</Text>
          {(detail.highlights ?? []).map((h, i) => (
            <Text key={i} className={styles.guideText}>• {h}</Text>
          ))}
        </View>
      )}
      {(detail.relatedItems ?? []).length > 0 && (
        <View className={styles.card}>
          <Text className={styles.cardTitle}>同类选择</Text>
          {(detail.relatedItems ?? []).map((rel) => (
            <View key={`${rel.softwareId}-${rel.relationType}`} style={{ marginTop: 8 }}>
              <Text className={styles.guideText}>
                [{rel.relationType === 'SIMILAR_USE' ? '同类用途' : '同平台替代'}] {rel.name}
                {rel.reason ? `——${rel.reason}` : ''}
              </Text>
            </View>
          ))}
        </View>
      )}
      {detail.publisherSummary && (
        <View className={styles.card}>
          <Text className={styles.cardTitle}>本站分享者</Text>
          <Text className={styles.guideText}>
            {detail.publisherSummary.nickname || `用户 ${detail.publisherSummary.userId}`}
            {' · '}公开投稿 {detail.publisherSummary.publishedCount} 款
            {detail.publisherSummary.lastPublishedAt
              ? ` · 最近发布 ${detail.publisherSummary.lastPublishedAt.slice(0, 10)}`
              : ''}
          </Text>
          <Text className={styles.guideText}>与软件开发者不同；仅展示公开投稿信息</Text>
        </View>
      )}

      <View className={styles.card}>
        <Text className={styles.cardTitle}>下载来源</Text>
        {releases.length > 1 ? (
          <View className={styles.optionRow}>
            {releases.map((release) => (
              <Text
                key={release.releaseKey}
                className={`${styles.option} ${
                  releaseKey === release.releaseKey ? styles.optionActive : ''
                }`}
                onClick={() => {
                  setReleaseKey(release.releaseKey)
                  setSourceId(undefined)
                  setResolved(null)
                }}
              >
                {release.versionName}
                {release.recommended ? '（推荐）' : ''}
              </Text>
            ))}
          </View>
        ) : null}
        {sources.length === 0 ? (
          <Text className={styles.emptyText}>暂无可用来源</Text>
        ) : (
          sources.map((source) => (
            <View key={source.sourceId} className={styles.sourceRow}>
              <Text
                className={`${styles.sourceItem} ${
                  sourceId === source.sourceId ? styles.sourceActive : ''
                }`}
                onClick={() => {
                  setSourceId(source.sourceId)
                  setResolved(null)
                }}
              >
                {source.label || source.provider || source.sourceType}
                {source.platform ? ` · ${source.platform}` : ''}
                {source.availability !== 'UNKNOWN' && source.availability !== 'AVAILABLE'
                  ? `（${source.availabilityTip ?? '需核实'}）`
                  : ''}
              </Text>
              {(source.sizeLabel || source.note) && (
                <Text className={styles.sourceMetaExtra}>
                  {[source.sizeLabel, source.note].filter(Boolean).join(' · ')}
                </Text>
              )}
              {source.claimedSha256 && (
                <Text className={styles.sourceMetaExtra}>
                  发布者提供的校验值（SHA-256）
                </Text>
              )}
              <Text
                className={styles.sourceFeedback}
                onClick={() => handleSourceFeedback(source)}
              >
                反馈问题
              </Text>
            </View>
          ))
        )}

        {accessState && !accessState.allowed ? (
          <View className={styles.lockedBox}>
            <Text className={styles.lockedTitle}>
              {ACCESS_MODE_LABEL[accessState.accessMode] ?? '当前不可获取'}
            </Text>
            {accessState.accessMode === 'COMMUNITY_LEVEL' ? (
              <Text className={styles.lockedHint}>
                需要社区等级 {accessState.requiredLevel ?? '-'}，当前{' '}
                {accessState.currentLevel ?? '-'}
              </Text>
            ) : null}
            {accessState.accessMode === 'ADMIN_PAID'
              ? accessState.contacts.map((contact) => (
                  <Text key={contact.id} className={styles.lockedHint}>
                    {contact.name}：{contact.contactValue}（点击复制）
                  </Text>
                ))
              : null}
            {/* SF04：访问申请入口（付款查看模式；提交后 PENDING 展示状态） */}
            {accessState.accessMode === 'ADMIN_PAID' ? (
              accessState.pendingRequest ? (
                <Text className={styles.lockedHint}>
                  访问申请{accessState.pendingRequest.status === 'PENDING' ? '审核中' : accessState.pendingRequest.status}
                  {accessState.pendingRequest.reviewNote ? `：${accessState.pendingRequest.reviewNote}` : ''}
                </Text>
              ) : (
                <Text
                  className={styles.lockedHint}
                  style={{ color: '#3d5afe' }}
                  onClick={() => {
                    void (async () => {
                      try {
                        await softwareApi.submitAccessRequest(
                          id,
                          '申请访问受限来源（小程序端提交，请补充用途说明后管理员核验）',
                        )
                        Taro.showToast({ title: '已提交，等待管理员核验', icon: 'success' })
                      } catch {
                        Taro.showToast({ title: '提交失败（可能已有申请）', icon: 'none' })
                      }
                    })()
                  }}
                >
                  提交访问申请（说明用途，人工核验后开通） →
                </Text>
              )
            ) : null}
          </View>
        ) : resolved ? (
          <View className={styles.resolvedBox}>
            <Text className={styles.resolvedUrl} onClick={() => handleCopy(resolved.url, '链接')}>
              {resolved.url}
            </Text>
            {resolved.extractionCode ? (
              <Text
                className={styles.resolvedCode}
                onClick={() => handleCopy(resolved.extractionCode ?? '', '提取码')}
              >
                提取码：{resolved.extractionCode}
              </Text>
            ) : null}
            <Text className={styles.resolvedHint}>
              链接由发布者提供，请在外部平台完成下载；复制后请尽快使用
            </Text>
          </View>
        ) : (
          <Text
            className={`${styles.resolveBtn} ${resolving ? styles.resolveBtnBusy : ''}`}
            onClick={() => void handleResolve()}
          >
            {resolving
              ? '获取中…'
              : (() => {
                  const sel = sources.find((source) => source.sourceId === sourceId);
                  return sel?.sourceType === 'HTTPS_DOWNLOAD'
                    ? '获取安装包地址'
                    : sel?.sourceType === 'OFFICIAL_PAGE'
                      ? '前往官网'
                      : sel?.sourceType === 'WEB_APP'
                        ? '在线使用'
                        : '获取下载链接';
                })()}
          </Text>
        )}
      </View>

      <View className={styles.card}>
        <Text className={styles.cardTitle}>软件介绍</Text>
        {/* 正文已由服务端消毒；复用全端 RichText 白名单渲染 */}
        <RichText content={detail.descriptionHtml} className={styles.richText} />
      </View>

      <View className={styles.card}>
        <Text className={styles.cardTitle}>版本记录</Text>
        {releases.length === 0 ? (
          <Text className={styles.emptyText}>暂无版本</Text>
        ) : (
          releases.map((release) => (
            <View key={release.releaseKey} className={styles.releaseItem}>
              <Text className={styles.releaseName}>
                {release.versionName}
                {release.recommended ? '（推荐）' : ''}
              </Text>
              <Text className={styles.releaseMeta}>
                {release.releaseDate ?? ''} · {release.changelog || '暂无更新日志'}
              </Text>
            </View>
          ))
        )}
      </View>

      <View className={styles.card}>
        <View className={styles.discussHead}>
          <Text className={styles.cardTitle}>交流讨论（{detail.commentCount}）</Text>
          <Text
            className={styles.reportLink}
            onClick={() => {
              setPendingReport({ targetType: 'SOFTWARE' })
              setReportReason('')
              setReportOpen(true)
            }}
          >
            举报本软件
          </Text>
        </View>
        {replyTarget && (
          <View className={styles.replyBanner}>
            <Text className={styles.replyBannerText}>回复 {replyTarget.nickname}</Text>
            <Text
              className={styles.replyBannerCancel}
              onClick={() => setReplyTarget(null)}
            >
              取消
            </Text>
          </View>
        )}
        <View className={styles.commentInputRow}>
          <Input
            className={styles.commentInput}
            value={commentText}
            placeholder={replyTarget ? `回复 ${replyTarget.nickname}` : '留下你的看法'}
            onInput={(event) => setCommentText(event.detail.value)}
          />
          <Text className={styles.commentSend} onClick={() => void handleSendComment()}>
            {sending ? '发送中' : '发送'}
          </Text>
        </View>
        {commentsHasMore && (
          <View
            className={styles.loadMore}
            onClick={() => {
              if (!commentsLoading) void loadComments(commentsPage + 1)
            }}
          >
            <Text className={styles.loadMoreText}>
              {commentsLoading ? '加载中…' : '加载更多评论'}
            </Text>
          </View>
        )}
        {comments.length === 0 ? (
          <Text className={styles.emptyText}>还没有评论</Text>
        ) : (
          comments.map((comment) => (
            <View key={comment.id} className={styles.commentItem}>
              <Text className={styles.commentUser}>
                {comment.nickname}
                {comment.pinned ? ' · 置顶' : ''}
              </Text>
              <Text className={styles.commentContent}>{comment.content}</Text>
              <View className={styles.commentActions}>
                <Text className={styles.commentLike} onClick={() => void handleLike(comment)}>
                  {comment.likedByMe ? '♥' : '♡'} {comment.likeCount}
                </Text>
                <View className={styles.commentOps}>
                  <Text
                    className={styles.commentOp}
                    onClick={() =>
                      setReplyTarget({
                        rootId: comment.parentId ?? comment.id,
                        replyToCommentId: comment.id,
                        nickname: comment.nickname,
                      })
                    }
                  >
                    回复
                  </Text>
                  {String(user?.id ?? '') === comment.userId ? (
                    <Text
                      className={styles.commentOpDanger}
                      onClick={() => void handleDeleteComment(comment)}
                    >
                      删除
                    </Text>
                  ) : (
                    <Text
                      className={styles.commentOp}
                      onClick={() => {
                        setPendingReport({ targetType: 'SOFTWARE_COMMENT', targetId: comment.id })
                        setReportReason('')
                        setReportOpen(true)
                      }}
                    >
                      举报
                    </Text>
                  )}
                </View>
                <Text className={styles.commentTime}>{comment.createdAt}</Text>
              </View>
              {(comment.replies ?? []).map((reply) => (
                <View key={reply.id} className={styles.replyItem}>
                  <Text className={styles.replyUser}>{reply.nickname}</Text>
                  <Text className={styles.replyContent}>{reply.content}</Text>
                </View>
              ))}
            </View>
          ))
        )}
      </View>

      <View className={styles.bottomSpace} />
      {reportOpen && (
        <View className={styles.reportMask}>
          <View className={styles.reportCard}>
            <Text className={styles.reportTitle}>
              {pendingReport?.targetType === 'SOFTWARE_COMMENT' ? '举报该评论' : '举报本软件'}
            </Text>
            <Input
              className={styles.reportInput}
              value={reportReason}
              placeholder="举报原因（可留空，默认“其他”）"
              onInput={(event) => setReportReason(event.detail.value)}
            />
            <View className={styles.reportActions}>
              <Text className={styles.reportCancel} onClick={() => setReportOpen(false)}>
                取消
              </Text>
              <Text className={styles.reportSubmit} onClick={() => void handleReport()}>
                提交举报
              </Text>
            </View>
          </View>
        </View>
      )}
      <View className={styles.bottomSpace} />
    </ScrollView>
  )
}
