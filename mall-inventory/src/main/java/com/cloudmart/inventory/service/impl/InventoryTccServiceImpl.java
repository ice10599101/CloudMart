package com.cloudmart.inventory.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.inventory.dto.TccDeductRequest;
import com.cloudmart.inventory.service.InventoryTccService;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 库存 TCC 模式实现（Seata TCC 框架集成版）。
 *
 * <p>使用 @TwoPhaseBusinessAction 注解声明 TCC 三阶段方法，
 * Seata TC 自动管理事务分支的提交与回滚。
 *
 * <h3>状态机与幂等控制</h3>
 * <p>引入 Redis 状态标记 {@code inventory:tcc:status:{xid}}，值流转：
 * <pre>
 *   (none) ──Try──▶ TRYING ──Confirm──▶ CONFIRMING ──▶ CONFIRMED（幂等终点）
 *                  │                   └──Cancel───▶ CANCELLING ──▶ CANCELLED（幂等终点 + 防悬挂标记）
 *                  └──Cancel(空回滚)──▶ CANCELLED
 * </pre>
 *
 * <h3>解决的 TCC 经典问题</h3>
 * <ul>
 *   <li><b>防悬挂</b>：Try 前检查 status=CANCELLED，若已取消则拒绝 Try</li>
 *   <li><b>幂等</b>：confirm/cancel 检查最终状态，已 CONFIRMED/CANCELLED 直接返回 true</li>
 *   <li><b>空回滚</b>：cancel 时即使 meta 不存在也标记 CANCELLED，防止后续 Try 悬挂</li>
 *   <li><b>防并发</b>：状态检查与转换通过 Lua 脚本原子执行，中间态 CONFIRMING/CANCELLING 允许重入重试</li>
 * </ul>
 */
@Service
public class InventoryTccServiceImpl implements InventoryTccService {

    private static final Logger log = LoggerFactory.getLogger(InventoryTccServiceImpl.class);

    private static final String FROZEN_KEY_PREFIX = "inventory:frozen:";
    private static final String STATUS_KEY_PREFIX = "inventory:tcc:status:";
    private static final Duration FROZEN_TTL = Duration.ofMinutes(30);

    // TCC 状态常量
    private static final String STATUS_TRYING = "TRYING";
    private static final String STATUS_CONFIRMING = "CONFIRMING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_CANCELLING = "CANCELLING";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * Lua 脚本：Try 阶段原子操作（防悬挂 + 原子扣减 + 写 meta + 写 status=TRYING）
     * <p>KEYS: [1]=status, [2]=stock, [3]=frozen, [4]=meta
     * <p>ARGV: [1]=quantity, [2]=ttl(秒), [3]=skuId, [4]=productId, [5]=orderId(可空)
     * <p>返回: 1=成功, 0=库存不足, -1=悬挂(CANCELLED), -2=重复 Try
     */
    private static final String TRY_LUA_SCRIPT = """
            local status = redis.call('GET', KEYS[1])
            if status == 'CANCELLED' then return -1 end
            if status == 'TRYING' or status == 'CONFIRMING' or status == 'CONFIRMED' then return -2 end
            local stock = redis.call('GET', KEYS[2])
            if not stock or tonumber(stock) < tonumber(ARGV[1]) then return 0 end
            redis.call('DECRBY', KEYS[2], ARGV[1])
            redis.call('SET', KEYS[1], 'TRYING', 'EX', ARGV[2])
            redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[2])
            redis.call('HSET', KEYS[4], 'skuId', ARGV[3], 'productId', ARGV[4], 'quantity', ARGV[1])
            if ARGV[5] ~= '' then redis.call('HSET', KEYS[4], 'orderId', ARGV[5]) end
            redis.call('EXPIRE', KEYS[4], ARGV[2])
            return 1
            """;

    /**
     * Lua 脚本：Confirm 准备阶段（检查状态 + 设 CONFIRMING + 检查 meta 是否存在）
     * <p>KEYS: [1]=status, [2]=meta
     * <p>ARGV: [1]=ttl(秒)
     * <p>返回: 1=需处理(meta存在), 0=幂等(已CONFIRMED或meta不存在), -1=状态异常
     */
    private static final String CONFIRM_LUA_SCRIPT = """
            local status = redis.call('GET', KEYS[1])
            if status == 'CONFIRMED' then return 0 end
            if status ~= 'TRYING' and status ~= 'CONFIRMING' then return -1 end
            redis.call('SET', KEYS[1], 'CONFIRMING', 'EX', ARGV[1])
            local qty = redis.call('HGET', KEYS[2], 'quantity')
            if not qty then return 0 end
            return 1
            """;

