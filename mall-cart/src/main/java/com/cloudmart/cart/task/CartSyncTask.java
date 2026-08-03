package com.cloudmart.cart.task;

import com.cloudmart.cart.service.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CartSyncTask {

    private static final Logger log = LoggerFactory.getLogger(CartSyncTask.class);
    private static final String CART_KEY_PATTERN = "cart:user:*";
    private static final String CART_KEY_PREFIX = "cart:user:";

    private final StringRedisTemplate redisTemplate;
    private final CartService cartService;

    public CartSyncTask(StringRedisTemplate redisTemplate, CartService cartService) {
        this.redisTemplate = redisTemplate;
        this.cartService = cartService;
    }

    @Scheduled(fixedRate = 300000)
    public void syncCartToDatabase() {
        List<String> keys = new ArrayList<>();
        try (var cursor = redisTemplate.scan(ScanOptions.scanOptions().match(CART_KEY_PATTERN).count(100).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            return;
        }

        log.info("开始同步购物车数据, 共{}个用户", keys.size());

        for (String key : keys) {
            try {
                String userIdStr = key.substring(CART_KEY_PREFIX.length());
                Long userId = Long.valueOf(userIdStr);
                cartService.syncToDatabase(userId);
            } catch (NumberFormatException e) {
                log.warn("解析用户ID失败, key={}", key);
            } catch (Exception e) {
                log.error("同步购物车失败, key={}", key, e);
            }
        }

        log.info("购物车数据同步完成");
    }
}
