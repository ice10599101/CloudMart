import { useCallback, useRef } from 'react'
import { Button, Modal } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { hashString, seededRandom, wrapText } from '@/utils/shareCard'

/**
 * 心愿分享卡片（Sprint 1.5）：Canvas 绘制星空主题卡片，支持预览 + PNG 下载。
 * 卡片内容：星空背景（确定性星点，同心愿视觉稳定）+ 心愿标题 + 作者 + 日期 + 果实标签。
 */

export interface ShareCardProps {
    title: string
    author: string
    dateText: string
    /** 果实标签文案与颜色（antd 颜色名可直接用作 canvas fillStyle） */
    fruitLabel: string
    fruitColor: string
    open: boolean
    onClose: () => void
}

const CARD_W = 750
const CARD_H = 1000
const TITLE_MAX_LINES = 6

/** 卡片内容字段（不含弹窗控制） */
type CardContent = Omit<ShareCardProps, 'open' | 'onClose'>

/** 星空卡片绘制（Canvas 2D） */
function drawCard(canvas: HTMLCanvasElement, props: CardContent) {
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const { title, author, dateText, fruitLabel, fruitColor } = props

    // ---- 背景渐变（夜空）----
    const bg = ctx.createLinearGradient(0, 0, 0, CARD_H)
    bg.addColorStop(0, '#0b1026')
    bg.addColorStop(0.55, '#1b2447')
    bg.addColorStop(1, '#2b1e4f')
    ctx.fillStyle = bg
    ctx.fillRect(0, 0, CARD_W, CARD_H)

    // ---- 星点（确定性：同标题同星空）----
    const rand = seededRandom(hashString(title || 'wish'))
    for (let i = 0; i < 220; i++) {
        const x = rand() * CARD_W
        const y = rand() * CARD_H * 0.86
        const r = 0.4 + rand() * 1.5
        ctx.globalAlpha = 0.35 + rand() * 0.65
        ctx.fillStyle = rand() > 0.82 ? '#ffd97a' : '#ffffff'
        ctx.beginPath()
        ctx.arc(x, y, r, 0, Math.PI * 2)
        ctx.fill()
    }
    ctx.globalAlpha = 1

    // ---- 月亮（右上，带光晕）----
    const moonX = CARD_W - 130
    const moonY = 150
    const moonR = 46
    const glow = ctx.createRadialGradient(moonX, moonY, moonR * 0.4, moonX, moonY, moonR * 2.6)
    glow.addColorStop(0, 'rgba(255, 240, 200, 0.35)')
    glow.addColorStop(1, 'rgba(255, 240, 200, 0)')
    ctx.fillStyle = glow
    ctx.fillRect(moonX - moonR * 3, moonY - moonR * 3, moonR * 6, moonR * 6)
    ctx.fillStyle = '#fdf6d8'
    ctx.beginPath()
    ctx.arc(moonX, moonY, moonR, 0, Math.PI * 2)
    ctx.fill()

    // ---- 顶部品牌 ----
    ctx.textAlign = 'center'
    ctx.fillStyle = 'rgba(255, 255, 255, 0.55)'
    ctx.font = '26px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText('✦ 心 愿 宇 宙 ✦', CARD_W / 2, 110)

    // ---- 心愿标题（自动换行 + 省略）----
    ctx.fillStyle = '#ffffff'
    ctx.font = 'bold 40px "PingFang SC", "Microsoft YaHei", sans-serif'
    const maxWidth = CARD_W - 140
    const lines = wrapText(title, maxWidth, TITLE_MAX_LINES, (t) => ctx.measureText(t).width)
    const lineHeight = 58
    const titleTop = 300
    lines.forEach((line, i) => {
        ctx.fillText(line, CARD_W / 2, titleTop + i * lineHeight)
    })

    // ---- 标题下方星光分隔 ----
    const dividerY = titleTop + lines.length * lineHeight + 18
    ctx.fillStyle = 'rgba(255, 217, 122, 0.9)'
    ctx.beginPath()
    ctx.arc(CARD_W / 2 - 60, dividerY, 3, 0, Math.PI * 2)
    ctx.arc(CARD_W / 2, dividerY + 4, 4, 0, Math.PI * 2)
    ctx.arc(CARD_W / 2 + 60, dividerY, 3, 0, Math.PI * 2)
    ctx.fill()

    // ---- 果实标签（圆角胶囊）----
    ctx.font = '24px "PingFang SC", "Microsoft YaHei", sans-serif'
    const label = fruitLabel
    const labelW = ctx.measureText(label).width + 44
    const labelH = 44
    const labelX = (CARD_W - labelW) / 2
    const labelY = dividerY + 42
    ctx.fillStyle = `${fruitColor}33`
    ctx.strokeStyle = fruitColor
    ctx.lineWidth = 1.5
    if (typeof ctx.roundRect === 'function') {
        ctx.beginPath()
        ctx.roundRect(labelX, labelY, labelW, labelH, 22)
        ctx.fill()
        ctx.stroke()
    } else {
        ctx.fillRect(labelX, labelY, labelW, labelH)
        ctx.strokeRect(labelX, labelY, labelW, labelH)
    }
    ctx.fillStyle = '#ffffff'
    ctx.fillText(label, CARD_W / 2, labelY + 30)

    // ---- 底部信息 ----
    ctx.textAlign = 'center'
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'
    ctx.font = '28px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText(`许愿人：${author}`, CARD_W / 2, CARD_H - 130)
    ctx.fillStyle = 'rgba(255, 255, 255, 0.45)'
    ctx.font = '22px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText(`许愿于 ${dateText}`, CARD_W / 2, CARD_H - 84)
    ctx.fillText('—— 愿望终会实现 ——', CARD_W / 2, CARD_H - 46)

    // ---- 边框 ----
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)'
    ctx.lineWidth = 2
    ctx.strokeRect(18, 18, CARD_W - 36, CARD_H - 36)
}

