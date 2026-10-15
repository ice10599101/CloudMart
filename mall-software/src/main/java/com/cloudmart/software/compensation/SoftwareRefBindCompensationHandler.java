package com.cloudmart.software.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 素材引用重绑补偿（方案 8.2/9.7.3）：本地保存失败/clone 后绑定缺口的持久化重试。
 * 载荷：{"assetId":x,"bizType":"SOFTWARE_REVISION","bizId":"y","ownerUserId":z}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareRefBindCompensationHandler implements CompensationHandler {

    public static final String ACTION = "software-ref-bind";

    private final SoftwareFileFeignClient fileFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public void execute(String aggregateId, String payload) {
        JsonNode node = objectMapper.readTree(payload);
        Long assetId = node.get("assetId").asLong();
        String bizType = node.get("bizType").asString();
        String bizId = node.get("bizId").asString();
        Long ownerUserId = node.get("ownerUserId").asLong();
        var response = fileFeignClient.bindReference(assetId,
                new com.cloudmart.software.dto.software.SoftwareAssetBindRequest(
                        bizType, bizId, ownerUserId, null, null, null));
        log.info("素材引用重绑补偿完成 assetId={} bizId={} alreadyBound={}",
                assetId, bizId, response.data() != null && response.data().alreadyBound());
    }
}
