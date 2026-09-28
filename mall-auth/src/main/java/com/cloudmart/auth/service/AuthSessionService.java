package com.cloudmart.auth.service;

import com.cloudmart.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * 认证会话与状态版本服务（SEC-03）。
 *
 * <p>两类 Redis 权威记录：</p>
 * <ul>
 *   <li><b>会话记录</b> {@code auth:session_valid:{sid}} → 该会话签发时的认证状态版本，
 *       TTL = 访问令牌有效期。网关每请求校验「会话存在且版本与令牌声明一致」；
 *       会话被删除或版本不匹配，旧访问令牌立即失效（目标撤销窗口 ≤ 5 秒）。</li>
 *   <li><b>认证状态版本</b> {@code auth:auth_version:{type}:{id}} → 主体当前版本。
 *       禁用/改密/重置密码（硬失效：版本递增 + 撤销全部刷新令牌家族）、
 *       角色/权限修改（软失效：仅版本递增，下次刷新拿到新权限集）时递增。</li>
 * </ul>
 *
 * <p>无版本的键视为版本 0（签发时同样记 0），保证首次部署即可用。</p>
 */
@Service
public class AuthSessionService {

    static final String SESSION_KEY_PREFIX = "auth:session_valid:";
    static final String VERSION_KEY_PREFIX = "auth:auth_version:";

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenService refreshTokenService;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthSessionService(StringRedisTemplate redisTemplate,
                              RefreshTokenService refreshTokenService,
                              @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration,
                              @Value("${auth.jwt.refresh-token-expiration:604800}") long refreshTokenExpiration) {
        this(redisTemplate, refreshTokenService, accessTokenExpiration, refreshTokenExpiration, Clock.systemUTC());
    }

    AuthSessionService(StringRedisTemplate redisTemplate, RefreshTokenService refreshTokenService,
                       long accessTokenExpiration, long refreshTokenExpiration, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.clock = clock;
    }

    /** 一次会话签发的结果 */
    public record IssuedSession(String sid, long authVersion) {
    }

    /**
     * 登录/刷新时签发新会话：会话键记录当前版本，供网关比对令牌声明。
     *
     * @throws BusinessException SESSION_UNAVAILABLE Redis 不可用时（fail-closed：不签发）
     */
    public IssuedSession issueSession(SubjectType subjectType, Long subjectId) {
        String sid = UUID.randomUUID().toString();
        long version = currentAuthVersion(subjectType, subjectId);
        try {
            redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sid, Long.toString(version),
                    Duration.ofSeconds(accessTokenExpiration));
        } catch (Exception e) {
            // fail-closed：写不进会话账本就不发令牌，否则令牌无法被撤销
            throw new BusinessException(
                    "SESSION_UNAVAILABLE", "会话服务暂不可用，请稍后重试");
        }
        return new IssuedSession(sid, version);
    }

    /** 主体当前认证状态版本；无记录视为 0 */
    public long currentAuthVersion(SubjectType subjectType, Long subjectId) {
        try {
            String value = redisTemplate.opsForValue().get(VERSION_KEY_PREFIX + key(subjectType, subjectId));
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            // 版本查询失败按 0 处理会导致"高版本仍有效"，必须 fail-closed
            throw new BusinessException(
                    "SESSION_UNAVAILABLE", "会话服务暂不可用，请稍后重试");
        }
    }

    /** 删除会话（登出）：该会话的访问令牌立即失效 */
    public void revokeSession(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(SESSION_KEY_PREFIX + sid);
        } catch (Exception e) {
            throw new BusinessException(
                    "SESSION_UNAVAILABLE", "会话服务暂不可用，请稍后重试");
        }
    }

    /**
     * 使主体认证状态失效。
     *
     * @param revokeRefreshTokens true 时同时撤销全部刷新令牌家族（禁用/改密等硬失效）；
     *                            false 仅递增版本（权限变更软失效，刷新后拿新权限集）
     * @throws BusinessException SESSION_UNAVAILABLE Redis 不可用时
     */
    public void invalidate(SubjectType subjectType, Long subjectId, boolean revokeRefreshTokens) {
        try {
            Long version = redisTemplate.opsForValue().increment(VERSION_KEY_PREFIX + key(subjectType, subjectId));
            if (version != null) {
                redisTemplate.expire(VERSION_KEY_PREFIX + key(subjectType, subjectId),
                        Duration.ofSeconds(refreshTokenExpiration));
            }
        } catch (Exception e) {
            throw new BusinessException(
                    "SESSION_UNAVAILABLE", "会话服务暂不可用，请稍后重试");
        }
        if (revokeRefreshTokens) {
            refreshTokenService.revokeAllTokensForSubject(subjectType, subjectId);
        }
    }

    private String key(SubjectType subjectType, Long subjectId) {
        return subjectType.segment() + ":" + subjectId;
    }
}
