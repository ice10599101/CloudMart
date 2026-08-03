import { View, Text } from '@tarojs/components'
import styles from './index.module.scss'

interface EmptyStateProps {
  title?: string
  description?: string
}

export default function EmptyState({ title = '暂无内容', description = '快去发现更多精彩吧' }: EmptyStateProps) {
  return (
    <View className={styles.container}>
      <Text className={styles.icon}>📭</Text>
      <Text className={styles.title}>{title}</Text>
      <Text className={styles.description}>{description}</Text>
    </View>
  )
}
