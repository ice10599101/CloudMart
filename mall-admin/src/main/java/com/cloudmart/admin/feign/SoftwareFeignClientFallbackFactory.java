package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * mall-software 管理端降级工厂：审核决策失败如实 503，
 * 绝不返回"已通过/已退回"假象（A22：管理动作必须真实落到软件库）。
 */
@Slf4j
@Component
public class SoftwareFeignClientFallbackFactory implements FallbackFactory<SoftwareFeignClient> {

    @Override
    public SoftwareFeignClient create(Throwable cause) {
        log.warn("mall-software 管理端接口不可用: {}", cause.getMessage());
        return (revisionId, body) -> {
            throw new BusinessException("SOFTWARE_SERVICE_UNAVAILABLE",
                    "软件服务暂不可用，请稍后重试", cause);
        };
    }
}
