package com.cloudmart.wish.enums;

/**
 * 漂流瓶状态机：FLOATING（漂流中，可被捞起）→ PICKED（已被捞起）。
 *
 * <p>捞瓶采用「随机候选 + 乐观条件更新」保证并发下同一漂流瓶只会被一人捞走。</p>
 */
public enum DriftBottleStatus {
    FLOATING,
    PICKED
}