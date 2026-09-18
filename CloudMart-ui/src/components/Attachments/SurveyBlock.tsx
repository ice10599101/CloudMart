import { useEffect, useState } from 'react'
import { Button, Checkbox, Empty, Input, Radio, Spin, Tag } from 'antd'
import { FormOutlined } from '@ant-design/icons'
import { getSurvey, submitSurveyResponse } from '@/api/attachment'
import type { SurveyData } from '@/api/attachment'
import { useAuthStore } from '@/stores/auth'
import { message } from '@/utils/appMessage'
import { history } from 'umi'
import styles from './attachments.module.css'

/**
 * 问卷交互块（RichText 渲染 data-survey 节点时使用）。
 *
 * 登录用户可作答（必答校验、重复提交覆盖）；问卷详情含各题聚合结果。
 * surveyId 缺失或查询失败时按插入时快照（config）静态展示。
 */

interface SurveyBlockProps {
  surveyId: string | null | undefined
  config: {
    title: string
    questions: { text: string; type: 'single' | 'multi' | 'text'; options: string[]; required: boolean }[]
  } | null
}

type DraftAnswers = Record<number, { optionIds?: number[]; text?: string }>

function normalizeAnswers(answers: SurveyData['myAnswers']): DraftAnswers {
  const result: DraftAnswers = {}
  for (const answer of answers) {
    result[answer.questionId] = { optionIds: answer.optionIds ?? [], text: answer.text ?? '' }
  }
  return result
}

