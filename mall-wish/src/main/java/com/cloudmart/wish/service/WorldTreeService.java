package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.TreeFruitsQuery;
import com.cloudmart.wish.vo.TreeFruitVO;
import com.cloudmart.wish.vo.WorldTreeVO;

import java.util.List;

/**
 * 世界生命树聚合服务（Sprint 2.1，文档 2.5 / 第二章 1.）。
 *
 * <p>两个只读接口均为四端 3D 场景数据源：</p>
 * <ul>
 *   <li>{@link #getTreeAggregation()}：树整体聚合状态（计数走 Redis 缓存
 *       TTL 5 分钟，环境/季节实时读取）</li>
 *   <li>{@link #listFruits(TreeFruitsQuery)}：树上果实列表（容量封顶单页全量，
 *       黄金角螺旋均匀布点，新果实取代旧果实）</li>
 * </ul>
 */
public interface WorldTreeService {

    /**
     * 世界树聚合状态。
     *
     * <p>计数三值（totalFruits/totalBloom/totalLight）Redis 缓存 TTL 5 分钟
     * （+随机抖动防集中过期），miss 时 SETNX 短锁防击穿；environment/season/
     * environmentUpdatedAt 实时读单行状态表 + UTC 日期计算。Redis 异常
     * Fail-Open 直查 DB，不阻塞业务。</p>
     */
    WorldTreeVO getTreeAggregation();

    /**
     * 树上果实列表（单页全量，最多 48 颗，无游标/视口过滤）。
     *
     * <p>上树口径与公开列表一致：visibility=PUBLIC + audit_status=APPROVED +
     * is_visible=1 + status ∈ (ACTIVE/FULFILLING/FULFILLED) + 未软删 +
     * tree_theta 非空。只挂最新的 48 颗；展示坐标按黄金角螺旋位次实时计算
     * （最新挂树顶，旧果实依次滑出树）。</p>
     */
    FruitPage listFruits(TreeFruitsQuery query);

    /**
     * 果实列表结果（保留分页封装字段以兼容前端协议；容量封顶后恒为单页全量）。
     *
     * @param records   树上果实（最新在前）
     * @param nextCursor 恒为 null（无翻页）
     * @param hasMore   恒为 false
     */
    record FruitPage(List<TreeFruitVO> records, String nextCursor, boolean hasMore) {
    }
}
