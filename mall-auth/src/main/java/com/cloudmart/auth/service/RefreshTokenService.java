package com.cloudmart.auth.service;

import com.cloudmart.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 刷新令牌服务（SEC-02 重写）：Token Family + Rotation + Reuse Detection。
 *
 * <p>模型：一次登录建立一个"令牌家族"（family），家族哈希记录
 * {@code subjectType / subjectId / currentToken / generation / absoluteExpire / revoked}。
 * 令牌值形如 {@code {u|a}:{familyId}:{tokenId}}，对客户端不透明。</p>
 *
 * <p>关键安全性质：</p>
 * <ul>
 *   <li><b>身份域隔离</b>：用户入口只接受 {@code u:} 令牌，管理员入口只接受
 *       {@code a:} 令牌；跨域提交在触碰 Redis 前拒绝且不消费原令牌；</li>
 *   <li><b>原子轮换</b>：消费旧令牌（推进 currentToken）、写入新令牌、代际推进由
 *       一段 Lua 脚本完成——100 个并发刷新至多一个逻辑轮换成功；</li>
 *   <li><b>重放检测</b>：提交的令牌与家族当前令牌不一致（含并发竞争）即判定
 *       重放，撤销整个家族（一个会话域，不误伤其他用户/其他身份域）；</li>
 *   <li><b>绝对到期上限</b>：轮换不得延长家族 {@code absoluteExpire}，杜绝
 *       "持续轮换 = 永久续期"；本版丢失令牌的固定策略为重新登录；</li>
 *   <li><b>Fail-closed</b>：Redis 异常时拒绝签发/轮换，绝不降级为无账本发 token。</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    static final String FAMILY_KEY_PREFIX = "auth:refresh_family:";
    static final String SUBJECT_INDEX_PREFIX = "auth:refresh_subject:";
    /** 重放撤销后家族记录保留时长（供审计与重复告警判定） */
    static final Duration REVOKED_FAMILY_TTL = Duration.ofHours(1);

    /**
     * 原子轮换脚本。
     * KEYS[1] = family 哈希
     * ARGV: [1]=旧令牌全值 [2]=当前 epoch 秒 [3]=标准TTL秒 [4]=新令牌全值 [5]=撤销保留秒
     * 返回：{OK, ttlSeconds} / {REUSED} / {EXPIRED} / {REVOKED}
     */
    static final String ROTATE_LUA = """
            local fam = redis.call('HGETALL', KEYS[1])
            if next(fam) == nil then return {'EXPIRED'} end
            local m = {}
            for i = 1, #fam, 2 do m[fam[i]] = fam[i + 1] end
            if m['revoked'] == '1' then return {'REVOKED'} end
            if m['currentToken'] ~= ARGV[1] then
              -- 重放/竞争：撤销整个家族，任何子令牌立即失效
              redis.call('HSET', KEYS[1], 'revoked', '1', 'revokedAt', ARGV[2])
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
              return {'REUSED'}
            end
            local now = tonumber(ARGV[2])
            local absExp = tonumber(m['absoluteExpire'])
            if now >= absExp then return {'EXPIRED'} end
            local ttl = tonumber(ARGV[3])
            if absExp - now < ttl then ttl = absExp - now end
            redis.call('HSET', KEYS[1], 'currentToken', ARGV[4],
                       'generation', tostring(tonumber(m['generation']) + 1))
            redis.call('EXPIRE', KEYS[1], ttl)
            return {'OK', tostring(ttl), m['subjectId']}
            """;

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenExpiration;
    private final Clock clock;
    private final DefaultRedisScript<List> rotateScript;

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(StringRedisTemplate redisTemplate,
                               @Value("${auth.jwt.refresh-token-expiration:604800}") long refreshTokenExpiration) {
        this(redisTemplate, refreshTokenExpiration, Clock.systemUTC());
    }

    /** 测试构造：注入受控时钟 */
    RefreshTokenService(StringRedisTemplate redisTemplate, long refreshTokenExpiration, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.clock = clock;
        this.rotateScript = new DefaultRedisScript<>(ROTATE_LUA, List.class);
    }

    /** 一次成功轮换的结果：主体、新令牌与本次有效期秒数 */
    public record RotationResult(SubjectType subjectType, Long subjectId, String tokenValue, long ttlSeconds) {
    }

    /**
     * 登录时创建新令牌家族并签发首个刷新令牌；家族绑定当前会话 sid，
     * "退出当前设备"据此只撤销本会话及其刷新家族，不影响其他设备。
     *
     * @throws BusinessException REFRESH_TOKEN_UNAVAILABLE 当 Redis 不可用时（fail-closed）
     */
    public String createRefreshToken(SubjectType subjectType, Long subjectId, String sid) {
        String familyId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        String tokenValue = subjectType.prefix() + familyId + ":" + tokenId;
        long now = clock.instant().getEpochSecond();
        long absoluteExpire = now + refreshTokenExpiration;

        try {
            String familyKey = FAMILY_KEY_PREFIX + familyId;
            redisTemplate.opsForHash().putAll(familyKey, Map.of(
                    "subjectType", subjectType.name(),
                    "subjectId", subjectId.toString(),
                    "currentToken", tokenValue,
                    "generation", "1",
                    "absoluteExpire", Long.toString(absoluteExpire),
                    "revoked", "0",
                    "sid", sid == null ? "" : sid));
            redisTemplate.expire(familyKey, Duration.ofSeconds(refreshTokenExpiration));

            // 主体 → 家族索引：logout/踢人/禁用时按主体撤销全部家族
            String indexKey = SUBJECT_INDEX_PREFIX + subjectType.segment() + ":" + subjectId;
            redisTemplate.opsForSet().add(indexKey, familyId);
            redisTemplate.expire(indexKey, Duration.ofSeconds(refreshTokenExpiration));
        } catch (Exception e) {
            // fail-closed：账本写不进去就不发令牌
            log.error("[SEC02] 创建刷新令牌失败（Redis 不可用？）: {}", e.getMessage());
            throw new BusinessException("REFRESH_TOKEN_UNAVAILABLE", "刷新令牌服务暂不可用，请稍后重试");
        }
        return tokenValue;
    }

    /**
     * 轮换后把家族重新绑定到新会话，并返回旧会话 sid 供调用方撤销。
     *
     * <p>尽力而为（不抛异常）：轮换已在 Lua 中原子提交，此时让刷新请求失败会让
     * 客户端带着已消费的令牌重试 → 命中重放检测 → 整个家族被误撤销。绑定失败的
     * 代价是"退出当前设备"可能漏掉该家族（旧会话仍有 TTL 兜底，下一次轮换会重绑），
     * 由 ERROR 日志暴露，不做静默吞掉。</p>
     *
     * @return 旧会话 sid；家族未绑定或 Redis 异常时返回 null
     */
    public String bindFamilySession(String tokenValue, String newSid) {
        String[] parts = tokenValue.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        String familyKey = FAMILY_KEY_PREFIX + parts[1];
        try {
            Object previousSid = redisTemplate.opsForHash().get(familyKey, "sid");
            redisTemplate.opsForHash().put(familyKey, "sid", newSid == null ? "" : newSid);
            return previousSid == null || String.valueOf(previousSid).isBlank()
                    ? null : String.valueOf(previousSid);
        } catch (Exception e) {
            log.error("[SEC02] 家族会话重绑失败 familyId={}（退出当前设备可能漏撤销该家族）: {}",
                    parts[1], e.getMessage());
            return null;
        }
    }

    /**
     * 原子轮换：仅接受与 {@code expectedDomain} 同域的令牌；重放撤销家族；跨域拒绝不消费。
     *
     * @return 新令牌与剩余秒数（不超过家族绝对到期）
     * @throws BusinessException INVALID_REFRESH_TOKEN / TOKEN_DOMAIN_MISMATCH /
     *                          TOKEN_REUSE_DETECTED / REFRESH_TOKEN_EXPIRED / REFRESH_TOKEN_UNAVAILABLE
     */
    public RotationResult rotateRefreshToken(SubjectType expectedDomain, String tokenValue) {
        SubjectType actualDomain = SubjectType.fromToken(tokenValue);
        if (tokenValue == null || tokenValue.isBlank() || actualDomain == null) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "无效的 Refresh Token");
        }
        if (actualDomain != expectedDomain) {
            // 跨域提交：拒绝且不消费原令牌（SEC-02 身份域隔离的核心判定）
            log.warn("[SEC02] 刷新令牌身份域不匹配 expected={} actual={}", expectedDomain, actualDomain);
            throw new BusinessException("TOKEN_DOMAIN_MISMATCH", "Refresh Token 身份域不匹配");
        }

        String[] parts = tokenValue.split(":", 3);
        if (parts.length != 3) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "无效的 Refresh Token");
        }
        String familyId = parts[1];
        String familyKey = FAMILY_KEY_PREFIX + familyId;
        long now = clock.instant().getEpochSecond();

        // 新令牌在脚本外生成随机段，脚本内与旧令牌校验/写入同事务完成
        String newTokenValue = expectedDomain.prefix() + familyId + ":" + UUID.randomUUID();

        List result;
        try {
            result = redisTemplate.execute(rotateScript,
                    List.of(familyKey),
                    tokenValue, Long.toString(now),
                    Long.toString(refreshTokenExpiration),
                    newTokenValue, Long.toString(REVOKED_FAMILY_TTL.toSeconds()));
        } catch (Exception e) {
            // fail-closed：Redis 异常时不签发任何新令牌
            log.error("[SEC02] 轮换脚本执行失败（Redis 不可用？）: {}", e.getMessage());
            throw new BusinessException("REFRESH_TOKEN_UNAVAILABLE", "刷新令牌服务暂不可用，请稍后重试");
        }
        if (result == null || result.isEmpty()) {
            throw new BusinessException("REFRESH_TOKEN_UNAVAILABLE", "刷新令牌服务暂不可用，请稍后重试");
        }

        String status = String.valueOf(result.get(0));
        return switch (status) {
            case "OK" -> new RotationResult(expectedDomain,
                    Long.valueOf(String.valueOf(result.get(2))),
                    newTokenValue, Long.parseLong(String.valueOf(result.get(1))));
            case "REUSED" -> {
                log.warn("[SEC02] 检测到刷新令牌重放，已撤销家族 familyId={}", familyId);
                throw new BusinessException("TOKEN_REUSE_DETECTED", "检测到 Refresh Token 重放，已撤销该会话全部令牌，请重新登录");
            }
            case "REVOKED" -> throw new BusinessException("TOKEN_REUSE_DETECTED", "该会话令牌已被撤销，请重新登录");
            case "EXPIRED" -> throw new BusinessException("REFRESH_TOKEN_EXPIRED", "Refresh Token 已过期");
            default -> throw new BusinessException("INVALID_REFRESH_TOKEN", "无效的 Refresh Token");
        };
    }

    /** 撤销某主体（指定身份域）名下的全部刷新令牌家族：踢人 / 禁用 / 退出全部设备即失效 */
    public void revokeAllTokensForSubject(SubjectType subjectType, Long subjectId) {
        try {
            String indexKey = SUBJECT_INDEX_PREFIX + subjectType.segment() + ":" + subjectId;
            Set<String> familyIds = redisTemplate.opsForSet().members(indexKey);
            if (familyIds != null) {
                for (String familyId : familyIds) {
                    String familyKey = FAMILY_KEY_PREFIX + familyId;
                    redisTemplate.opsForHash().put(familyKey, "revoked", "1");
                    redisTemplate.expire(familyKey, REVOKED_FAMILY_TTL);
                }
            }
            redisTemplate.delete(indexKey);
        } catch (Exception e) {
            // 撤销失败必须显式失败：调用方（logout/踢人）需要知道令牌仍在流通
            log.error("[SEC02] 撤销主体令牌失败 subject={}:{}: {}", subjectType, subjectId, e.getMessage());
            throw new BusinessException("REFRESH_TOKEN_UNAVAILABLE", "令牌撤销失败，请稍后重试");
        }
    }

    /**
     * 撤销绑定在指定会话（sid）上的刷新令牌家族（SEC-02"退出当前设备"）：
     * 其他设备（不同 sid）的家族不受影响。
     *
     * @return 实际撤销的家族数（0 表示该会话没有可撤销家族，如已撤销过）
     * @throws BusinessException REFRESH_TOKEN_UNAVAILABLE Redis 不可用时（fail-closed）
     */
    public int revokeFamilyBySession(SubjectType subjectType, Long subjectId, String sid) {
        if (sid == null || sid.isBlank()) {
            return 0;
        }
        try {
            String indexKey = SUBJECT_INDEX_PREFIX + subjectType.segment() + ":" + subjectId;
            Set<String> familyIds = redisTemplate.opsForSet().members(indexKey);
            int revoked = 0;
            if (familyIds != null) {
                for (String familyId : familyIds) {
                    String familyKey = FAMILY_KEY_PREFIX + familyId;
                    Object boundSid = redisTemplate.opsForHash().get(familyKey, "sid");
                    if (boundSid == null || !sid.equals(String.valueOf(boundSid))) {
                        continue;
                    }
                    redisTemplate.opsForHash().put(familyKey, "revoked", "1");
                    redisTemplate.expire(familyKey, REVOKED_FAMILY_TTL);
                    redisTemplate.opsForSet().remove(indexKey, familyId);
                    revoked++;
                }
            }
            return revoked;
        } catch (Exception e) {
            log.error("[SEC02] 按会话撤销刷新家族失败 subject={}:{} sid={}: {}",
                    subjectType, subjectId, sid, e.getMessage());
            throw new BusinessException("REFRESH_TOKEN_UNAVAILABLE", "令牌撤销失败，请稍后重试");
        }
    }
}
