import { View } from '@tarojs/components'
import { useState, useEffect, ReactNode } from 'react'
import styles from './index.module.scss'

interface WaterfallFlowProps {
  children: ReactNode[]
  gap?: number
}

export default function WaterfallFlow({ children, gap = 16 }: WaterfallFlowProps) {
  const [columns, setColumns] = useState<{ left: ReactNode[]; right: ReactNode[] }>({
    left: [],
    right: [],
  })

  useEffect(() => {
    const left: ReactNode[] = []
    const right: ReactNode[] = []
    children.forEach((child, index) => {
      if (index % 2 === 0) {
        left.push(child)
      } else {
        right.push(child)
      }
    })
    setColumns({ left, right })
  }, [children])

  return (
    <View className={styles.container} style={{ gap: `${gap}rpx` }}>
      <View className={styles.column} style={{ gap: `${gap}rpx` }}>
        {columns.left}
      </View>
      <View className={styles.column} style={{ gap: `${gap}rpx` }}>
        {columns.right}
      </View>
    </View>
  )
}
