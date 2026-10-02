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
    private final com.cloudmart.inventory.repository.InventoryReservationMapper reservationMapper;
    private final InventoryConverter inventoryConverter;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductInventoryScript;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public InventoryServiceImpl(InventoryMapper inventoryMapper,
                                InventoryLogMapper inventoryLogMapper,
                                com.cloudmart.inventory.repository.InventoryReservationMapper reservationMapper,
                                InventoryConverter inventoryConverter,
                                StringRedisTemplate redisTemplate,
                                DefaultRedisScript<Long> deductInventoryScript,
                                RedissonClient redissonClient,
                                TransactionTemplate transactionTemplate) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
        this.reservationMapper = reservationMapper;
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
        // T04/LC04：预占必须绑定真实订单事实——零订单预占不可创建（否则后续释放/确认无台账可依）
        if (request.orderId() == null || request.orderId() <= 0) {
            throw new BusinessException("INVENTORY_ORDER_REQUIRED", "库存预占必须携带真实订单 ID");
        }

        // T04：同事实 (orderId, skuId) 幂等——持锁内先查台账；同量 RESERVED 重放直接成功，
        // 不得重复扣减；异量或已终态为冲突。锁内当前读不存在并发插入窗口。
        var existingReservation = reservationMapper.findByOrderAndSku(request.orderId(), request.skuId());
        if (existingReservation != null) {
            if ("RESERVED".equals(existingReservation.getStatus())
                    && existingReservation.getQuantity().equals(request.quantity())) {
                log.info("库存预占同事实重放, orderId={}, skuId={}, quantity={}",
                        request.orderId(), request.skuId(), request.quantity());
                return true;
            }
            throw new BusinessException("INVENTORY_DUPLICATE_RESERVATION",
                    "该订单对此 SKU 已有预占记录且事实不一致");
        }

        String key = INVENTORY_KEY_PREFIX + request.skuId();
        Long result = redisTemplate.execute(
                deductInventoryScript,
                Collections.singletonList(key),
                String.valueOf(request.quantity())
        );

        // 缓存缺失（未预热/已过期/TTL 到期）≠ 库存不足：回源 DB 预热后重试一次。
        // 本方法持有该 SKU 的分布式锁，预热-重试不会与并发扣减竞态。
        if (result != null && result == 2L) {
            log.info("库存缓存缺失, 回源 DB 预热, skuId={}", request.skuId());
            Inventory inventory = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, request.skuId()));
            if (inventory == null) {
                throw new BusinessException("INVENTORY_NOT_FOUND", "库存记录不存在");
            }
            redisTemplate.opsForValue().set(key, String.valueOf(inventory.getAvailable()), buildTtlWithJitter());
            result = redisTemplate.execute(
                    deductInventoryScript,
                    Collections.singletonList(key),
                    String.valueOf(request.quantity())
            );
        }

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

        // STOCK-01：登记订单级预占台账——UNIQUE(order_id, sku_id) 幂等，重复预占显式拒绝
        int inserted = reservationMapper.insertReservation(
                request.orderId(), request.skuId(), request.quantity());
        if (inserted == 0) {
            // 持锁下不应发生（同事实已在方法头重放返回）；兜底回滚 DB 预扣并归还 Redis
            redisTemplate.opsForValue().increment(key, request.quantity());
            throw new BusinessException("INVENTORY_DUPLICATE_RESERVATION",
                    "该订单对此 SKU 已有预占记录");
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
        // T04/LC04：释放只按本订单台账 CAS 迁移——零订单/无台账一律拒绝核查，
        // 不能按客户端声明的数量裸更新库存行（会消耗其他订单的预占）
        if (request.orderId() == null || request.orderId() <= 0) {
            throw new BusinessException("INVENTORY_ORDER_REQUIRED", "库存释放必须携带真实订单 ID");
        }
        var reservation = reservationMapper.findByOrderAndSku(request.orderId(), request.skuId());
        if (reservation == null) {
            throw new BusinessException("INVENTORY_RESERVATION_MISSING",
                    "预占台账不存在，请人工核查后处理, orderId=" + request.orderId() + ", skuId=" + request.skuId());
        }
        if ("RELEASED".equals(reservation.getStatus())) {
            log.info("预占已释放（幂等成功）, orderId={}, skuId={}", request.orderId(), request.skuId());
            return;
        }
        if (!"RESERVED".equals(reservation.getStatus())) {
            // 已确认销售：不得复用"释放预占"回补可售库存（退货应走 RETURN_INBOUND）
            throw new BusinessException("INVENTORY_RELEASE_CONFLICT",
                    "预占已确认销售，不能按释放处理");
        }
        if (reservationMapper.releaseReservation(request.orderId(), request.skuId()) == 0) {
            throw new BusinessException("INVENTORY_RELEASE_CONFLICT", "预占状态已变更，释放失败");
        }
        doReleaseStockRow(request.skuId(), reservation.getQuantity(), request.orderId());
    }

    private void doReleaseStockRow(Long skuId, Integer quantity, Long orderId) {
        int updated = inventoryMapper.releaseStock(skuId, quantity);

        if (updated == 0) {
            throw new BusinessException("INVENTORY_NOT_FOUND", "库存记录不存在或预占不足，无法释放");
        }

        String key = INVENTORY_KEY_PREFIX + skuId;
        redisTemplate.opsForValue().increment(key, quantity);

        InventoryLog logEntry = new InventoryLog();
        logEntry.setSkuId(skuId);
        logEntry.setType("RELEASE");
        logEntry.setQuantity(quantity);
        logEntry.setOrderId(orderId);
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
        // T04/LC04：确认只按本订单台账 CAS 迁移——零订单/无台账一律拒绝，不能裸更新库存行
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("INVENTORY_ORDER_REQUIRED", "库存确认必须携带真实订单 ID");
        }
        var reservation = reservationMapper.findByOrderAndSku(orderId, skuId);
        if (reservation == null) {
            throw new BusinessException("INVENTORY_RESERVATION_MISSING",
                    "预占台账不存在，请人工核查后处理, orderId=" + orderId + ", skuId=" + skuId);
        }
        if ("CONFIRMED".equals(reservation.getStatus())) {
            log.info("预占已确认（幂等成功）, orderId={}, skuId={}", orderId, skuId);
            return;
        }
        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new BusinessException("INVENTORY_CONFIRM_CONFLICT", "预占已释放，不能确认销售");
        }
        if (quantity != null && quantity > 0 && !quantity.equals(reservation.getQuantity())) {
            log.warn("确认数量与台账不一致，以台账为准, orderId={}, skuId={}, client={}, ledger={}",
                    orderId, skuId, quantity, reservation.getQuantity());
        }
        if (reservationMapper.confirmReservation(orderId, skuId) == 0) {
            throw new BusinessException("INVENTORY_CONFIRM_CONFLICT", "预占状态已变更，确认失败");
        }
        int updated = inventoryMapper.confirmDeduct(skuId, reservation.getQuantity());

        if (updated == 0) {
            throw new BusinessException("INVENTORY_CONFIRM_FAILED", "库存确认扣减失败，预占库存不足");
        }

        InventoryLog logEntry = new InventoryLog();
        logEntry.setSkuId(skuId);
        logEntry.setType("CONFIRM");
        logEntry.setQuantity(reservation.getQuantity());
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

    @Override
    public java.util.List<com.cloudmart.inventory.dto.ReservationScanDTO> scanReservationsForReconciliation(
            java.time.LocalDateTime since, long lastId, int limit) {
        // T11：台账行按订单聚合——每订单取一行（id 最小行代表创建时间/数量合计另算）
        java.util.List<com.cloudmart.inventory.entity.InventoryReservation> rows = reservationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.inventory.entity.InventoryReservation>()
                        .ge(com.cloudmart.inventory.entity.InventoryReservation::getCreatedAt, since)
                        .gt(com.cloudmart.inventory.entity.InventoryReservation::getId, lastId)
                        .orderByAsc(com.cloudmart.inventory.entity.InventoryReservation::getId)
                        .last("LIMIT " + limit));
        // 按 orderId 聚合（LinkedHashMap 保序）：status 优先级 RESERVED>CONFIRMED>RELEASED
        java.util.Map<Long, com.cloudmart.inventory.dto.ReservationScanDTO> byOrder = new java.util.LinkedHashMap<>();
        for (com.cloudmart.inventory.entity.InventoryReservation row : rows) {
            com.cloudmart.inventory.dto.ReservationScanDTO existing = byOrder.get(row.getOrderId());
            int qty = row.getQuantity() == null ? 0 : row.getQuantity();
            if (existing == null) {
                byOrder.put(row.getOrderId(), new com.cloudmart.inventory.dto.ReservationScanDTO(
                        row.getOrderId(), row.getStatus(), qty, row.getCreatedAt()));
            } else {
                int mergedQty = existing.quantity() + qty;
                String mergedStatus = "RESERVED".equals(existing.status()) || "RESERVED".equals(row.getStatus())
                        ? "RESERVED" : ("CONFIRMED".equals(existing.status()) || "CONFIRMED".equals(row.getStatus())
                        ? "CONFIRMED" : existing.status());
                byOrder.put(row.getOrderId(), new com.cloudmart.inventory.dto.ReservationScanDTO(
                        existing.orderId(), mergedStatus, mergedQty, existing.createdAt()));
            }
        }
        return new java.util.ArrayList<>(byOrder.values());
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
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务繁忙，请稍后重试");
    }

    /** Sentinel 降级只接管流量异常；业务/数据异常上抛。返回 false 会被下单方解读为"库存不足"，掩盖真实故障。 */
    public boolean deductStockFallback(DeductRequest request, Throwable throwable) {
        log.warn("deductStock fallback triggered, skuId={}: {}", request.skuId(), throwable.getMessage());
        if (throwable instanceof com.alibaba.csp.sentinel.slots.block.BlockException) {
            throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务繁忙，请稍后重试");
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务暂时不可用，请稍后重试");
    }
}
