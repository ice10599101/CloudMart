package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * UserFeignClient 降级工厂。
 *
 * <p>降级策略（Fail Open）：mall-user 不可用时返回占位数据（昵称="心愿旅人"，avatar=""），
 * 不阻塞心愿主链路。仅对 4xx 错误抛出业务异常（如用户不存在），5xx 错误降级处理。</p>
 *
 * <p>对应 AGENTS.md 第 20 章 Fail-Safe 策略：关键外部依赖必须定义失败策略。</p>
 */
@Component
@Slf4j
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    private final ObjectMapper objectMapper;

    public UserFeignClientFallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("心愿模块调用用户服务失败: {}", cause.getMessage());

        return new UserFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> getUserById(Long id) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("USER_SERVICE_ERROR", "用户服务请求失败");
                    }
                }
                // 5xx / 超时 / 连接失败 → Fail Open 返回占位数据
                log.warn("用户服务不可用，返回占位用户数据, userId={}", id);
                return ApiResponse.ok(Map.of(
                        "id", id,
                        "nickname", "心愿旅人",
                        "avatar", ""
                ));
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> batchGetUsers(List<Long> ids) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("USER_SERVICE_ERROR", "用户服务请求失败");
                    }
                }
                // 5xx → Fail Open 返回占位数据
                log.warn("用户服务不可用，返回占位批量用户数据, count={}", ids.size());
                List<Map<String, Object>> placeholders = ids.stream()
                        .map(id -> Map.<String, Object>of(
                                "id", id,
                                "nickname", "心愿旅人",
                                "avatar", ""
                        ))
                        .toList();
                return ApiResponse.ok(placeholders);
            }
        };
    }

    private BusinessException extractBusinessException(Throwable cause) {
        if (cause instanceof FeignException feignException) {
            try {
                String body = feignException.contentUTF8();
                if (body != null && !body.isEmpty()) {
                    ApiResponse<?> response = objectMapper.readValue(body, ApiResponse.class);
                    if (response.error() != null) {
                        return new BusinessException(response.error().code(), response.error().message(), cause);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Feign 错误响应失败: {}", e.getMessage());
            }
        }
        return new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试", cause);
    }
}
