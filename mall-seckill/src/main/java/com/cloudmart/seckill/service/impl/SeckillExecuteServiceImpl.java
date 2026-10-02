package com.cloudmart.seckill.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillMessage;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.mq.SeckillMQProducer;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillExecuteService;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.service.SeckillRequestService;
import com.cloudmart.seckill.support.SeckillRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 秒杀执行编排（T09）：Redis 只做流量预筛（售罄/重复提前拒绝），
 * 购买限额与库存以 DB 事实为准（请求行唯一键 + available_stock 原子预减），
 * 成交价在请求落库时冻结，requestId 贯穿消息/订单/查询。
 *
 * <p>发送未知不回补：MQ 发送失败时请求保持 PENDING 且 Redis/DB 占用不回滚——
 * 超时不代表 Broker 未收，盲目回补会双卖；恢复任务按请求事实重发（消费侧
 * request_key 幂等）或超时终态失败并释放占用。</p>
 */
@Service
public class SeckillExecuteServiceImpl implements SeckillExecuteService {

    private static final Logger log = LoggerFactory.getLogger(SeckillExecuteServiceImpl.class);

    private static final int SOLD_OUT_MARKER_MAX_SIZE = 10000;

    private final SeckillActivityMapper activityMapper;
    private final SeckillProductMapper productMapper;
    private final SeckillProductService seckillProductService;
    private final SeckillRequestService requestService;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final SeckillMQProducer mqProducer;

    private final ConcurrentHashMap<String, Boolean> soldOutMarkers = new ConcurrentHashMap<>();

