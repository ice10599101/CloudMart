package com.cloudmart.payment.feign;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预占对账客户端降级（T11）：fail-closed——台账查不到时该批跳过不误报
 * （差异必须两侧可核验才成立），调用方按空批处理顺延。
 */
@Slf4j
@Component
public class InventoryReconFeignClientFallbackFactory implements FallbackFactory<InventoryReconFeignClient> {

    @Override
    public InventoryReconFeignClient create(Throwable cause) {
        log.warn("[T11] 库存预占台账查询不可用: {}", cause.getMessage());
        return (since, lastId, limit) -> {
            throw FeignBusinessErrors.parse(cause, "INVENTORY_RECON_UNAVAILABLE", "库存预占台账暂不可用");
        };
    }
}
