package com.cloudmart.software.dto.software;

/**
 * 文件素材绑定结果（mall-file 契约镜像）：资产事实摘要，供快照留痕与补偿核对。
 */
public record SoftwareAssetBindingResult(
        String assetId,
        Long ownerId,
        String mime,
        Long sizeBytes,
        String visibility,
        String status,
        boolean alreadyBound) {
}
