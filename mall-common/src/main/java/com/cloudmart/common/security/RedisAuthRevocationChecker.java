package com.cloudmart.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 权威账本实现（SEC-01）：键格式与 mall-auth {@code AuthSessionService}
 * 完全一致——会话 {@code auth:session_valid:{sid}}、主体版本
 * {@code auth:auth_version:{subjectType}:{subjectId}}（subjectType 即
 * user/admin，与 {@code SubjectType#segment} 同源）。
 *
 * <p>任一读取异常抛 {@link AuthStateException} 由过滤器 fail-closed，
 * 不缓存、不降级——撤销窗口要求秒级，陈旧的本地判断比多一次查询危险。</p>
 */
public class RedisAuthRevocationChecker implements AuthRevocationChecker {

    static final String SESSION_KEY_PREFIX = "auth:session_valid:";
    static final String VERSION_KEY_PREFIX = "auth:auth_version:";

    private final StringRedisTemplate redisTemplate;

    public RedisAuthRevocationChecker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isActive(String subjectType, String subjectId, String sid, long authVersion) {
        try {
            String sessionVersion = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sid);
            if (sessionVersion == null || !sessionVersion.equals(Long.toString(authVersion))) {
                return false;
            }
            String currentVersion = redisTemplate.opsForValue()
                    .get(VERSION_KEY_PREFIX + subjectType + ":" + subjectId);
            return (currentVersion == null ? 0L : Long.parseLong(currentVersion)) == authVersion;
        } catch (Exception e) {
            throw new AuthStateException("会话撤销状态校验不可用（fail-closed）", e);
        }
    }
}
