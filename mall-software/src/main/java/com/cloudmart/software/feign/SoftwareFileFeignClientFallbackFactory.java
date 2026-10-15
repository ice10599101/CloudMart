package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareAssetBindRequest;
import com.cloudmart.software.dto.software.SoftwareAssetBindingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 文件引用降级工厂：绑定失败不得当成功（预检失败保留草稿重试），
 * 解绑失败留在补偿任务重试，绝不伪造"已解绑"。
 */
@Slf4j
@Component
public class SoftwareFileFeignClientFallbackFactory
        implements FallbackFactory<SoftwareFileFeignClient> {

    @Override
    public SoftwareFileFeignClient create(Throwable cause) {
        log.warn("mall-file 引用接口不可用: {}", cause.getMessage());
        return new SoftwareFileFeignClient() {
            @Override
            public ApiResponse<SoftwareAssetBindingResult> bindReference(
                    Long assetId, SoftwareAssetBindRequest request) {
                throw new BusinessException("FILE_SERVICE_UNAVAILABLE",
                        "素材服务暂不可用，请稍后重试", cause);
            }

            @Override
            public ApiResponse<Void> unbindReference(Long assetId, String bizType, String bizId) {
                throw new BusinessException("FILE_SERVICE_UNAVAILABLE",
                        "素材服务暂不可用，解绑将自动重试", cause);
            }
        };
    }
}