    public SeckillExecuteServiceImpl(SeckillActivityMapper activityMapper,
                                     SeckillProductMapper productMapper,
                                     SeckillProductService seckillProductService,
                                     SeckillRequestService requestService,
                                     StringRedisTemplate redisTemplate,
                                     SeckillMQProducer mqProducer) {
        this.activityMapper = activityMapper;
        this.productMapper = productMapper;
        this.seckillProductService = seckillProductService;
        this.requestService = requestService;
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
        LocalDateTime now = LocalDateTime.now();
        if (!"ONGOING".equals(activity.getStatus())
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            return SeckillResultDTO.of("FAILED", null, "活动未开始或已结束", null);
        }

        SeckillProduct product = productMapper.selectById(request.seckillProductId());
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "秒杀商品不存在");
        }
        // T09：活动与活动 SKU 必须匹配——活动 A 不接受活动 B 的 SKU
        if (!activity.getId().equals(product.getActivityId())) {
            throw new BusinessException("PRODUCT_ACTIVITY_MISMATCH", "秒杀商品不属于该活动");
        }
        if (!"ON_SHELF".equals(product.getStatus())) {
            return SeckillResultDTO.of("FAILED", null, "秒杀商品已下架", null);
        }

        String stockKey = stockKey(request.activityId(), request.seckillProductId());
        String userSetKey = userSetKey(request.activityId(), request.seckillProductId());

        // 既有资格幂等返回：刷新/重复提交继续查原请求，只有终态失败可重新发起
        SeckillRequest existing = requestService.findByUser(userId, request.activityId(), request.seckillProductId());
        if (existing != null && !SeckillRequest.STATUS_FAILED.equals(existing.getStatus())) {
            return toResult(existing);
        }

        if (soldOutMarkers.containsKey(stockKey)) {
            return SeckillResultDTO.of("FAILED", null, "商品已售罄", null);
        }

        // 兜底：Redis 库存 key 缺失（实例重启后未预热）时从 DB 回填一次，
        // 避免把"未预热"误判为"已售罄"
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            seckillProductService.loadStockToRedis(request.activityId(), request.seckillProductId());
        }

        Long luaResult = redisTemplate.execute(
                seckillScript,
                List.of(stockKey, userSetKey),
                userId.toString(), "1"
        );

        if (luaResult == null) {
            return SeckillResultDTO.of("FAILED", null, "系统异常，请重试", null);
        }

        if (luaResult.intValue() == 0) {
            markSoldOut(stockKey);
            return SeckillResultDTO.of("FAILED", null, "商品已售罄", null);
        }
        if (luaResult.intValue() == 2) {
            // Redis 集合里已有用户，但走到这里说明 DB 事实不存在或已终态失败——
            // 集合是陈旧投影（释放时 Redis 故障等），回退集合成员，让 DB 唯一键裁决
            redisTemplate.opsForSet().remove(userSetKey, userId.toString());
        }

        SeckillRequest held;
        try {
            held = requestService.holdSeat(userId, request.activityId(), request.seckillProductId(),
                    product.getSkuId(), product.getSeckillPrice(), 1);
        } catch (SeckillRequestService.SeatSoldOutException e) {
            // DB 口径售罄：回退本次 Redis 预扣，投影与事实对齐
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(userSetKey, userId.toString());
            markSoldOut(stockKey);
            return SeckillResultDTO.of("FAILED", null, "商品已售罄", null);
        } catch (SeckillRequestService.SeatExistsException e) {
            return toResult(e.existing());
        }

        sendOrderMessage(held);
        return SeckillResultDTO.of("PENDING", null, "排队中，请稍候", held.getRequestId());
    }

    /**
     * 发送下单消息（发送未知不回补）：异常只记录——请求行出生即调度恢复检查，
     * 恢复任务会重发本消息（消费侧以 requestId 幂等）。
     */
    private void sendOrderMessage(SeckillRequest held) {
        SeckillMessage message = new SeckillMessage(held.getRequestId(), held.getUserId(),
                held.getActivityId(), held.getProductId(), held.getSkuId(),
                held.getSeckillPrice(), held.getQuantity());
        try {
            mqProducer.sendSeckillMessage(message);
        } catch (Exception e) {
            log.error("[T09] 秒杀消息发送未知结果，交由恢复任务按请求事实处理 requestId={}",
                    held.getRequestId(), e);
        }
    }

    @Override
    public SeckillResultDTO getSeckillResult(Long userId, Long activityId, Long seckillProductId) {
        SeckillRequest request = requestService.findByUser(userId, activityId, seckillProductId);
        if (request == null) {
            return SeckillResultDTO.of("FAILED", null, "未找到秒杀记录", null);
        }
        return toResult(request);
    }

    @Override
    public SeckillResultDTO getSeckillResultByRequest(Long userId, String requestId) {
        SeckillRequest request = requestService.findByRequestId(requestId);
        if (request == null || !request.getUserId().equals(userId)) {
            // 归属校验：不能凭 requestId 探测他人秒杀结果
            return SeckillResultDTO.of("FAILED", null, "未找到秒杀记录", null);
        }
        return toResult(request);
    }

    private SeckillResultDTO toResult(SeckillRequest request) {
        return switch (request.getStatus()) {
            case SeckillRequest.STATUS_PENDING -> SeckillResultDTO.of("PENDING", null,
                    "排队中，请稍候", request.getRequestId());
            case SeckillRequest.STATUS_SUCCESS -> SeckillResultDTO.of("SUCCESS", request.getOrderId(),
                    "秒杀成功", request.getRequestId());
            default -> SeckillResultDTO.of("FAILED", null,
                    request.getFailReason() == null ? "秒杀失败" : request.getFailReason(),
                    request.getRequestId());
        };
    }

    private void markSoldOut(String stockKey) {
        soldOutMarkers.put(stockKey, true);
        if (soldOutMarkers.size() > SOLD_OUT_MARKER_MAX_SIZE) {
            evictExpiredMarkers();
        }
    }

    private String stockKey(Long activityId, Long productId) {
        return SeckillRedisKeys.stockKey(activityId, productId);
    }

    private String userSetKey(Long activityId, Long productId) {
        return SeckillRedisKeys.userSetKey(activityId, productId);
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
        return SeckillResultDTO.of("FAILED", null, "请求过于频繁，请稍后再试", null);
    }

}
