import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
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

  const loadComments = useCallback(async () => {
    const res = await softwareApi.comments(id, 1, 20)
    setComments(res.data.data?.records ?? [])
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
      await softwareApi.createComment(id, content)
      setCommentText('')
      await loadComments()
    } catch {
      // 违规/限频等由拦截器提示；输入保留
    } finally {
      setSending(false)
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
          </View>
        </View>

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
              <TouchableOpacity
                key={source.sourceId}
                onPress={() => {
                  setSourceId(source.sourceId)
                  setResolved(null)
                }}
                style={{
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
              </TouchableOpacity>
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
                {resolving ? '获取中…' : '获取下载链接'}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* 交流讨论 */}
        <View style={{ borderRadius: BorderRadius.md, padding: Spacing.md, backgroundColor: theme.bgContainer }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>
            交流讨论（{detail.commentCount}）
          </Text>
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
                  <Text style={{ color: theme.textTertiary, fontSize: FontSize.xs }}>
                    {comment.createdAt}
                  </Text>
                </View>
              </View>
            ))
          )}
        </View>
      </ScrollView>
    </View>
  )
}
