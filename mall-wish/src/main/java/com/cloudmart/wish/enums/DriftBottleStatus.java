package com.cloudmart.wish.enums;

/**
 * 漂流瓶物理状态机：FLOATING（漂流中，可被捞起）→ PICKED（已被捞起）
 * → RETURNED（被扔回海里，回到海面可再被捞起）。
 *
 * <p>捞瓶采用「随机候选 + 乐观条件更新」保证并发下同一漂流瓶只会被一人捞走；
 * 扔回海里后回到可捞池。「被回复/被收藏」为展示层状态（评论数/收藏标记推导），不入库。</p>
 */
public enum DriftBottleStatus {
    FLOATING,
    PICKED,
    RETURNED
}