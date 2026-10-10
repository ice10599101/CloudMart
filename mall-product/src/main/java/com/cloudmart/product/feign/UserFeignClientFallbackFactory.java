package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** N-5：昵称解析降级——展示型数据 fail-open（返回空表，前端落占位昵称）。 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        return ids -> {
            log.warn("N-5 用户昵称批量解析失败（fail-open 返回空表）: {}", cause.getMessage());
            return ApiResponse.ok(List.of());
        };
    }
}
