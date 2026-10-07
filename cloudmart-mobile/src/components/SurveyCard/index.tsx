import { useEffect, useState } from 'react'
import { View, Text, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi, type SurveyData } from '@/api/community'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

/**
 * P0-6 问卷交互卡（对齐 Web 端 SurveyBlock）。
 * 题型 SINGLE/MULTI/TEXT；必填校验前端先行；重复提交由后端覆盖更新。
 */

interface SurveyCardProps {
  surveyId: string
}

export default function SurveyCard({ surveyId }: SurveyCardProps) {
  const { isLoggedIn } = useAuthStore()
  const [survey, setSurvey] = useState<SurveyData | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  /** 我的作答：questionId → 选项下标数组 / 填空文本 */
  const [optionAnswers, setOptionAnswers] = useState<Record<number, number[]>>({})
  const [textAnswers, setTextAnswers] = useState<Record<number, string>>({})
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!surveyId) return
    setLoading(true)
    communityApi
      .getSurvey(surveyId)
      .then((res) => {
        const data = res.data?.data
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
    if (!isLoggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    for (const question of survey.questions) {
      if (!question.required) continue
      const hasAnswer =
        question.type === 'TEXT' ? Boolean(textAnswers[question.id]?.trim()) : (optionAnswers[question.id]?.length ?? 0) > 0
      if (!hasAnswer) {
        Taro.showToast({ title: '请完成必填题', icon: 'none' })
        return
      }
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
      const data = res.data?.data
      if (data) setSurvey(data)
      Taro.showToast({ title: '问卷已提交', icon: 'success' })
    } catch {
      Taro.showToast({ title: '提交失败', icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View className={styles.card}>
        <Text className={styles.hint}>问卷加载中...</Text>
      </View>
    )
  }

  if (notFound) {
    return (
      <View className={styles.card}>
        <Text className={styles.hint}>问卷暂不可用</Text>
      </View>
    )
  }

  const answered = survey ? survey.questions.every((q) => (q.type === 'TEXT' ? textAnswers[q.id]?.trim() : (optionAnswers[q.id]?.length ?? 0) > 0) || !q.required) : false

  return (
    <View className={styles.card}>
      <View className={styles.head}>
        <Text className={styles.headIcon}>📝</Text>
        <Text className={styles.headText}>{survey?.title}</Text>
      </View>

      {(survey?.questions ?? []).map((question, qIndex) => (
        <View key={question.id} className={styles.questionBlock}>
          <Text className={styles.questionText}>
            {qIndex + 1}. {question.text}
            {question.required && <Text className={styles.requiredMark}> *</Text>}
          </Text>
          {question.type === 'TEXT' ? (
            <Input
              className={styles.textInput}
              value={textAnswers[question.id] ?? ''}
              placeholder='请输入你的回答'
              placeholderClass={styles.inputPlaceholder}
              maxlength={200}
              onInput={(e) => setTextAnswers((prev) => ({ ...prev, [question.id]: e.detail.value }))}
            />
          ) : (
            <View className={styles.optionList}>
              {question.options.map((option, optionIndex) => {
                const active = (optionAnswers[question.id] ?? []).includes(optionIndex)
                return (
                  <View key={optionIndex} className={styles.optionRow} onClick={() => toggleOption(question.id, optionIndex, question.type === 'MULTI')}>
                    <Text className={`${styles.optionMark} ${active ? styles.optionMarkActive : ''}`}>
                      {question.type === 'MULTI' ? (active ? '☑' : '☐') : active ? '◉' : '○'}
                    </Text>
                    <Text className={`${styles.optionText} ${active ? styles.optionTextActive : ''}`}>{option}</Text>
                  </View>
                )
              })}
            </View>
          )}
        </View>
      ))}

      <View className={styles.footer}>
        <Text className={styles.total}>{survey?.responseCount ?? 0} 人已答</Text>
        <View
          className={`${styles.submitBtn} ${answered ? '' : styles.submitBtnDisabled}`}
          onClick={submitting || !answered ? undefined : handleSubmit}
        >
          <Text className={styles.submitBtnText}>{submitting ? '提交中...' : '提交问卷'}</Text>
        </View>
      </View>
    </View>
  )
}
