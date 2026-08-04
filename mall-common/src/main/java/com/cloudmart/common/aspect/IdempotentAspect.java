package com.cloudmart.common.aspect;

import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;

/**
 * 幂等性切面，通过 Redis SETNX 拦截重复请求。
 *
 * <p>配合 {@link Idempotent} 注解使用，标注在 Controller 方法上。
 * 首次请求通过 SETNX 抢占 key 后放行，重复请求在 TTL 内直接拒绝。</p>
 */
@Aspect
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private static final String KEY_PREFIX = "idempotent:";
    private static final String HEADER_NAME = "X-Idempotency-Key";

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String idempotencyKey = resolveKey(joinPoint, idempotent);
        String redisKey = KEY_PREFIX + idempotent.prefix() + ":" + idempotencyKey;
        Duration ttl = Duration.ofSeconds(idempotent.ttl());

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, String.valueOf(System.currentTimeMillis()), ttl);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("幂等拦截：重复请求 key={}", redisKey);
            throw new BusinessException("IDEMPOTENT_ERROR", "重复请求，请勿重复提交");
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // 方法执行失败时删除 key，允许客户端重试
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }

    /**
     * 解析幂等 key：优先从请求头获取，其次从参数拼装。
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (idempotent.source() == Idempotent.KeySource.HEADER) {
            HttpServletRequest request = currentRequest();
            if (request != null) {
                String key = request.getHeader(HEADER_NAME);
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
            // HEADER 模式但请求头缺失，降级为参数拼装
            log.debug("请求头 {} 缺失，降级为参数拼装", HEADER_NAME);
        }

        return spellKey(joinPoint);
    }

    /**
     * 从方法签名 + 参数哈希拼装幂等 key。
     */
    private String spellKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodKey = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        String argsHash = Objects.hash(joinPoint.getArgs()) + "";
        return methodKey + ":" + argsHash;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
