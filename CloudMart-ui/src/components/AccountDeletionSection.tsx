import { useState, useEffect, useCallback } from 'react'
import { Button, Input, Modal, Popconfirm } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { message } from '@/utils/appMessage'
import { useAuthStore } from '@/stores/auth'
import {
  sendDeletionCode,
  applyAccountDeletion,
  cancelAccountDeletion,
  getAccountDeletionStatus,
  type AccountDeletionStatus,
} from '@/api/wish'

/** 发码 60 秒冷却（与服务端每用户冷却一致，仅 UI 层提示） */
const SEND_CODE_COOLDOWN_SECONDS = 60

/**
 * 危险区 · 注销账号（合规 34.2，四AB A1）。
 * 自包含：状态加载 / 发送验证码 / 申请注销 / 撤回申请。
 * 挂载位置：设置页（/settings）——用户中心不展示危险操作。
 */
export default function AccountDeletionSection() {
  const { user } = useAuthStore()
  const [deletion, setDeletion] = useState<AccountDeletionStatus | null>(null)
  const [deletionModalOpen, setDeletionModalOpen] = useState(false)
  const [deletionCode, setDeletionCode] = useState('')
  const [deletionReason, setDeletionReason] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [deletionBusy, setDeletionBusy] = useState(false)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const loadDeletion = useCallback(async () => {
    if (!user) return
    try {
      const res = await getAccountDeletionStatus()
      if (res.data.success) setDeletion(res.data.data)
      else setDeletion(null)
    } catch {
      setDeletion(null)
    }
  }, [user])

  useEffect(() => {
    if (user) loadDeletion()
  }, [user, loadDeletion])

  const handleSendCode = async () => {
    setDeletionBusy(true)
    try {
      const res = await sendDeletionCode()
      if (res.data.success) {
        const result = res.data.data as { sent?: boolean; devCode?: string; message?: string }
        // 后端不假成功：sent=false（频控/通道未配置/发送失败）时如实提示，不进入已发码态
        if (result.sent) {
          message.success(result.devCode ? `验证码已发送（开发回显：${result.devCode}）` : '验证码已发送，请查收短信/邮件')
          setCodeSent(true)
          setCountdown(SEND_CODE_COOLDOWN_SECONDS)
        } else {
          message.warning(result.message || '验证码发送失败，请稍后重试')
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setDeletionBusy(false)
    }
  }

  const handleApplyDeletion = async () => {
    if (!/\d{6}/.test(deletionCode)) {
      message.error('请输入 6 位验证码')
      return
    }
    setDeletionBusy(true)
    try {
      const res = await applyAccountDeletion(deletionCode, deletionReason.trim() || undefined)
      if (res.data.success) {
        setDeletionModalOpen(false)
        setDeletionCode('')
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
                ? `注销申请处理中：${new Date(deletion.executeAfter).toLocaleDateString('zh-CN')} 生效，期间可撤回`
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
        okButtonProps={{ danger: true, disabled: !codeSent }}
        confirmLoading={deletionBusy}
        onOk={handleApplyDeletion}
        onCancel={() => setDeletionModalOpen(false)}
        destroyOnHidden
      >
        <p style={{ color: '#ff4d4f', fontSize: 13 }}>
          注销后 30 天宽限期内可撤回；到期将清除心愿、成长记录等个人数据且不可恢复。
        </p>
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <Button
            onClick={handleSendCode}
            loading={deletionBusy}
            disabled={countdown > 0}
          >
            {countdown > 0 ? `${countdown}s 后重发` : codeSent ? '重新发送验证码' : '发送验证码'}
          </Button>
          <Input
            placeholder="6 位验证码"
            maxLength={6}
            value={deletionCode}
            onChange={(e) => setDeletionCode(e.target.value.replace(/\D/g, ''))}
            style={{ flex: 1 }}
          />
        </div>
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
