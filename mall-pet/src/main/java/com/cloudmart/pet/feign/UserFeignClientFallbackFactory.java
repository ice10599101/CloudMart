package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * mall-user 客户端降级：昵称/头像是展示型数据，Fail-Open 返回空列表（占位昵称兜底）。
 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        return new UserFeignClient() {
            @Override
            public ApiResponse<List<Map<String, Object>>> batchGetUsers(List<Long> ids) {
                log.warn("mall-user 批量用户信息查询失败（Fail-Open 返回空）: {}", cause.getMessage());
                return ApiResponse.ok(List.of());
            }
        };
    }
}
