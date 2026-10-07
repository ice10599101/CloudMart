import { View, Text, TouchableOpacity } from 'react-native'
import { useEffect, useState } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { communityApi, type PollData } from '@/api/community'
import type { PollAttachmentConfig } from '@/utils/postAttachments'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/**
 * P0-6 投票交互卡（对齐 Web 端 PollBlock / Taro 端 PollCard）。
 * pollId 查询失败时按插入时快照（config）静态展示；已投/未登录不可投。
 */

interface PollCardProps {
  pollId: string | null | undefined
  config: PollAttachmentConfig | null
}

export default function PollCard({ pollId, config }: PollCardProps) {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
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
        const data = (res.data as unknown as { data?: PollData })?.data
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
    if (!isLoggedIn) return
    setSubmitting(true)
    try {
      await communityApi.votePoll(pollId, selected)
      const res = await communityApi.getPoll(pollId)
      const data = (res.data as unknown as { data?: PollData })?.data
      if (data) setPoll(data)
    } catch {
      // 保留当前结果展示
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginVertical: Spacing.md }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>投票加载中...</Text>
      </View>
    )
  }

  return (
    <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginVertical: Spacing.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, marginBottom: Spacing.md }}>
        <Text style={{ fontSize: FontSize.md }}>📊</Text>
        <Text style={{ flex: 1, fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>{question}</Text>
        <Text style={{ fontSize: FontSize.xs, color: isMultiple ? '#a855f7' : theme.primary, backgroundColor: isMultiple ? 'rgba(168,85,247,0.12)' : 'rgba(0,212,255,0.1)', paddingHorizontal: Spacing.sm, paddingVertical: 2, borderRadius: BorderRadius.full }}>
          {isMultiple ? '多选' : '单选'}
        </Text>
      </View>

      {notFound && config ? (
        <>
          {options.map((option) => (
            <Text key={option.id} style={{ fontSize: FontSize.md, color: theme.text, paddingVertical: Spacing.xs }}>
              · {option.content}
            </Text>
          ))}
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>发布后即可参与投票</Text>
        </>
      ) : (
        <>
          {options.map((option) => {
            const percent = Math.round((option.voteCount / maxVotes) * 100)
            const active = selected.includes(option.id)
            return (
              <TouchableOpacity
                key={option.id}
                activeOpacity={hasVoted || !isLoggedIn ? 1 : 0.7}
                onPress={() => toggleOption(option.id)}
                style={{ marginBottom: Spacing.sm }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                  <Text style={{ fontSize: FontSize.md, color: active ? theme.primary : theme.textTertiary }}>
                    {isMultiple ? (active ? '☑' : '☐') : active ? '◉' : '○'}
                  </Text>
                  <Text style={{ flex: 1, fontSize: FontSize.md, color: active ? theme.primary : theme.text, fontWeight: active ? '600' : '400' }}>
                    {option.content}
                  </Text>
                </View>
                {(hasVoted || totalVotes > 0) && (
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingLeft: 24, marginTop: 4 }}>
                    <View style={{ flex: 1, height: 6, borderRadius: 3, backgroundColor: theme.bgInput, overflow: 'hidden' }}>
                      <View style={{ width: `${percent}%`, height: '100%', borderRadius: 3, backgroundColor: theme.primary }} />
                    </View>
                    <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{option.voteCount} 票</Text>
                  </View>
                )}
              </TouchableOpacity>
            )
          })}

          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.md }}>
            <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>{totalVotes} 人参与</Text>
            {!hasVoted && isLoggedIn && (
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={submitting || selected.length === 0 ? () => undefined : handleVote}
                disabled={submitting || selected.length === 0}
                style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.full, backgroundColor: theme.primary, opacity: selected.length === 0 ? 0.4 : 1 }}
              >
                <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>{submitting ? '提交中...' : '投票'}</Text>
              </TouchableOpacity>
            )}
            {!hasVoted && !isLoggedIn && <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>登录后参与投票</Text>}
            {hasVoted && <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>已投票</Text>}
          </View>
        </>
      )}
    </View>
  )
}
