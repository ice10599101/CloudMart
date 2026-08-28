import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/**
 * 灰度功能开关客户端（Sprint 2.8，契约对齐 mall-wish FeatureFlagController）。
 *
 * <p>会话内拉取一次并内存缓存（5 分钟兜底过期）；灰度命中由服务端按
 * 用户 ID 稳定哈希判定（同一用户四端命中同一档）；匿名仅全量功能放行。
 * 降级开关语义：flag=false 时回退到上一代体验（如 3D 粒子减半/关闭
 * 自动旋转），灰度切换对用户无感知，回滚=管理端比例置 0。</p>
 */

export type FeatureKey =
  | 'wish_ai_assistant'
  | 'wish_tree_hole'
  | 'wish_time_capsule'
  | 'wish_match_squad'
  | 'wish_leaderboard'
  | 'wish_legacy_flow'
  | 'wish_world_tree_enhanced'

let cache: { flags: Record<string, boolean>; loadedAt: number } | null = null
const CACHE_TTL_MS = 5 * 60 * 1000
let inflight: Promise<Record<string, boolean>> | null = null

/** 拉取全部功能开关（并发去重；失败时全部回退 true——Fail-Open 保守降级方向） */
export async function fetchFeatureFlags(): Promise<Record<string, boolean>> {
  if (cache && Date.now() - cache.loadedAt < CACHE_TTL_MS) {
    return cache.flags
  }
  if (!inflight) {
    inflight = request
      .get<ApiResponse<Record<string, boolean>>>('/wish/feature-flags')
      .then((res) => {
        const flags = res.data.success && res.data.data ? res.data.data : {}
        cache = { flags, loadedAt: Date.now() }
        return flags
      })
      .catch(() => {
        // flags 服务不可用：按全量放行处理（灰度是增强控制，不阻断功能）
        return {} as Record<string, boolean>
      })
      .finally(() => {
        inflight = null
      })
  }
  return inflight
}

/** 同步读取（需先 fetchFeatureFlags；未加载时默认 true=全量放行） */
export function isFeatureEnabled(key: FeatureKey): boolean {
  if (!cache) {
    return true
  }
  return cache.flags[key] ?? true
}

/**
 * 机型分档（WEB 3D 性能基线，文档 2.8 机型适配）：
 * 高档=并发≥8 且内存≥8GB；中档=并发≥4；低档=其余。
 */
export function deviceTier(): 'HIGH' | 'MID' | 'LOW' {
  if (typeof navigator === 'undefined') {
    return 'MID'
  }
  const nav = navigator as Navigator & { deviceMemory?: number }
  const cores = nav.hardwareConcurrency ?? 4
  const memory = nav.deviceMemory ?? 4
  if (cores >= 8 && memory >= 8) return 'HIGH'
  if (cores >= 4) return 'MID'
  return 'LOW'
}
