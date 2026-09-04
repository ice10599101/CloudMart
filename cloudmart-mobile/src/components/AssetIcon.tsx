import { Image, Text, View } from '@tarojs/components'
import type { CSSProperties } from 'react'

interface AssetIconProps {
  /** 资产图标：emoji 或图片 URL；空值不渲染 */
  icon?: string | null
  size?: number
  style?: CSSProperties
}

const isUrl = (v: string) => /^https?:\/\//.test(v)

/**
 * 虚拟资产图标（emoji / URL 二态）。
 *
 * URL 场景必须 no-referrer：外链图床（如 B 站 hdslb）有 Referer 防盗链，
 * H5 端携带本站 Referer 会被 403（地址栏直接打开正常、Image 不显示的根因）。
 * H5 的 <img> 原生支持 referrerPolicy；小程序 Image 未识别该属性会被忽略，不影响渲染。
 */
export default function AssetIcon({ icon, size = 32, style }: AssetIconProps) {
  if (!icon) return null

  if (isUrl(icon)) {
    return (
      <Image
        src={icon}
        mode="aspectFill"
        // Taro ImageProps 未声明 referrerPolicy，展开透传：H5 端落到原生 <img> 生效；
        // 小程序 image 组件忽略未知属性（其图片请求 Referer 由微信平台决定，外链防盗链需数据源治理）
        {...({ referrerPolicy: 'no-referrer' } as Record<string, string>)}
        style={{ width: `${size}px`, height: `${size}px`, borderRadius: '6px', flexShrink: 0, ...style }}
      />
    )
  }

  return (
    <View style={{ height: `${size}px`, justifyContent: 'center', flexShrink: 0, ...style }}>
      <Text style={{ fontSize: `${Math.round(size * 0.7)}px`, lineHeight: `${size}px` }}>{icon}</Text>
    </View>
  )
}
