import { useState } from 'react'
import { Modal, Radio, Space, Switch, message } from 'antd'
import { getLiveWidget, selfConfigLiveWidget } from '@/api/wish'

/**
 * 直播心愿挂件主播自助配置（T22"直播心愿挂件"行 Web 端入口）：
 * 仅主播本人可见（isAnchor 由页面以 user.id === room.anchorUserId 判定）；
 * 打开时回显当前挂件位置与可见性（GET /live/widget/{streamerId} 公开数据）；
 * 保存仅提交 position + isVisible——后端 updateById 忽略 null 字段，
 * styleConfig 原样保留，避免前端误写样式 JSON。
 */

const POSITION_OPTIONS = [
  { label: '左上', value: 'TOP_LEFT' },
  { label: '右上', value: 'TOP_RIGHT' },
  { label: '左下', value: 'BOTTOM_LEFT' },
  { label: '右下', value: 'BOTTOM_RIGHT' },
]

export default function WishLiveWidgetConfig({
  streamerId,
  isAnchor,
}: {
  streamerId: number
  isAnchor: boolean
}) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [position, setPosition] = useState<string>('TOP_LEFT')
  const [visible, setVisible] = useState(true)

  const openConfig = async () => {
    setOpen(true)
    setLoading(true)
    try {
      const res = await getLiveWidget(streamerId)
      const data = res.data.data
      if (data) {
        if (data.position) setPosition(data.position)
        setVisible(data.visible !== false)
      }
    } catch {
      // 回显失败不阻断：使用默认值保存（首次配置本就无回显）
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const res = await selfConfigLiveWidget({ position, isVisible: visible })
      if (res.data.success) {
        message.success('挂件配置已保存，直播间约 10 秒内生效')
        setOpen(false)
      }
    } catch (error) {
      const msg = (error as { response?: { data?: { error?: { message?: string } } } })
        ?.response?.data?.error?.message
      message.error(msg ?? '保存失败，请稍后重试')
    } finally {
      setSaving(false)
    }
  }

  if (!isAnchor) return null

  return (
    <>
      <button type="button" className="wish-widget-config-btn" onClick={openConfig}>
        🎯 挂件设置
      </button>
      <Modal
        title="心愿挂件设置"
        open={open}
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={handleSave}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>挂件展示主播心愿进度/打卡天数/星光，观众在直播间实时可见。</div>
          <div>
            <div style={{ marginBottom: 8 }}>显示位置</div>
            <Radio.Group
              options={POSITION_OPTIONS}
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              optionType="button"
              buttonStyle="solid"
              disabled={loading}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Switch checked={visible} onChange={setVisible} disabled={loading} />
            <span>在直播间展示挂件</span>
          </div>
        </Space>
      </Modal>
    </>
  )
}
