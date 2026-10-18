import { useEffect, useState } from 'react'
import { Input, Modal, Radio, Typography } from 'antd'
import { setSourceState } from '@/api/admin/software'
import { useMessage } from '@/utils/useMessage'

const AVAILABILITY_OPTIONS = [
  { value: 'AVAILABLE', label: '核实可用' },
  { value: 'SUSPECTED', label: '标记可疑' },
  { value: 'INVALID', label: '标记失效' },
  { value: 'DISABLED', label: '平台禁用（无条件生效）' },
  { value: 'UNKNOWN', label: '解除处置（回到待核实）' },
]

/**
 * 来源状态处置弹窗（SW06）：链接反馈与软件来源管理共用同一组件/API。
 * 核实结论按当前内容指纹匹配应用；DISABLED 无条件生效，作者改链不能自行恢复。
 */
export default function SourceStateModal({
  open,
  softwareId,
  sourceKey,
  contextLabel,
  onClose,
  onSuccess,
}: {
  open: boolean
  softwareId: string
  sourceKey: string
  contextLabel?: string
  onClose: () => void
  onSuccess: () => void
}) {
  const message = useMessage()
  const [availability, setAvailability] = useState('AVAILABLE')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) {
      setAvailability('AVAILABLE')
      setReason('')
    }
  }, [open])

  const submit = async () => {
    if (!reason.trim()) {
      message.warning('请填写处置原因')
      return
    }
    setSubmitting(true)
    try {
      await setSourceState(softwareId, {
        sourceKey,
        availability,
        reason: reason.trim(),
      })
      message.success('来源状态已更新（核实结论按当前指纹生效）')
      onSuccess()
      onClose()
    } catch {
      // 业务错误已由请求层提示
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="来源状态处置"
      open={open}
      onCancel={onClose}
      onOk={submit}
      confirmLoading={submitting}
      okText="确认"
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        软件 {softwareId} · 来源键 {sourceKey}
        {contextLabel ? `（${contextLabel}）` : ''}。核实结论按当前内容指纹匹配应用；
        平台禁用无条件生效，需作者改链生成新指纹后才可能恢复。
      </Typography.Paragraph>
      <Radio.Group
        value={availability}
        onChange={(e) => setAvailability(e.target.value)}
        style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 12 }}
        options={AVAILABILITY_OPTIONS}
      />
      <Input.TextArea
        rows={3}
        maxLength={500}
        showCount
        placeholder="处置原因（1~500 字，必填，写入审计）"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
    </Modal>
  )
}
