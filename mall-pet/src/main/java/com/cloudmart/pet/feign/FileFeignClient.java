package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.config.FileServiceTokenConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-file 内部能力客户端（R04）：相册文件引用的绑定/解绑与业务授权预览地址。
 *
 * <p>契约（§9.2）：fileId 是不透明 ID；归属/READY/MIME/大小/可见性由文件服务
 * 以台账事实裁决，宠物服务不做本地正则放行。跨服务无分布式事务——绑定/解绑
 * 以幂等引用键 PET_ALBUM:{albumAssetId} 收敛，重试安全。</p>
 */
@FeignClient(name = "mall-file", contextId = "petFileFeignClient",
        configuration = FileServiceTokenConfig.class)
public interface FileFeignClient {

    /** 校验并绑定业务引用（幂等；alreadyBound=true 表示引用键已存在） */
    @PostMapping("/internal/assets/{id}/references")
    ApiResponse<Map<String, Object>> bindReference(
            @PathVariable("id") Long id,
            @RequestBody BindReferenceRequest request);

    /** 解绑业务引用（幂等；删除相册后释放文件引用） */
    @DeleteMapping("/internal/assets/{id}/references/{bizType}/{bizId}")
    ApiResponse<Void> unbindReference(
            @PathVariable("id") Long id,
            @PathVariable("bizType") String bizType,
            @PathVariable("bizId") String bizId);

    /** 业务授权短期下载地址（调用方完成权限裁决后调用；TTL 默认 60 秒） */
    @PostMapping("/internal/assets/{id}/download-url")
    ApiResponse<Map<String, Object>> internalDownloadUrl(
            @PathVariable("id") Long id,
            @RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds);

    /** 绑定请求（约束由宠物业务声明、文件服务按台账裁决） */
    record BindReferenceRequest(
            String bizType,
            String bizId,
            Long ownerUserId,
            List<String> allowedMimes,
            Long maxSizeBytes,
            String requireVisibility) {
    }
}
