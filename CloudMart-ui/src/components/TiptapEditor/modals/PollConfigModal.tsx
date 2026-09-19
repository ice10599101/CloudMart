import { useState } from 'react'
import { App, Button, Input, Modal, Space, Switch, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import styles from './modals.module.css'

/**
 * 投票配置弹窗（编辑器「投票」按钮）。
 *
 * 生成 PollConfig：问题 + 2-10 个选项 + 单/多选。
 * pollId 由编辑器生成 UUID 并随节点写入正文，发布时幂等落库。
 */

interface PollConfigModalProps {
  open: boolean
  onClose: () => void
  onSubmit: (config: { question: string; options: string[]; multiple: boolean }) => void
}

const MIN_OPTIONS = 2
const MAX_OPTIONS = 10

export default function PollConfigModal({ open, onClose, onSubmit }: PollConfigModalProps) {
  const { modal } = App.useApp()
  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState<string[]>(['', ''])
  const [multiple, setMultiple] = useState(false)
  const [questionError, setQuestionError] = useState('')
  const [optionsError, setOptionsError] = useState('')

  const reset = () => {
    setQuestion('')
    setOptions(['', ''])
    setMultiple(false)
    setQuestionError('')
    setOptionsError('')
  }

  /** 关闭一律二次确认，防止误触丢失已填写内容 */
  const requestClose = () => {
    modal.confirm({
      title: '关闭后填写的内容将丢失',
      content: '确定要关闭投票配置吗？',
      okText: '关闭并放弃',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      zIndex: 2000,
      onOk: () => {
        reset()
        onClose()
      },
    })
  }

  const handleOptionChange = (index: number, value: string) => {
    setOptions((prev) => prev.map((option, i) => (i === index ? value : option)))
  }

  const handleSubmit = () => {
    const trimmedQuestion = question.trim()
    const trimmedOptions = options.map((option) => option.trim()).filter(Boolean)
    if (!trimmedQuestion) {
      setQuestionError('请输入投票问题')
      return
    }
    if (trimmedOptions.length < MIN_OPTIONS) {
      setOptionsError(`至少需要 ${MIN_OPTIONS} 个选项`)
      return
    }
    onSubmit({ question: trimmedQuestion, options: trimmedOptions, multiple })
    reset()
    onClose()
  }

  return (
    <Modal
      title="发起投票"
      open={open}
      onCancel={requestClose}
      width={480}
      footer={
        <Space>
          <Button onClick={requestClose}>
            取消
          </Button>
          <Button type="primary" onClick={handleSubmit}>
            插入投票
          </Button>
        </Space>
      }
      destroyOnHidden
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <div>
          <Typography.Text strong>问题</Typography.Text>
          <Input
            value={question}
            onChange={(event) => {
              setQuestion(event.target.value)
              setQuestionError('')
            }}
            placeholder="例如：周末去哪里玩？"
            maxLength={200}
            showCount
            status={questionError ? 'error' : undefined}
          />
          {questionError && <Typography.Text type="danger" style={{ fontSize: 12 }}>{questionError}</Typography.Text>}
        </div>

        <div>
          <Typography.Text strong>选项（{MIN_OPTIONS}-{MAX_OPTIONS} 个）</Typography.Text>
          <Space direction="vertical" style={{ width: '100%' }} size={6}>
            {options.map((option, index) => (
              <Space.Compact key={index} style={{ width: '100%' }}>
                <Input
                  value={option}
                  onChange={(event) => handleOptionChange(index, event.target.value)}
                  placeholder={`选项 ${index + 1}`}
                  maxLength={100}
                />
                <Button
                  icon={<DeleteOutlined />}
                  disabled={options.length <= MIN_OPTIONS}
                  onClick={() => setOptions((prev) => prev.filter((_, i) => i !== index))}
                  aria-label="删除选项"
                />
              </Space.Compact>
            ))}
          </Space>
          <Button
            type="dashed"
            block
            icon={<PlusOutlined />}
            disabled={options.length >= MAX_OPTIONS}
            onClick={() => setOptions((prev) => [...prev, ''])}
            className={styles.pollAddOption}
          >
            添加选项
          </Button>
          {optionsError && <Typography.Text type="danger" style={{ fontSize: 12 }}>{optionsError}</Typography.Text>}
        </div>

        <div className={styles.pollMultipleRow}>
          <span>允许多选</span>
          <Switch checked={multiple} onChange={setMultiple} aria-label="允许多选" />
        </div>
      </Space>
    </Modal>
  )
}
