import { useEffect, useState } from 'react'
import { Button, Checkbox, Empty, Input, Progress, Radio, Spin, Tag } from 'antd'
import { BarChartOutlined } from '@ant-design/icons'
import { getPoll, votePoll } from '@/api/attachment'
import type { PollData } from '@/api/attachment'
import { useAuthStore } from '@/stores/auth'
import { history } from 'umi'
import styles from './attachments.module.css'

/**
 * 投票交互块（RichText 渲染 data-poll 节点时使用）。
 *
 * 显示问题/选项/票数百分比；登录用户可投票（单选/多选），未投票时也可查看实时结果。
 * pollId 缺失或查询失败时按插入时快照（config）静态展示。
 */

interface PollBlockProps {
  pollId: string | null | undefined
  config: { question: string; options: string[]; multiple: boolean } | null
}

export default function PollBlock({ pollId, config }: PollBlockProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const [poll, setPoll] = useState<PollData | null>(null)
  const [loading, setLoading] = useState(Boolean(pollId))
  const [selected, setSelected] = useState<number[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (!pollId) return
    setLoading(true)
    getPoll(pollId)
      .then(({ data: res }) => {
        if (res.success && res.data) {
          setPoll(res.data)
          setSelected(res.data.myOptionIds ?? [])
        } else {
          setNotFound(true)
        }
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [pollId])

  const question = poll?.question ?? config?.question ?? '投票'
  const options = poll?.options ?? (config?.options ?? []).map((content, index) => ({ id: index, content, voteCount: 0 }))
  const isMultiple = poll?.multiple ?? config?.multiple ?? false
  const hasVoted = poll ? (poll.myOptionIds?.length ?? 0) > 0 : false
  const totalVotes = poll?.totalVotes ?? 0
  const maxVotes = Math.max(1, ...options.map((option) => option.voteCount))

  const handleSubmit = async () => {
    if (!pollId || !poll || submitting) return
    if (!accessToken) {
      history.push('/login')
      return
    }
    setSubmitting(true)
    try {
      await votePoll(pollId, selected)
      const { data: res } = await getPoll(pollId)
      if (res.success && res.data) setPoll(res.data)
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
        <BarChartOutlined className={styles.attachmentHeadIcon} />
        <span className={styles.attachmentHeadText}>{question}</span>
        <Tag color={isMultiple ? 'purple' : 'blue'} style={{ marginLeft: 'auto' }}>
          {isMultiple ? '多选' : '单选'}
        </Tag>
      </div>

      {notFound && config ? (
        <>
          <div className={styles.pollOptionList}>
            {options.map((option) => (
              <div key={option.id} className={styles.pollOptionRow}>
                <span>{option.content}</span>
              </div>
            ))}
          </div>
          <div className={styles.pollHint}>发布后即可参与投票</div>
        </>
      ) : (
        <>
          <div className={styles.pollOptionList}>
            {options.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无选项" />
            ) : (
              options.map((option) => (
                <div key={option.id} className={styles.pollOptionRow}>
                  <div className={styles.pollOptionControl}>
                    {isMultiple ? (
                      <Checkbox
                        checked={selected.includes(option.id)}
                        disabled={hasVoted || !accessToken}
                        onChange={(event) =>
                          setSelected((prev) =>
                            event.target.checked
                              ? [...prev, option.id]
                              : prev.filter((item) => item !== option.id),
                          )
                        }
                      >
                        {option.content}
                      </Checkbox>
                    ) : (
                      <Radio
                        checked={selected.includes(option.id)}
                        disabled={hasVoted || !accessToken}
                        onChange={() => setSelected([option.id])}
                      >
                        {option.content}
                      </Radio>
                    )}
                  </div>
                  {(hasVoted || totalVotes > 0) && (
                    <div className={styles.pollOptionResult}>
                      <Progress
                        percent={Math.round((option.voteCount / maxVotes) * 100)}
                        size="small"
                        showInfo={false}
                        strokeColor="#00d4ff"
                      />
                      <span className={styles.pollOptionCount}>
                        {option.voteCount} 票
                      </span>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>

          <div className={styles.pollFooter}>
            <span className={styles.pollTotal}>{totalVotes} 人参与</span>
            {!hasVoted && accessToken && (
              <Button
                type="primary"
                size="small"
                disabled={selected.length === 0 || (poll ? poll.options.length === 0 : false)}
                loading={submitting}
                onClick={handleSubmit}
              >
                投票
              </Button>
            )}
            {!accessToken && <span className={styles.pollHint}>登录后参与投票</span>}
          </div>
        </>
      )}
    </div>
  )
}
