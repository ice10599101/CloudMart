import { View, Text, TouchableOpacity, TextInput } from 'react-native'
import { useEffect, useState } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { communityApi, type SurveyData } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

/**
 * P0-6 问卷交互卡（对齐 Web 端 SurveyBlock / Taro 端 SurveyCard）。
 * 题型 SINGLE/MULTI/TEXT；必填校验前端先行；重复提交由后端覆盖更新。
 */

interface SurveyCardProps {
  surveyId: string
}

export default function SurveyCard({ surveyId }: SurveyCardProps) {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [survey, setSurvey] = useState<SurveyData | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [optionAnswers, setOptionAnswers] = useState<Record<number, number[]>>({})
  const [textAnswers, setTextAnswers] = useState<Record<number, string>>({})
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!surveyId) return
    setLoading(true)
    communityApi
      .getSurvey(surveyId)
      .then((res) => {
        const data = (res.data as unknown as { data?: SurveyData })?.data
        if (data) {
          setSurvey(data)
          const optAns: Record<number, number[]> = {}
          const txtAns: Record<number, string> = {}
          for (const answer of data.myAnswers ?? []) {
            if (answer.optionIds?.length) optAns[answer.questionId] = answer.optionIds
            if (answer.text) txtAns[answer.questionId] = answer.text
          }
          setOptionAnswers(optAns)
          setTextAnswers(txtAns)
        } else {
          setNotFound(true)
        }
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [surveyId])

  const toggleOption = (questionId: number, optionIndex: number, multi: boolean) => {
    setOptionAnswers((prev) => {
      const current = prev[questionId] ?? []
      if (multi) {
        return { ...prev, [questionId]: current.includes(optionIndex) ? current.filter((i) => i !== optionIndex) : [...current, optionIndex] }
      }
      return { ...prev, [questionId]: [optionIndex] }
    })
  }

  const handleSubmit = async () => {
    if (!survey || submitting) return
    if (!isLoggedIn) return
    for (const question of survey.questions) {
      if (!question.required) continue
      const hasAnswer =
        question.type === 'TEXT' ? Boolean(textAnswers[question.id]?.trim()) : (optionAnswers[question.id]?.length ?? 0) > 0
      if (!hasAnswer) return
    }
    setSubmitting(true)
    try {
      await communityApi.submitSurveyResponse(
        surveyId,
        survey.questions.map((question) => ({
          questionId: question.id,
          optionIds: question.type === 'TEXT' ? undefined : (optionAnswers[question.id] ?? []),
          text: question.type === 'TEXT' ? (textAnswers[question.id]?.trim() || undefined) : undefined,
        })),
      )
      const res = await communityApi.getSurvey(surveyId)
      const data = (res.data as unknown as { data?: SurveyData })?.data
      if (data) setSurvey(data)
    } catch {
      // 保留当前作答
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginVertical: Spacing.md }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>问卷加载中...</Text>
      </View>
    )
  }

  if (notFound) {
    return (
      <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginVertical: Spacing.md }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>问卷暂不可用</Text>
      </View>
    )
  }

  const answered = survey
    ? survey.questions.every(
        (q) => (q.type === 'TEXT' ? textAnswers[q.id]?.trim() : (optionAnswers[q.id]?.length ?? 0) > 0) || !q.required,
      )
    : false

  return (
    <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: theme.border, padding: Spacing.lg, marginVertical: Spacing.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, marginBottom: Spacing.md }}>
        <Text style={{ fontSize: FontSize.md }}>📝</Text>
        <Text style={{ flex: 1, fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>{survey?.title}</Text>
      </View>

      {(survey?.questions ?? []).map((question, qIndex) => (
        <View key={question.id} style={{ paddingVertical: Spacing.sm, borderTopWidth: 1, borderTopColor: theme.border }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '500', color: theme.text, marginBottom: Spacing.sm, lineHeight: 22 }}>
            {qIndex + 1}. {question.text}
            {question.required ? ' *' : ''}
          </Text>
          {question.type === 'TEXT' ? (
            <TextInput
              value={textAnswers[question.id] ?? ''}
              onChangeText={(text) => setTextAnswers((prev) => ({ ...prev, [question.id]: text }))}
              placeholder="请输入你的回答"
              placeholderTextColor={theme.textTertiary}
              maxLength={200}
              style={{
                borderWidth: 1,
                borderColor: theme.border,
                borderRadius: BorderRadius.md,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.sm,
                color: theme.text,
                fontSize: FontSize.sm,
                backgroundColor: theme.bgInput,
              }}
            />
          ) : (
            question.options.map((option, optionIndex) => {
              const active = (optionAnswers[question.id] ?? []).includes(optionIndex)
              return (
                <TouchableOpacity
                  key={optionIndex}
                  activeOpacity={0.7}
                  onPress={() => toggleOption(question.id, optionIndex, question.type === 'MULTI')}
                  style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingVertical: Spacing.xs }}
                >
                  <Text style={{ fontSize: FontSize.md, color: active ? theme.primary : theme.textTertiary }}>
                    {question.type === 'MULTI' ? (active ? '☑' : '☐') : active ? '◉' : '○'}
                  </Text>
                  <Text style={{ flex: 1, fontSize: FontSize.md, color: active ? theme.primary : theme.text, fontWeight: active ? '600' : '400' }}>
                    {option}
                  </Text>
                </TouchableOpacity>
              )
            })
          )}
        </View>
      ))}

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.md }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>{survey?.responseCount ?? 0} 人已答</Text>
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={submitting || !answered ? () => undefined : handleSubmit}
          disabled={submitting || !answered}
          style={{ paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm, borderRadius: BorderRadius.full, backgroundColor: theme.primary, opacity: answered ? 1 : 0.4 }}
        >
          <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>{submitting ? '提交中...' : '提交问卷'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}
