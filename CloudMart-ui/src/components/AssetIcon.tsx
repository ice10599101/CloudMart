import { useState } from 'react'

interface AssetIconProps {
  /** 资产图标：emoji 或图片 URL；空值不渲染（由调用方决定占位） */
  icon?: string | null
  /** 图片加载失败时的替代文字 */
  alt?: string
  /** 渲染尺寸（px），默认 32 */
  size?: number
}

/**
 * 虚拟资产图标渲染（emoji / URL 二态）。
 *
 * URL 场景必须 no-referrer：外链图床（如 B 站 hdslb）有 Referer 防盗链，
 * 携带本站 Referer 会被 403（地址栏直接打开正常、img 标签不显示的根因）。
 * 加载失败降级为 🎁 占位，避免破图。
 */
export default function AssetIcon({ icon, alt = '资产图标', size = 32 }: AssetIconProps) {
  const [imgFailed, setImgFailed] = useState(false)

  if (!icon) return null

  if (/^https?:\/\//.test(icon) && !imgFailed) {
    return (
      <img
        src={icon}
        alt={alt}
        referrerPolicy="no-referrer"
        onError={() => setImgFailed(true)}
        style={{ width: size, height: size, borderRadius: 6, objectFit: 'cover', flexShrink: 0 }}
      />
    )
  }

  const fallback = /^https?:\/\//.test(icon) && imgFailed ? '🎁' : icon
  return (
    <span style={{ fontSize: size * 0.7, lineHeight: `${size}px`, display: 'inline-block', flexShrink: 0 }}>
      {fallback}
    </span>
  )
}