    /**
     * Lua 脚本：Cancel 准备阶段（检查状态 + 设 CANCELLING + 检查 meta 是否存在）
     * <p>KEYS: [1]=status, [2]=meta
     * <p>ARGV: [1]=ttl(秒)
     * <p>返回: 1=需归还(meta存在), 2=空回滚(meta不存在), 0=幂等(已CANCELLED), -1=已CONFIRMED
     */
    private static final String CANCEL_LUA_SCRIPT = """
            local status = redis.call('GET', KEYS[1])
            if status == 'CANCELLED' then return 0 end
            if status == 'CONFIRMED' or status == 'CONFIRMING' then return -1 end
            redis.call('SET', KEYS[1], 'CANCELLING', 'EX', ARGV[1])
            local qty = redis.call('HGET', KEYS[2], 'quantity')
            if not qty then return 2 end
            return 1
            """;

    /**
     * Lua 脚本：完成阶段（设置最终状态 + 删除 meta）
     * <p>KEYS: [1]=status, [2]=meta
     * <p>ARGV: [1]=finalStatus, [2]=ttl(秒)
     */
    private static final String FINISH_LUA_SCRIPT = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            redis.call('DEL', KEYS[2])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public InventoryTccServiceImpl(StringRedisTemplate redisTemplate,
                                    JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @TwoPhaseBusinessAction(
            name = "inventoryTccDeduct",
            commitMethod = "confirmDeduct",
            rollbackMethod = "cancelDeduct"
    )
    public String tryDeduct(
            @BusinessActionContextParameter(paramName = "request") TccDeductRequest request) {
        String xid = UUID.randomUUID().toString().replace("-", "");
        String statusKey = STATUS_KEY_PREFIX + xid;
        String stockKey = "inventory:product:" + request.productId();
        String frozenKey = FROZEN_KEY_PREFIX + xid;
        String metaKey = FROZEN_KEY_PREFIX + xid + ":meta";
        String orderIdStr = request.orderId() != null ? request.orderId().toString() : "";

        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(TRY_LUA_SCRIPT, Long.class),
                List.of(statusKey, stockKey, frozenKey, metaKey),
                request.quantity().toString(),
                String.valueOf(FROZEN_TTL.toSeconds()),
                request.skuId().toString(),
                request.productId().toString(),
                orderIdStr
        );

        if (result == null || result == 0) {
            throw new BusinessException("INSUFFICIENT_STOCK", "库存不足，TCC Try 失败");
        }
        if (result == -1) {
            throw new BusinessException("TCC_SUSPENDED", "TCC Try 被拒绝：检测到 Cancel 已先执行（悬挂控制）");
        }
        if (result == -2) {
            throw new BusinessException("TCC_DUPLICATE_TRY", "TCC Try 重复执行");
        }

        log.info("TCC Try success: xid={}, skuId={}, quantity={}", xid, request.skuId(), request.quantity());
        return xid;
    }

