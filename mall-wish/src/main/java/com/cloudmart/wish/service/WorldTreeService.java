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
 *   <li>{@link #listFruits(TreeFruitsQuery)}：果实视口分页（cursor + bounds，
 *       视口外果实不返回，支持前端动态加载）</li>
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
     * 果实视口分页（cursor 游标 + bounds 球面视口过滤）。
     *
     * <p>上树口径与公开列表一致：visibility=PUBLIC + audit_status=APPROVED +
     * is_visible=1 + status ∈ (ACTIVE/FULFILLING/FULFILLED) + 未软删 +
     * tree_theta 非空。按 id DESC 排序，游标为 id；bounds 解析规则见
     * {@code TreeBoundsParser}（异常参数兜底全量，不报错）。</p>
     */
    FruitPage listFruits(TreeFruitsQuery query);

    /**
     * 果实分页结果（与既有 cursor 分页封装语义一致）。
     *
     * @param records   当前页果实
     * @param nextCursor 下一页游标（首页/无更多时为 null）
     * @param hasMore   是否还有下一页
     */
    record FruitPage(List<TreeFruitVO> records, String nextCursor, boolean hasMore) {
    }
}
