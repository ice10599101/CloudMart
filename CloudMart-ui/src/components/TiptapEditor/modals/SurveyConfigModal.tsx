import { useState } from 'react'
import { Button, Checkbox, Input, Modal, Select, Space, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { SurveyQuestionConfig, SurveyQuestionType } from '../extensions/surveyNode'
import styles from './modals.module.css'

/**
 * 问卷配置弹窗（编辑器「问卷」按钮）。
 *
 * 生成 SurveyConfig：标题 + 1-10 道题（单选/多选/填空，可设必答）。
 * surveyId 由编辑器生成 UUID 并随节点写入正文，发布时幂等落库。
 */

interface SurveyConfigModalProps {
  open: boolean
  onClose: () => void
  onSubmit: (config: { title: string; questions: SurveyQuestionConfig[] }) => void
}

const MIN_QUESTIONS = 1
const MAX_QUESTIONS = 10

const TYPE_LABEL: Record<SurveyQuestionType, string> = {
  single: '单选',
  multi: '多选',
  text: '填空',
}

function emptyQuestion(): SurveyQuestionConfig {
  return { text: '', type: 'single', options: ['', ''], required: true }
}

export default function SurveyConfigModal({ open, onClose, onSubmit }: SurveyConfigModalProps) {
  const [title, setTitle] = useState('')
  const [questions, setQuestions] = useState<SurveyQuestionConfig[]>([emptyQuestion()])
  const [error, setError] = useState('')

  const reset = () => {
    setTitle('')
    setQuestions([emptyQuestion()])
    setError('')
  }

  const updateQuestion = (index: number, patch: Partial<SurveyQuestionConfig>) => {
    setQuestions((prev) => prev.map((question, i) => (i === index ? { ...question, ...patch } : question)))
  }

  const isDirty = title.trim() !== ''
    || questions.some((question) => question.text.trim() !== '' || question.options.some((option) => option.trim() !== ''))

  /** 关闭前二次确认：已填写内容时防误触丢失 */
  const requestClose = () => {
    if (!isDirty) {
      reset()
      onClose()
      return
    }
    Modal.confirm({
      title: '关闭后填写的内容将丢失',
      content: '确定要关闭问卷配置吗？',
      okText: '关闭并放弃',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => {
        reset()
        onClose()
      },
    })
  }

  const handleSubmit = () => {
    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setError('请输入问卷标题')
      return
    }
    const cleaned: SurveyQuestionConfig[] = []
    for (const question of questions) {
      const text = question.text.trim()
      if (!text) {
        setError('每道题都需要填写题干')
        return
      }
      if (question.type !== 'text') {
        const options = question.options.map((option) => option.trim()).filter(Boolean)
        if (options.length < 2) {
          setError(`「${text}」至少需要 2 个选项`)
          return
        }
        cleaned.push({ text, type: question.type, options, required: question.required })
      } else {
        cleaned.push({ text, type: 'text', options: [], required: question.required })
      }
    }
    onSubmit({ title: trimmedTitle, questions: cleaned })
    reset()
    onClose()
  }

  return (
    <Modal
      title="发起问卷"
      open={open}
      onCancel={requestClose}
      width={560}
      footer={
        <Space>
          <Button onClick={requestClose}>
            取消
          </Button>
          <Button type="primary" onClick={handleSubmit}>
            插入问卷
          </Button>
        </Space>
      }
      destroyOnHidden
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <div>
          <Typography.Text strong>问卷标题</Typography.Text>
          <Input
            value={title}
            onChange={(event) => {
              setTitle(event.target.value)
              setError('')
            }}
            placeholder="例如：2026 年度社区满意度调查"
            maxLength={200}
            showCount
          />
        </div>

        {questions.map((question, index) => (
          <div key={index} className={styles.surveyQuestionCard}>
            <div className={styles.surveyQuestionHead}>
              <Typography.Text strong>第 {index + 1} 题</Typography.Text>
              <Space size={8}>
                <Checkbox
                  checked={question.required}
                  onChange={(event) => updateQuestion(index, { required: event.target.checked })}
                >
                  必答
                </Checkbox>
                <Button
                  size="small"
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  disabled={questions.length <= MIN_QUESTIONS}
                  onClick={() => setQuestions((prev) => prev.filter((_, i) => i !== index))}
                  aria-label="删除题目"
                />
              </Space>
            </div>
            <Input
              value={question.text}
              onChange={(event) => {
                updateQuestion(index, { text: event.target.value })
                setError('')
              }}
              placeholder="题干，例如：你对社区功能满意吗？"
              maxLength={200}
            />
            <Space size={8} className={styles.surveyQuestionTypeRow}>
              <Select
                value={question.type}
                onChange={(value: SurveyQuestionType) =>
                  updateQuestion(index, {
                    type: value,
                    options: value === 'text' ? [] : question.options.length >= 2 ? question.options : ['', ''],
                  })
                }
                options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
                style={{ width: 96 }}
              />
              {question.type !== 'text' && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {TYPE_LABEL[question.type]}题
                </Typography.Text>
              )}
            </Space>
            {question.type !== 'text' && (
              <Space direction="vertical" style={{ width: '100%' }} size={6}>
                {question.options.map((option, optionIndex) => (
                  <Space.Compact key={optionIndex} style={{ width: '100%' }}>
                    <Input
                      value={option}
                      onChange={(event) =>
                        updateQuestion(index, {
                          options: question.options.map((item, i) => (i === optionIndex ? event.target.value : item)),
                        })
                      }
                      placeholder={`选项 ${optionIndex + 1}`}
                      maxLength={100}
                    />
                    <Button
                      icon={<DeleteOutlined />}
                      disabled={question.options.length <= 2}
                      onClick={() =>
                        updateQuestion(index, { options: question.options.filter((_, i) => i !== optionIndex) })
                      }
                      aria-label="删除选项"
                    />
                  </Space.Compact>
                ))}
                <Button
                  type="dashed"
                  block
                  icon={<PlusOutlined />}
                  disabled={question.options.length >= 10}
                  onClick={() => updateQuestion(index, { options: [...question.options, ''] })}
                >
                  添加选项
                </Button>
              </Space>
            )}
          </div>
        ))}

        <Button
          type="dashed"
          block
          icon={<PlusOutlined />}
          disabled={questions.length >= MAX_QUESTIONS}
          onClick={() => setQuestions((prev) => [...prev, emptyQuestion()])}
        >
          添加题目
        </Button>
        {error && <Typography.Text type="danger" style={{ fontSize: 12 }}>{error}</Typography.Text>}
      </Space>
    </Modal>
  )
}
