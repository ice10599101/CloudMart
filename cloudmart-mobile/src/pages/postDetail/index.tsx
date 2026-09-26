import RichText from '@/components/RichText'
import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView, Textarea, Button } from '@tarojs/components'
import Taro, { useShareAppMessage } from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import GiftSection from '@/components/GiftSection'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Post, Comment } from '@/types'
import styles from './index.module.scss'

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

export default function PostDetailPage() {
  const id = Taro.getCurrentInstance().router?.params?.id || ''
  // 相关推荐（对齐 Web 端 PostDetail：同话题帖 fallback 推荐流）
  const [relatedPosts, setRelatedPosts] = useState<Post[]>([])
  const [giftTick] = useState(0)
  const [post, setPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [commentContent, setCommentContent] = useState('')
  const [isFollowing, setIsFollowing] = useState(false)
  const [replyTo, setReplyTo] = useState<Comment | null>(null)
  const [commentPage, setCommentPage] = useState(1)
  const [commentHasMore, setCommentHasMore] = useState(false)
  const { dataTheme, themeStyle } = useThemeClass()
  const { user } = useAuthStore()

  useEffect(() => {
    if (id) {
      loadPost()
      loadComments()
    }
  }, [id])

  const loadPost = async () => {
    try {
      const res = await communityApi.getPost(id)
      const detail = res.data?.data
      setPost(detail)
      // 浏览足迹上报（对齐 Web 端 PostDetail）
      if (detail && user?.id) {
        communityApi
          .recordBrowseHistory({
            targetType: 'POST',
            targetId: detail.id,
            title: detail.title,
            cover: detail.coverImage,
          })
          .catch(() => {})
        loadRelatedPosts(detail)
      }
      // 关注状态回显（后端 PostVO.user.isFollowed）
      if (detail?.user && (detail.user as { isFollowed?: boolean }).isFollowed != null) {
        setIsFollowing(!!(detail.user as { isFollowed?: boolean }).isFollowed)
      }
    } catch {
      // API unavailable
    }
  }

  /** 相关推荐：首个话题下的帖子，不足时回退推荐流（对齐 Web 端逻辑） */
  const loadRelatedPosts = async (detail: Post) => {
    const firstTagId = detail.tags?.[0]?.id
    try {
      if (firstTagId) {
        const res = await communityApi.getTagPosts(firstTagId, { page: 1, pageSize: 8 })
        const list = res.data?.data?.list || res.data?.data || []
        setRelatedPosts(list.filter((p) => p.id !== detail.id).slice(0, 4))
        if (list.filter((p) => p.id !== detail.id).length >= 4) return
      }
      const res = await communityApi.getFeed({ page: 1, pageSize: 8 })
      const list = res.data?.data?.list || []
      setRelatedPosts((prev) => {
        const seen = new Set([detail.id, ...prev.map((p) => p.id)])
        const extra = list.filter((p) => !seen.has(p.id)).slice(0, 4 - prev.length)
        return [...prev, ...extra]
      })
    } catch {
      // 相关推荐失败静默
    }
  }

  const loadComments = async (pageNum = 1, append = false) => {
    try {
      const res = await communityApi.getComments(id, { page: pageNum, pageSize: 20 })
      const list = res.data?.data?.list || []
      setComments((prev) => (append ? [...prev, ...list] : list))
      setCommentPage(pageNum)
      setCommentHasMore(list.length >= 20)
    } catch {
      // API unavailable
    }
  }

  const handleLike = async () => {
    if (!post) return
    try {
      if (post.isLiked) {
        await communityApi.unlikePost(post.id)
      } else {
        await communityApi.likePost(post.id)
      }
      setPost({ ...post, isLiked: !post.isLiked, likeCount: post.isLiked ? post.likeCount - 1 : post.likeCount + 1 })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleCollect = async () => {
    if (!post) return
    try {
      if (post.isCollected) {
        await communityApi.uncollectPost(post.id)
      } else {
        await communityApi.collectPost(post.id)
      }
      setPost({ ...post, isCollected: !post.isCollected, collectCount: post.isCollected ? post.collectCount - 1 : post.collectCount + 1 })
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleFollow = async () => {
    if (!post?.user) return
    try {
      if (isFollowing) {
        await communityApi.unfollowUser(post.user.id)
      } else {
        await communityApi.followUser(post.user.id)
      }
      setIsFollowing(!isFollowing)
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  const handleComment = async () => {
    if (!commentContent.trim()) return
    if (!user?.id) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    try {
      // 回复模式：携带 parentId 与 replyToUserId（对齐 Web 端楼中楼）
      await communityApi.createComment(id, {
        content: commentContent,
        parentId: replyTo?.parentId ?? replyTo?.id,
        replyToUserId: replyTo ? (replyTo.replyToUserId ?? replyTo.userId) : undefined,
      })
      setCommentContent('')
      setReplyTo(null)
      Taro.showToast({ title: '评论成功', icon: 'success' })
      loadComments(1, false)
    } catch {
      Taro.showToast({ title: '评论失败', icon: 'none' })
    }
  }

  /** 评论点赞/取消（对齐 Web 端评论工具条） */
  const handleCommentLike = async (comment: Comment) => {
    try {
      if (comment.isLiked) {
        await communityApi.unlikeComment(comment.id)
      } else {
        await communityApi.likeComment(comment.id)
      }
      const patch = (c: Comment): Comment =>
        c.id === comment.id
          ? { ...c, isLiked: !c.isLiked, likeCount: c.isLiked ? c.likeCount - 1 : c.likeCount + 1 }
          : { ...c, replies: c.replies?.map(patch) }
      setComments((prev) => prev.map(patch))
    } catch {
      Taro.showToast({ title: '操作失败', icon: 'none' })
    }
  }

  /** 删除自己的评论（对齐 Web 端 modal.confirm 语义） */
  const handleCommentDelete = (comment: Comment) => {
    Taro.showModal({
      title: '删除评论',
      content: '确定删除这条评论吗？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await communityApi.deleteComment(id, comment.id)
          setComments((prev) => prev.filter((c) => c.id !== comment.id))
          Taro.showToast({ title: '已删除', icon: 'success' })
        } catch {
          Taro.showToast({ title: '删除失败', icon: 'none' })
        }
      },
    })
  }

  /** 点击评论弹出操作（回复/删除） */
  const handleCommentPress = (comment: Comment) => {
    const isMine = comment.userId === user?.id
    const options = isMine ? ['回复', '删除'] : ['回复']
    Taro.showActionSheet({ itemList: options })
      .then((res) => {
        if (options[res.tapIndex] === '回复') {
          setReplyTo(comment)
        } else if (options[res.tapIndex] === '删除') {
          handleCommentDelete(comment)
        }
      })
      .catch(() => {})
  }

  /** 删除自己的帖子（对齐 Web 端 Dropdown+确认） */
  const handleDeletePost = () => {
    Taro.showModal({
      title: '删除帖子',
      content: '删除后不可恢复，确定删除吗？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await communityApi.deletePost(post!.id)
          Taro.showToast({ title: '已删除', icon: 'success' })
          setTimeout(() => Taro.switchTab({ url: '/pages/home/index' }), 1_200)
        } catch {
          Taro.showToast({ title: '删除失败', icon: 'none' })
        }
      },
    })
  }

  const handleEditPost = () => {
    Taro.navigateTo({ url: `/pages/publish/index?edit=${post!.id}` })
  }

  // 小程序原生分享内容（「分享到微信」入口由 useShareAppMessage 承接）
  useShareAppMessage(() => ({
    title: post ? post.title : 'CloudMart 社区',
    path: `/pages/postDetail/index?id=${id}`,
  }))

  const isWeapp = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
  const [shareOpen, setShareOpen] = useState(false)

  const handleShare = async () => {
    if (isWeapp) {
      // 小程序：弹出原生分享按钮（open-type=share 触发 useShareAppMessage 分享面板）
      setShareOpen(true)
      return
    }
    try {
      await Taro.showActionSheet({
        itemList: ['复制链接', '微信内分享'],
      }).then(async (res) => {
        if (res.tapIndex === 0) {
          const url = `${window.location.origin}/pages/postDetail/index?id=${id}`
          await Taro.setClipboardData({ data: url })
          await communityApi.sharePost(id)
          Taro.showToast({ title: '链接已复制', icon: 'success' })
        } else if (res.tapIndex === 1) {
          // H5 浏览器内无法直接调起微信分享（需微信 JSSDK 签名），引导使用右上角菜单
          await communityApi.sharePost(id)
          Taro.showToast({ title: '请点击浏览器右上角 ··· 转发给好友', icon: 'none', duration: 2500 })
        }
      }).catch(() => {})
    } catch {
      // User cancelled
    }
  }

  const handleReport = async () => {
    const { tapIndex } = await Taro.showActionSheet({
      itemList: REPORT_REASONS,
    })
    const reason = REPORT_REASONS[tapIndex]
    try {
      await communityApi.report({ targetType: 'POST', targetId: id, reason })
      Taro.showToast({ title: '举报成功', icon: 'success' })
    } catch {
      Taro.showToast({ title: '举报失败', icon: 'none' })
    }
  }

  const handleMoreActions = () => {
    const isMine = post?.userId === user?.id
    const options = isMine ? ['分享', '编辑', '删除', '举报'] : ['分享', '举报']
    Taro.showActionSheet({
      itemList: options,
    }).then((res) => {
      const chosen = options[res.tapIndex]
      if (chosen === '分享') handleShare()
      else if (chosen === '编辑') handleEditPost()
      else if (chosen === '删除') handleDeletePost()
      else if (chosen === '举报') handleReport()
    }).catch(() => {})
  }

  const formatTime = (time: string) => {
    const date = new Date(time)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }

  if (!post) {
    return <View className={styles.page}><Text>加载中...</Text></View>
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.content}>
        {/* Author */}
        <View className={styles.author}>
          {post.user && (
            <DecoratedAvatar src={post.user.avatar} userId={post.user.id} size={40} fallbackText={post.user.nickname?.[0]} />
          )}
          <View className={styles.authorInfo}>
            {post.user && <Text className={styles.authorName} onClick={() => Taro.navigateTo({ url: `/pages/userProfile/index?id=${post.user?.id}` })}>{post.user.nickname}</Text>}
            <Text className={styles.postTime}>{formatTime(post.createdAt)}</Text>
          </View>
          <View className={styles.followBtn} onClick={handleFollow}>
            <Text className={styles.followText}>{isFollowing ? '已关注' : '关注'}</Text>
          </View>
        </View>

        {/* Content */}
        <View className={styles.postContent}>
          <Text className={styles.postTitle}>{post.title}</Text>
          <RichText content={post.content} className={styles.postText} />
          {post.images && post.images.map((img, i) => (
            <Image key={i} className={styles.postImage} src={img} mode='widthFix' />
          ))}
          {post.tags && post.tags.length > 0 && (
            <View className={styles.tags}>
              {post.tags.map((tag) => (
                <Text key={tag.id} className={styles.tag} onClick={() => Taro.navigateTo({ url: `/pages/topicDetail/index?id=${tag.id}` })}>#{tag.name}</Text>
              ))}
            </View>
          )}
        </View>

        {/* 全站虚拟礼物（对齐 Web 端帖子详情礼物区块） */}
        <GiftSection targetType='POST' targetId={id} refreshTick={giftTick} />

        {/* 相关推荐（对齐 Web 端 PostDetail） */}
        {relatedPosts.length > 0 && (
          <View className={styles.commentSection}>
            <Text className={styles.commentTitle}>相关推荐</Text>
            {relatedPosts.map((rel) => (
              <View key={rel.id} className={styles.relatedRow} onClick={() => Taro.navigateTo({ url: `/pages/postDetail/index?id=${rel.id}` })}>
                {rel.coverImage && <Image className={styles.relatedCover} src={rel.coverImage} mode='aspectFill' />}
                <View className={styles.relatedInfo}>
                  <Text className={styles.relatedTitle} numberOfLines={2}>{rel.title}</Text>
                  <Text className={styles.relatedMeta}>❤ {rel.likeCount} · 💬 {rel.commentCount}</Text>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* Comments */}
        <View className={styles.commentSection}>
          <Text className={styles.commentTitle}>评论 ({post.commentCount})</Text>
          {comments.map((comment) => (
            <View key={comment.id} className={styles.commentItem} onClick={() => handleCommentPress(comment)}>
              {(comment.user?.avatar || comment.authorAvatar) && (
                <Image className={styles.commentAvatar} src={(comment.user?.avatar || comment.authorAvatar) ?? ''} />
              )}
              <View className={styles.commentBody}>
                <Text className={styles.commentName}>
                  {comment.user?.nickname ?? comment.authorNickname ?? '匿名用户'}
                  {comment.replyToNickname ? ` · 回复 @${comment.replyToNickname}` : ''}
                </Text>
                <Text className={styles.commentText}>{comment.content}</Text>
                <View className={styles.commentMetaRow}>
                  <Text className={styles.commentTime}>{formatTime(comment.createdAt)}</Text>
                  <Text
                    className={styles.commentLikeBtn}
                    onClick={(e) => {
                      e.stopPropagation()
                      handleCommentLike(comment)
                    }}
                  >
                    {comment.isLiked ? '❤️' : '🤍'} {comment.likeCount > 0 ? comment.likeCount : ''}
                  </Text>
                </View>
                {comment.replies && comment.replies.length > 0 && (
                  <View className={styles.repliesBlock}>
                    {comment.replies.map((reply) => (
                      <View key={reply.id} className={styles.replyItem} onClick={() => handleCommentPress(reply)}>
                        <Text className={styles.replyName}>
                          {reply.user?.nickname ?? reply.authorNickname ?? '匿名用户'}
                          {reply.replyToNickname ? ` · 回复 @${reply.replyToNickname}` : ''}
                        </Text>
                        <Text className={styles.commentText}>{reply.content}</Text>
                      </View>
                    ))}
                  </View>
                )}
              </View>
            </View>
          ))}
          {commentHasMore && (
            <View className={styles.commentLoadMore} onClick={() => loadComments(commentPage + 1, true)}>
              <Text className={styles.commentLoadMoreText}>加载更多评论</Text>
            </View>
          )}
        </View>
      </ScrollView>

      {/* Bottom Action Bar */}
      {replyTo && (
        <View className={styles.replyBanner}>
          <Text className={styles.replyBannerText}>回复 @{replyTo.user?.nickname ?? '匿名用户'}</Text>
          <Text className={styles.replyBannerCancel} onClick={() => setReplyTo(null)}>取消</Text>
        </View>
      )}
      <View className={styles.bottomBar}>
        <View className={styles.commentInput}>
          <Textarea
            className={styles.input}
            placeholder='写评论...'
            value={commentContent}
            onInput={(e) => setCommentContent(e.detail.value)}
            maxlength={500}
          />
        </View>
        <View className={styles.actionBtn} onClick={handleComment}>
          <Text>发送</Text>
        </View>
        <View className={styles.actionBtn} onClick={handleLike}>
          <Text>{post.isLiked ? '❤️' : '🤍'}</Text>
          <Text className={styles.actionCount}>{post.likeCount}</Text>
        </View>
        <View className={styles.actionBtn} onClick={handleCollect}>
          <Text>{post.isCollected ? '⭐' : '☆'}</Text>
          <Text className={styles.actionCount}>{post.collectCount}</Text>
        </View>
        <View className={styles.actionBtn} onClick={handleMoreActions}>
          <Text>⋯</Text>
        </View>
      </View>
      {/* 小程序原生分享弹层（open-type=share 触发微信分享面板） */}
      {shareOpen && (
        <View className={styles.shareMask} onClick={() => setShareOpen(false)}>
          <View className={styles.shareModal} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.shareModalTitle}>分享给朋友</Text>
            <Button
              className={styles.shareWechatBtn}
              openType='share'
              onClick={() => {
                void communityApi.sharePost(id).catch(() => {})
                setShareOpen(false)
              }}
            >
              微信好友
            </Button>
            <Text
              className={styles.shareCopyBtn}
              onClick={async () => {
                const url = `${window.location.origin}/pages/postDetail/index?id=${id}`
                await Taro.setClipboardData({ data: url }).catch(() => {})
                void communityApi.sharePost(id).catch(() => {})
                setShareOpen(false)
                Taro.showToast({ title: '链接已复制', icon: 'success' })
              }}
            >
              复制链接
            </Text>
            <Text className={styles.shareCancel} onClick={() => setShareOpen(false)}>取消</Text>
          </View>
        </View>
      )}
    </View>
  )
}
