import { useCallback, useEffect, useRef, useState } from 'react'
import { Progress } from 'antd'
import { history } from 'umi'
import { getLiveWidget, type LiveWidgetData } from '@/api/wish'

/**
 * 直播心愿挂件（Sprint 3.4 WEB 端）：叠加在直播间上方，展示主播当前
 * 心愿进度/打卡天数/星光。10s 轮询（服务端 10s 缓存）；可关闭；点击
 * 跳转心愿详情；主播无心愿显示"去许愿"引导；接口失败自动隐藏（降级）。
 *
 * 性能：10s 一次数据轮询 + CSS 定位，渲染 < 5ms/帧（不参与直播流渲染）。
 */

const POLL_MS = 10_000

export default function WishLiveWidget({ streamerId }: { streamerId: number }) {
  const [widget, setWidget] = useState<LiveWidgetData | null>(null)
  const [closed, setClosed] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = useCallback(async () => {
    try {
      const res = await getLiveWidget(streamerId)
      // 接口失败/降级（visible=false 之外的服务异常）→ 保留上次数据；连续失败由 visible 控制
      if (res.data.success && res.data.data) {
        setWidget(res.data.data)
      }
    } catch {
      // wish 服务不可用：挂件数据保留上次值；首次失败则保持隐藏
      setWidget((prev) => prev)
    }
  }, [streamerId])

  useEffect(() => {
    load()
    timerRef.current = setInterval(load, POLL_MS)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [load])

  if (closed) {
    return (
      <button type="button" className="wish-live-widget-closed" onClick={() => setClosed(false)}>
        心愿
      </button>
    )
  }

  if (!widget || !widget.visible) {
    return null
  }

  return (
    <div className="wish-live-widget" role="button" tabIndex={0}
      onClick={() => widget.hasWish && widget.wishId && history.push(`/wish/${widget.wishId}`)}>
      <div className="wish-live-widget-head">
        <span className="wish-live-widget-title">
          {widget.hasWish ? `🎯 ${widget.title}` : '💫 主播还没许愿'}
        </span>
        <button
          type="button"
          className="wish-live-widget-close"
          onClick={(e) => { e.stopPropagation(); setClosed(true) }}
        >
          ✕
        </button>
      </div>
      {widget.hasWish ? (
        <>
          <Progress
            percent={widget.progressPercentage ?? 0}
            size="small"
            strokeColor={{ from: '#e94560', to: '#ffd700' }}
            format={(p) => `${p}%`}
          />
          <div className="wish-live-widget-stats">
            <span>📅 打卡 {widget.checkinDays} 天</span>
            <span>⭐ {widget.starlightBalance}</span>
          </div>
        </>
      ) : (
        <div className="wish-live-widget-guide">去许愿，和主播一起追</div>
      )}
    </div>
  )
}
