import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Switch, Textarea, Image, Picker } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import RichText from '@/components/RichText'
import type { DriftBottleItem, DriftBottleCommentItem, DriftBottleCandidateWish } from '@/types'
import styles from './index.module.scss'

// 编辑器组件：H5 用 TiptapEditor，小程序用 MiniProgramEditor
// 使用 Taro 条件编译在构建时静态选择，避免 Tiptap/ProseMirror 被打包进小程序
// #ifdef H5
import TiptapEditor from '@/components/TiptapEditor'
// #endif
// #ifdef WEAPP
import MiniProgramEditor from '@/components/MiniProgramEditor'
// #endif

let EditorComponent: React.FC<{ value?: string; onChange?: (v: string) => void; placeholder?: string }> | null = null
// #ifdef H5
EditorComponent = TiptapEditor
// #endif
// #ifdef WEAPP
EditorComponent = MiniProgramEditor
// #endif

const ROLE_LABELS: Record<DriftBottleItem['role'], string> = {
  THROWN: '我投出的',
  PICKED: '我捞到的',
}

const STATUS_LABELS: Record<DriftBottleItem['status'], string> = {
  FLOATING: '漂流中',
  PICKED: '已被捞起',
  RETURNED: '被扔回海里',
}

/** 富文本转纯文本（供判空）：strip 标签 + 解码常见实体 + 折叠空白 */
function richTextToPlainText(html: string): string {
  if (!html) return ''
  return html
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
}

interface BottleCommentSectionProps {
  bottleId: number
  onCountChange: (delta: number) => void
}

/**
 * 单瓶评论树：首次展开时挂载加载首屏（pageSize 10），
 * cursor 分页经 meta.nextCursor/hasMore 提供「加载更多」。
 */
