package com.cloudmart.software.service.impl;

import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.service.SoftwareAssetBindingService;
import com.cloudmart.common.async.compensation.CompensationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 素材引用绑定实现（方案 8.2）：
 * <ul>
 *   <li>绑定：按资产逐个幂等调用文件内部 API（重复绑定 alreadyBound 幂等成功）；
 *       任一失败即整体失败——保留草稿重试，禁止把预检失败当成功（A21）；</li>
 *   <li>补偿：SOFTWARE_REF_BIND（重绑，自愈缺口）/SOFTWARE_REF_UNBIND（本地回滚
 *       后解绑），task_id 幂等（software-ref-bind:{revisionId}:{assetId}）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareAssetBindingServiceImpl implements SoftwareAssetBindingService {

    public static final String ACTION_BIND = "software-ref-bind";
    public static final String ACTION_UNBIND = "software-ref-unbind";
    private static final String BIZ_TYPE = "SOFTWARE_REVISION";

    private final SoftwareFileFeignClient fileFeignClient;
    private final CompensationTaskService compensationTaskService;

    @Override
    public void bindRevisionAssets(Long revisionId, Long ownerUserId, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        for (Long assetId : assetIds.stream().distinct().toList()) {
            var response = fileFeignClient.bindReference(assetId,
                    com.cloudmart.software.dto.software.SoftwareAssetBindRequest.revision(
                            revisionId, ownerUserId));
            // fallback 抛 FILE_SERVICE_UNAVAILABLE；业务失败（归属不符/类型不符）同样视为失败，
            // 草稿保留可重试——绝不在绑定未成功时写 media 行（无绑定不得进入待审）
            if (response.data() == null) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "FILE_SERVICE_UNAVAILABLE", "素材绑定失败，请稍后重试");
            }
        }
        log.debug("素材引用绑定完成 revisionId={} assets={}", revisionId, assetIds.size());
    }

    @Override
    public void registerUnbindCompensation(Long revisionId, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        for (Long assetId : assetIds.stream().distinct().toList()) {
            compensationTaskService.createIfAbsent(
                    "software-ref-unbind:" + revisionId + ":" + assetId,
                    ACTION_UNBIND, String.valueOf(revisionId),
                    toJson(Map.of("assetId", assetId, "bizType", BIZ_TYPE,
                            "bizId", String.valueOf(revisionId))));
        }
    }

    /** 本地保存失败后的重绑补偿（自愈 submit 前缺口；幂等同引用键） */
    public void registerBindCompensation(Long revisionId, Long ownerUserId, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        for (Long assetId : assetIds.stream().distinct().toList()) {
            compensationTaskService.createIfAbsent(
                    "software-ref-bind:" + revisionId + ":" + assetId,
                    ACTION_BIND, String.valueOf(revisionId),
                    toJson(Map.of("assetId", assetId, "bizType", BIZ_TYPE,
                            "bizId", String.valueOf(revisionId), "ownerUserId", ownerUserId)));
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return new tools.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("补偿载荷序列化失败", e);
        }
    }
}