    @Override
    @Transactional
    public boolean confirmDeduct(String xid) {
        String statusKey = STATUS_KEY_PREFIX + xid;
        String metaKey = FROZEN_KEY_PREFIX + xid + ":meta";
        String frozenKey = FROZEN_KEY_PREFIX + xid;

        Long code = redisTemplate.execute(
                new DefaultRedisScript<>(CONFIRM_LUA_SCRIPT, Long.class),
                List.of(statusKey, metaKey),
                String.valueOf(FROZEN_TTL.toSeconds())
        );

        if (code == null || code == 0) {
            log.info("TCC Confirm idempotent skip: xid={} (already confirmed or meta cleaned)", xid);
            return true;
        }
        if (code == -1) {
            throw new BusinessException("TCC_STATE_INVALID",
                    "TCC Confirm 状态异常：当前状态非 TRYING/CONFIRMING，xid=" + xid);
        }

        // code == 1：读取 meta 执行 DB 确认扣减
        String quantityStr = (String) redisTemplate.opsForHash().get(metaKey, "quantity");
        String skuIdStr = (String) redisTemplate.opsForHash().get(metaKey, "skuId");
        int quantity = Integer.parseInt(quantityStr);
        long skuId = Long.parseLong(skuIdStr);

        // DB 幂等条件：locked_stock >= ? 保证并发下只有一次真正扣减
        int updated = jdbcTemplate.update(
                "UPDATE inventories SET available_stock = available_stock - ?, " +
                        "locked_stock = locked_stock - ?, updated_at = NOW() " +
                        "WHERE sku_id = ? AND locked_stock >= ?",
                quantity, quantity, skuId, quantity
        );

        if (updated == 0) {
            // 并发下已被其他 confirm 处理，标记为 CONFIRMED 幂等返回
            log.warn("TCC Confirm DB updated=0, treat as idempotent: xid={}, skuId={}", xid, skuId);
        } else {
            log.info("TCC Confirm success: xid={}, skuId={}, quantity={}", xid, skuId, quantity);
        }

        // 标记最终状态 + 清理 Redis 冻结记录
        redisTemplate.execute(
                new DefaultRedisScript<>(FINISH_LUA_SCRIPT, Long.class),
                List.of(statusKey, metaKey),
                STATUS_CONFIRMED,
                String.valueOf(FROZEN_TTL.toSeconds())
        );
        redisTemplate.delete(frozenKey);
        return true;
    }

    @Override
    public boolean cancelDeduct(String xid) {
        String statusKey = STATUS_KEY_PREFIX + xid;
        String metaKey = FROZEN_KEY_PREFIX + xid + ":meta";
        String frozenKey = FROZEN_KEY_PREFIX + xid;

        Long code = redisTemplate.execute(
                new DefaultRedisScript<>(CANCEL_LUA_SCRIPT, Long.class),
                List.of(statusKey, metaKey),
                String.valueOf(FROZEN_TTL.toSeconds())
        );

        if (code == null || code == 0) {
            log.info("TCC Cancel idempotent skip: xid={} (already cancelled)", xid);
            return true;
        }
        if (code == -1) {
            throw new BusinessException("TCC_ALREADY_CONFIRMED",
                    "TCC Cancel 失败：事务已 Confirm，无法取消，xid=" + xid);
        }

        if (code == 2) {
            // 空回滚：Try 未执行或未成功，仅标记 CANCELLED 防悬挂
            log.info("TCC Cancel空回滚: xid={} (Try未执行，标记CANCELLED防悬挂)", xid);
            redisTemplate.execute(
                    new DefaultRedisScript<>(FINISH_LUA_SCRIPT, Long.class),
                    List.of(statusKey, metaKey),
                    STATUS_CANCELLED,
                    String.valueOf(FROZEN_TTL.toSeconds())
            );
            return true;
        }

        // code == 1：读取 meta 归还库存
        String quantityStr = (String) redisTemplate.opsForHash().get(metaKey, "quantity");
        String productIdStr = (String) redisTemplate.opsForHash().get(metaKey, "productId");
        int quantity = Integer.parseInt(quantityStr);
        long productId = Long.parseLong(productIdStr);

        String stockKey = "inventory:product:" + productId;
        redisTemplate.opsForValue().increment(stockKey, quantity);

        // 标记最终状态 + 清理冻结记录
        redisTemplate.execute(
                new DefaultRedisScript<>(FINISH_LUA_SCRIPT, Long.class),
                List.of(statusKey, metaKey),
                STATUS_CANCELLED,
                String.valueOf(FROZEN_TTL.toSeconds())
        );
        redisTemplate.delete(frozenKey);

        log.info("TCC Cancel success: xid={}, productId={}, quantity={}", xid, productId, quantity);
        return true;
    }

    /**
     * Seata TCC 框架回调方法：从 BusinessActionContext 提取 xid 并调用 confirmDeduct
     */
    public boolean confirm(BusinessActionContext context) {
        String xid = context.getXid();
        return confirmDeduct(xid);
    }

    /**
     * Seata TCC 框架回调方法：从 BusinessActionContext 提取 xid 并调用 cancelDeduct
     */
    public boolean cancel(BusinessActionContext context) {
        String xid = context.getXid();
        return cancelDeduct(xid);
    }
}
