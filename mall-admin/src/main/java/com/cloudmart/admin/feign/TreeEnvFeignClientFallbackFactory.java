package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class TreeEnvFeignClientFallbackFactory implements FallbackFactory<TreeEnvFeignClient> {

    @Override
    public TreeEnvFeignClient create(Throwable cause) {
        log.error("生命树环境服务调用失败: {}", cause.getMessage());
        return new TreeEnvFeignClient() {
            @Override
            public ApiResponse<Object> triggerSpecialEvent(Map<String, Object> data) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> endSpecialEvent(Long eventId) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listSpecialEvents(int limit) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listEnvConfigs() {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createEnvConfig(Map<String, Object> data) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateEnvConfig(Long configId, Map<String, Object> data) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateEnvConfigStatus(Long configId, Map<String, Object> data) {
                throw new BusinessException("TREE_ENV_SERVICE_UNAVAILABLE", "生命树环境服务不可用，请稍后重试");
            }
        };
    }
}
