package com.cloudmart.software.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 素材引用解绑补偿（方案 8.2）：本地保存回滚后清理预绑定引用（幂等，引用不存在亦成功）。
 * 载荷：{"assetId":x,"bizType":"SOFTWARE_REVISION","bizId":"y"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareRefUnbindCompensationHandler implements CompensationHandler {

    public static final String ACTION = "software-ref-unbind";

    private final SoftwareFileFeignClient fileFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public void execute(String aggregateId, String payload) {
        JsonNode node = objectMapper.readTree(payload);
        fileFeignClient.unbindReference(node.get("assetId").asLong(),
                node.get("bizType").asString(), node.get("bizId").asString());
        log.info("素材引用解绑补偿完成 assetId={} bizId={}",
                node.get("assetId").asLong(), node.get("bizId").asString());
    }
}
