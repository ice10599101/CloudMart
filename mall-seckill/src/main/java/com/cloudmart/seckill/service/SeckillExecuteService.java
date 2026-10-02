package com.cloudmart.seckill.service;

import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;

public interface SeckillExecuteService {

    /**
     * 执行秒杀（T09）：返回携带 requestId 的结果——客户端凭 requestId 轮询
     * {@link #getSeckillResultByRequest}，刷新后继续查原请求；
     * 只有终态失败允许重新发起。
     */
    SeckillResultDTO executeSeckill(Long userId, SeckillExecuteRequest request);

    /** 按用户+活动+商品查既有请求结果（DB 事实，Redis 投影可重建） */
    SeckillResultDTO getSeckillResult(Long userId, Long activityId, Long seckillProductId);

    /** 按 requestId 查结果（归属校验：只能查本人的请求） */
    SeckillResultDTO getSeckillResultByRequest(Long userId, String requestId);
}
