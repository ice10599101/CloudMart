import { View, Text, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import type { Post } from '@/types'
import { ICON_BASE64 } from '@/components/Icon'
import styles from './index.module.scss'

interface PostCardProps {
  post: Post
}

export default function PostCard({ post }: PostCardProps) {
  const handleClick = () => {
    Taro.navigateTo({ url: `/pages/postDetail/index?id=${post.id}` })
  }

  return (
    <View className={styles.card} onClick={handleClick}>
      {post.images && post.images.length > 0 && (
        <Image className={styles.cover} src={post.images[0]} mode='aspectFill' />
      )}
      <View className={styles.info}>
        <Text className={styles.title}>{post.title}</Text>
        <View className={styles.author}>
          {post.user && <Image className={styles.avatar} src={post.user.avatar} />}
          {post.user && <Text className={styles.name}>{post.user.nickname}</Text>}
          <View className={styles.likeWrap}>
            <Image src={ICON_BASE64.heart[post.isLiked ? 'active' : 'default']} style={{ width: '14px', height: '14px' }} mode='aspectFit' />
            <Text className={styles.likeCount}>{post.likeCount}</Text>
          </View>
        </View>
      </View>
    </View>
  )
}
