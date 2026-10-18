package com.cloudmart.software.compensation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 素材引用解绑补偿（方案 8.2 / SW08）：解绑前**重新核对当前有效引用**——
 * 资产仍被该快照的媒体行或图标引用时跳过，保证旧操作的过期意图
 * 不会撤销后续成功保存的绑定；引用不存在亦成功（幂等）。
 * 载荷：{"assetId":x,"bizType":"SOFTWARE_REVISION","bizId":"y","operationId":"...","kind":"..."}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareRefUnbindCompensationHandler implements CompensationHandler {

    public static final String ACTION = "software-ref-unbind";

    private final SoftwareFileFeignClient fileFeignClient;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public void execute(String aggregateId, String payload) {
        JsonNode node = objectMapper.readTree(payload);
        long assetId = node.get("assetId").asLong();
        String bizType = node.get("bizType").asString();
        String bizId = node.get("bizId").asString();
        if ("SOFTWARE_REVISION".equals(bizType) && stillReferenced(Long.valueOf(bizId), assetId)) {
            log.info("素材仍被当前内容引用，跳过解绑 assetId={} revisionId={} operationId={} kind={}",
                    assetId, bizId,
                    node.hasNonNull("operationId") ? node.get("operationId").asString() : "-",
                    node.hasNonNull("kind") ? node.get("kind").asString() : "-");
            return;
        }
        fileFeignClient.unbindReference(assetId, bizType, bizId);
        log.info("素材引用解绑补偿完成 assetId={} bizId={}", assetId, bizId);
    }

    /** 当前有效引用 = 媒体行 + 图标（与冻结提交/发布展示同口径） */
    private boolean stillReferenced(Long revisionId, long assetId) {
        Long mediaCount = revisionMediaMapper.selectCount(
                new LambdaQueryWrapper<SoftwareRevisionMedia>()
                        .eq(SoftwareRevisionMedia::getRevisionId, revisionId)
                        .eq(SoftwareRevisionMedia::getAssetId, assetId));
        if (mediaCount != null && mediaCount > 0) {
            return true;
        }
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        return revision != null && revision.getIconAssetId() != null
                && revision.getIconAssetId() == assetId;
    }
}
