package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.time.UserBusinessCalendar;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** R22 时区查询失败 fail-open 默认业务区，不阻断配额/任务主流程。 */
@Slf4j
@Component
public class WishUserProfileFeignClientFallbackFactory
        implements FallbackFactory<WishUserProfileFeignClient> {

    @Override
    public WishUserProfileFeignClient create(Throwable cause) {
        log.warn("R22 pet 查询用户时区失败，fail-open 默认业务区 {}: {}",
                UserBusinessCalendar.DEFAULT_ZONE_ID, cause.getMessage());
        return userId -> ApiResponse.ok(Map.of(
                "userId", String.valueOf(userId),
                "timezone", UserBusinessCalendar.DEFAULT_ZONE_ID));
    }
}
