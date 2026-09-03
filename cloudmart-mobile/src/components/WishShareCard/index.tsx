import { View, Text, Canvas } from '@tarojs/components'
import Taro, { getCurrentInstance } from '@tarojs/taro'
import { useEffect, useRef } from 'react'
import { hashString, seededRandom, wrapText, buildCardFileName } from '@/utils/wishShareCard'
import styles from './index.module.scss'

/**
 * 心愿分享卡片（Sprint 1.5，四AC R5 Mobile）：
 * Taro Canvas(type=2d) 绘制星空卡片（与 WEB ShareCardModal 同一套确定性算法），
 * H5 端下载 PNG；小程序端保存到相册（需用户授权）。
 */

const CARD_W = 750
const CARD_H = 1000
const TITLE_MAX_LINES = 6
const FONT_STACK = 'bold 40px "PingFang SC", "Microsoft YaHei", sans-serif'

export interface WishShareCardProps {
  visible: boolean
  onClose: () => void
  title: string
  author: string
  dateText: string
  /** 果实标签文案与颜色（hex，如 #ffd700） */
  fruitLabel: string
  fruitColor: string
}

/** 星空卡片绘制（Canvas 2D，与 WEB 端视觉规格一致） */
function drawCard(ctx: CanvasRenderingContext2D, props: Omit<WishShareCardProps, 'visible' | 'onClose'>) {
  const { title, author, dateText, fruitLabel, fruitColor } = props

  // 背景渐变（夜空）
  const bg = ctx.createLinearGradient(0, 0, 0, CARD_H)
  bg.addColorStop(0, '#0b1026')
  bg.addColorStop(0.55, '#1b2447')
  bg.addColorStop(1, '#2b1e4f')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, CARD_W, CARD_H)

  // 星点（确定性：同标题同星空）
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

  // 月亮（右上，带光晕）
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

  // 顶部品牌
  ctx.textAlign = 'center'
  ctx.fillStyle = 'rgba(255, 255, 255, 0.55)'
  ctx.font = '26px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText('✦ 心 愿 宇 宙 ✦', CARD_W / 2, 110)

  // 标题（自动换行 + 省略）
  ctx.fillStyle = '#ffffff'
  ctx.font = FONT_STACK
  const maxWidth = CARD_W - 140
  const lines = wrapText(title, maxWidth, TITLE_MAX_LINES, (t) => ctx.measureText(t).width)
  const lineHeight = 58
  const titleTop = 300
  lines.forEach((line, i) => {
    ctx.fillText(line, CARD_W / 2, titleTop + i * lineHeight)
  })

  // 标题下方星光分隔
  const dividerY = titleTop + lines.length * lineHeight + 18
  ctx.fillStyle = 'rgba(255, 217, 122, 0.9)'
  ctx.beginPath()
  ctx.arc(CARD_W / 2 - 60, dividerY, 3, 0, Math.PI * 2)
  ctx.arc(CARD_W / 2, dividerY + 4, 4, 0, Math.PI * 2)
  ctx.arc(CARD_W / 2 + 60, dividerY, 3, 0, Math.PI * 2)
  ctx.fill()

  // 果实标签（圆角胶囊）
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

  // 底部信息
  ctx.textAlign = 'center'
  ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'
  ctx.font = '28px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(`许愿人：${author}`, CARD_W / 2, CARD_H - 130)
  ctx.fillStyle = 'rgba(255, 255, 255, 0.45)'
  ctx.font = '22px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(`许愿于 ${dateText}`, CARD_W / 2, CARD_H - 84)
  ctx.fillText('—— 愿望终会实现 ——', CARD_W / 2, CARD_H - 46)

  // 边框
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)'
  ctx.lineWidth = 2
  ctx.strokeRect(18, 18, CARD_W - 36, CARD_H - 36)
}

export default function WishShareCard(props: WishShareCardProps) {
  const { visible, onClose, ...cardProps } = props
  const canvasRef = useRef<{ node: HTMLCanvasElement | null }>({ node: null })

  useEffect(() => {
    if (!visible) return
    // Canvas(type=2d) 节点挂载后异步初始化；Taro 组件内需限定页面作用域
    const page = getCurrentInstance().page
    if (!page) return
    const query = Taro.createSelectorQuery().in(page)
    query
      .select('#wishShareCard')
      .fields({ node: true, size: true })
      .exec((res) => {
        const info = res?.[0]
        if (!info?.node) return
        const canvas = info.node as HTMLCanvasElement & { width: number; height: number }
        const dpr = Taro.getSystemInfoSync().pixelRatio || 2
        canvas.width = CARD_W * dpr
        canvas.height = CARD_H * dpr
        const ctx = canvas.getContext('2d')
        if (!ctx) return
        ctx.scale(dpr, dpr)
        drawCard(ctx, cardProps)
        canvasRef.current.node = canvas
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible])

  /** 保存：H5 下载 PNG / 小程序保存相册 */
  const handleSave = () => {
    const canvas = canvasRef.current.node
    if (!canvas) return
    if (Taro.getEnv() === Taro.ENV_TYPE.WEB) {
      const url = canvas.toDataURL('image/png')
      const link = document.createElement('a')
      link.href = url
      link.download = buildCardFileName(cardProps.title)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      return
    }
    Taro.canvasToTempFilePath({
      // Taro 类型将 canvas 声明为其组件实例；type=2d 模式实际传原生 node
      canvas: canvas as unknown as Parameters<typeof Taro.canvasToTempFilePath>[0]['canvas'],
      success: (tmp) => {
        Taro.saveImageToPhotosAlbum({
          filePath: tmp.tempFilePath,
          success: () => Taro.showToast({ title: '已保存到相册', icon: 'none' }),
          fail: () => Taro.showToast({ title: '未授权保存，可在设置中开启', icon: 'none' }),
        })
      },
      fail: () => Taro.showToast({ title: '生成卡片失败', icon: 'none' }),
    })
  }

  if (!visible) return null

  return (
    <View className={styles.mask} onClick={onClose}>
      <View className={styles.sheet} onClick={(e) => e.stopPropagation()}>
        <Text className={styles.sheetTitle}>分享心愿卡片</Text>
        <Canvas
          type="2d"
          id="wishShareCard"
          className={styles.canvas}
          style={{ width: '100%', height: '800rpx' }}
        />
        <View className={styles.saveBtn} onClick={handleSave}>
          <Text>保存卡片</Text>
        </View>
        <View className={styles.closeBtn} onClick={onClose}>
          <Text>关闭</Text>
        </View>
      </View>
    </View>
  )
}
