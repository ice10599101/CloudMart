package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.dto.software.SoftwareAssetBindRequest;
import com.cloudmart.software.dto.software.SoftwareAssetBindingResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件素材引用客户端（方案 8.2）：出站令牌 scope=file:internal；
 * 绑定幂等（同引用键重复绑定成功），解绑幂等（引用不存在亦成功）。
 */
@FeignClient(name = "mall-file", contextId = "softwareFileFeignClient",
        fallbackFactory = SoftwareFileFeignClientFallbackFactory.class)
public interface SoftwareFileFeignClient {

    @PostMapping("/internal/assets/{id}/references")
    ApiResponse<SoftwareAssetBindingResult> bindReference(
            @PathVariable("id") Long assetId,
            @RequestBody SoftwareAssetBindRequest request);

    @DeleteMapping("/internal/assets/{id}/references/{bizType}/{bizId}")
    ApiResponse<Void> unbindReference(
            @PathVariable("id") Long assetId,
            @PathVariable("bizType") String bizType,
            @PathVariable("bizId") String bizId);
}
