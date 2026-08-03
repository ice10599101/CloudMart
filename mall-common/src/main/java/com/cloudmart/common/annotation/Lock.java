package com.cloudmart.common.annotation;

import com.cloudmart.common.lock.LockFailureStrategy;
import com.cloudmart.common.lock.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用分布式锁注解，基于 Redisson + AOP 实现。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 固定锁名
 * @Lock(name = "lock:inventory:deduct")
 * public void deductStock(Long skuId) { ... }
 *
 * // 2. SPEL 动态锁名（引用方法参数）
 * @Lock(name = "'lock:coupon:claim:' + #couponId")
 * public void claimCoupon(Long couponId, Long userId) { ... }
 *
 * // 3. 公平锁 + 超时重试
 * @Lock(name = "'lock:order:create:' + #orderId",
 *        type = LockType.FAIR,
 *        failureStrategy = LockFailureStrategy.RETRY_TIMEOUT,
 *        waitTime = 5)
 * public void createOrder(Long orderId) { ... }
 *
 * // 4. 读写锁
 * @Lock(name = "'lock:product:' + #productId", type = LockType.READ)
 * public Product getProduct(Long productId) { ... }
 *
 * @Lock(name = "'lock:product:' + #productId", type = LockType.WRITE)
 * public void updateProduct(Long productId, ProductDTO dto) { ... }
 * }</pre>
 *
 * <h3>事务边界</h3>
 * <p>切面 {@code @Order(Ordered.HIGHEST_PRECEDENCE)} 保证锁在事务外层获取，
 * 避免"锁在事务内、事务提交后锁已释放"的并发安全问题。
 *
 * <h3>看门狗续期</h3>
 * <p>{@code leaseTime = -1}（默认）时启用 Redisson 看门狗，自动续期，适合执行时间不确定的业务。
 * 设置 {@code leaseTime > 0} 则使用固定租约时间，到期自动释放。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Lock {

    /**
     * 锁名称，支持 SPEL 表达式。
     * <p>建议使用完整 Redis key 规范：{@code lock:{模块}:{业务}:{标识}}
     * <p>例如：{@code 'lock:coupon:claim:' + #couponId}
     */
    String name();

    /**
     * 锁类型，默认可重入锁。
     */
    LockType type() default LockType.REENTRANT;

    /**
     * 获取锁失败时的策略，默认快速失败。
     */
    LockFailureStrategy failureStrategy() default LockFailureStrategy.FAST_FAIL;

    /**
     * 获取锁的等待时间（秒），仅在 {@link LockFailureStrategy#RETRY_TIMEOUT} 时生效。
     */
    long waitTime() default 3;

    /**
     * 锁的持有时间（秒）。
     * <p>{@code -1}（默认）表示启用 Redisson 看门狗自动续期。
     * <p>设置正数则使用固定租约时间，到期自动释放。
     */
    long leaseTime() default -1;
}
