package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.AdminFileAssetPage;
import com.cloudmart.admin.dto.AdminFileAssetRow;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** 文件服务降级：连接/超时类故障统一转为明确的服务不可用，不伪装成功。 */
@Component
@Slf4j
public class FileFeignClientFallbackFactory implements FallbackFactory<FileFeignClient> {

    @Override
    public FileFeignClient create(Throwable cause) {
        log.error("文件服务调用失败: {}", cause.getMessage());
        return new FileFeignClient() {
            @Override
            public ApiResponse<AdminFileAssetPage> pageAdminAssets(int page, int size, String status, String purpose) {
                throw FeignBusinessErrors.parse(cause, "FILE_SERVICE_UNAVAILABLE", "文件服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<AdminFileAssetRow> adminAsset(Long id) {
                throw FeignBusinessErrors.parse(cause, "FILE_SERVICE_UNAVAILABLE", "文件服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteAdminAsset(Long id) {
                throw FeignBusinessErrors.parse(cause, "FILE_SERVICE_UNAVAILABLE", "文件服务不可用，请稍后重试");
            }
        };
    }
}
