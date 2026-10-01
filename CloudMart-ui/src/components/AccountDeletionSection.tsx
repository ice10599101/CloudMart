import { useState, useEffect, useCallback } from 'react'
import { Button, Input, Modal, Popconfirm } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { message } from '@/utils/appMessage'
import { useAuthStore } from '@/stores/auth'
import {
  applyAccountDeletion,
  cancelAccountDeletion,
  getAccountDeletionStatus,
} from '@/api/wish'

/** W03 统一编排状态（mall-user 权威；含分服务清理进度） */
interface DeletionStatus {
  status: string
  executeAfter?: string
  serviceProgress?: string
  executedAt?: string
}

/**
 * 危险区 · 注销账号（W03：mall-user 唯一权威）。
 * 自包含：状态加载 / 申请注销（仅原因，无验证码链路）/ 撤回申请。
 * 挂载位置：设置页（/settings）——用户中心不展示危险操作。
 */
export default function AccountDeletionSection() {
  const { user } = useAuthStore()
  const [deletion, setDeletion] = useState<DeletionStatus | null>(null)
  const [deletionModalOpen, setDeletionModalOpen] = useState(false)
  const [deletionReason, setDeletionReason] = useState('')
  const [deletionBusy, setDeletionBusy] = useState(false)

  const loadDeletion = useCallback(async () => {
    if (!user) return
    try {
      const res = await getAccountDeletionStatus()
      if (res.data.success) setDeletion((res.data.data ?? null) as DeletionStatus | null)
      else setDeletion(null)
    } catch {
      setDeletion(null)
    }
  }, [user])

  useEffect(() => {
    if (user) loadDeletion()
  }, [user, loadDeletion])

  const handleApplyDeletion = async () => {
    setDeletionBusy(true)
    try {
      const res = await applyAccountDeletion(deletionReason.trim() || undefined)
      if (res.data.success) {
        setDeletionModalOpen(false)
        message.success('注销申请已提交，30 天宽限期内可撤回')
        loadDeletion()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setDeletionBusy(false)
    }
  }

  const handleCancelDeletion = async () => {
    setDeletionBusy(true)
    try {
      const res = await cancelAccountDeletion()
      if (res.data.success) {
        message.success('已撤回注销申请')
        loadDeletion()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setDeletionBusy(false)
    }
  }

  if (!user) return null

  return (
    <>
      <div style={{ marginTop: 24, padding: '16px 1em', borderRadius: 8, border: '1px solid rgba(255, 77, 79, 0.4)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          <div>
            <div style={{ fontSize: 14, fontWeight: 600, color: '#ff4d4f' }}>危险区 · 注销账号</div>
            <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', marginTop: 4 }}>
              {deletion?.status === 'PENDING'
                ? `注销申请处理中：${deletion.executeAfter ? new Date(deletion.executeAfter).toLocaleDateString('zh-CN') : ''} 生效，期间可撤回`
                : '申请后进入 30 天宽限期，期间可随时撤回；到期将清除心愿等个人数据'}
            </div>
          </div>
          {deletion?.status === 'PENDING' ? (
            <Popconfirm title="确认撤回注销申请？" onConfirm={handleCancelDeletion}>
              <Button size="small" loading={deletionBusy}>撤回注销</Button>
            </Popconfirm>
          ) : (
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => setDeletionModalOpen(true)}>
              申请注销
            </Button>
          )}
        </div>
      </div>

      <Modal
        open={deletionModalOpen}
        title="申请注销账号"
        okText="确认申请"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={deletionBusy}
        onOk={handleApplyDeletion}
        onCancel={() => setDeletionModalOpen(false)}
        destroyOnHidden
      >
        <p style={{ color: '#ff4d4f', fontSize: 13 }}>
          注销后 30 天宽限期内可撤回；到期将清除心愿、成长记录等个人数据且不可恢复。
        </p>
        <Input.TextArea
          rows={2}
          maxLength={500}
          placeholder="注销原因（可选）"
          value={deletionReason}
          onChange={(e) => setDeletionReason(e.target.value)}
        />
      </Modal>
    </>
  )
}
