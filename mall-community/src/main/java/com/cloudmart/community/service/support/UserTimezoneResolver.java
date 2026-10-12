package com.cloudmart.community.service.support;

import com.cloudmart.common.time.UserBusinessCalendar;
import com.cloudmart.community.feign.WishUserProfileFeignClient;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R22 用户业务时区解析器：以 mall-wish wish_user_stat.timezone 为权威来源，
 * 本地短 TTL 缓存（5 分钟）避免每次签到/发奖都跨服务调用；失败/缺省 fail-open
 * 到 {@link UserBusinessCalendar#DEFAULT_ZONE_ID}。
 *
 * <p>时区变更有最多 5 分钟传播延迟——用户改时区后短暂沿用旧归属，不影响资金/等级
 * 正确性（R22 防双份由 UTC 事实时刻对当前业务日界判定保证，不依赖缓存即时性）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserTimezoneResolver {

    /** 时区缓存有效期（毫秒）：用户改时区后最多 5 分钟传播 */
    private static final long TTL_MS = 5 * 60_000L;

    private final WishUserProfileFeignClient timezoneFeignClient;

    private record CachedZone(ZoneId zone, long expiresAt) {
        boolean fresh() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    private final Map<Long, CachedZone> cache = new ConcurrentHashMap<>();

    /** 解析用户业务时区（永不抛异常，非法/远端失败均回退默认区） */
    public ZoneId resolve(Long userId) {
        if (userId == null) {
            return ZoneId.of(UserBusinessCalendar.DEFAULT_ZONE_ID);
        }
        CachedZone cached = cache.get(userId);
        if (cached != null && cached.fresh()) {
            return cached.zone();
        }
        ZoneId resolved = fetchZone(userId);
        cache.put(userId, new CachedZone(resolved, System.currentTimeMillis() + TTL_MS));
        return resolved;
    }

    private ZoneId fetchZone(Long userId) {
        try {
            var resp = timezoneFeignClient.getTimezone(userId);
            if (resp != null && resp.success() && resp.data() != null) {
                return UserBusinessCalendar.zone(resp.data().get("timezone"));
            }
        } catch (Exception e) {
            log.warn("R22 拉取用户时区失败，回退默认区: userId={}, {}", userId, e.getMessage());
        }
        return ZoneId.of(UserBusinessCalendar.DEFAULT_ZONE_ID);
    }
}
