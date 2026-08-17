package com.cloudmart.wish.enums;

/**
 * 心愿可见性枚举。
 *
 * <p>PRIVATE 与 TREE_HOLE 在可见性上相同（都不公开、不展示在生命树、不进入LBS地图），
 * 差异通过 {@code enableAiReply}、{@code auditStrategy}、{@code triggerEnvEmo} 特性字段控制。</p>
 */
public enum WishVisibility {
    /** 公开：展示在生命树、进入LBS地图 */
    PUBLIC,
    /** 个人私密：不公开、不展示、无AI回复、先发后审 */
    PRIVATE,
    /** 树洞：不公开、有AI治愈回复、实时审核高危词、负面情绪可触发生命树环境联动 */
    TREE_HOLE
}
