package com.cloudmart.community.service;

import java.util.List;
import java.util.Map;

/**
 * 点赞服务接口。
 *
 * <p>基于 Redis Set + ZSet 实现高性能点赞：
 * <ul>
 *   <li>Redis Set 记录点赞关系（谁点赞了什么），提供 O(1) 的去重和查询</li>
 *   <li>Redis ZSet 作为待同步点赞数变更队列，定时任务批量消费并通过 MQ 异步更新数据库</li>
 * </ul>
 */
public interface LikeService {

    /**
     * 点赞。
     *
     * @param userId     点赞用户ID
     * @param targetType 目标类型（POST / COMMENT）
     * @param targetId   目标ID
     * @return true 表示首次点赞成功，false 表示已点赞过（幂等）
     */
    boolean like(Long userId, String targetType, Long targetId);

    /**
     * 取消点赞。
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return true 表示取消成功，false 表示原本未点赞（幂等）
     */
    boolean unlike(Long userId, String targetType, Long targetId);

    /**
     * 查询单个目标的点赞状态。
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return true 表示已点赞
     */
    boolean isLiked(Long userId, String targetType, Long targetId);

    /**
     * 批量查询点赞状态（使用 Pipeline 避免 N 次网络往返）。
     *
     * @param userId      用户ID
     * @param targetType  目标类型
     * @param targetIds   目标ID列表
     * @return key=targetId, value=是否已点赞
     */
    Map<Long, Boolean> batchIsLiked(Long userId, String targetType, List<Long> targetIds);

    /**
     * 获取用户点赞过的目标ID列表（分页）。
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param page       页码（从1开始）
     * @param size       每页数量
     * @return 目标ID列表
     */
    List<Long> getLikedTargetIds(Long userId, String targetType, int page, int size);

    /**
     * 获取用户点赞的目标总数。
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @return 点赞总数
     */
    long countLiked(Long userId, String targetType);

    /**
     * 将 Redis ZSet 中待同步的点赞数变更通过 MQ 发送，由消费者异步更新数据库。
     * 由定时任务调用。
     */
    void syncLikedTimesToMQ();
}
