package com.cloudmart.pet.service.support;

import com.cloudmart.common.time.UserBusinessCalendar;
import com.cloudmart.pet.feign.WishUserProfileFeignClient;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R22 宠物域用户业务时区解析：mall-wish 权威来源 + 本地 5 分钟缓存 + fail-open 默认区。
 * 供每日配额（PetQuotaService）与每日任务（PetDailyQuest）按用户业务日归属。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPetTimezoneResolver {

    private static final long TTL_MS = 5 * 60_000L;

    private final WishUserProfileFeignClient timezoneFeignClient;

    private record CachedZone(ZoneId zone, long expiresAt) {
        boolean fresh() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    private final Map<Long, CachedZone> cache = new ConcurrentHashMap<>();

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
            log.warn("R22 pet 拉取用户时区失败，回退默认区: userId={}, {}", userId, e.getMessage());
        }
        return ZoneId.of(UserBusinessCalendar.DEFAULT_ZONE_ID);
    }
}
