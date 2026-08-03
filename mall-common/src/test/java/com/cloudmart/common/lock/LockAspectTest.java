package com.cloudmart.common.lock;

import com.cloudmart.common.annotation.Lock;
import com.cloudmart.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LockAspect} 单元测试，覆盖 SPEL 锁名解析、锁获取/释放、失败策略和异常处理。
 */
class LockAspectTest {

    private LockFactory lockFactory;
    private LockFailureHandler failureHandler;
    private LockAspect lockAspect;

    @BeforeEach
    void setUp() {
        lockFactory = mock(LockFactory.class);
        failureHandler = mock(LockFailureHandler.class);
        lockAspect = new LockAspect(lockFactory, failureHandler);
    }

    /**
     * 测试目标方法：固定锁名
     */
    public void fixedLockMethod(String param) {
    }

    /**
     * 测试目标方法：SPEL 动态锁名
     */
    public void spelLockMethod(Long couponId, Long userId) {
    }

    private Lock createLockAnnotation(String name, LockType type, LockFailureStrategy strategy,
                                       long waitTime, long leaseTime) {
        return new Lock() {
            @Override
            public Class<Lock> annotationType() {
                return Lock.class;
            }
            @Override
            public String name() {
                return name;
            }
            @Override
            public LockType type() {
                return type;
            }
            @Override
            public LockFailureStrategy failureStrategy() {
                return strategy;
            }
            @Override
            public long waitTime() {
                return waitTime;
            }
            @Override
            public long leaseTime() {
                return leaseTime;
            }
        };
    }

    private ProceedingJoinPoint createJoinPoint(Method method, Object[] args) throws Exception {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @Test
    @DisplayName("固定锁名应直接使用常量，不走 SPEL 解析")
    void fixedLockName_shouldNotUseSpel() throws Throwable {
        Method method = LockAspectTest.class.getMethod("fixedLockMethod", String.class);
        Lock annotation = createLockAnnotation("lock:test:fixed", LockType.REENTRANT,
                LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{"param"});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create("lock:test:fixed", LockType.REENTRANT)).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = lockAspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("result");
        verify(lockFactory).create("lock:test:fixed", LockType.REENTRANT);
        verify(rLock).isHeldByCurrentThread();
    }

    @Test
    @DisplayName("SPEL 动态锁名应正确解析方法参数")
    void spelLockName_shouldResolveMethodParam() throws Throwable {
        Method method = LockAspectTest.class.getMethod("spelLockMethod", Long.class, Long.class);
        Lock annotation = createLockAnnotation("'lock:coupon:claim:' + #couponId",
                LockType.REENTRANT, LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{100L, 200L});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create("lock:coupon:claim:100", LockType.REENTRANT)).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("claimed");

        Object result = lockAspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("claimed");
        verify(lockFactory).create("lock:coupon:claim:100", LockType.REENTRANT);
    }

    @Test
    @DisplayName("获取锁失败应抛 LOCK_ACQUIRE_FAILED 异常")
    void acquireFailed_shouldThrowException() throws Throwable {
        Method method = LockAspectTest.class.getMethod("fixedLockMethod", String.class);
        Lock annotation = createLockAnnotation("lock:test:fail", LockType.REENTRANT,
                LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{"param"});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create(anyString(), any())).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenReturn(false);

        assertThatThrownBy(() -> lockAspect.around(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("LOCK_ACQUIRE_FAILED"));

        verify(joinPoint, org.mockito.Mockito.never()).proceed();
    }

    @Test
    @DisplayName("业务方法抛异常时仍应释放锁")
    void businessThrows_shouldStillReleaseLock() throws Throwable {
        Method method = LockAspectTest.class.getMethod("fixedLockMethod", String.class);
        Lock annotation = createLockAnnotation("lock:test:exception", LockType.REENTRANT,
                LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{"param"});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create(anyString(), any())).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        assertThatThrownBy(() -> lockAspect.around(joinPoint, annotation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("业务异常");

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("获取锁被中断应恢复中断状态并抛异常")
    void acquireInterrupted_shouldRestoreInterruptAndThrow() throws Throwable {
        Method method = LockAspectTest.class.getMethod("fixedLockMethod", String.class);
        Lock annotation = createLockAnnotation("lock:test:interrupt", LockType.REENTRANT,
                LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{"param"});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create(anyString(), any())).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenThrow(new InterruptedException("被中断"));

        assertThatThrownBy(() -> lockAspect.around(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("LOCK_INTERRUPTED"));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // 清除中断标记
    }

    @Test
    @DisplayName("释放锁异常不应影响业务结果")
    void unlockException_shouldNotAffectResult() throws Throwable {
        Method method = LockAspectTest.class.getMethod("fixedLockMethod", String.class);
        Lock annotation = createLockAnnotation("lock:test:unlock-fail", LockType.REENTRANT,
                LockFailureStrategy.FAST_FAIL, 3, -1);
        ProceedingJoinPoint joinPoint = createJoinPoint(method, new Object[]{"param"});

        RLock rLock = mock(RLock.class);
        when(lockFactory.create(anyString(), any())).thenReturn(rLock);
        when(failureHandler.acquire(rLock, annotation)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Redis 连接断开"))
                .when(rLock).unlock();

        Object result = lockAspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("success");
    }
}