export default function SurveyBlock({ surveyId, config }: SurveyBlockProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const [survey, setSurvey] = useState<SurveyData | null>(null)
  const [loading, setLoading] = useState(Boolean(surveyId))
  const [draft, setDraft] = useState<DraftAnswers>({})
  const [submitting, setSubmitting] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (!surveyId) return
    setLoading(true)
    getSurvey(surveyId)
      .then(({ data: res }) => {
        if (res.success && res.data) {
          setSurvey(res.data)
          setDraft(normalizeAnswers(res.data.myAnswers ?? []))
        } else {
          setNotFound(true)
        }
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [surveyId])

  const title = survey?.title ?? config?.title ?? '问卷'
  const questions = survey?.questions ?? (config?.questions ?? []).map((question, index) => ({
    id: index,
    text: question.text,
    type: question.type.toUpperCase() as 'SINGLE' | 'MULTI' | 'TEXT',
    options: question.options,
    required: question.required,
    optionCounts: [],
    textAnswerCount: 0,
  }))
  const hasSubmitted = survey ? (survey.myAnswers?.length ?? 0) > 0 : false

  const setAnswer = (questionId: number, patch: { optionIds?: number[]; text?: string }) => {
    setDraft((prev) => ({ ...prev, [questionId]: { ...prev[questionId], ...patch } }))
  }

  const handleSubmit = async () => {
    if (!survey || submitting) return
    if (!accessToken) {
      history.push('/login')
      return
    }
    const answers = survey.questions
      .filter((question) => question.type === 'TEXT' || (draft[question.id]?.optionIds?.length ?? 0) > 0)
      .map((question) => ({
        questionId: question.id,
        optionIds: question.type === 'TEXT' ? undefined : draft[question.id]?.optionIds,
        text: question.type === 'TEXT' ? draft[question.id]?.text : undefined,
      }))
    setSubmitting(true)
    try {
      await submitSurveyResponse(survey.id, answers)
      message.success('问卷已提交')
      const { data: res } = await getSurvey(survey.id)
      if (res.success && res.data) {
        setSurvey(res.data)
        setDraft(normalizeAnswers(res.data.myAnswers ?? []))
      }
    } catch {
      // 全局拦截器已提示错误（必答缺失/题目非法）
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <div className={styles.attachmentCard}><Spin size="small" /></div>
  }

  return (
    <div className={styles.attachmentCard}>
      <div className={styles.attachmentHead}>
        <FormOutlined className={styles.attachmentHeadIcon} />
        <span className={styles.attachmentHeadText}>{title}</span>
        <Tag color="cyan" style={{ marginLeft: 'auto' }}>{survey?.responseCount ?? 0} 人已答</Tag>
      </div>

      {notFound && config ? (
        <>
          {config.questions.map((question, index) => (
            <div key={index} className={styles.surveyQuestionRow}>
              <div className={styles.surveyQuestionText}>
                {index + 1}. {question.text}
                {question.required && <span className={styles.surveyRequired}>*</span>}
              </div>
              {question.type !== 'text' && (
                <div className={styles.surveyOptionsPreview}>
                  {question.options.map((option, optionIndex) => (
                    <span key={optionIndex} className={styles.surveyOptionChip}>{option}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
          <div className={styles.pollHint}>发布后即可填写问卷</div>
        </>
      ) : (
        <>
          {questions.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="问卷暂无题目" />
          ) : (
            questions.map((question, index) => {
              const answer = draft[question.id] ?? {}
              const isChoice = question.type === 'SINGLE' || question.type === 'MULTI'
              return (
                <div key={question.id} className={styles.surveyQuestionRow}>
                  <div className={styles.surveyQuestionText}>
                    {index + 1}. {question.text}
                    {question.required && <span className={styles.surveyRequired}>*</span>}
                    <span className={styles.surveyTypeLabel}>
                      {question.type === 'SINGLE' ? '单选' : question.type === 'MULTI' ? '多选' : '填空'}
                    </span>
                  </div>
                  {isChoice && (
                    <div className={styles.surveyOptionsGroup}>
                      {question.options.map((option, optionIndex) =>
                        question.type === 'SINGLE' ? (
                          <Radio
                            key={optionIndex}
                            checked={answer.optionIds?.includes(optionIndex)}
                            disabled={hasSubmitted || !accessToken}
                            onChange={() => setAnswer(question.id, { optionIds: [optionIndex] })}
                          >
                            {option}
                            {hasSubmitted && question.optionCounts?.[optionIndex] > 0 && (
                              <span className={styles.surveyOptionCount}>（{question.optionCounts[optionIndex]} 票）</span>
                            )}
                          </Radio>
                        ) : (
                          <Checkbox
                            key={optionIndex}
                            checked={answer.optionIds?.includes(optionIndex)}
                            disabled={hasSubmitted || !accessToken}
                            onChange={(event) =>
                              setAnswer(question.id, {
                                optionIds: event.target.checked
                                  ? [...(answer.optionIds ?? []), optionIndex]
                                  : (answer.optionIds ?? []).filter((item) => item !== optionIndex),
                              })
                            }
                          >
                            {option}
                            {hasSubmitted && question.optionCounts?.[optionIndex] > 0 && (
                              <span className={styles.surveyOptionCount}>（{question.optionCounts[optionIndex]} 票）</span>
                            )}
                          </Checkbox>
                        ),
                      )}
                    </div>
                  )}
                  {question.type === 'TEXT' && (
                    <Input.TextArea
                      value={answer.text ?? ''}
                      onChange={(event) => setAnswer(question.id, { text: event.target.value })}
                      disabled={hasSubmitted || !accessToken}
                      placeholder="请输入你的回答"
                      maxLength={500}
                      autoSize={{ minRows: 2, maxRows: 6 }}
                      showCount
                    />
                  )}
                </div>
              )
            })
          )}

          {survey && (
            <div className={styles.pollFooter}>
              <span className={styles.pollTotal}>{survey.responseCount} 人已答</span>
              {!hasSubmitted && accessToken && (
                <Button type="primary" size="small" loading={submitting} onClick={handleSubmit}>
                  提交问卷
                </Button>
              )}
              {!accessToken && <span className={styles.pollHint}>登录后填写问卷</span>}
              {hasSubmitted && <span className={styles.pollHint}>已提交（重复提交将覆盖）</span>}
            </div>
          )}
        </>
      )}
    </div>
  )
}
