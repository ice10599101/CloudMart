import { useEffect, useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi, type PollData } from '@/api/community'
import type { PollAttachmentConfig } from '@/utils/postAttachments'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

/**
 * P0-6 投票交互卡（对齐 Web 端 PollBlock）。
 * pollId 查询失败时按插入时快照（config）静态展示；已投/未登录不可投。
 */

interface PollCardProps {
  pollId: string | null | undefined
  config: PollAttachmentConfig | null
}

export default function PollCard({ pollId, config }: PollCardProps) {
  const { isLoggedIn } = useAuthStore()
  const [poll, setPoll] = useState<PollData | null>(null)
  const [loading, setLoading] = useState(Boolean(pollId))
  const [selected, setSelected] = useState<number[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (!pollId) return
    setLoading(true)
    communityApi
      .getPoll(pollId)
      .then((res) => {
        const data = res.data?.data
        if (data) {
          setPoll(data)
          setSelected(data.myOptionIds ?? [])
        } else {
          setNotFound(true)
        }
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [pollId])

  const question = poll?.question ?? config?.question ?? '投票'
  const options =
    poll?.options ?? (config?.options ?? []).map((content, index) => ({ id: index, content, voteCount: 0 }))
  const isMultiple = poll?.multiple ?? config?.multiple ?? false
  const hasVoted = poll ? (poll.myOptionIds?.length ?? 0) > 0 : false
  const totalVotes = poll?.totalVotes ?? 0
  const maxVotes = Math.max(1, ...options.map((o) => o.voteCount))

  const toggleOption = (optionId: number) => {
    if (hasVoted || !isLoggedIn || !poll) return
    setSelected((prev) => {
      if (isMultiple) {
        return prev.includes(optionId) ? prev.filter((id) => id !== optionId) : [...prev, optionId]
      }
      return [optionId]
    })
  }

  const handleVote = async () => {
    if (!pollId || !poll || submitting || selected.length === 0) return
    if (!isLoggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    setSubmitting(true)
    try {
      await communityApi.votePoll(pollId, selected)
      const res = await communityApi.getPoll(pollId)
      const data = res.data?.data
      if (data) setPoll(data)
      Taro.showToast({ title: '投票成功', icon: 'success' })
    } catch {
      Taro.showToast({ title: '投票失败', icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View className={styles.card}>
        <Text className={styles.hint}>投票加载中...</Text>
      </View>
    )
  }

  return (
    <View className={styles.card}>
      <View className={styles.head}>
        <Text className={styles.headIcon}>📊</Text>
        <Text className={styles.headText}>{question}</Text>
        <Text className={`${styles.headTag} ${isMultiple ? styles.tagMulti : styles.tagSingle}`}>{isMultiple ? '多选' : '单选'}</Text>
      </View>

      {notFound && config ? (
        <>
          <View className={styles.optionList}>
            {options.map((option) => (
              <View key={option.id} className={styles.optionRow}>
                <Text className={styles.optionText}>{option.content}</Text>
              </View>
            ))}
          </View>
          <Text className={styles.hint}>发布后即可参与投票</Text>
        </>
      ) : (
        <>
          <View className={styles.optionList}>
            {options.map((option) => {
              const percent = Math.round((option.voteCount / maxVotes) * 100)
              return (
                <View key={option.id} className={styles.optionRow} onClick={() => toggleOption(option.id)}>
                  <View className={styles.optionMain}>
                    <Text className={`${styles.optionMark} ${selected.includes(option.id) ? styles.optionMarkActive : ''}`}>
                      {isMultiple ? (selected.includes(option.id) ? '☑' : '☐') : selected.includes(option.id) ? '◉' : '○'}
                    </Text>
                    <Text className={`${styles.optionText} ${selected.includes(option.id) ? styles.optionTextActive : ''}`}>
                      {option.content}
                    </Text>
                  </View>
                  {(hasVoted || totalVotes > 0) && (
                    <View className={styles.optionResult}>
                      <View className={styles.optionBarTrack}>
                        <View className={styles.optionBarFill} style={{ width: `${percent}%` }} />
                      </View>
                      <Text className={styles.optionCount}>{option.voteCount} 票</Text>
                    </View>
                  )}
                </View>
              )
            })}
          </View>

          <View className={styles.footer}>
            <Text className={styles.total}>{totalVotes} 人参与</Text>
            {!hasVoted && isLoggedIn && (
              <View
                className={`${styles.voteBtn} ${selected.length === 0 ? styles.voteBtnDisabled : ''}`}
                onClick={submitting || selected.length === 0 ? undefined : handleVote}
              >
                <Text className={styles.voteBtnText}>{submitting ? '提交中...' : '投票'}</Text>
              </View>
            )}
            {!hasVoted && !isLoggedIn && <Text className={styles.hint}>登录后参与投票</Text>}
            {hasVoted && <Text className={styles.hint}>已投票</Text>}
          </View>
        </>
      )}
    </View>
  )
}
