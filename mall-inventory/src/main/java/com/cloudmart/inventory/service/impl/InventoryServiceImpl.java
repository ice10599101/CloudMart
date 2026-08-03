package com.cloudmart.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.entity.Inventory;
import com.cloudmart.inventory.entity.InventoryLog;
import com.cloudmart.inventory.repository.InventoryLogMapper;
import com.cloudmart.inventory.repository.InventoryMapper;
import com.cloudmart.inventory.service.InventoryService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private static final String INVENTORY_KEY_PREFIX = "inventory:product:";
    private static final String LOCK_KEY_PREFIX = "lock:inventory:sku:";
    private static final long CACHE_TTL_SECONDS = 3600;
    private static final long TTL_JITTER_SECONDS = 300;
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 10;

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final InventoryConverter inventoryConverter;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductInventoryScript;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public InventoryServiceImpl(InventoryMapper inventoryMapper,
                                InventoryLogMapper inventoryLogMapper,
                                InventoryConverter inventoryConverter,
                                StringRedisTemplate redisTemplate,
                                DefaultRedisScript<Long> deductInventoryScript,
                                RedissonClient redissonClient,
                                TransactionTemplate transactionTemplate) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
        this.inventoryConverter = inventoryConverter;
        this.redisTemplate = redisTemplate;
        this.deductInventoryScript = deductInventoryScript;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Page<InventoryDTO> listInventory(Long productId, int page, int size) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(productId != null, Inventory::getProductId, productId)
                .orderByDesc(Inventory::getUpdatedAt);

        Page<Inventory> inventoryPage = inventoryMapper.selectPage(new Page<>(page, size), wrapper);
        Page<InventoryDTO> dtoPage = new Page<>(inventoryPage.getCurrent(), inventoryPage.getSize(), inventoryPage.getTotal());
        dtoPage.setRecords(inventoryPage.getRecords().stream().map(inventoryConverter::toDTO).toList());
        return dtoPage;
    }

    @Override
    @SentinelResource(value = "getStock", fallback = "getStockFallback")
    public InventoryDTO getInventory(Long skuId) {
        String key = INVENTORY_KEY_PREFIX + skuId;

        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId)
        );
        if (inventory == null) {
            throw new BusinessException("INVENTORY_NOT_FOUND", "库存记录不存在");
        }

        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            redisTemplate.opsForValue().set(key, String.valueOf(inventory.getAvailable()), buildTtlWithJitter());
        }

        return inventoryConverter.toDTO(inventory);
    }

    @Override
    @SentinelResource(value = "deductStock", fallback = "deductStockFallback")
    public boolean deductStock(DeductRequest request) {
        if (request.quantity() <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "扣减数量必须大于0");
        }

        String lockKey = LOCK_KEY_PREFIX + request.skuId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INVENTORY_LOCK_INTERRUPTED", "获取库存锁被中断");
        }

        if (!acquired) {
            throw new BusinessException("INVENTORY_BUSY", "库存操作繁忙，请稍后重试");
        }

        try {
            Boolean result = transactionTemplate.execute(status -> doDeductStock(request));
            return Boolean.TRUE.equals(result);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean doDeductStock(DeductRequest request) {
        String key = INVENTORY_KEY_PREFIX + request.skuId();
        Long result = redisTemplate.execute(
                deductInventoryScript,
                Collections.singletonList(key),
                String.valueOf(request.quantity())
        );

        if (result == null || result == 0L) {
            log.warn("库存预扣失败, skuId={}, quantity={}", request.skuId(), request.quantity());
            return false;
        }

        int updated = inventoryMapper.deductStock(request.skuId(), request.quantity());

        if (updated == 0) {
            redisTemplate.opsForValue().increment(key, request.quantity());
            log.warn("DB库存预扣失败, skuId={}, quantity={}", request.skuId(), request.quantity());
            return false;
        }

        InventoryLog logEntry = new InventoryLog();
        logEntry.setSkuId(request.skuId());
        logEntry.setType("DEDUCT");
        logEntry.setQuantity(request.quantity());
        logEntry.setOrderId(request.orderId());
        inventoryLogMapper.insert(logEntry);

        return true;
    }

    @Override
    public void releaseStock(ReleaseRequest request) {
        if (request.quantity() <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "释放数量必须大于0");
        }

        String lockKey = LOCK_KEY_PREFIX + request.skuId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INVENTORY_LOCK_INTERRUPTED", "获取库存锁被中断");
        }

        if (!acquired) {
            throw new BusinessException("INVENTORY_BUSY", "库存操作繁忙，请稍后重试");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> doReleaseStock(request));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doReleaseStock(ReleaseRequest request) {
        int updated = inventoryMapper.releaseStock(request.skuId(), request.quantity());

        if (updated == 0) {
            throw new BusinessException("INVENTORY_NOT_FOUND", "库存记录不存在，无法释放");
        }

        String key = INVENTORY_KEY_PREFIX + request.skuId();
        redisTemplate.opsForValue().increment(key, request.quantity());

        InventoryLog logEntry = new InventoryLog();
        logEntry.setSkuId(request.skuId());
        logEntry.setType("RELEASE");
        logEntry.setQuantity(request.quantity());
        logEntry.setOrderId(request.orderId());
        inventoryLogMapper.insert(logEntry);
    }

    @Override
    public void confirmDeduct(Long skuId, Integer quantity, Long orderId) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "确认扣减数量必须大于0");
        }

        String lockKey = LOCK_KEY_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INVENTORY_LOCK_INTERRUPTED", "获取库存锁被中断");
        }

        if (!acquired) {
            throw new BusinessException("INVENTORY_BUSY", "库存操作繁忙，请稍后重试");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> doConfirmDeduct(skuId, quantity, orderId));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doConfirmDeduct(Long skuId, Integer quantity, Long orderId) {
        int updated = inventoryMapper.confirmDeduct(skuId, quantity);

        if (updated == 0) {
            throw new BusinessException("INVENTORY_CONFIRM_FAILED", "库存确认扣减失败，预占库存不足");
        }

        InventoryLog logEntry = new InventoryLog();
        logEntry.setSkuId(skuId);
        logEntry.setType("CONFIRM");
        logEntry.setQuantity(quantity);
        logEntry.setOrderId(orderId);
        inventoryLogMapper.insert(logEntry);
    }

    @Override
    public void initStock(Long skuId, Long productId, Integer stock) {
        String lockKey = LOCK_KEY_PREFIX + skuId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INVENTORY_LOCK_INTERRUPTED", "获取库存锁被中断");
        }

        if (!acquired) {
            throw new BusinessException("INVENTORY_BUSY", "库存操作繁忙，请稍后重试");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> doInitStock(skuId, productId, stock));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doInitStock(Long skuId, Long productId, Integer stock) {
        Inventory existing = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId)
        );

        if (existing != null) {
            existing.setAvailable(stock);
            inventoryMapper.updateById(existing);
        } else {
            Inventory inventory = new Inventory();
            inventory.setSkuId(skuId);
            inventory.setProductId(productId);
            inventory.setAvailable(stock);
            inventory.setReserved(0);
            inventoryMapper.insert(inventory);
        }

        String key = INVENTORY_KEY_PREFIX + skuId;
        redisTemplate.opsForValue().set(key, String.valueOf(stock), buildTtlWithJitter());
    }

    private Duration buildTtlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(0, TTL_JITTER_SECONDS);
        return Duration.ofSeconds(CACHE_TTL_SECONDS + jitter);
    }

    public InventoryDTO getStockFallback(Long skuId, Throwable throwable) {
        log.warn("getStock fallback triggered, skuId={}: {}", skuId, throwable.getMessage());
        return null;
    }

    public boolean deductStockFallback(DeductRequest request, Throwable throwable) {
        log.warn("deductStock fallback triggered, skuId={}: {}", request.skuId(), throwable.getMessage());
        return false;
    }
}
