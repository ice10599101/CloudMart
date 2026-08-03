package com.cloudmart.common.lock;

import com.cloudmart.common.annotation.Lock;
import com.cloudmart.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 分布式锁 AOP 切面，拦截 {@link Lock} 注解方法。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>SPEL 解析锁名（支持方法参数引用）</li>
 *   <li>通过 {@link LockFactory} 创建 Redisson 锁实例</li>
 *   <li>通过 {@link LockFailureHandler} 按策略获取锁</li>
 *   <li>执行业务方法</li>
 *   <li>finally 块释放锁（仅当前线程持有时释放）</li>
 * </ol>
 *
 * <h3>切面顺序</h3>
 * <p>{@code @Order(Ordered.HIGHEST_PRECEDENCE)} 保证锁在事务外层获取，
 * 避免"事务提交前锁已释放"的并发安全问题。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LockAspect {

    private static final Logger log = LoggerFactory.getLogger(LockAspect.class);

    private final LockFactory lockFactory;
    private final LockFailureHandler failureHandler;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public LockAspect(LockFactory lockFactory, LockFailureHandler failureHandler) {
        this.lockFactory = lockFactory;
        this.failureHandler = failureHandler;
    }

    @Around("@annotation(lockAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Lock lockAnnotation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String lockName = resolveLockName(lockAnnotation.name(), signature.getMethod(), joinPoint.getArgs());

        RLock lock = lockFactory.create(lockName, lockAnnotation.type());
        boolean acquired;
        try {
            acquired = failureHandler.acquire(lock, lockAnnotation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("LOCK_INTERRUPTED", "获取分布式锁被中断: " + lockName);
        }

        if (!acquired) {
            log.warn("分布式锁获取失败: lockName={}, strategy={}", lockName, lockAnnotation.failureStrategy());
            throw new BusinessException("LOCK_ACQUIRE_FAILED", "获取分布式锁失败: " + lockName);
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("分布式锁释放异常: lockName={}", lockName, e);
                }
            }
        }
    }

    /**
     * 解析锁名称，支持 SPEL 表达式和纯文本常量。
     *
     * <p>SPEL 上下文可引用方法参数（通过参数名），例如：
     * <pre>{@code
     * @Lock(name = "'lock:coupon:claim:' + #couponId")
     * public void claimCoupon(Long couponId, Long userId) { ... }
     * }</pre>
     *
     * <p>如果 name 不含 SPEL 语法（无 # 或 '），直接作为常量返回，避免解析开销。
     */
    private String resolveLockName(String expression, java.lang.reflect.Method method, Object[] args) {
        if (expression.indexOf('#') < 0 && expression.indexOf('\'') < 0) {
            return expression;
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, args, nameDiscoverer);
        Expression exp = parser.parseExpression(expression);
        String resolved = exp.getValue(context, String.class);
        if (resolved == null || resolved.isBlank()) {
            throw new BusinessException("LOCK_NAME_INVALID", "分布式锁名称解析为空: " + expression);
        }
        return resolved;
    }
}
