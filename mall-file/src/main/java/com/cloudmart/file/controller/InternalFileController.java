package com.cloudmart.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.entity.FileReference;
import com.cloudmart.file.repository.FileReferenceMapper;
import com.cloudmart.file.service.FileAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * R04 文件业务引用绑定（内部服务间接口，服务令牌 scope=file:internal）：
 *
 * <p>消费方（如 mall-pet 相册）在本地登记引用前，先经本控制器校验并绑定：
 * 本人归属、READY 状态、真实 MIME 白名单、大小上限、可见性（相册要求 PRIVATE）——
 * 全部由文件服务以台账事实裁决，调用方不得凭 URL/ID 自行放行。</p>
 *
 * <p>幂等：引用键 uk(asset_id,biz_type,biz_id)——同键重复绑定返回既有事实（幂等成功），
 * 跨事务补偿重试安全；解绑幂等（无引用也返回成功）。</p>
 */
@RestController
@RequestMapping("/internal/assets")
@Tag(name = "文件内部接口", description = "服务间引用绑定/解绑与业务授权预览（R04）")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('INTERNAL')")
public class InternalFileController {

    private final FileAssetService fileAssetService;
    private final FileReferenceMapper fileReferenceMapper;

    /** 绑定请求：约束由调用方声明、文件服务按台账裁决 */
    public record BindReferenceRequest(
            String bizType,
            String bizId,
            Long ownerUserId,
            List<String> allowedMimes,
            Long maxSizeBytes,
            String requireVisibility) {
    }

    /** 绑定结果（资产事实摘要，供调用方留痕） */
    public record AssetBindingResult(
            String assetId,
            Long ownerId,
            String mime,
            Long sizeBytes,
            String visibility,
            String status,
            boolean alreadyBound) {
    }

    @PostMapping("/{id}/references")
    @Operation(summary = "校验并绑定业务引用", description = "校验归属/READY/MIME白名单/大小/可见性后登记引用；"
            + "同引用键重复绑定幂等成功；失败返回明确错误码，不产生半绑定")
    public ApiResponse<AssetBindingResult> bindReference(
            @PathVariable("id") Long id,
            @RequestBody BindReferenceRequest request) {
        validateRequest(request);
        FileAsset asset = fileAssetService.requireAsset(id);
        if (!"READY".equals(asset.getStatus())) {
            throw new BusinessException("FILE_NOT_READY", "文件未处于可用状态: " + asset.getStatus());
        }
        if (request.ownerUserId() == null || asset.getOwnerId() == null
                || !asset.getOwnerId().equals(request.ownerUserId())) {
            throw new BusinessException("FILE_FORBIDDEN", "文件归属与绑定请求不符");
        }
        if (request.requireVisibility() != null && !request.requireVisibility().equals(asset.getVisibility())) {
            throw new BusinessException("FILE_VISIBILITY_MISMATCH",
                    "文件可见性不符合业务要求: " + asset.getVisibility());
        }
        if (request.allowedMimes() != null && !request.allowedMimes().isEmpty()
                && !request.allowedMimes().contains(asset.getMime())) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "文件类型不符合业务要求: " + asset.getMime());
        }
        if (request.maxSizeBytes() != null && asset.getSizeBytes() != null
                && asset.getSizeBytes() > request.maxSizeBytes()) {
            throw new BusinessException("FILE_TOO_LARGE", "文件超过业务允许的大小上限");
        }
        boolean alreadyBound;
        try {
            FileReference reference = new FileReference();
            reference.setAssetId(id);
            reference.setBizType(request.bizType());
            reference.setBizId(request.bizId());
            fileReferenceMapper.insert(reference);
            alreadyBound = false;
        } catch (DuplicateKeyException e) {
            // 同引用键已绑定（补偿重试/重复回调）：幂等成功，不产生第二行
            alreadyBound = true;
        }
        log.info("文件引用绑定: assetId={}, bizType={}, bizId={}, alreadyBound={}",
                id, request.bizType(), request.bizId(), alreadyBound);
        return ApiResponse.ok(new AssetBindingResult(String.valueOf(asset.getId()), asset.getOwnerId(),
                asset.getMime(), asset.getSizeBytes(), asset.getVisibility(), asset.getStatus(), alreadyBound));
    }

    @DeleteMapping("/{id}/references/{bizType}/{bizId}")
    @Operation(summary = "解绑业务引用", description = "幂等：引用不存在也返回成功；解绑后该文件方可按归属授权删除")
    public ApiResponse<Void> unbindReference(
            @PathVariable("id") Long id,
            @PathVariable("bizType") String bizType,
            @PathVariable("bizId") String bizId) {
        fileReferenceMapper.delete(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getAssetId, id)
                .eq(FileReference::getBizType, bizType)
                .eq(FileReference::getBizId, bizId));
        log.info("文件引用解绑: assetId={}, bizType={}, bizId={}", id, bizType, bizId);
        return ApiResponse.ok(null);
    }

    /** 业务授权预览 TTL（秒）：R04 默认 60，短于用户自取的 600s */
    @org.springframework.beans.factory.annotation.Value("${file.internal-preview-ttl-seconds:60}")
    private long internalPreviewTtlSeconds;

    @PostMapping("/{id}/download-url")
    @Operation(summary = "业务授权短期下载地址", description = "调用方完成业务权限裁决后，为本人资产签发短 TTL 授权地址；"
            + "不校验请求者身份（身份裁决在调用方业务内），但要求资产存在且未删除")
    public ApiResponse<Map<String, Object>> internalDownloadUrl(
            @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds) {
        long ttl = ttlSeconds != null && ttlSeconds > 0 && ttlSeconds <= 600
                ? Math.min(ttlSeconds, internalPreviewTtlSeconds > 0 ? internalPreviewTtlSeconds : 60) : 60;
        FileAsset asset = fileAssetService.requireAsset(id);
        String path = fileAssetService.buildInternalDownloadPath(asset.getId(),
                java.time.Instant.now(), ttl);
        return ApiResponse.ok(Map.of("downloadPath", path, "expiresInSeconds", ttl));
    }

    private void validateRequest(BindReferenceRequest request) {
        if (request == null || request.bizType() == null || request.bizType().isBlank()
                || request.bizId() == null || request.bizId().isBlank()) {
            throw new BusinessException("FILE_REFERENCE_INVALID", "引用键不完整（bizType/bizId 必填）");
        }
        if (!request.bizType().matches("^[A-Z0-9_]{2,50}$")) {
            throw new BusinessException("FILE_REFERENCE_INVALID", "bizType 非法");
        }
        if (request.bizId().length() > 64) {
            throw new BusinessException("FILE_REFERENCE_INVALID", "bizId 超长");
        }
    }
}
