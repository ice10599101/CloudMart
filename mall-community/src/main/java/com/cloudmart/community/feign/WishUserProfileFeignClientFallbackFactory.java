package com.cloudmart.community.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.time.UserBusinessCalendar;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * R22 时区查询降级：wish 不可用/超时/解码失败时 fail-open 到默认业务区
 * （Asia/Shanghai，与 wish_user_stat.timezone 缺省一致），绝不阻断签到/发奖主流程。
 */
@Slf4j
@Component
public class WishUserProfileFeignClientFallbackFactory
        implements FallbackFactory<WishUserProfileFeignClient> {

    @Override
    public WishUserProfileFeignClient create(Throwable cause) {
        log.warn("R22 查询用户业务时区失败，fail-open 默认业务区 {}: {}",
                UserBusinessCalendar.DEFAULT_ZONE_ID, cause.getMessage());
        return userId -> ApiResponse.ok(Map.of(
                "userId", String.valueOf(userId),
                "timezone", UserBusinessCalendar.DEFAULT_ZONE_ID));
    }
}
