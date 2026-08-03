package com.cloudmart.seckill.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillMessage;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.mq.SeckillMQProducer;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillExecuteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeckillExecuteServiceImpl implements SeckillExecuteService {

    private static final Logger log = LoggerFactory.getLogger(SeckillExecuteServiceImpl.class);
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SET_KEY_PREFIX = "seckill:users:";
    private static final String RESULT_KEY_PREFIX = "seckill:result:";
    private static final long RESULT_TTL_SECONDS = 3600;
    private static final int SOLD_OUT_MARKER_MAX_SIZE = 10000;

    private final SeckillActivityMapper activityMapper;
    private final SeckillProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final SeckillMQProducer mqProducer;

    private final ConcurrentHashMap<String, Boolean> soldOutMarkers = new ConcurrentHashMap<>();

    public SeckillExecuteServiceImpl(SeckillActivityMapper activityMapper,
                                     SeckillProductMapper productMapper,
                                     StringRedisTemplate redisTemplate,
                                     SeckillMQProducer mqProducer) {
        this.activityMapper = activityMapper;
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.mqProducer = mqProducer;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/seckill_execute.lua"));
        script.setResultType(Long.class);
        this.seckillScript = script;
    }

    @Override
    @SentinelResource(value = "executeSeckill", blockHandler = "executeSeckillBlockHandler")
    public SeckillResultDTO executeSeckill(Long userId, SeckillExecuteRequest request) {
        SeckillActivity activity = activityMapper.selectById(request.activityId());
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        if (!"ONGOING".equals(activity.getStatus())) {
            return new SeckillResultDTO("FAILED", null, "活动未开始或已结束");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            return new SeckillResultDTO("FAILED", null, "活动未开始或已结束");
        }

        SeckillProduct product = productMapper.selectById(request.seckillProductId());
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "秒杀商品不存在");
        }

        String stockKey = STOCK_KEY_PREFIX + request.activityId() + ":" + request.seckillProductId();
        String userSetKey = USER_SET_KEY_PREFIX + request.activityId() + ":" + request.seckillProductId();

        if (soldOutMarkers.containsKey(stockKey)) {
            return new SeckillResultDTO("FAILED", null, "商品已售罄");
        }

        Long result = redisTemplate.execute(
                seckillScript,
                List.of(stockKey, userSetKey),
                userId.toString(), "1"
        );

        if (result == null) {
            return new SeckillResultDTO("FAILED", null, "系统异常，请重试");
        }

        return switch (result.intValue()) {
            case 0 -> {
                soldOutMarkers.put(stockKey, true);
                if (soldOutMarkers.size() > SOLD_OUT_MARKER_MAX_SIZE) {
                    evictExpiredMarkers();
                }
                yield new SeckillResultDTO("FAILED", null, "商品已售罄");
            }
            case 1 -> {
                String resultKey = RESULT_KEY_PREFIX + request.activityId() + ":" + request.seckillProductId() + ":" + userId;
                redisTemplate.opsForValue().set(resultKey, "PENDING", Duration.ofSeconds(RESULT_TTL_SECONDS));

                SeckillMessage mqMessage = new SeckillMessage(
                    userId, request.activityId(), request.seckillProductId(),
                    product.getSkuId(), product.getSeckillPrice(), 1
                );
                try {
                    mqProducer.sendSeckillMessage(mqMessage);
                    yield new SeckillResultDTO("PENDING", null, "排队中，请稍候");
                } catch (Exception e) {
                    log.error("MQ send failed, rolling back Redis stock for activityId={}, productId={}, userId={}",
                            request.activityId(), request.seckillProductId(), userId, e);
                    redisTemplate.opsForValue().increment(stockKey);
                    redisTemplate.opsForSet().remove(userSetKey, userId.toString());
                    redisTemplate.delete(resultKey);
                    yield new SeckillResultDTO("FAILED", null, "系统繁忙，请重试");
                }
            }
            case 2 -> new SeckillResultDTO("FAILED", null, "请勿重复抢购");
            default -> new SeckillResultDTO("FAILED", null, "系统异常，请重试");
        };
    }

    @Override
    public SeckillResultDTO getSeckillResult(Long userId, Long activityId, Long seckillProductId) {
        String resultKey = RESULT_KEY_PREFIX + activityId + ":" + seckillProductId + ":" + userId;
        String status = redisTemplate.opsForValue().get(resultKey);

        if (status == null) {
            return new SeckillResultDTO("FAILED", null, "未找到秒杀记录");
        }

        Long orderId = null;
        if ("SUCCESS".equals(status)) {
            String orderKey = RESULT_KEY_PREFIX + activityId + ":" + seckillProductId + ":" + userId + ":orderId";
            String orderIdStr = redisTemplate.opsForValue().get(orderKey);
            if (orderIdStr != null) {
                orderId = Long.parseLong(orderIdStr);
            }
        }

        String message = switch (status) {
            case "PENDING" -> "排队中，请稍候";
            case "SUCCESS" -> "秒杀成功";
            case "FAILED" -> "秒杀失败";
            default -> "未知状态";
        };

        return new SeckillResultDTO(status, orderId, message);
    }

    public void clearSoldOutMarker(String activityId, String productId) {
        String stockKey = STOCK_KEY_PREFIX + activityId + ":" + productId;
        soldOutMarkers.remove(stockKey);
    }

    private void evictExpiredMarkers() {
        for (Map.Entry<String, Boolean> entry : soldOutMarkers.entrySet()) {
            String stockValue = redisTemplate.opsForValue().get(entry.getKey());
            if (stockValue != null && Long.parseLong(stockValue) > 0) {
                soldOutMarkers.remove(entry.getKey());
            }
        }
        if (soldOutMarkers.size() > SOLD_OUT_MARKER_MAX_SIZE / 2) {
            soldOutMarkers.clear();
            log.warn("Sold-out markers force-cleared due to size overflow");
        }
    }

    public SeckillResultDTO executeSeckillBlockHandler(Long userId, SeckillExecuteRequest request, BlockException ex) {
        log.warn("executeSeckill blocked by Sentinel: {}", ex.getRule());
        return new SeckillResultDTO("FAILED", null, "请求过于频繁，请稍后再试");
    }

}
