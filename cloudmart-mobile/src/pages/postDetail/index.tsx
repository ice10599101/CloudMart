import { useState, useEffect } from 'react'
import { View, Text, Image, ScrollView, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Post, Comment } from '@/types'
import styles from './index.module.scss'

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

export default function PostDetailPage() {
  const id = Taro.getCurrentInstance().router?.params?.id || ''
  const [post, setPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [commentContent, setCommentContent] = useState('')
  const [isFollowing, setIsFollowing] = useState(false)
  const { dataTheme, themeStyle } = useThemeClass()

  useEffect(() => {
    if (id) {
      loadPost()
      loadComments()
    }
  }, [id])

  const loadPost = async () => {
    try {
      const res = await communityApi.getPost(id)
      setPost(res.data?.data)
    } catch {
      // API unavailable
    }
  }

  const loadComments = async () => {
    try {
      const res = await communityApi.getComments(id, { page: 1, pageSize: 50 })
      setComments(res.data?.data?.list || [])
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
    try {
      await communityApi.createComment(id, { content: commentContent })
      setCommentContent('')
      Taro.showToast({ title: '评论成功', icon: 'success' })
      loadComments()
    } catch {
      Taro.showToast({ title: '评论失败', icon: 'none' })
    }
  }

  const handleShare = async () => {
    try {
      await Taro.showActionSheet({
        itemList: ['复制链接', '分享到微信'],
      }).then(async (res) => {
        if (res.tapIndex === 0) {
          const url = `${window.location.origin}/pages/postDetail/index?id=${id}`
          await Taro.setClipboardData({ data: url })
          await communityApi.sharePost(id)
          Taro.showToast({ title: '链接已复制', icon: 'success' })
        } else if (res.tapIndex === 1) {
          await communityApi.sharePost(id)
          Taro.showToast({ title: '请复制链接分享到微信', icon: 'none' })
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
    Taro.showActionSheet({
      itemList: ['分享', '举报'],
    }).then((res) => {
      if (res.tapIndex === 0) handleShare()
      else if (res.tapIndex === 1) handleReport()
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
            <Image className={styles.authorAvatar} src={post.user.avatar} onClick={() => Taro.navigateTo({ url: `/pages/userProfile/index?id=${post.user?.id}` })} />
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
          <Text className={styles.postText}>{post.content}</Text>
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

        {/* Comments */}
        <View className={styles.commentSection}>
          <Text className={styles.commentTitle}>评论 ({post.commentCount})</Text>
          {comments.map((comment) => (
            <View key={comment.id} className={styles.commentItem}>
              {comment.user && <Image className={styles.commentAvatar} src={comment.user.avatar} />}
              <View className={styles.commentBody}>
                {comment.user && <Text className={styles.commentName}>{comment.user.nickname}</Text>}
                <Text className={styles.commentText}>{comment.content}</Text>
                <Text className={styles.commentTime}>{formatTime(comment.createdAt)}</Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>

      {/* Bottom Action Bar */}
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
    </View>
  )
}
