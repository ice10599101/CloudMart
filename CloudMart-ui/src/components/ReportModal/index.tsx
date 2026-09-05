import { useState } from 'react'
import { Modal, Select, Input } from 'antd'
import { message } from '@/utils/appMessage'
import { WarningOutlined } from '@ant-design/icons'
import { createReport } from '@/api/community'

interface ReportModalProps {
  visible: boolean
  onClose: () => void
  targetType: 'POST' | 'COMMENT'
  targetId: number
}

const REPORT_REASONS = [
  { value: '垃圾广告', label: '垃圾广告' },
  { value: '色情低俗', label: '色情低俗' },
  { value: '违法违规', label: '违法违规' },
  { value: '侵权抄袭', label: '侵权抄袭' },
  { value: '人身攻击', label: '人身攻击' },
  { value: '虚假信息', label: '虚假信息' },
  { value: '其他', label: '其他' },
]

const TARGET_TYPE_LABEL: Record<string, string> = {
  POST: '帖子',
  COMMENT: '评论',
}

export default function ReportModal({ visible, onClose, targetType, targetId }: ReportModalProps) {
  const [reason, setReason] = useState<string | undefined>(undefined)
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleClose = () => {
    setReason(undefined)
    setDescription('')
    setSubmitting(false)
    onClose()
  }

  const handleSubmit = async () => {
    if (!reason) {
      message.warning('请选择举报原因')
      return
    }
    setSubmitting(true)
    try {
      await createReport({
        targetType,
        targetId,
        reason,
        description: description.trim() || undefined,
      })
      message.success('举报已提交，我们会尽快处理')
      handleClose()
    } catch {
      message.error('举报提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={visible}
      onCancel={handleClose}
      onOk={handleSubmit}
      okText="提交举报"
      cancelText="取消"
      confirmLoading={submitting}
      okButtonProps={{
        disabled: !reason,
        style: {
          background: reason ? 'linear-gradient(135deg, #FF6B6B, #E04040)' : undefined,
          borderColor: 'transparent',
        },
      }}
      cancelButtonProps={{
        style: { border: '1px solid rgba(255,255,255,0.12)', color: 'var(--color-text-secondary)' },
      }}
      width={460}
      centered
      title={null}
      styles={{
        body: { padding: 0 },
        mask: { background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' },
      }}
      destroyOnHidden
    >
      <div style={{ padding: '28px 28px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
          <div style={{
            width: 40,
            height: 40,
            borderRadius: 10,
            background: 'rgba(255, 107, 107, 0.12)',
            border: '1px solid rgba(255, 107, 107, 0.25)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#FF6B6B',
            fontSize: 18,
          }}>
            <WarningOutlined />
          </div>
          <div>
            <h2 style={{ color: 'var(--color-text-secondary)', fontSize: 18, fontWeight: 700, margin: 0 }}>
              举报{TARGET_TYPE_LABEL[targetType]}
            </h2>
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              举报类型：{TARGET_TYPE_LABEL[targetType]}
            </span>
          </div>
        </div>

        <div style={{ marginBottom: 20 }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, fontWeight: 500, marginBottom: 8 }}>
            举报原因 <span style={{ color: '#FF6B6B' }}>*</span>
          </div>
          <Select
            value={reason}
            onChange={setReason}
            placeholder="请选择举报原因"
            options={REPORT_REASONS}
            style={{ width: '100%' }}
            popupClassName="report-reason-dropdown"
          />
        </div>

        <div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 13, fontWeight: 500, marginBottom: 8 }}>
            补充说明 <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>(选填)</span>
          </div>
          <Input.TextArea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="请详细描述举报原因，帮助我们更快处理..."
            autoSize={{ minRows: 3, maxRows: 6 }}
            maxLength={500}
            showCount
            style={{
              background: 'var(--color-bg-input)',
              border: '1px solid var(--color-border)',
              borderRadius: 10,
              color: 'var(--color-text-secondary)',
              fontSize: 14,
              padding: '10px 16px',
              resize: 'none',
            }}
          />
        </div>
      </div>
    </Modal>
  )
}
