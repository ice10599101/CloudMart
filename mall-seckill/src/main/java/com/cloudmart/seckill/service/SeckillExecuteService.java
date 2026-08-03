package com.cloudmart.seckill.service;

import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;

public interface SeckillExecuteService {

    SeckillResultDTO executeSeckill(Long userId, SeckillExecuteRequest request);

    SeckillResultDTO getSeckillResult(Long userId, Long activityId, Long seckillProductId);
}
