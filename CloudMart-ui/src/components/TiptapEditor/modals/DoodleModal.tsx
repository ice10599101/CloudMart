import { useEffect, useRef, useState } from 'react'
import { App, Button, ColorPicker, Modal, Popconfirm, Space, Slider, Typography } from 'antd'
import { CheckOutlined, ClearOutlined, UndoOutlined } from '@ant-design/icons'
import { uploadFile } from '@/api/file'
import { message } from '@/utils/appMessage'
import styles from './modals.module.css'

/**
 * 涂鸦画板弹窗（编辑器「涂鸦」按钮）。
 *
 * Pointer Events 绘制（兼容触屏/鼠标/手写笔），画布导出 PNG 经 mall-file
 * 上传后以图片插入正文。撤销采用 ImageData 快照栈。
 */

interface DoodleModalProps {
  open: boolean
  onClose: () => void
  onUploaded: (url: string) => void
}

const CANVAS_WIDTH = 520
const CANVAS_HEIGHT = 340
const PALETTE = ['#333333', '#ffffff', '#f5222d', '#fa8c16', '#52c41a', '#1890ff', '#722ed1', '#eb2f96']
const MAX_UNDO_STEPS = 20

export default function DoodleModal({ open, onClose, onUploaded }: DoodleModalProps) {
  const { modal } = App.useApp()
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const drawingRef = useRef(false)
  const undoStackRef = useRef<ImageData[]>([])
  const [color, setColor] = useState(PALETTE[0])
  const [lineWidth, setLineWidth] = useState(3)
  const [uploading, setUploading] = useState(false)
  const [hasStrokes, setHasStrokes] = useState(false)

  useEffect(() => {
    if (!open) return
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    undoStackRef.current = []
    setHasStrokes(false)
  }, [open])

  const getPointerPos = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current!
    const rect = canvas.getBoundingClientRect()
    return {
      x: ((event.clientX - rect.left) / rect.width) * canvas.width,
      y: ((event.clientY - rect.top) / rect.height) * canvas.height,
    }
  }

  const pushUndoSnapshot = () => {
    const ctx = canvasRef.current?.getContext('2d')
    if (!ctx || !canvasRef.current) return
    undoStackRef.current.push(ctx.getImageData(0, 0, canvasRef.current.width, canvasRef.current.height))
    if (undoStackRef.current.length > MAX_UNDO_STEPS) undoStackRef.current.shift()
  }

  const handlePointerDown = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (uploading) return
    event.currentTarget.setPointerCapture(event.pointerId)
    pushUndoSnapshot()
    const ctx = canvasRef.current!.getContext('2d')!
    const pos = getPointerPos(event)
    drawingRef.current = true
    setHasStrokes(true)
    ctx.strokeStyle = color
    ctx.lineWidth = lineWidth
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    ctx.moveTo(pos.x, pos.y)
  }

  const handlePointerMove = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current) return
    const ctx = canvasRef.current!.getContext('2d')!
    const pos = getPointerPos(event)
    ctx.lineTo(pos.x, pos.y)
    ctx.stroke()
  }

  const handlePointerUp = () => {
    drawingRef.current = false
  }

  const handleUndo = () => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    const snapshot = undoStackRef.current.pop()
    if (!canvas || !ctx || !snapshot) return
    ctx.putImageData(snapshot, 0, 0)
    setHasStrokes(undoStackRef.current.length > 0)
  }

  const handleClear = () => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return
    pushUndoSnapshot()
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    setHasStrokes(false)
  }

  /** 关闭一律二次确认，防止误触丢失已绘制内容 */
  const requestClose = () => {
    modal.confirm({
      title: '关闭后画的内容将丢失',
      content: '确定要关闭涂鸦画板吗？',
      okText: '关闭并放弃',
      okButtonProps: { danger: true },
      cancelText: '继续涂鸦',
      zIndex: 2000,
      onOk: onClose,
    })
  }

  const handleConfirm = async () => {
    const canvas = canvasRef.current
    if (!canvas || uploading) return
    setUploading(true)
    try {
      const blob = await new Promise<Blob | null>((resolve) => {
        canvas.toBlob(resolve, 'image/png')
      })
      if (!blob) throw new Error('导出画布失败')
      const file = new File([blob], `doodle-${Date.now()}.png`, { type: 'image/png' })
      const { data: response } = await uploadFile(file)
      if (response.data?.url) {
        onUploaded(response.data.url)
        onClose()
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '涂鸦上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Modal
      title="涂鸦"
      open={open}
      onCancel={requestClose}
      width={600}
      footer={
        <Space>
          <Button onClick={requestClose}>取消</Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            disabled={!hasStrokes}
            loading={uploading}
            onClick={handleConfirm}
          >
            插入涂鸦
          </Button>
        </Space>
      }
      destroyOnHidden
    >
      <Space direction="vertical" style={{ width: '100%' }} size={10}>
        <Space size={8} wrap align="center">
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>颜色</Typography.Text>
          <ColorPicker
            value={color}
            onChange={(c) => setColor(c.toHexString())}
            showText
            presets={[{ label: '常用色', colors: [...PALETTE] }]}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>粗细</Typography.Text>
          <Slider
            min={1}
            max={20}
            value={lineWidth}
            onChange={setLineWidth}
            style={{ width: 120 }}
            aria-label="画笔粗细"
          />
          <Button size="small" icon={<UndoOutlined />} disabled={undoStackRef.current.length === 0} onClick={handleUndo}>
            撤销
          </Button>
          <Popconfirm title="清空画布？" onConfirm={handleClear}>
            <Button size="small" icon={<ClearOutlined />}>清空</Button>
          </Popconfirm>
        </Space>
        <canvas
          ref={canvasRef}
          width={CANVAS_WIDTH}
          height={CANVAS_HEIGHT}
          className={styles.doodleCanvas}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
        />
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          支持鼠标 / 触屏 / 手写笔；插入后计入每日上传配额
        </Typography.Text>
      </Space>
    </Modal>
  )
}
