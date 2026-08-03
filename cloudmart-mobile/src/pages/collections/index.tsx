import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface PostItem {
  id: number
  title: string
  content?: string
  summary?: string
  coverImage?: string
  likeCount: number
  commentCount: number
  createdAt: string
  status?: number
}

interface CommentItem {
  id: number
  postId: number
  postTitle?: string
  content: string
  replyToNickname?: string
  likeCount: number
  createdAt: string
}

const TAB_LIST = [
  { key: 'posts', label: '我的帖子', icon: '📝' },
  { key: 'drafts', label: '我的草稿', icon: '📋' },
  { key: 'liked', label: '我的点赞', icon: '👍' },
  { key: 'replies', label: '我的回复', icon: '💬' },
]

type TabKey = typeof TAB_LIST[number]['key']

export default function CollectionsPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { user } = useAuthStore()
  useAuthGuard()

  const initialType = Taro.getCurrentInstance().router?.params?.type || 'posts'
  const initialTab = TAB_LIST.some((t) => t.key === initialType) ? initialType as TabKey : 'posts'
  const [activeTab, setActiveTab] = useState<TabKey>(initialTab)
  const [posts, setPosts] = useState<PostItem[]>([])
  const [drafts, setDrafts] = useState<PostItem[]>([])
  const [likedPosts, setLikedPosts] = useState<PostItem[]>([])
  const [comments, setComments] = useState<CommentItem[]>([])
  const [loading, setLoading] = useState(false)

  const fetchPosts = useCallback(async () => {
    if (!user?.id) return
    setLoading(true)
    try {
      const res = await communityApi.getUserPosts(user.id, { page: 1, pageSize: 50 })
      setPosts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setPosts([])
    } finally {
      setLoading(false)
    }
  }, [user?.id])

  const fetchDrafts = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getUserDrafts({ page: 1, pageSize: 50 })
      setDrafts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setDrafts([])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchLiked = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getLikedPosts({ page: 1, pageSize: 50 })
      setLikedPosts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setLikedPosts([])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchComments = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getMyComments({ page: 1, pageSize: 50 })
      setComments(res.data?.data?.list || res.data?.data || [])
    } catch {
      setComments([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'posts') fetchPosts()
    else if (activeTab === 'drafts') fetchDrafts()
    else if (activeTab === 'liked') fetchLiked()
    else if (activeTab === 'replies') fetchComments()
  }, [activeTab, fetchPosts, fetchDrafts, fetchLiked, fetchComments])

  const handlePublishDraft = async (id: number) => {
    const res = await Taro.showModal({
      title: '确认发布',
      content: '确定要发布该草稿吗？发布后将在社区公开展示。',
    })
    if (!res.confirm) return
    try {
      await communityApi.updatePost(id, { status: 1 })
      Taro.showToast({ title: '发布成功', icon: 'success' })
      fetchDrafts()
    } catch {
      Taro.showToast({ title: '发布失败', icon: 'error' })
    }
  }

  const handleDeleteDraft = async (id: number) => {
    const res = await Taro.showModal({
      title: '确认删除',
      content: '确定要删除该草稿吗？删除后无法恢复。',
    })
    if (!res.confirm) return
    try {
      await communityApi.deletePost(id)
      Taro.showToast({ title: '删除成功', icon: 'success' })
      fetchDrafts()
    } catch {
      Taro.showToast({ title: '删除失败', icon: 'error' })
    }
  }

  const renderPostCard = (post: PostItem) => (
    <View key={post.id} className={styles.postCard} onClick={() => Taro.navigateTo({ url: `/pages/postDetail/index?id=${post.id}` })}>
      {post.coverImage ? (
        <Image className={styles.postCover} src={post.coverImage} mode='aspectFill' />
      ) : (
        <View className={styles.postCoverPlaceholder}>
          <Text className={styles.placeholderIcon}>📝</Text>
        </View>
      )}
      <View className={styles.postInfo}>
        <Text className={styles.postTitle}>{post.title || '未命名'}</Text>
        <View className={styles.postMeta}>
          <Text className={styles.metaItem}>❤️ {post.likeCount}</Text>
          <Text className={styles.metaItem}>💬 {post.commentCount}</Text>
          <Text className={styles.metaDate}>{new Date(post.createdAt).toLocaleDateString()}</Text>
        </View>
      </View>
    </View>
  )

  const renderDraftCard = (draft: PostItem) => (
    <View key={draft.id} className={styles.draftCard} onClick={() => Taro.navigateTo({ url: `/pages/publish/index?edit=${draft.id}` })}>
      <View className={styles.draftContent}>
        <Text className={styles.draftTitle}>{draft.title || '未命名草稿'}</Text>
        <Text className={styles.draftSummary}>{draft.summary || draft.content?.substring(0, 80) || '暂无内容'}</Text>
        <Text className={styles.draftDate}>最后编辑：{new Date(draft.createdAt).toLocaleString()}</Text>
      </View>
      <View className={styles.draftActions}>
        <View className={styles.publishBtn} onClick={(e) => { e.stopPropagation(); handlePublishDraft(draft.id) }}>
          <Text className={styles.publishBtnText}>发布</Text>
        </View>
        <View className={styles.deleteBtn} onClick={(e) => { e.stopPropagation(); handleDeleteDraft(draft.id) }}>
          <Text className={styles.deleteBtnText}>删除</Text>
        </View>
      </View>
    </View>
  )

  const renderCommentCard = (comment: CommentItem) => (
    <View key={comment.id} className={styles.commentCard} onClick={() => Taro.navigateTo({ url: `/pages/postDetail/index?id=${comment.postId}` })}>
      <Text className={styles.commentContent}>{comment.content}</Text>
      <View className={styles.commentMeta}>
        <Text className={styles.commentPost}>原帖：{comment.postTitle || `帖子#${comment.postId}`}</Text>
        {comment.replyToNickname && (
          <Text className={styles.commentReply}>回复 @{comment.replyToNickname}</Text>
        )}
      </View>
      <View className={styles.commentBottom}>
        <Text className={styles.commentDate}>{new Date(comment.createdAt).toLocaleString()}</Text>
        <Text className={styles.commentLike}>❤️ {comment.likeCount}</Text>
      </View>
    </View>
  )

  const renderEmpty = (icon: string, text: string, subText?: string) => (
    <View className={styles.empty}>
      <Text className={styles.emptyIcon}>{icon}</Text>
      <Text className={styles.emptyText}>{text}</Text>
      {subText && <Text className={styles.emptySubText}>{subText}</Text>}
    </View>
  )

  const renderContent = () => {
    if (loading) {
      return (
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      )
    }

    switch (activeTab) {
      case 'posts':
        return posts.length > 0
          ? <View className={styles.postGrid}>{posts.map(renderPostCard)}</View>
          : renderEmpty('📝', '暂无帖子', '去社区发帖吧')
      case 'drafts':
        return drafts.length > 0
          ? <View className={styles.draftList}>{drafts.map(renderDraftCard)}</View>
          : renderEmpty('📋', '暂无草稿', '发布内容时可保存为草稿稍后编辑')
      case 'liked':
        return likedPosts.length > 0
          ? <View className={styles.postGrid}>{likedPosts.map(renderPostCard)}</View>
          : renderEmpty('👍', '暂无点赞')
      case 'replies':
        return comments.length > 0
          ? <View className={styles.commentList}>{comments.map(renderCommentCard)}</View>
          : renderEmpty('💬', '暂无回复')
      default:
        return null
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.tabs}>
        {TAB_LIST.map((tab) => (
          <View
            key={tab.key}
            className={`${styles.tab} ${activeTab === tab.key ? styles.tabActive : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            <Text className={styles.tabIcon}>{tab.icon}</Text>
            <Text className={activeTab === tab.key ? styles.tabTextActive : styles.tabText}>
              {tab.label}
            </Text>
          </View>
        ))}
      </View>
      <ScrollView scrollY className={styles.content}>
        {renderContent()}
      </ScrollView>
    </View>
  )
}
