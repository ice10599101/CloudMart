import { Image, Text, View } from 'react-native'

interface AssetIconProps {
  /** 资产图标：emoji 或图片 URL；空值不渲染 */
  icon?: string | null
  size?: number
}

const isUrl = (v: string) => /^https?:\/\//.test(v)

/**
 * 虚拟资产图标（emoji / URL 二态）。
 *
 * RN 的 Image 请求（iOS NSURLSession / Android OkHttp）默认不携带 Referer，
 * 外链图床（如 B 站 hdslb）的 Referer 防盗链不影响 RN 端，无需特殊处理；
 * 组件存在的意义是把「URL 渲染成图片而非一串文字」的二态逻辑统一收口。
 */
export default function AssetIcon({ icon, size = 32 }: AssetIconProps) {
  if (!icon) return null

  if (isUrl(icon)) {
    return (
      <Image
        source={{ uri: icon }}
        style={{ width: size, height: size, borderRadius: 6, flexShrink: 0 }}
        resizeMode="cover"
      />
    )
  }

  return (
    <View style={{ height: size, justifyContent: 'center', flexShrink: 0 }}>
      <Text style={{ fontSize: Math.round(size * 0.7), lineHeight: size }}>{icon}</Text>
    </View>
  )
}
