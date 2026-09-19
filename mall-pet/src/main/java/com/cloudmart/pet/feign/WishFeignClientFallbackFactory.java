package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * mall-wish 降级工厂（Fail-Closed）：捞瓶/星光发放是资金与资产操作，
 * 静默返回占位数据会导致用户损失，降级统一抛 503 由调用方决定补偿语义
 * （捞瓶 FAILED 可重试领取；打工作业领取整体回滚）。
 */
@Slf4j
@Component
public class WishFeignClientFallbackFactory implements FallbackFactory<WishFeignClient> {

    @Override
    public WishFeignClient create(Throwable cause) {
        log.error("mall-wish Feign 调用降级: {}", cause.getMessage());
        return new WishFeignClient() {
            @Override
            public ApiResponse<WishFeignClient.WishBottleVO> fishForPet() {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Integer> earnStarlight(Long userId, Integer amount, Long refId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> batchGetUsers(List<Long> ids) {
                // 昵称查询是展示型数据：Fail-Open 返回空列表，调用方使用占位昵称
                return ApiResponse.ok(List.of());
            }

            private BusinessException unavailable(Throwable cause) {
                return new BusinessException("WISH_SERVICE_UNAVAILABLE",
                        "心愿宇宙服务暂时不可用，请稍后再试", cause);
            }
        };
    }
}