/** 下载文件名（去文件系统非法字符） */
export function buildCardFileName(title: string): string {
    return `${title.replace(/[\\/:*?"<>|]/g, '')}-心愿卡片.png`
}

export default function ShareCardModal({ open, onClose, ...cardProps }: ShareCardProps) {
    const canvasRef = useRef<HTMLCanvasElement | null>(null)
    const propsRef = useRef(cardProps)
    propsRef.current = cardProps

    /**
     * callback ref：canvas 节点挂载时立即绘制。
     * 不用 useEffect——antd Modal(destroyOnHidden) 的 portal 内容挂载时序
     * 与 effect 的相对顺序不稳定，callback ref 保证画到当前可见节点上。
     */
    const attachCanvas = useCallback((node: HTMLCanvasElement | null) => {
        canvasRef.current = node
        if (node) {
            drawCard(node, propsRef.current)
        }
    }, [])

    const handleDownload = () => {
        const canvas = canvasRef.current
        if (!canvas) return
        canvas.toBlob((blob) => {
            if (!blob) return
            const url = URL.createObjectURL(blob)
            const link = document.createElement('a')
            link.href = url
            link.download = buildCardFileName(cardProps.title)
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            URL.revokeObjectURL(url)
        }, 'image/png')
    }

    return (
        <Modal
            title="分享心愿卡片"
            open={open}
            onCancel={onClose}
            footer={[
                <Button key="download" type="primary" icon={<DownloadOutlined />} onClick={handleDownload}>
                    下载 PNG
                </Button>,
            ]}
            width={420}
            destroyOnHidden
        >
            <canvas
                ref={attachCanvas}
                width={CARD_W}
                height={CARD_H}
                style={{ display: 'block', width: '100%', height: 'auto', borderRadius: 12 }}
            />
        </Modal>
    )
}
