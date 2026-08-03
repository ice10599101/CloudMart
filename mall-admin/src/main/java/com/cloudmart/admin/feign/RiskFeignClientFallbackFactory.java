package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class RiskFeignClientFallbackFactory implements FallbackFactory<RiskFeignClient> {

    @Override
    public RiskFeignClient create(Throwable cause) {
        log.error("风控服务调用失败: {}", cause.getMessage());
        return new RiskFeignClient() {
            @Override
            public ApiResponse<Object> addToBlacklist(String targetType, String targetValue,
                                                       String reason, LocalDateTime expiredAt) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> removeFromBlacklist(String targetType, String targetValue) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Boolean> checkBlacklist(String targetType, String targetValue) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listBlacklist(Map<String, Object> params) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listRiskRecords(Map<String, Object> params) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getRiskRecord(Long id) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listRiskRules(Map<String, Object> params) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createRiskRule(Map<String, Object> body) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateRiskRule(Long id, Map<String, Object> body) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteRiskRule(Long id) {
                throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务不可用，请稍后重试");
            }
        };
    }
}