function BottleCommentSection({ bottleId, onCountChange }: BottleCommentSectionProps) {
  const [comments, setComments] = useState<DriftBottleCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [content, setContent] = useState('')
  const [isAnonymous, setIsAnonymous] = useState(true)
  const [replyTo, setReplyTo] = useState<DriftBottleCommentItem | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const loadFirstPage = useCallback(async () => {
    setLoading(true)
    try {
      const res = await wishApi.listDriftBottleComments(bottleId, { pageSize: 10 })
      if (res.data.success) {
        setComments(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoading(false)
    }
  }, [bottleId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore || !cursor) return
    setLoadingMore(true)
    try {
      const res = await wishApi.listDriftBottleComments(bottleId, { cursor, pageSize: 10 })
      if (res.data.success) {
        setComments((prev) => {
          const seen = new Set(prev.map((c) => c.id))
          return [...prev, ...res.data.data.filter((c) => !seen.has(c.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoadingMore(false)
    }
  }

  const handleSubmit = async () => {
    const trimmed = content.trim()
    if (!trimmed) {
      Taro.showToast({ title: '写点什么再发送吧', icon: 'none' })
      return
    }
    setSubmitting(true)
    try {
      const res = await wishApi.addDriftBottleComment(bottleId, {
        content: trimmed,
        parentId: replyTo?.id,
        isAnonymous,
      })
      if (res.data.success) {
        setContent('')
        setReplyTo(null)
        onCountChange(1)
        loadFirstPage()
        Taro.showToast({ title: replyTo ? '回复已发送' : '评论已发表', icon: 'none' })
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '发表失败，请稍后重试', icon: 'none' })
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setSubmitting(false)
    }
  }

  const goProfile = (userId: number | null) => {
    if (userId == null) return
    Taro.navigateTo({ url: `/pages/userProfile/index?id=${userId}` })
  }

  return (
    <View className={styles.commentSection}>
      <View className={styles.composer}>
        {replyTo && (
          <View className={styles.replyBanner}>
            <Text className={styles.replyBannerText}>回复 @{replyTo.nickname}</Text>
            <Text className={styles.replyCancel} onClick={() => setReplyTo(null)}>取消</Text>
          </View>
        )}
        <Textarea
          className={styles.composerInput}
          value={content}
          onInput={(e) => setContent(e.detail.value.slice(0, 500))}
          maxlength={500}
          placeholder={replyTo ? `回复 @${replyTo.nickname}...` : '给这只漂流瓶留句话...'}
          disabled={submitting}
          showConfirmBar={false}
        />
        <View className={styles.composerActions}>
          <View className={styles.anonSwitch}>
            <Text className={styles.anonLabel}>匿名</Text>
            <Switch
              checked={isAnonymous}
              onChange={(e) => setIsAnonymous(e.detail.value)}
              color='#4a90d9'
            />
          </View>
          <View
            className={`${styles.submitBtn} ${!content.trim() || submitting ? styles.submitBtnDisabled : ''}`}
            onClick={() => content.trim() && !submitting && handleSubmit()}
          >
            <Text className={styles.submitBtnText}>{replyTo ? '发送回复' : '发送'}</Text>
          </View>
        </View>
      </View>

      {loading ? (
        <View className={styles.loadingWrap}>
          <Text className={styles.loadingText}>加载中...</Text>
        </View>
      ) : comments.length === 0 ? (
        <View className={styles.emptyWrap}>
          <Text className={styles.emptyWrapText}>还没有评论，来写下第一条回应吧</Text>
        </View>
      ) : (
        <View className={styles.commentList}>
          {comments.map((comment) => {
            const clickable = comment.userId != null
            return (
              <View key={comment.id} className={styles.commentItem}>
                <View
                  className={styles.commentAvatarWrap}
                  onClick={clickable ? () => goProfile(comment.userId) : undefined}
                >
                  {comment.avatar ? (
                    <Image className={styles.commentAvatar} src={comment.avatar} mode='aspectFill' />
                  ) : (
                    <View className={styles.commentAvatarPlaceholder}>
                      <Text className={styles.commentAvatarStar}>瓶</Text>
                    </View>
                  )}
                </View>
                <View className={styles.commentBody}>
                  <View className={styles.commentMeta}>
                    <Text
                      className={styles.commentNickname}
                      onClick={clickable ? () => goProfile(comment.userId) : undefined}
                    >
                      {comment.nickname}
                    </Text>
                    {comment.isAnonymous && (
                      <Text className={styles.anonBadge}>匿名</Text>
                    )}
                    <Text className={styles.commentTime}>
                      {new Date(comment.createdAt).toLocaleString('zh-CN')}
                    </Text>
                  </View>
                  <Text className={styles.commentContent} userSelect>
                    {comment.replyToNickname ? `回复 @${comment.replyToNickname}：` : ''}
                    {comment.content}
                  </Text>
                  <View className={styles.commentActionRow}>
                    <Text className={styles.commentReply} onClick={() => setReplyTo(comment)}>
                      回复
                    </Text>
                  </View>
                </View>
              </View>
            )
          })}
          {hasMore && (
            <View className={styles.loadMoreWrap} onClick={() => !loadingMore && handleLoadMore()}>
              <Text className={styles.loadMoreText}>{loadingMore ? '加载中...' : '加载更多'}</Text>
            </View>
          )}
          {!hasMore && (
            <View className={styles.loadMoreWrap}>
              <Text className={styles.loadMoreText}>已经到底啦~</Text>
            </View>
          )}
        </View>
      )}
    </View>
  )
}

interface BottleCardProps {
  bottle: DriftBottleItem
  interacting: boolean
  onInteract: (type: 'BLESS' | 'LIGHT') => void
  onCountChange: (delta: number) => void
}

function BottleCard({ bottle, interacting, onInteract, onCountChange }: BottleCardProps) {
  const [expanded, setExpanded] = useState(false)
  const canInteract = bottle.role === 'PICKED' && bottle.wishId != null

  return (
    <View className={styles.bottleCard}>
      <View className={styles.bottleHeader}>
        <Text className={styles.roleTag}>{ROLE_LABELS[bottle.role]}</Text>
        <Text className={`${styles.statusTag} ${bottle.status === 'PICKED' ? styles.statusPicked : ''}`}>
          {STATUS_LABELS[bottle.status]}
        </Text>
      </View>
      <Text className={styles.bottleTime}>
        {bottle.role === 'THROWN'
          ? `投出于 ${new Date(bottle.thrownAt).toLocaleString('zh-CN')}`
          : `捞起于 ${new Date(bottle.thrownAt).toLocaleString('zh-CN')}`}
      </Text>

      {bottle.content && (
        <View className={styles.bottleContent}>
          <RichText content={bottle.content} />
        </View>
      )}

      {bottle.wishId != null && (
        <View className={styles.wishSnapshot}>
          <Text className={styles.wishSnapshotTitle}>
            {bottle.wishTitle || `关联心愿 #${bottle.wishId}`}
          </Text>
          {bottle.wishTags.length > 0 && (
            <View className={styles.tagRow}>
              {bottle.wishTags.map((tag) => (
                <Text key={tag} className={styles.tagChip}>{tag}</Text>
              ))}
            </View>
          )}
        </View>
      )}

      {!bottle.isAnonymous && bottle.throwerNickname && (
        <Text className={styles.throwerText}>投瓶人：{bottle.throwerNickname}</Text>
      )}

      {canInteract && (
        <View className={styles.bottleActions}>
          <View
            className={styles.interactBtn}
            onClick={interacting ? undefined : () => onInteract('BLESS')}
          >
            <Text>🌟 祝福</Text>
          </View>
          <View
            className={styles.interactBtn}
            onClick={interacting ? undefined : () => onInteract('LIGHT')}
          >
            <Text>✨ 点亮</Text>
          </View>
        </View>
      )}

      <View className={styles.commentToggle} onClick={() => setExpanded((v) => !v)}>
        <Text className={styles.commentToggleText}>
          {expanded ? '收起评论 ▴' : `评论 ${bottle.commentCount} ▾`}
        </Text>
      </View>

      {expanded && (
        <BottleCommentSection bottleId={bottle.bottleId} onCountChange={onCountChange} />
      )}
    </View>
  )
}

/**
 * 漂流瓶页面：替换旧的「相遇信笺」体验。
 * 捞瓶区 + 投瓶区（自由文字/关联心愿二选一 + 匿名） + 我的漂流瓶（含瓶下评论树）。
 */
export default function EncounterLettersPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()

  // 捞瓶
  const [fishing, setFishing] = useState(false)
  const [fishedBottle, setFishedBottle] = useState<DriftBottleItem | null>(null)

  // 投瓶
  const [throwMode, setThrowMode] = useState<'text' | 'wish'>('text')
  const [content, setContent] = useState('')
  const [candidateWishes, setCandidateWishes] = useState<DriftBottleCandidateWish[]>([])
  const [selectedWishId, setSelectedWishId] = useState<number | null>(null)
  const [isAnonymousThrow, setIsAnonymousThrow] = useState(true)
  const [throwing, setThrowing] = useState(false)

  // 我的漂流瓶
  const [bottles, setBottles] = useState<DriftBottleItem[]>([])
  const [loadingMine, setLoadingMine] = useState(true)
  const [interactingId, setInteractingId] = useState<number | null>(null)

  const loadMine = useCallback(async () => {
    if (!isLoggedIn) return
    try {
      const res = await wishApi.listMyDriftBottles()
      if (res.data.success) setBottles(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoadingMine(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    if (!isLoggedIn) return
    loadMine()
    wishApi.listDriftBottleCandidateWishes()
      .then((res) => { if (res.data.success) setCandidateWishes(res.data.data ?? []) })
      .catch(() => undefined)
  }, [isLoggedIn, loadMine])

  const handleFish = async () => {
    if (fishing) return
    setFishing(true)
    try {
      const res = await wishApi.fishDriftBottle()
      if (res.data.success) {
        if (res.data.data) {
          setFishedBottle(res.data.data)
          Taro.showToast({ title: '捞到一只漂流瓶 🍾', icon: 'none' })
          loadMine()
        } else {
          setFishedBottle(null)
          Taro.showToast({ title: '海面暂时没有漂流瓶', icon: 'none' })
        }
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '捞瓶失败，请稍后重试', icon: 'none' })
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setFishing(false)
    }
  }

  const handleThrow = async () => {
    if (throwing) return
    if (throwMode === 'text') {
      if (!richTextToPlainText(content)) {
        Taro.showToast({ title: '写点什么再投出吧', icon: 'none' })
        return
      }
    } else if (selectedWishId == null) {
      Taro.showToast({ title: '请选择要关联的心愿', icon: 'none' })
      return
    }

    setThrowing(true)
    try {
      const payload =
        throwMode === 'text'
          ? { content, isAnonymous: isAnonymousThrow }
          : { wishId: selectedWishId as number, isAnonymous: isAnonymousThrow }
      const res = await wishApi.throwDriftBottle(payload)
      if (res.data.success) {
        Taro.showToast({ title: '漂流瓶已投出 🍾', icon: 'none' })
        setContent('')
        setSelectedWishId(null)
        loadMine()
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '投瓶失败，请稍后重试', icon: 'none' })
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setThrowing(false)
    }
  }

  const handleInteract = async (bottle: DriftBottleItem, type: 'BLESS' | 'LIGHT') => {
    setInteractingId(bottle.bottleId)
    try {
      const res = await wishApi.interactDriftBottle(bottle.bottleId, type)
      if (res.data.success) {
        setBottles((prev) => prev.map((it) => (it.bottleId === bottle.bottleId ? res.data.data : it)))
        setFishedBottle((prev) => (prev && prev.bottleId === bottle.bottleId ? res.data.data : prev))
        Taro.showToast({ title: type === 'BLESS' ? '已送上祝福 🌟' : '已为 TA 点亮 ✨', icon: 'none' })
      } else if (res.data.error?.code === 'WISH_RATE_LIMITED') {
        Taro.showToast({ title: '这个漂流瓶今天已经回应过啦', icon: 'none' })
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '回应失败，请稍后重试', icon: 'none' })
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setInteractingId(null)
    }
  }

  const handleCommentCountChange = useCallback((bottleId: number, delta: number) => {
    const patch = (b: DriftBottleItem): DriftBottleItem => ({
      ...b,
      commentCount: Math.max(0, b.commentCount + delta),
    })
    setBottles((prev) => prev.map((it) => (it.bottleId === bottleId ? patch(it) : it)))
    setFishedBottle((prev) => (prev && prev.bottleId === bottleId ? patch(prev) : prev))
  }, [])

  const selectedWishIndex = selectedWishId == null
    ? 0
    : candidateWishes.findIndex((w) => w.wishId === selectedWishId)

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title='漂流瓶' back />

      {!isLoggedIn ? (
        <View className={styles.empty}>
          <Text>请先登录后使用漂流瓶</Text>
        </View>
      ) : (
        <ScrollView className={styles.scroll} scrollY>
          {/* 捞瓶区 */}
          <View className={styles.section}>
            <Text className={styles.sectionTitle}>🌊 捞一只漂流瓶</Text>
            <View className={styles.fishBtn} onClick={() => !fishing && handleFish()}>
              <Text className={styles.fishBtnText}>{fishing ? '打捞中...' : '🎣 捞漂流瓶'}</Text>
            </View>
            {fishedBottle && (
              <BottleCard
                bottle={fishedBottle}
                interacting={interactingId === fishedBottle.bottleId}
                onInteract={(type) => handleInteract(fishedBottle, type)}
                onCountChange={(delta) => handleCommentCountChange(fishedBottle.bottleId, delta)}
              />
            )}
          </View>

          {/* 投瓶区 */}
          <View className={styles.section}>
            <Text className={styles.sectionTitle}>🍾 投一只漂流瓶</Text>

            <View className={styles.modeSwitch}>
              <View
                className={`${styles.modeOption} ${throwMode === 'text' ? styles.modeOptionActive : ''}`}
                onClick={() => setThrowMode('text')}
              >
                <Text className={styles.modeOptionText}>自由文字</Text>
              </View>
              <View
                className={`${styles.modeOption} ${throwMode === 'wish' ? styles.modeOptionActive : ''}`}
                onClick={() => setThrowMode('wish')}
              >
                <Text className={styles.modeOptionText}>关联心愿</Text>
              </View>
            </View>

            {throwMode === 'text' ? (
              <View className={styles.editorWrap}>
                {EditorComponent ? (
                  <EditorComponent
                    value={content}
                    onChange={setContent}
                    placeholder='写下此刻想说的话，抛向大海...'
                  />
                ) : (
                  <Textarea
                    className={styles.composerInput}
                    value={content}
                    onInput={(e) => setContent(e.detail.value)}
                    placeholder='写下此刻想说的话，抛向大海...'
                  />
                )}
              </View>
            ) : (
              <View className={styles.wishPickerWrap}>
                {candidateWishes.length === 0 ? (
                  <Text className={styles.wishPickerEmpty}>暂无进行中的心愿，先去发布一个吧</Text>
                ) : (
                  <Picker
                    mode='selector'
                    range={candidateWishes.map((w) => w.title)}
                    value={selectedWishIndex >= 0 ? selectedWishIndex : 0}
                    onChange={(e) => {
                      const idx = Number(e.detail.value)
                      setSelectedWishId(candidateWishes[idx]?.wishId ?? null)
                    }}
                  >
                    <View className={styles.wishPickerValue}>
                      <Text className={styles.wishPickerText}>
                        {selectedWishId != null && selectedWishIndex >= 0
                          ? candidateWishes[selectedWishIndex].title
                          : '选择要关联的心愿'}
                      </Text>
                      <Text className={styles.wishPickerArrow}>▾</Text>
                    </View>
                  </Picker>
                )}
              </View>
            )}

            <View className={styles.throwFooter}>
              <View className={styles.anonSwitch}>
                <Text className={styles.anonLabel}>匿名投出（默认）</Text>
                <Switch
                  checked={isAnonymousThrow}
                  onChange={(e) => setIsAnonymousThrow(e.detail.value)}
                  color='#4a90d9'
                />
              </View>
              <View
                className={`${styles.throwBtn} ${throwing ? styles.throwBtnDisabled : ''}`}
                onClick={() => !throwing && handleThrow()}
              >
                <Text className={styles.throwBtnText}>{throwing ? '投出中...' : '投出 🍾'}</Text>
              </View>
            </View>
          </View>

          {/* 我的漂流瓶 */}
          <View className={styles.section}>
            <Text className={styles.sectionTitle}>🗺️ 我的漂流瓶</Text>
            {loadingMine ? (
              <View className={styles.emptyWrap}>
                <Text className={styles.loadingText}>加载中...</Text>
              </View>
            ) : bottles.length === 0 ? (
              <View className={styles.emptyWrap}>
                <Text className={styles.emptyWrapText}>还没有漂流瓶，去投一只或捞一只吧</Text>
              </View>
            ) : (
              <View className={styles.bottleList}>
                {bottles.map((bottle) => (
                  <BottleCard
                    key={bottle.bottleId}
                    bottle={bottle}
                    interacting={interactingId === bottle.bottleId}
                    onInteract={(type) => handleInteract(bottle, type)}
                    onCountChange={(delta) => handleCommentCountChange(bottle.bottleId, delta)}
                  />
                ))}
              </View>
            )}
          </View>
        </ScrollView>
      )}
    </View>
  )
}